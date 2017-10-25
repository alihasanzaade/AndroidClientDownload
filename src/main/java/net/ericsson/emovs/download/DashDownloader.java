package net.ericsson.emovs.download;

import android.util.Log;

import com.ebs.android.utilities.ErrorCodes;

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
import java.util.Map;
import java.util.Scanner;

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
    final int FIRST_INDEX = 1;

	final String ADAPTATION_SET = "//urn:mpeg:dash:schema:mpd:2011:AdaptationSet";
	final String MPD = "//urn:mpeg:dash:schema:mpd:2011:MPD";
	final String REPRESENTATION = "Representation";
	final String SEGMENT_TEMPLATE = "SegmentTemplate";
	final String MEDIA = "media";
	final String BASE_URL = "BaseURL";
	final String INITIALIZATION = "initialization";
	final String DURATION = "duration";
	final String TIMESCALE = "timescale";
	final String ID = "id";
    
	int streamDuration;
	Configuration conf;

	HashMap<String, String> chunkMemory;
	HashMap<String, Integer> currentIndexMap;	
	ArrayList<AsyncFileWriter> pendingWriters;
	HashMap<String, IDownloadEventListener> stateUpdaters;
    DownloadItem parent;
    
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

	public void init(String manifestUrl, String destFolder) {
		this.conf = new Configuration(manifestUrl, destFolder);
	}

	public void setCallback(String key, IDownloadEventListener callback) {
		this.stateUpdaters.put(key, callback);
	}

	public void notifyUpdatersError(int errorCode, String message) {
		if (this.parent != null) {
			this.parent.setState(DownloadItem.STATE_FAILED);
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
	}

	private void notifyUpdatersStart() {
		for(IDownloadEventListener callback : this.stateUpdaters.values()) {
			callback.onStart();
		}
	}

    public void notifyUpdatersPause() {
        for(IDownloadEventListener callback : this.stateUpdaters.values()) {
            callback.onPause();
        }
    }

    public void notifyUpdatersResume() {
        for(IDownloadEventListener callback : this.stateUpdaters.values()) {
            callback.onResume();
        }
    }

	@Override
	public void run() {
		try {
            notifyUpdatersStart();
			download();
		}
        catch(InterruptedException e) {
            e.printStackTrace();
        }
		catch(Exception e) {
			e.printStackTrace();
			notifyUpdatersError(ErrorCodes.DOWNLOAD_RUNTIME_ERROR, e.getMessage());
		}
	}
	
    public void download() throws Exception {
		downloadManifest();

		if (conf.adaptationSets == 0) {
            dispose();
			return;
		}

		if (downloadStreamInit() == false) {
			return;
		}
        
		while (isEndOfStream() == false) {
			if (this.parent.getState() == DownloadItem.STATE_PAUSED) {
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
    	for(AsyncFileWriter writer : pendingWriters) {
    		writer.interrupt();
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
    
    public int getIndex(String hash) {
    	if(currentIndexMap.containsKey(hash)) {
    		return currentIndexMap.get(hash);	
    	}
    	currentIndexMap.put(hash, FIRST_INDEX);
    	return FIRST_INDEX;
    }
    
    public void incIndex(String hash, int val) {
    	int newV = val;
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
    	for (int i = 0; i < conf.eos.length; ++i) {
    		if (conf.eos[i] == false) {
    			return false;
    		}
    	}
    	return true;
    }
    
    public static int getDuration(String mediaPresentationDuration) {
    	mediaPresentationDuration = mediaPresentationDuration.replaceAll("S", "");
    	String[] durationParts = mediaPresentationDuration.split("M");
    	
    	int seconds = (int) Math.ceil(Double.parseDouble(durationParts[1]));
    	
    	String durationRest = durationParts[0]
				.replaceAll("P", "")
				.replaceAll("DT", " ")
				.replaceAll("H", " ")
				.replaceAll("M", " ");
    		
    		Scanner scanner = new Scanner (durationRest);
    		
    		// period id
    		scanner.nextInt();	
    		int pHours = scanner.nextInt();
    		int pMinutes = scanner.nextInt();    		
    		int duration = seconds + pMinutes*60 + pHours*3600;

    		scanner.close();
    		
    		return duration;
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
            //String localManifestContent = "";
            DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
            domFactory.setNamespaceAware(true); 
    	    DocumentBuilder builder = domFactory.newDocumentBuilder();
    	    Document doc = builder.parse(new ByteArrayInputStream(manifestContent.getBytes()));
    	    XPath xpath = XPathFactory.newInstance().newXPath();
    	   
    		Node mpdRoot = (Node) xpath.compile(MPD).evaluate(doc, XPathConstants.NODE);
    		
    		NamedNodeMap mpdAttrs = mpdRoot.getAttributes();
    	    Node mediaPresentationDurationNode = mpdAttrs.getNamedItem("mediaPresentationDuration");
    	    String mediaPresentationDuration = mediaPresentationDurationNode.getNodeValue();
    	    this.streamDuration = getDuration(mediaPresentationDuration);
    	    
    		System.out.println("Stream Duration: " + streamDuration);
    		
    	    NodeList adaptationSets = (NodeList) xpath.compile(ADAPTATION_SET).evaluate(doc, XPathConstants.NODESET);
    	    
    	    for (int i = 0; i < adaptationSets.getLength(); i++) {
    	    	Node set = adaptationSets.item(i);

    	    	if(set == null) continue;
	    		NamedNodeMap setAttrs = set.getAttributes();
    	    	
    	    	Node idNode = setAttrs.getNamedItem(ID);
	    		String id = idNode.getNodeValue();
	    		
    	    	int currentSetIdx = conf.createAdaptationSet(id);
    	    	NodeList children = set.getChildNodes();
    	    	
    	    	int currentRepresentation = 0;
				int maxBandwidth = -1;
				HashMap<Integer, Node> bandwidthNodeMapper = new HashMap<Integer, Node>();

    	    	for (int k = 0; k < children.getLength(); k++) {
    	    		Node child = children.item(k);
    	    		if (child == null) {
    	    			continue;
    	    		}
    	    		if (child.getNodeName().equals(SEGMENT_TEMPLATE)) {
    	    			String media = child.getAttributes().getNamedItem(MEDIA).getNodeValue();
    	    			String init = child.getAttributes().getNamedItem(INITIALIZATION).getNodeValue();   
    	    			int duration = Integer.parseInt(child.getAttributes().getNamedItem(DURATION).getNodeValue());
    	    			int timescale = Integer.parseInt(child.getAttributes().getNamedItem(TIMESCALE).getNodeValue());
        	    		conf.segmentUrl[currentSetIdx] = media;
        	    		conf.initUrl[currentSetIdx] = init;
        	    		conf.segmentDurations[currentSetIdx] = duration/timescale;
        	    		conf.segmentCount[currentSetIdx] = (int) Math.ceil(((double) streamDuration)/conf.segmentDurations[currentSetIdx]);
					}
    	    		else if (child.getNodeName().equals(REPRESENTATION)) {
    	    			int bandwidth = Integer.parseInt(child.getAttributes().getNamedItem("bandwidth").getNodeValue());
						bandwidthNodeMapper.put(bandwidth, child);
						if (bandwidth < maxBandwidth) {
    	    				continue;
    	    			}
    	    			maxBandwidth = bandwidth;
    	    			NodeList repChildren = child.getChildNodes();
    	    			for (int j=0; j < repChildren.getLength(); ++j) {
    	    				Node rChild = repChildren.item(j);
    	    				if(rChild.getNodeName().equals(BASE_URL)) {
    	    					String baseUrl = rChild.getTextContent();
    	    					conf.baseUrls[currentSetIdx] = baseUrl;

								URL mediaGrab = new URL(baseUrl);
								String host = mediaGrab.getHost();
								String protocol = mediaGrab.getProtocol();
								System.out.println("EMP HOST+PROTOCOL: " + protocol+"://"+host);
								rChild.setTextContent(baseUrl.replace(protocol+"://"+host+"", conf.folder));
    	    				}
    	    			}
    	    			String representationId = child.getAttributes().getNamedItem(ID).getNodeValue();
						conf.representationIds[currentSetIdx].ids.clear();
    	    			conf.representationIds[currentSetIdx].ids.put(currentRepresentation, representationId);
                        currentRepresentation++;
    	    		}
    	    	}

				for (Integer bandwidth : bandwidthNodeMapper.keySet()) {
					if(bandwidth < maxBandwidth) {
						System.out.println("DashDownloader: removing unnecessary Representation bitrate - " + bandwidth);
						Node nodeToRemove = bandwidthNodeMapper.get(bandwidth);
						set.removeChild(nodeToRemove);
					}
				}
	    	}
			File manifestLocal = new File(conf.folder + "/manifest_local.mpd");
			FileUtils.writeByteArrayToFile(manifestLocal, getStringFromDocument(doc).getBytes());
        }  
    }

    public void determineInitialChunkIndex(String indexId, String urlRaw, String destFilePath) {
        int chunkIndex = getIndex(indexId);
        if (chunkIndex == FIRST_INDEX) {
            String fileNamePath = null;
            try {
                fileNamePath = new URL(urlRaw).getFile().replace("$Number$", "*");
                File destFile = new File(destFilePath + fileNamePath);
                String fileWildcard = destFile.getName();
                FileFilter fileFilter = new WildcardFileFilter(fileWildcard);
                File[] alreadyPresent = destFile.getParentFile().listFiles(fileFilter);
                HashMap<String, Object> memory = new HashMap<>();
                for(File f : alreadyPresent) {
                    memory.put(f.getName(), null);
                }
                for (int i = FIRST_INDEX; i < Integer.MAX_VALUE; ++i) {
                    String fToCheck = new URL(urlRaw).getFile().replace("$Number$", Integer.toString(i));
                    if (memory.containsKey(new File(fToCheck).getName()) == false) {
                        incIndex(indexId, i - FIRST_INDEX);
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
			Thread.sleep(20);
    	}
    	
    	ArrayList<AsyncFileWriter> writers = new ArrayList<AsyncFileWriter>();
    	HashMap<String, String> tmpFiles = new HashMap<String, String>();
    	
        for (int i = 0; i < conf.adaptationSets; i++) {
            String segmentUrl = conf.segmentUrl[i];
            int j=0;
            for (String id : conf.representationIds[i].ids.values ()) {
                String urlRaw = conf.baseUrls[i] + segmentUrl.replace ("$RepresentationID$", id);
                
                String indexId = i + ":" + id;

                determineInitialChunkIndex(indexId, urlRaw, conf.folder);

                int chunkIndex = getIndex(indexId);
                final String chunkIndexStr = Integer.toString(chunkIndex);

				if(conf.segmentCount[i] > 0) {
					notifyUpdatersProgress(Math.round((chunkIndex-1) * 100.0 / conf.segmentCount[i]));
				}
				else {
					//notifyUpdatersProgress(0.0);
				}

                if (chunkIndex > conf.segmentCount[i]) {
                	conf.eos[i] = true;
                	continue;
                }
                		
                final String url = urlRaw.replace("$Number$", chunkIndexStr);

                String fileName = new URL(url).getFile();
                String destFilePath = conf.folder + fileName;

                boolean contains = this.chunkMemory.containsKey(destFilePath);
                
                if (contains == false) {
                	System.out.println("Chunk: " + destFilePath);
                	
                	this.chunkMemory.put(destFilePath, destFilePath);
                	
                	tmpFiles.put(id, destFilePath);
                    AsyncFileWriter ifw = new AsyncFileWriter(this, url, destFilePath, id, chunkIndexStr);
                    writers.add(ifw);
                    ifw.start();
                                    
                    incIndex(indexId, 1);
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
    	ArrayList<AsyncFileWriter> writers = new ArrayList<AsyncFileWriter>();
        for (int i = 0;i < conf.adaptationSets; i++) {
            String initUrl = conf.initUrl[i];
            for (String id : conf.representationIds[i].ids.values ()) {
            	String url = conf.baseUrls[i] + initUrl.replace ("$RepresentationID$", id);
            	String destFilePath = conf.folder + new URL(url).getFile();
            	AsyncFileWriter ifw = new AsyncFileWriter(this, url, destFilePath, "", "INIT");
                writers.add(ifw);
                ifw.start();
            }
        }
        
        for(AsyncFileWriter ifw : writers) {
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
    	private final int MAX_ADAPTATION_SETS = 2;
        
        String manifestUrl = null;
        
        String[] segmentUrl = new String[MAX_ADAPTATION_SETS];
        String[] initUrl = new String[MAX_ADAPTATION_SETS];
        String[] baseUrls = new String[MAX_ADAPTATION_SETS];
        
        int[] segmentDurations = new int[MAX_ADAPTATION_SETS];
        int[] segmentCount = new int[MAX_ADAPTATION_SETS];
        boolean[] eos = new boolean[MAX_ADAPTATION_SETS];
        
        Reps[] representationIds = new Reps[MAX_ADAPTATION_SETS];
        
        String folder = null;
        
        int adaptationSets = 0;
            
        public Configuration(String manifestUrl, String destFolder) {
            //this.folder = destFolder + UUID.randomUUID().toString().substring(0, 5).replaceAll("-", "");
			this.folder = destFolder;
            this.manifestUrl = manifestUrl;
			File destFolderTest = new File(this.folder);
			if (!destFolderTest.exists()) {
				destFolderTest.mkdirs();
			}
        }

        int createAdaptationSet(String i) {
            if ("0".equals(i)){
            	// video
            	adaptationSets++;
                return 0;
            }
            else {
            	// audio
                adaptationSets++;
                return 1;
            }
        }

        void reset() {
            this.segmentUrl = new String[MAX_ADAPTATION_SETS];
            this.initUrl = new String[MAX_ADAPTATION_SETS];
            this.representationIds = new Reps[MAX_ADAPTATION_SETS];
            for (int i = 0; i < MAX_ADAPTATION_SETS; ++i) {
            	this.representationIds[i] = new Reps();
            }
            this.adaptationSets = 0;
        }

        public class Reps {
             public Map<Integer,String> ids = new HashMap<Integer, String>();
        }
    }
    
}