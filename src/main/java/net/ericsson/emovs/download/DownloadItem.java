package net.ericsson.emovs.download;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import net.ericsson.emovs.exposure.auth.SharedPropertiesICredentialsStorage;
import net.ericsson.emovs.exposure.entitlements.EMPEntitlementProvider;
import net.ericsson.emovs.utilities.entitlements.EntitledRunnable;
import net.ericsson.emovs.utilities.entitlements.EntitlementCallback;
import net.ericsson.emovs.utilities.interfaces.IPlayable;
import net.ericsson.emovs.utilities.models.EmpAsset;
import net.ericsson.emovs.utilities.models.EmpOfflineAsset;
import net.ericsson.emovs.utilities.entitlements.Entitlement;
import net.ericsson.emovs.utilities.errors.ErrorCodes;
import net.ericsson.emovs.utilities.errors.ErrorRunnable;
import net.ericsson.emovs.utilities.system.FileSerializer;
import net.ericsson.emovs.utilities.system.RunnableThread;

import net.ericsson.emovs.download.interfaces.IDownload;
import net.ericsson.emovs.download.interfaces.IDownloadEventListener;
import net.ericsson.emovs.utilities.emp.EMPRegistry;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

/**
 * Created by Joao Coelho on 2017-09-06.
 */

public class DownloadItem implements IDownload {
    private static final String TAG = DownloadItem.class.toString();

    public enum State {
        UNKNOWN,
        QUEUED,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        FAILED,
    };

    private UUID uuid;
    private State state;
    private double progress;
    private IPlayable onlinePlayable;
    private EmpOfflineAsset offlinePlayable;
    private Entitlement entitlement;
    private String downloadPath;
    private long downloadedSize;

    Context app;
    SharedPropertiesICredentialsStorage credentialsStorage;

    DashDownloader downloaderWorker;

    public DownloadItem(Context app, DownloadInfo info) {
        this.app = app;
        this.uuid = info.uuid;
        this.state = info.state;
        this.credentialsStorage = SharedPropertiesICredentialsStorage.getInstance();
        this.downloaderWorker = new DashDownloader(this);
        this.progress = info.progress;
        this.downloadPath = info.downloadPath;
        this.offlinePlayable = info.offlinePlayable;
        this.onlinePlayable = info.onlinePlayable;
        this.downloadedSize = info.downloadedBytes;
        if (state == State.DOWNLOADING || state == State.PAUSED || downloadedSize == 0) {
            new RunnableThread(new Runnable() {
                @Override
                public void run() {
                    if (state == State.DOWNLOADING || state == State.PAUSED) {
                        download(getId(), null);
                    }
                    if (downloadedSize == 0) {
                        updateDownloadedSize();
                    }
                }
            }).start();
        }
        setAnalytics();
    }

    public DownloadItem(Context app, IPlayable onlinePlayable) {
        this.app = app;
        this.uuid = UUID.randomUUID();
        this.credentialsStorage = SharedPropertiesICredentialsStorage.getInstance();
        this.downloaderWorker = new DashDownloader(this);
        this.onlinePlayable = onlinePlayable;
        this.downloadPath = DownloadItemManager.DOWNLOAD_BASE_PATH + onlinePlayable.getId();
        setAnalytics();
        setState(State.QUEUED);
    }

    private void setAnalytics() {
        this.downloaderWorker.setCallback("ANALYTICS", new EMPAnalyticsConnector(this));
    }

    public void download() {
        setState(State.DOWNLOADING);
        download(getId(), null);
    }

    public void delete() {
        dispose();
        File f = new File(this.downloadPath, "info.ser");
        if (f.exists()) {
            f.delete();
        }
    }

    public void updateDownloadedSize() {
        File path = new File(getDownloadPath());
        if(path.exists()) {
            this.downloadedSize = FileUtils.sizeOfDirectory(new File(getDownloadPath()));
        }
        else {
            this.downloadedSize = 0;
        }
    }

    public void dispose() {
        if (this.downloaderWorker.getState() != Thread.State.NEW) {
            if (this.downloaderWorker.isAlive()) {
                this.downloaderWorker.interrupt();
            }
        }
        this.downloaderWorker.dispose();
    }

    public String getDownloadPath() {
        return this.downloadPath;
    }

