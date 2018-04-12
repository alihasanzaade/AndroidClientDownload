package net.ericsson.emovs.download;

import android.text.TextUtils;
import android.util.Log;


import net.ericsson.emovs.download.models.Manifest;
import net.ericsson.emovs.download.models.adaptation.AdaptationSet;
import net.ericsson.emovs.download.models.adaptation.AudioAdaptationSet;
import net.ericsson.emovs.download.models.adaptation.TextAdaptationSet;
import net.ericsson.emovs.download.models.adaptation.VideoAdaptationSet;
import net.ericsson.emovs.download.models.tracks.AudioTrack;
import net.ericsson.emovs.download.models.tracks.TextTrack;
import net.ericsson.emovs.download.models.tracks.Track;
import net.ericsson.emovs.download.models.tracks.VideoTrack;
import net.ericsson.emovs.utilities.entitlements.Entitlement;
import net.ericsson.emovs.utilities.errors.ErrorCodes;
import net.ericsson.emovs.utilities.system.RunnableThread;

import net.ericsson.emovs.download.interfaces.IDownloadEventListener;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder; 
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class DashDownloader extends Thread {
	final int MAX_CONCURRENT_DOWNLOADS = 2;
	final int MAX_DOWNLOAD_ATTEMPTS = 5;
	final int MAX_SEGMENT_DOWNLOAD_TIMEOUT = 20000;
	final int MAX_HTTP_CONNECTION_TIMEOUT = 1000;

	final String ADAPTATION_SET = "//urn:mpeg:dash:schema:mpd:2011:AdaptationSet";
	final String MPD = "//urn:mpeg:dash:schema:mpd:2011:MPD";
	final String REPRESENTATION = "Representation";
	final String SEGMENT_TEMPLATE = "SegmentTemplate";
	final String SEGMENT_TIMELINE = "SegmentTimeline";
	final String MEDIA = "media";
	final String BASE_URL = "BaseURL";
	final String INITIALIZATION = "initialization";
	final String DURATION = "duration";
	final String TIMESCALE = "timescale";
	final String ID = "id";

    protected Manifest remoteManifest;
	protected Configuration conf;

	protected HashMap<String, String> chunkMemory;
	protected HashMap<String, Long> currentIndexMap;
	protected ArrayList<AsyncFileWriter> pendingWriters;
	protected HashMap<String, IDownloadEventListener> stateUpdaters;
	protected DownloadItem parent;

	protected RunnableThread sizeEstimator;

	protected int errorCode;
	protected String errorMessage;
    
	public DashDownloader (DownloadItem parent) {
		this.chunkMemory = new HashMap<>();
		this.currentIndexMap = new HashMap<>();
		this.pendingWriters = new ArrayList<>();
		this.stateUpdaters = new HashMap<>();
        this.parent = parent;
    }

	public DashDownloader(DashDownloader other) {
		this.chunkMemory = new HashMap<>();
		this.currentIndexMap = new HashMap<>();
		this.pendingWriters = new ArrayList<>();
		this.stateUpdaters = other.stateUpdaters;
		this.parent = other.parent;
	}

	public void init(String manifestUrl, String destFolder, DownloadProperties properties) {
		this.conf = new Configuration(manifestUrl, destFolder, properties);
	}

	public void setCallback(String key, IDownloadEventListener callback) {
		this.stateUpdaters.put(key, callback);
	}

	public void unsetCallback(String key) {
		this.stateUpdaters.remove(key);
	}

	public void notifyUpdatersError(int errorCode, String message) {
		this.errorCode = errorCode;
		this.errorMessage = message;

		if (this.parent != null) {
			this.parent.setState(DownloadItem.State.FAILED);
		}
		for(IDownloadEventListener callback : this.stateUpdaters.values()) {
			callback.onError(errorCode, message);
		}
	}

	private void notifyUpdatersSuccess() {
        String localMpdPath = conf.folder + "/manifest_local.mpd";
        if (this.parent != null) {
            this.parent.onDownloadSuccess(localMpdPath);
        }
        for (IDownloadEventListener callback : this.stateUpdaters.values()) {
			callback.onSuccess();
		}
	}

	private void notifyUpdatersProgress(double progress) {
        if (this.parent != null) {
            this.parent.setProgress(progress);
        }
		for(IDownloadEventListener callback : this.stateUpdaters.values()) {
			callback.onProgressUpdate(progress);
		}
		if(this.sizeEstimator == null || this.sizeEstimator.isAlive() == false) {
			this.sizeEstimator = new RunnableThread(new Runnable() {
				@Override
				public void run() {
					parent.updateDownloadedSize();
				}
			});
			this.sizeEstimator.start();
		}
	}

	private void notifyUpdatersStop() {
		for (IDownloadEventListener callback : this.stateUpdaters.values()) {
			callback.onStop();
		}
	}

	private void notifyUpdatersStart() {
		for (IDownloadEventListener callback : this.stateUpdaters.values()) {
			callback.onStart();
		}
	}

    public void notifyUpdatersPause() {
        for (IDownloadEventListener callback : this.stateUpdaters.values()) {
            callback.onPause();
        }
    }

    public void notifyUpdatersResume() {
        for (IDownloadEventListener callback : this.stateUpdaters.values()) {
            callback.onResume();
        }
    }

	public void notifyEntitlement(Entitlement entitlement) {
		for (IDownloadEventListener callback : this.stateUpdaters.values()) {
			callback.onEntitlement(entitlement);
		}
	}

	@Override
	public void run() {
		try {
			errorCode = 0;
			errorMessage = null;
            notifyUpdatersStart();
			download();
		}
        catch(InterruptedException e) {
			notifyUpdatersStop();
            e.printStackTrace();
        }
		catch(Exception e) {
			e.printStackTrace();
			notifyUpdatersStop();
			notifyUpdatersError(ErrorCodes.DOWNLOAD_RUNTIME_ERROR, e.getMessage());
		}
	}

	public int getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

    public void download() throws Exception {
		downloadManifest();

		if (this.remoteManifest.adaptationSets.size() == 0) {
            dispose();
			return;
		}

		if (downloadStreamInit() == false) {
			return;
		}
        
		while (isEndOfStream() == false) {
			if (this.parent.getState() == DownloadItem.State.PAUSED) {
                Thread.sleep(100);
                continue;
            }
			boolean ret = downloadSegments ();
			if(ret == false) {
				dispose();
				return;
			}
        }

		//waitForWriters();

		notifyUpdatersProgress(100.0);
		notifyUpdatersSuccess();
    }

    public void waitForWriters() throws InterruptedException {
    	while(isEmptyWriterPool() == false) {
    		Thread.sleep(1);
    	}
    }
    
    public void dispose() {
    	for (AsyncFileWriter writer : pendingWriters) {
    		writer.interrupt();
    	}
		if (this.sizeEstimator != null && this.sizeEstimator.isAlive() == true) {
			this.sizeEstimator.interrupt();
		}
    }
    
    public synchronized void addWriter(AsyncFileWriter writer) {
    	pendingWriters.add(writer);
    }
    
    public synchronized void pushWriter(AsyncFileWriter writer) {
    	pendingWriters.add(0, writer);
    }
    
    public boolean isEmptyWriterPool() {
    	if(pendingWriters.size() == 0) {
    		return true;
    	}
    	
    	return false;
    }
    
    public int writerCount() {
    	return pendingWriters.size();
    }
    
    public long getIndex(AdaptationSet set, String hash) {
    	if(currentIndexMap.containsKey(hash)) {
    		return currentIndexMap.get(hash);	
    	}
    	currentIndexMap.put(hash, set.startNumber);
    	return set.startNumber;
    }
    
    public void incIndex(String hash, long val) {
    	long newV = val;
    	if(currentIndexMap.containsKey(hash)) {
    		newV = currentIndexMap.get(hash);
    		newV += val;
    		currentIndexMap.remove(hash);
    	}
    	
    	currentIndexMap.put(hash, newV);
    }

    public boolean hasManifest () {
		try {
			URL u = new URL(conf.manifestUrl); 
			HttpURLConnection huc = (HttpURLConnection) u.openConnection(); 
			huc.setRequestMethod("GET"); 
			huc.connect() ; 
			int code = huc.getResponseCode();
			if (code == 200) {
				return true;
			}
		} 
		catch (Exception e) {
			return false;
		}
		
        return false;
    }
    
    public boolean isEndOfStream() throws Exception {
    	for (Boolean eosState : conf.eos.values()) {
    		if (eosState == false) {
    			return false;
    		}
    	}
    	return true;
    }

	public static long getDuration(String value) {
		Matcher matcher = Pattern.compile("^(-)?P(([0-9]*)Y)?(([0-9]*)M)?(([0-9]*)D)?" + "(T(([0-9]*)H)?(([0-9]*)M)?(([0-9.]*)S)?)?$").matcher(value);
		if (matcher.matches()) {
			boolean negated = !TextUtils.isEmpty(matcher.group(1));
			String years = matcher.group(3);
			double durationSeconds = (years != null) ? Double.parseDouble(years) * 31556908 : 0;
			String months = matcher.group(5);
			durationSeconds += (months != null) ? Double.parseDouble(months) * 2629739 : 0;
			String days = matcher.group(7);
			durationSeconds += (days != null) ? Double.parseDouble(days) * 86400 : 0;
			String hours = matcher.group(10);
			durationSeconds += (hours != null) ? Double.parseDouble(hours) * 3600 : 0;
			String minutes = matcher.group(12);
			durationSeconds += (minutes != null) ? Double.parseDouble(minutes) * 60 : 0;
			String seconds = matcher.group(14);
			durationSeconds += (seconds != null) ? Double.parseDouble(seconds) : 0;
			long durationMillis = (long) (durationSeconds * 1000);
			return negated ? -durationMillis : durationMillis;
		} else {
			return (long) (Double.parseDouble(value) * 3600 * 1000);
		}
	}
    
    private boolean loadManifest(URL u, File manifestFile){
		for(int i = 0; i < MAX_DOWNLOAD_ATTEMPTS; ++i) {
			try {
				FileUtils.copyURLToFile(u, manifestFile, MAX_HTTP_CONNECTION_TIMEOUT, MAX_HTTP_CONNECTION_TIMEOUT);
				return true;
			}
			catch (Exception e) {
				e.printStackTrace();
				try {
					Thread.sleep(10);
				} catch (Exception e1) {}
			}
		}
		
		return false;
    }

	public void downloadManifest() throws Exception {
		this.remoteManifest = new Manifest();
		conf.reset();

		URL u = new URL(conf.manifestUrl);
		File manifestFile = new File(conf.folder + "/manifest.mpd");

		boolean result = loadManifest(u, manifestFile);
		if(result == false) {
			System.err.println("StreamHandler: error downloading manifest.");
			notifyUpdatersError(ErrorCodes.DOWNLOAD_MANIFEST_FAILED, "Error downloading manifest.");
			return;
		}

		if (manifestFile.exists ()) {
			String manifestContent = FileUtils.readFileToString(manifestFile, "UTF-8");

			DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
			domFactory.setNamespaceAware(true);
			DocumentBuilder builder = domFactory.newDocumentBuilder();
			Document doc = builder.parse(new ByteArrayInputStream(manifestContent.getBytes()));
			XPath xpath = XPathFactory.newInstance().newXPath();

			Node mpdRoot = (Node) xpath.compile(MPD).evaluate(doc, XPathConstants.NODE);

			NamedNodeMap mpdAttrs = mpdRoot.getAttributes();
			Node mediaPresentationDurationNode = mpdAttrs.getNamedItem("mediaPresentationDuration");
			String mediaPresentationDuration = mediaPresentationDurationNode.getNodeValue();

			// TODO: refactor this to use milliseconds
            remoteManifest.durationSeconds = Math.ceil(getDuration(mediaPresentationDuration) / 1000.0);

			System.out.println("Stream Duration: " + remoteManifest.durationSeconds);

			NodeList adaptationSets = (NodeList) xpath.compile(ADAPTATION_SET).evaluate(doc, XPathConstants.NODESET);

			for (int i = 0; i < adaptationSets.getLength(); i++) {
				Node set = adaptationSets.item(i);
				AdaptationSet remoteAdaptationSet = new AdaptationSet();

				Node period = set.getParentNode();
				NodeList baseUrlCands = period.getChildNodes();
				String baseUrl = null;
				if (baseUrl == null) {
					for (int k = 0; k < baseUrlCands.getLength(); ++k) {
						Node baseUrlCand = baseUrlCands.item(k);
						if("BaseURL".equals(baseUrlCand.getNodeName())) {
							if (baseUrlCand.getTextContent().startsWith("http") || baseUrlCand.getTextContent().startsWith("//")) {
								baseUrl = baseUrlCand.getTextContent();
								// TODO: rename path for local base url
							}
							else {
								File mpdFile = new File(u.getPath());
								String parentPath = mpdFile.getParent();
								URL parentUrl = new URL(u.getProtocol( ), u.getHost( ), u.getPort( ), parentPath);
								baseUrl = parentUrl.toString() + "/" + baseUrlCand.getTextContent();
								baseUrlCand.setTextContent(conf.folder + "/" + baseUrlCand.getTextContent());
							}
						}
					}
				}

				String mimeType = set.getAttributes().getNamedItem("mimeType") != null ? set.getAttributes().getNamedItem("mimeType").getNodeValue() : null;
				remoteAdaptationSet.lang = set.getAttributes().getNamedItem("lang") != null ? set.getAttributes().getNamedItem("lang").getNodeValue() : null;
                remoteAdaptationSet.id = set.getAttributes().getNamedItem("id").getNodeValue();

				if(set == null) continue;

				NodeList setChildren = set.getChildNodes();
                ArrayList<Track> remoteTracks = new ArrayList<>();
                HashMap<String, Boolean> adaptationSetEos = new HashMap<>();

				for (int k = 0; k < setChildren.getLength(); k++) {
					Node setChild = setChildren.item(k);
					if (setChild == null) {
						continue;
					}
					if (setChild.getNodeName().equals(SEGMENT_TEMPLATE)) {
						remoteAdaptationSet.timescale = Long.parseLong(setChild.getAttributes().getNamedItem(TIMESCALE).getNodeValue());
						remoteAdaptationSet.initSegment = setChild.getAttributes().getNamedItem(INITIALIZATION) != null ? setChild.getAttributes().getNamedItem(INITIALIZATION).getNodeValue() : null;
						remoteAdaptationSet.segmentTemplate = setChild.getAttributes().getNamedItem(MEDIA).getNodeValue();

						boolean timlineFound = false;
						NodeList templateChildren = setChild.getChildNodes();
						for (int l = 0; l < templateChildren.getLength(); l++) {
							Node timeline = templateChildren.item(l);
							if (SEGMENT_TIMELINE.equals(timeline.getNodeName())) {
								timlineFound = true;
								NodeList SChildren = timeline.getChildNodes();
								for (int m = 0; m < SChildren.getLength(); m++) {
									Node S = SChildren.item(m);
									if ("S".equals(S.getNodeName()) && S.getAttributes().getNamedItem("t") != null) {
										remoteAdaptationSet.increment = Long.parseLong(S.getAttributes().getNamedItem("d").getNodeValue());
										remoteAdaptationSet.startNumber = Long.parseLong(S.getAttributes().getNamedItem("t").getNodeValue());
										remoteAdaptationSet.segmentDuration = remoteAdaptationSet.increment;
									}
								}
							}
						}

						if (!timlineFound) {
							remoteAdaptationSet.increment = 1;
							remoteAdaptationSet.segmentDuration = Long.parseLong(setChild.getAttributes().getNamedItem(DURATION).getNodeValue());
							remoteAdaptationSet.startNumber = Long.parseLong(setChild.getAttributes().getNamedItem("startNumber").getNodeValue());
						}

                        remoteAdaptationSet.segmentDurationSeconds = ((double) remoteAdaptationSet.segmentDuration) / remoteAdaptationSet.timescale;
                        remoteAdaptationSet.segmentCount = (long) Math.ceil(remoteManifest.durationSeconds / remoteAdaptationSet.segmentDurationSeconds);
					}
					else if (setChild.getNodeName().equals(REPRESENTATION)) {
                        String trackMimeType = setChild.getAttributes().getNamedItem("mimeType") != null ? setChild.getAttributes().getNamedItem("mimeType").getNodeValue() : null;
                        if (trackMimeType != null) {
                            mimeType = trackMimeType;
                        }

                        Track remoteTrack = null;

                        if (mimeType.contains("video")) {
                            remoteTrack = new VideoTrack();
                        }
                        else if (mimeType.contains("audio")) {
                            remoteTrack = new AudioTrack();
                        }
                        else if (mimeType.contains("text")) {
                            remoteTrack = new TextTrack();
                        }

                        if (remoteTrack == null) {
                            continue;
                        }

                        remoteTrack.id = setChild.getAttributes().getNamedItem("id").getNodeValue();

                        // trik mode is a trick used for fast forward and rewind - not supported for download yet
                        if (remoteTrack.id.contains("mode=trik")) {
                        	continue;
						}

                        remoteTrack.mimeType = trackMimeType;
                        remoteTrack.bandwidth = Long.parseLong(setChild.getAttributes().getNamedItem("bandwidth").getNodeValue());

						if (remoteTrack instanceof  AudioTrack && (remoteTrack.bandwidth < this.conf.properties.getMinAudioBitrate() || remoteTrack.bandwidth > this.conf.properties.getMaxAudioBitrate())) {
							set.removeChild(setChild);
							continue;
						}
                        else if (remoteTrack instanceof VideoTrack && (remoteTrack.bandwidth < this.conf.properties.getMinVideoBitrate() || remoteTrack.bandwidth > this.conf.properties.getMaxVideoBitrate())) {
                            set.removeChild(setChild);
                            continue;
                        }

                        adaptationSetEos.put(remoteAdaptationSet.id + ":" + remoteTrack.id, false);

						NodeList repChildren = setChild.getChildNodes();
						for (int j=0; j < repChildren.getLength(); ++j) {
							Node repsentation = repChildren.item(j);
							if (repsentation.getNodeName().equals(BASE_URL)) {
								String baseUrlInner = repsentation.getTextContent();
                                remoteTrack.baseUrl = baseUrlInner;

								URL mediaGrab = new URL(baseUrlInner);
								String host = mediaGrab.getHost();
								String protocol = mediaGrab.getProtocol();
								System.out.println("EMP HOST+PROTOCOL: " + protocol+"://"+host);
								repsentation.setTextContent(baseUrlInner.replace(protocol+"://"+host+"", conf.folder));
							}
						}

						if (remoteTrack.baseUrl == null) {
							remoteTrack.baseUrl = baseUrl;
						}

                        remoteTracks.add(remoteTrack);
					}
				}

                remoteAdaptationSet.tracks = remoteTracks;

				if (mimeType != null) {
					if (mimeType.contains("video")) {
						remoteAdaptationSet = new VideoAdaptationSet(remoteAdaptationSet);
					}
					else if (mimeType.contains("audio")) {
						if (this.conf.properties.hasAudioLanguage (remoteAdaptationSet.lang) == false) {
							set.getParentNode().removeChild(set);
							continue;
						}
						remoteAdaptationSet = new AudioAdaptationSet(remoteAdaptationSet);
					}
					else if (mimeType.contains("text")) {
						if (this.conf.properties.hasTextLanguage (remoteAdaptationSet.lang) == false) {
							set.getParentNode().removeChild(set);
							continue;
						}
						remoteAdaptationSet = new TextAdaptationSet(remoteAdaptationSet);
					}
					else {
						remoteAdaptationSet = null;
					}
				}

				if (remoteAdaptationSet != null) {
					remoteManifest.adaptationSets.add(remoteAdaptationSet);
                    this.conf.eos.putAll(adaptationSetEos);
				}
			}

			File manifestLocal = new File(conf.folder + "/manifest_local.mpd");
			FileUtils.writeByteArrayToFile(manifestLocal, getStringFromDocument(doc).getBytes());
		}
	}

    public void determineInitialChunkIndex(AdaptationSet adaptationSet, String indexId, String urlRaw, String destFilePath) {
        long chunkIndex = getIndex(adaptationSet, indexId);
        if (chunkIndex == adaptationSet.startNumber) {
            String fileNamePath = null;
            try {
                fileNamePath = new URL(urlRaw).getFile().replace("$Time$", "*").replace("$Number$", "*");
                File destFile = new File(destFilePath + fileNamePath);
                String fileWildcard = destFile.getName();
                FileFilter fileFilter = new WildcardFileFilter(fileWildcard);
                File[] alreadyPresent = destFile.getParentFile().listFiles(fileFilter);
                HashMap<String, Object> memory = new HashMap<>();
                if (alreadyPresent != null) {
                    for(File f : alreadyPresent) {
                        memory.put(f.getName(), null);
                    }
                }
                for (long i = adaptationSet.startNumber; i < Integer.MAX_VALUE; i += adaptationSet.increment) {
                	String suffix = Long.toString(i);
                    String fToCheck = new URL(urlRaw).getFile().replace("$Number$", suffix).replace("$Time$", suffix);
                    if (memory.containsKey(new File(fToCheck).getName()) == false) {
                        incIndex(indexId, i - adaptationSet.startNumber);
                        break;
                    }
                }
            }
            catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean downloadSegments () throws Exception {
        while (this.pendingWriters.size() > MAX_CONCURRENT_DOWNLOADS) {
            ArrayList<AsyncFileWriter> toRemove = new ArrayList<>();
            for(AsyncFileWriter ifw : this.pendingWriters) {
                if(ifw.error) {
                    notifyUpdatersError(ErrorCodes.DOWNLOAD_SEGMENT_FAILED, "Failed to download media segment.");
                    dispose();
                    return false;
                }
                if(ifw.finished) {
                    toRemove.add(ifw);
                }
            }
            this.pendingWriters.removeAll(toRemove);
            if(isEndOfStream()) {
                return true;
            }
            Thread.sleep(5);
        }

        ArrayList<AsyncFileWriter> writers = new ArrayList<>();
        HashMap<String, String> tmpFiles = new HashMap<>();

        for (int i = 0; i < this.remoteManifest.adaptationSets.size(); i++) {
            AdaptationSet adaptationSet = this.remoteManifest.adaptationSets.get(i);
            String segmentTemplate = adaptationSet.segmentTemplate;

            for (int j = 0; j < adaptationSet.tracks.size(); ++j) {
                Track track = adaptationSet.tracks.get(j);
                String urlRaw = track.baseUrl + segmentTemplate.replace ("$RepresentationID$", track.id);
                String overallId = adaptationSet.id + ":" + track.id;

                determineInitialChunkIndex(adaptationSet, overallId, urlRaw, conf.folder);

                long chunkIndex = getIndex(adaptationSet, overallId);
                final String chunkIndexStr = Long.toString(chunkIndex);

                conf.currentSegment.put(overallId, chunkIndex / adaptationSet.increment);

                if (adaptationSet.segmentCount > 0) {
                    double totalChunks = 0, totalCurrent = 0;
                    for (int k = 0; k < remoteManifest.adaptationSets.size(); ++k) {
                        AdaptationSet counterSet = this.remoteManifest.adaptationSets.get(k);
                        totalChunks  += counterSet.segmentCount * counterSet.tracks.size();
                        for (int l = 0; l < counterSet.tracks.size(); ++l) {
                            Track counterTrack = counterSet.tracks.get(l);
                            String counterKey = counterSet.id + ":" + counterTrack.id;
                            totalCurrent += conf.currentSegment.containsKey(counterKey) ? conf.currentSegment.get(counterKey) - 1 : 0;
                        }
                    }
                    notifyUpdatersProgress(Math.round(totalCurrent * 100.0 / totalChunks));
                }

                if (chunkIndex / adaptationSet.increment > adaptationSet.segmentCount) {
                    conf.eos.put(overallId, true);
                    continue;
                }

                final String url = urlRaw.replace("$Number$", chunkIndexStr).replace("$Time$", chunkIndexStr);

                String fileName = new URL(url).getFile();
                String destFilePath = conf.folder + fileName;

                boolean contains = this.chunkMemory.containsKey(destFilePath);

                if (contains == false) {
                    System.out.println("Chunk: " + destFilePath);

                    this.chunkMemory.put(destFilePath, destFilePath);

                    tmpFiles.put(track.id, destFilePath);
                    AsyncFileWriter ifw = new AsyncFileWriter(this, url, destFilePath, track.id, chunkIndexStr);
                    writers.add(ifw);
                    ifw.start();

                    incIndex(overallId, adaptationSet.increment);
                }

                j++;
            }
        }

        for(AsyncFileWriter ifw : writers) {
            addWriter(ifw);
        }

        return true;
    }
    

    public boolean downloadStreamInit () throws Exception {
    	ArrayList<AsyncFileWriter> writers = new ArrayList<>();
        for (int i = 0; i < this.remoteManifest.adaptationSets.size(); i++) {
            AdaptationSet adaptationSet = this.remoteManifest.adaptationSets.get(i);
            String initUrl = adaptationSet.initSegment;
            if(initUrl == null) {
                continue;
            }
            for (int j = 0; j < adaptationSet.tracks.size(); ++j) {
                Track track = adaptationSet.tracks.get(j);
            	String url = track.baseUrl + initUrl.replace ("$RepresentationID$", track.id);
            	String destFilePath = conf.folder + new URL(url).getFile();
            	AsyncFileWriter ifw = new AsyncFileWriter(this, url, destFilePath, "", "INIT");
                writers.add(ifw);
                ifw.start();
            }
        }
        
        for (AsyncFileWriter ifw : writers) {
        	ifw.join();
			if (ifw.error()) {
				notifyUpdatersError(ErrorCodes.DOWNLOAD_INIT_CHUNK_FAILED, "Failed to download initialization chunk.");
				return false;
			}
        }

		return true;
    }
    
    public synchronized void removeWriter(AsyncFileWriter me) {
    	this.pendingWriters.remove(me);
    }

	private String getStringFromDocument(Document doc) {
		try {
			DOMSource domSource = new DOMSource(doc);
			StringWriter writer = new StringWriter();
			StreamResult result = new StreamResult(writer);
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.transform(domSource, result);
			return writer.toString();
		}
		catch(TransformerException ex) {
			ex.printStackTrace();
			return null;
		}
	}

    public class AsyncFileWriter extends Thread {
    	final String id;
    	final String url;
    	final String destFile;
    	final String chunkIndex;
    	
    	DashDownloader parent;
    	boolean finished;
    	boolean error;
    	
    	public AsyncFileWriter(DashDownloader parent, final String url, final String dest_file, final String id, String chunkIndex) {
    		this.url = url;
    		this.destFile = dest_file;
    		this.error = false;
    		this.parent = parent;
    		this.id = id;
    		this.finished = false;
    		this.chunkIndex = chunkIndex;
    	}
    	
    	public String getChunkIndex() {
			return this.chunkIndex;
		}

		private boolean download() {
    		try {
	    		URL url = new URL (this.url);
				File dest_file = new File (this.destFile);
				Log.d("EMP2 FILE PATH", this.destFile);
                if (dest_file.getParentFile().exists() == false) {
                    dest_file.getParentFile().mkdirs();
                }
				if (dest_file.exists () == false) {
	                FileUtils.copyURLToFile (url, dest_file, MAX_HTTP_CONNECTION_TIMEOUT, MAX_SEGMENT_DOWNLOAD_TIMEOUT);
	            }
	            else {
	            	System.out.println("AsyncFileWriter: weird... trying to write same file.");
	            }
    		}
            catch(Exception e) {
            	e.printStackTrace();
            	File dest_file = new File (this.destFile);
            	if(dest_file.exists()) {
            		dest_file.delete();
            	}
            	System.out.println("AsyncFileWriter: failed to download... trying again...\n\n");
            	return false;
            }
    		
    		//parent.removeWriter(this);
    		
            return true;
    	}
    	
    	public void run() {
    		for(int i = 0; i < MAX_DOWNLOAD_ATTEMPTS; ++i) {
				if (download() == true) {
					this.error = false;
                	this.finished = true;
					return;
				}
    		}
    		
    		System.err.println("Error downloading: " + this.url);
    		this.error = true;
        	this.finished = true;
        }
    	
    	public boolean error() {
    		return this.error;
    	}
    	
    	public boolean finished() {
    		return this.finished;
    	}
    	
    	public String getRepresentationId() {
    		return this.id;
    	}
    	
    	public String getPath() {
    		return this.destFile;
    	}
    }
    
    private class Configuration {
        String manifestUrl = null;
        HashMap<String, Long> currentSegment;
        HashMap<String, Boolean> eos;
		DownloadProperties properties;
        String folder = null;
            
        public Configuration(String manifestUrl, String destFolder, DownloadProperties properties) {
            this.properties = properties;
			this.folder = destFolder;
            this.manifestUrl = manifestUrl;
			File destFolderTest = new File(this.folder);
			if (!destFolderTest.exists()) {
				destFolderTest.mkdirs();
			}
        }

        void reset() {
            eos = new HashMap<>();
            currentSegment = new HashMap<>();
        }
    }
    
}