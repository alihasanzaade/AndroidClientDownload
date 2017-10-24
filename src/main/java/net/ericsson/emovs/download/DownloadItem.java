package net.ericsson.emovs.download;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import com.ebs.android.exposure.auth.SharedPropertiesICredentialsStorage;

import com.ebs.android.exposure.entitlements.EMPEntitlementProvider;
import com.ebs.android.exposure.entitlements.EntitledRunnable;
import com.ebs.android.exposure.entitlements.Entitlement;
import com.ebs.android.exposure.entitlements.EntitlementCallback;
import com.ebs.android.exposure.interfaces.IPlayable;
import com.ebs.android.exposure.models.EmpAsset;
import com.ebs.android.exposure.models.EmpOfflineAsset;
import com.ebs.android.utilities.ErrorCodes;
import com.ebs.android.utilities.ErrorRunnable;
import com.ebs.android.utilities.FileSerializer;

import net.ericsson.emovs.download.interfaces.IDownload;
import net.ericsson.emovs.download.interfaces.IDownloadEventListener;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import static android.os.Environment.getExternalStorageDirectory;

/**
 * Created by Joao Coelho on 2017-09-06.
 */

public class DownloadItem implements IDownload {
    private final String TAG = DownloadItem.class.toString();

    public static final int STATE_QUEUED = 0;
    public static final int STATE_DOWNLOADING = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_COMPLETED = 3;
    public static final int STATE_FAILED = 4;

    private UUID uuid;
    private int state;
    private double progress;
    private IPlayable onlinePlayable;
    private EmpOfflineAsset offlinePlayable;
    private Entitlement entitlement;
    private String downloadPath;

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
    }

    public DownloadItem(Context app) {
        this.app = app;
        this.uuid = UUID.randomUUID();
        this.credentialsStorage = SharedPropertiesICredentialsStorage.getInstance();
        this.downloaderWorker = new DashDownloader(this);
        setState(STATE_QUEUED);
    }

    public void download() {
        setState(STATE_DOWNLOADING);
        download(getId(), null);
    }

    // TODO: make this private
    public void download(final String assetId, final IDownloadEventListener callback) {
        final DownloadItem self = this;
        final EntitledRunnable onEntitlementRunnable = new EntitledRunnable() {
            @Override
            public void run() {
                self.entitlement = entitlement;

                if (callback != null) {
                    callback.onEntitlement(entitlement);
                }

                self.downloadPath = DownloadItemManager.DOWNLOAD_BASE_PATH + assetId;// + "/" + UUID.randomUUID().toString().substring(0, 5).replaceAll("-", "");

                if (new File(downloadPath).exists() == false) {
                    new File(self.downloadPath).mkdirs();
                }

                FileSerializer.write(entitlement, self.downloadPath + "/entitlement.ser");

                Log.d("EMP MEDIA LOCATOR", entitlement.mediaLocator);

                downloadLicense(assetId, entitlement.mediaLocator, entitlement.playToken);
                downloadMedia(self.downloadPath, entitlement, callback);
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
        EMPEntitlementProvider.getInstance().playVod(assetId, new EntitlementCallback(assetId, null, null, onEntitlementRunnable, onErrorRunnable));
    }

    public void onDownloadSuccess(String manifestPath) {
        if (this.offlinePlayable != null) {
            this.offlinePlayable = new EmpOfflineAsset();
        }

        this.offlinePlayable.entitlement = this.entitlement;
        this.offlinePlayable.localMediaPath = manifestPath;
        setState(STATE_COMPLETED);

        FileSerializer.write(this.offlinePlayable, this.downloadPath + "/offline_asset.ser");
        saveDownloadInfo();
    }

    private Entitlement readEntitlement(String localBasePath) {
        try {
            FileInputStream fileIn = new FileInputStream(localBasePath + "/entitlement.ser");
            ObjectInputStream in = new ObjectInputStream(fileIn);
            Entitlement entitlement = (Entitlement) in.readObject();
            in.close();
            fileIn.close();
            return entitlement;
        }
        catch(ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void downloadLicense(String mediaId, String manifestUrl, String playToken) {
        Pair<String, String> licenseDetails = DownloadItem.getLicenseDetails(manifestUrl, false);
        if (licenseDetails == null) {
            return;
        }
        WidevineOfflineLicenseManager downloader = new WidevineOfflineLicenseManager(this.app);

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
            Log.d("EMP LICENSE SERVER URL", "Node Count: " + laurls.getLength());
            String drmLicenseUrl = null;
            String drmInitializationBase64 = null;
            if (laurls.getLength() > 0) {
                Node laurl = laurls.item(0);
                NamedNodeMap laurlAttrs = laurl.getAttributes();
                Node licenseServerUrlNode = laurlAttrs.getNamedItem("licenseUrl");
                drmLicenseUrl = licenseServerUrlNode.getNodeValue();
                Log.d("EMP LICENSE SERVER URL", drmLicenseUrl);

                NodeList psshCandidates = laurl.getParentNode().getChildNodes();
                for (int j = 0; j < psshCandidates.getLength(); ++j) {
                    Node pssh = psshCandidates.item(j);
                    if (pssh.getNodeName().contains("pssh")) {
                        drmInitializationBase64 = pssh.getTextContent();
                        Log.d("EMP Initialization Data", drmInitializationBase64);
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

        Log.d("EMP LICENSE SERVER URL", manifestContent);

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

    public void setState(int state) {
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
        this.progress = progress;
        saveDownloadInfo();
    }

    public void pause() {
        // TODO: implement
    }

    public void resume() {
        // TODO: implement
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
        FileSerializer.write(info, new File(this.downloadPath, "info.ser").getAbsolutePath());
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

    // TODO: implement
    @Override
    public EmpOfflineAsset getDownloadedAsset() {
        return this.offlinePlayable;
    }

    // TODO: implement
    public int getState() {
        return state;
    }

    // TODO: implement
    @Override
    public String getErrorCode() {
        return null;
    }

    // TODO: implement
    @Override
    public String getErrorMessage() {
        return null;
    }

    // TODO: implement
    @Override
    public long getMediaFullSize() {
        return 0;
    }

    // TODO: implement
    @Override
    public long getDownloadedSize() {
        return 0;
    }

    // TODO: implement
    @Override
    public void addEventListener(IDownloadEventListener listener) {
        if (this.downloaderWorker == null) {
            return;
        }
        this.downloaderWorker.setCallback(UUID.randomUUID().toString(), listener);
    }

    @Override
    public IPlayable getOnlinePlayable() {
        return this.onlinePlayable;
    }

    // TODO: calculate getDownloadedSize() / getMediaFullSize() instead
    @Override
    public double getProgress() {
        return progress;
    }
}