    private void download(final String assetId, final IDownloadEventListener callback) {
        final DownloadItem self = this;
        final EntitledRunnable onEntitlementRunnable = new EntitledRunnable() {
            @Override
            public void run() {
                self.entitlement = entitlement;

                self.downloaderWorker.notifyEntitlement(entitlement);

                if (callback != null) {
                    callback.onEntitlement(entitlement);
                }

                if (new File(downloadPath).exists() == false) {
                    new File(self.downloadPath).mkdirs();
                }

                FileSerializer.writeJson(entitlement, self.downloadPath + "/entitlement.ser");

                Log.d(TAG, "Locator: " + entitlement.mediaLocator);

                downloadLicense(assetId, entitlement.mediaLocator, entitlement.playToken);
                downloadMedia(self.downloadPath, entitlement, callback);
                createDownloadedAsset(self.downloadPath + "/manifest_local.mpd");
            }
        };
        final ErrorRunnable onErrorRunnable = new ErrorRunnable() {
            @Override
            public void run(int errorCode, String errorMessage) {
                Log.e(TAG, errorMessage);
                if (downloaderWorker != null) {
                    downloaderWorker.notifyUpdatersError(ErrorCodes.DOWNLOAD_ENTITLEMENT_ERROR, errorMessage);
                }
            }
        };
        EMPEntitlementProvider.getInstance().download(assetId, new EntitlementCallback(assetId, null, null, onEntitlementRunnable, onErrorRunnable));
    }

    public void onDownloadSuccess(String manifestPath) {
        createDownloadedAsset(manifestPath);
        setState(State.COMPLETED);
//        FileSerializer.write(this.offlinePlayable, this.downloadPath + "/offline_asset.ser");
    }

    private void createDownloadedAsset(String manifestPath) {
        if (this.offlinePlayable == null) {
            this.offlinePlayable = new EmpOfflineAsset();
        }
        this.offlinePlayable.localMediaPath = manifestPath;
        this.offlinePlayable.setJson(this.onlinePlayable.getJson());
        saveDownloadInfo();
    }

    private void downloadLicense(String mediaId, String manifestUrl, String playToken) {
        Pair<String, String> licenseDetails = DownloadItem.getLicenseDetails(manifestUrl, false);
        if (licenseDetails == null) {
            return;
        }
        WidevineOfflineLicenseManager downloader = new WidevineOfflineLicenseManager(EMPRegistry.applicationContext());

        String licenseWithToken = Uri.parse(licenseDetails.first)
                .buildUpon()
                .appendQueryParameter("token", "Bearer " + playToken)
                .build().toString();
        licenseDetails = new Pair<>(licenseWithToken, licenseDetails.second);

        byte[] license = downloader.download(licenseDetails.first, licenseDetails.second);
        if(license != null) {
            downloader.store(mediaId, license);
        }
    }

    private void downloadMedia(String downloadFolder, Entitlement entitlement, final IDownloadEventListener callback) {
        if (this.downloaderWorker.getState() != Thread.State.NEW) {
            if (this.downloaderWorker.isAlive()) {
                this.downloaderWorker.interrupt();
            }
            this.downloaderWorker = new DashDownloader(this.downloaderWorker);
        }
        this.downloaderWorker.init(entitlement.mediaLocator, downloadFolder);
        if (callback != null) {
            this.downloaderWorker.setCallback("SINGLE_DOWNLOAD_ACTIVITY_CALLBACK", callback);
        }
        this.downloaderWorker.start();
    }

    public static Pair<String, String> getLicenseDetails(String manifestUrl, boolean isOffline) {
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            Document mpd = null;
            if(isOffline) {
                mpd = getManifestDocument(new File(manifestUrl));
            }
            else {
                mpd = getManifestDocument(new URL(manifestUrl));
            }
            NodeList laurls = (NodeList) xpath.compile("//urn:microsoft:laurl").evaluate(mpd, XPathConstants.NODESET);
            Log.d(TAG, "Node Count: " + laurls.getLength());
            String drmLicenseUrl = null;
            String drmInitializationBase64 = null;
            if (laurls.getLength() > 0) {
                Node laurl = laurls.item(0);
                NamedNodeMap laurlAttrs = laurl.getAttributes();
                Node licenseServerUrlNode = laurlAttrs.getNamedItem("licenseUrl");
                drmLicenseUrl = licenseServerUrlNode.getNodeValue();
                Log.d(TAG, "License Server URL: " + drmLicenseUrl);

                NodeList psshCandidates = laurl.getParentNode().getChildNodes();
                for (int j = 0; j < psshCandidates.getLength(); ++j) {
                    Node pssh = psshCandidates.item(j);
                    if (pssh.getNodeName().contains("pssh")) {
                        drmInitializationBase64 = pssh.getTextContent();
                        Log.d(TAG, "EMP Initialization Data: " + drmInitializationBase64);
                        break;
                    }
                }
            }

            return new Pair<>(drmLicenseUrl, drmInitializationBase64);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Document getManifestDocument(File manifestFile) throws Exception {
        String manifestContent = FileUtils.readFileToString(manifestFile, "UTF-8");

        Log.d(TAG, manifestContent);

        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        domFactory.setNamespaceAware(true);
        DocumentBuilder builder = domFactory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(manifestContent.getBytes()));
        return doc;
    }

    private static Document getManifestDocument(URL manifestUrl) throws Exception {
        File temp = File.createTempFile(UUID.randomUUID().toString(), ".mpd");
        FileUtils.copyURLToFile(manifestUrl, temp);
        return getManifestDocument(temp);
    }

    public void setDownloadedAsset(EmpOfflineAsset offlinePlayable) {
        this.offlinePlayable = offlinePlayable;
    }

    public void setState(State state) {
        Log.d(TAG, "New download state: " + state.name());
        this.state = state;
        saveDownloadInfo();
    }

    public void setOnlinePlayable(IPlayable onlinePlayable) {
        this.onlinePlayable = onlinePlayable;
        this.offlinePlayable = new EmpOfflineAsset();
        if (this.onlinePlayable instanceof EmpAsset) {
            this.offlinePlayable.setProps((EmpAsset) this.onlinePlayable);
        }
        saveDownloadInfo();
    }

    public void setProgress(double progress) {
        if (progress < this.progress) {
            return;
        }
        this.progress = progress;
        //saveDownloadInfo();
    }

    public void pause() {
        if (getState() == State.DOWNLOADING) {
            setState(State.PAUSED);
            this.downloaderWorker.notifyUpdatersPause();
        }
    }

    public void resume() {
        if (getState() == State.PAUSED) {
            setState(State.DOWNLOADING);
            this.downloaderWorker.notifyUpdatersResume();
        }
    }

    public void retry() {
        this.download();
    }

    public void saveDownloadInfo() {
        DownloadInfo info = new DownloadInfo();
        info.downloadPath = this.downloadPath;
        info.offlinePlayable = this.offlinePlayable;
        info.onlinePlayable = this.onlinePlayable;
        info.progress = this.progress;
        info.state = this.state;
        info.uuid = this.uuid;
        info.downloadedBytes = this.downloadedSize;
        FileSerializer.write(info, new File(this.downloadPath, "info.ser").getAbsolutePath());
        DownloadItemManager.updateSummary(info.onlinePlayable.getId(), info);
    }

    public String getId() {
        if (onlinePlayable != null) {
            return onlinePlayable.getId();
        }
        else if (offlinePlayable != null) {
            return offlinePlayable.getId();
        }
        return this.uuid.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DownloadItem that = (DownloadItem) o;

        if (!getId().equals(that.getId()))  {
            return false;
        }

        return true;
    }

    // IDownload implementation

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public EmpOfflineAsset getDownloadedAsset() {
        return this.offlinePlayable;
    }

    public State getState() {
        return state;
    }

    @Override
    public int getErrorCode() {
        if(this.downloaderWorker == null) {
            return 0;
        }
        return this.downloaderWorker.getErrorCode();
    }

    @Override
    public String getErrorMessage() {
        if(this.downloaderWorker == null) {
            return null;
        }
        return this.downloaderWorker.getErrorMessage();
    }

    @Override
    public long getMediaEstimatedSize() {
        double progress = getProgress() / 100;
        if(progress < 0.0001) {
            return -1;
        }
        if(progress > 1) {
            progress = 1;
        }
        return (long) (getDownloadedSize() / progress);
    }

    @Override
    public long getDownloadedSize() {
        return this.downloadedSize;
    }

    @Override
    public void addEventListener(String key, IDownloadEventListener listener) {
        if (this.downloaderWorker == null) {
            return;
        }
        this.downloaderWorker.setCallback(key, listener);
    }

    @Override
    public void removeEventListener(String key) {
        if (this.downloaderWorker == null) {
            return;
        }
        this.downloaderWorker.unsetCallback(key);
    }

    @Override
    public IPlayable getOnlinePlayable() {
        return this.onlinePlayable;
    }

    @Override
    public double getProgress() {
        return progress;
    }

    public String getVersion() {
        return EMPRegistry.applicationContext().getString(R.string.empdownloader_version);
    }
}
