package net.ericsson.emovs.download;

import android.util.Log;

import net.ericsson.emovs.utilities.interfaces.IPlayable;
import net.ericsson.emovs.exposure.metadata.builders.EmpBaseBuilder;
import net.ericsson.emovs.utilities.models.EmpAsset;
import net.ericsson.emovs.utilities.models.EmpOfflineAsset;
import net.ericsson.emovs.utilities.system.FileSerializer;

import net.ericsson.emovs.download.interfaces.IDownload;
import net.ericsson.emovs.utilities.emp.EMPRegistry;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.UUID;

import static android.os.Environment.getExternalStorageDirectory;

/**
 * This class manages all download entries, states and which assets were flagged for deletion
 *
 * Created by Joao Coelho on 2017-10-05.
 */
public class DownloadItemManager {
    private static final String TAG = DownloadItemManager.class.toString();
    private static final String DOWNLOAD_FOLDER = "EMPDownloads";
    public static String DOWNLOAD_BASE_PATH = null;
    static HashMap<String, DownloadInfo> summary = new HashMap<>();

    private final int DEFAULT_CONCURRENT_DOWNLOADS = 2;

    private HashMap<String, DownloadItem> downloadItems;
    private LinkedList<String> assetsToDelete;
    private int maxConcurrentDownloads;

    private static class DownloadItemManagerHolder {
        private final static DownloadItemManager sInstance = new DownloadItemManager();
    }

    public static DownloadItemManager getInstance() {
        return DownloadItemManagerHolder.sInstance;
    }

    protected DownloadItemManager() {
        DOWNLOAD_BASE_PATH = getExternalStorageDirectory().getPath() + "/" + DOWNLOAD_FOLDER + "/" + EMPRegistry.applicationContext().getPackageName() + "/";
        this.downloadItems = new HashMap<>();
        this.maxConcurrentDownloads = DEFAULT_CONCURRENT_DOWNLOADS;
        this.assetsToDelete = new LinkedList<>();
    }

    /**
     * Adds new download entry
     *
     * @param playable
     * @return
     */
    public boolean createItem(IPlayable playable, DownloadProperties properties) {
        if (this.downloadItems.containsKey(playable.getId())) {
            return false;
        }
        DownloadItem item = new DownloadItem(EMPRegistry.applicationContext(), playable, properties);
        this.downloadItems.put(playable.getId(), item);
        return true;
    }

    /**
     * Recreates a download entry from a DownloadInfo object saved in filesystem
     *
     * @param info
     */
    private void createItemFromDownloadInfo(DownloadInfo info) {
        if (this.downloadItems.containsKey(info.onlinePlayable.getId())) {
           return;
        }
        DownloadItem item = new DownloadItem(EMPRegistry.applicationContext(), info);
        this.downloadItems.put(info.onlinePlayable.getId(), item);
    }

    /**
     * Counts the number of download entries with a specific state
     *
     * @param state
     * @return
     */
    public int count(DownloadItem.State state) {
        int n = 0;
        for(DownloadItem item : this.downloadItems.values()) {
            if (item.getState() == state) {
                n++;
            }
        }
        return n;
    }

    /**
     * Checks if there is room to start a new download (by checking how many concurrent downloads and the max allowed concurrent download count)
     *
     * @return
     */
    public boolean canStartNewDownload() {
        return count(DownloadItem.State.PAUSED) + count(DownloadItem.State.DOWNLOADING) < maxConcurrentDownloads;
    }


    /**
     * Informs if there are assets flagged for deletion
     * @return
     */
    public boolean hasAssetsToDelete() {
        return this.assetsToDelete.size() > 0;
    }

    /**
     * Starts the download of the next asset in queue
     */
    public void downloadNext() {
        for (DownloadItem item : this.downloadItems.values()) {
            if (item.getState() == DownloadItem.State.QUEUED) {
                item.download();
                break;
            }
        }
    }

    /**
     * Gets download entries for asset with a specific state
     *
     * @param stateFilter
     * @return
     */
    public ArrayList<IDownload> getDownloads(DownloadItem.State stateFilter) {
        ArrayList<IDownload> returnDownloads = new ArrayList<>();
        for (DownloadItem item : this.downloadItems.values()) {
            if (item.getState() == stateFilter) {
                returnDownloads.add(item);
            }
        }
        return returnDownloads;
    }

    /**
     * Gets all download entries
     *
     * @return
     */
    public ArrayList<IDownload> getDownloads() {
        ArrayList<IDownload> returnDownloads = new ArrayList<>();
        for (DownloadItem item : this.downloadItems.values()) {
            returnDownloads.add(item);
        }
        return returnDownloads;
    }

    /**
     * Pauses a download
     *
     * @param playable
     */
    public void pause(IPlayable playable) {
        boolean contains = this.downloadItems.containsKey(playable.getId());
        if(contains == false) {
            return;
        }
        this.downloadItems.get(playable.getId()).pause();
    }

    /**
     * Resumes a download
     *
     * @param playable
     */
    public void resume(IPlayable playable) {
        boolean contains = this.downloadItems.containsKey(playable.getId());
        if(contains == false) {
            return;
        }
        this.downloadItems.get(playable.getId()).resume();
    }

    /**
     * Deletes a download
     *
     * @param playable
     */
    public void delete(IPlayable playable) {
        if (this.downloadItems.containsKey(playable.getId()) == false) {
            return;
        }
        this.downloadItems.get(playable.getId()).delete();
        this.assetsToDelete.add(playable.getId());
        updateSummary(playable.getId(), null);
    }

    /**
     * Retries a failed download
     *
     * @param playable
     */
    public void retry(IPlayable playable) {
        boolean contains = this.downloadItems.containsKey(playable.getId());
        if(contains == false) {
            return;
        }
        this.downloadItems.get(playable.getId()).retry();
    }

    /**
     * Deletes flagged assets from filesystem
     *
     */
    public void flushRemovedAssets() {
        while(this.assetsToDelete.size() > 0) {
            String assetId = this.assetsToDelete.pop();
            boolean contains = this.downloadItems.containsKey(assetId);
            if(contains == false) {
                continue;
            }
            File dir = new File(this.downloadItems.get(assetId).getDownloadPath());
            if(dir.exists()) {
                try {
                    FileUtils.deleteDirectory(dir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            this.downloadItems.remove(assetId);
        }
    }

    /**
     * Saves json download summary in filesystem
     */
    public static void saveJsonSummary() {
        JSONArray summaryJson = new JSONArray();
        for (DownloadInfo info : summary.values()) {
            JSONObject infoJson = new JSONObject();
            try {
                infoJson.put("state", info.state.ordinal());
                infoJson.put("path", info.downloadPath);
                infoJson.put("progress", info.progress);
                infoJson.put("downloadedBytes", info.downloadedBytes);
                infoJson.put("uuid", info.uuid.toString());
                infoJson.put("online", info.onlinePlayable.getJson());
                infoJson.put("offline", info.offlinePlayable == null ? null : info.offlinePlayable.getJson());
                infoJson.put("properties", info.properties == null ? null : info.properties.getJson());
                summaryJson.put(infoJson);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        File rootDir = new File(DOWNLOAD_BASE_PATH);
        File summaryJsonFile = new File(rootDir, "summary.json");
        try {
            if(summaryJsonFile.getParentFile().exists() == false) {
                summaryJsonFile.getParentFile().mkdirs();
            }
            FileUtils.writeStringToFile(summaryJsonFile, summaryJson.toString(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates download summary
     *
     * @param assetId
     * @param info
     */
    public synchronized static void updateSummary(String assetId, DownloadInfo info) {
        long currentMillis = System.currentTimeMillis();

        File rootDir = new File(DOWNLOAD_BASE_PATH);
        File summaryFile = new File(rootDir, "summary.ser");
        if (info == null) {
            summary.remove(assetId);
        }
        else {
            summary.put(assetId, info);
        }
        //FileSerializer.write(summary, summaryFile.getAbsolutePath());
        saveJsonSummary();
        long elapsedTime = System.currentTimeMillis() - currentMillis;

        Log.d(TAG, "Summary serialization segmentDuration: " + elapsedTime + "ms");
    }

    /**
     * Syncs download summary with filesystem
     */
    public void syncWithStorage() {
        long currentMillis = System.currentTimeMillis();
        File rootDir = new File(DOWNLOAD_BASE_PATH);
        File summaryFile = new File(rootDir, "summary.json");
        if (summaryFile.exists()) {
            try {
                String summaryContent = FileUtils.readFileToString(summaryFile, "UTF-8");
                JSONArray summaryJson = new JSONArray(summaryContent);
                EmpBaseBuilder assetBuilder = new EmpBaseBuilder(null);
                summary = new HashMap<>();
                for (int i = 0; i < summaryJson.length(); ++i) {
                    JSONObject infoJson = summaryJson.getJSONObject(i);
                    DownloadInfo info = new DownloadInfo();

                    info.state = DownloadItem.State.values()[infoJson.optInt("state", DownloadItem.State.FAILED.ordinal())];
                    info.downloadPath = infoJson.optString("path", "");
                    info.progress = infoJson.optDouble("progress", 0.0);
                    info.downloadedBytes = infoJson.optLong("downloadedBytes", 0);
                    info.uuid = UUID.fromString(infoJson.optString("uuid", UUID.randomUUID().toString()));
                    info.properties = new DownloadProperties();
                    info.properties.fromJson(infoJson.optJSONObject("properties"));

                    EmpAsset onlineAsset = new EmpAsset();
                    onlineAsset = assetBuilder.getAsset(infoJson.optJSONObject("online"), onlineAsset, false);
                    info.onlinePlayable = onlineAsset;

                    JSONObject offlineJson = infoJson.optJSONObject("offline");
                    if (offlineJson != null && offlineJson.has("localSrc")) {
                        EmpOfflineAsset offlineAsset = new EmpOfflineAsset();
                        offlineAsset.setProps(onlineAsset);
                        offlineAsset.localMediaPath = offlineJson.optString("localSrc");
                        info.offlinePlayable = offlineAsset;
                    }
                    summary.put(info.onlinePlayable.getId(), info);
                    this.createItemFromDownloadInfo(info);
                }
            } catch (Exception e) {
                summaryFile.delete();
                syncWithStorageSlow();
                saveJsonSummary();
                e.printStackTrace();
            }
        }
        else {
            summary = new HashMap<>();
        }
        long elapsedTime = System.currentTimeMillis() - currentMillis;
        Log.d(TAG, "Sync segmentDuration: " + elapsedTime + "ms, items: " + (summary == null ? 0 : summary.size()));
    }

    public void syncWithStorageSerialized() {
        long currentMillis = System.currentTimeMillis();
        File rootDir = new File(DOWNLOAD_BASE_PATH);
        File summaryFile = new File(rootDir, "summary.ser");
        if (summaryFile.exists()) {
            summary = FileSerializer.read(summaryFile.getAbsolutePath());
            if(summary != null) {
                for(DownloadInfo info : summary.values()) {
                    if(info == null) {
                        continue;
                    }
                    this.createItemFromDownloadInfo(info);
                }
            }
            else {
                summaryFile.delete();
                syncWithStorageSlow();
            }
        }
        else {
            summary = new HashMap<>();
        }
        long elapsedTime = System.currentTimeMillis() - currentMillis;
        Log.d(TAG, "Sync segmentDuration: " + elapsedTime + "ms, items: " + (summary == null ? 0 : summary.size()));
    }

    private void syncWithStorageSlow() {
        long currentMillis = System.currentTimeMillis();
        summary = new HashMap<>();
        File rooDir = new File(DOWNLOAD_BASE_PATH);
        File[] assets = rooDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory();
            }
        });

        if (assets == null) {
            return;
        }

        for (File file : assets) {
            if (file.getName().equals(".") || file.getName().equals("..")) {
                continue;
            }
            File downloadInfo = new File (file.getAbsoluteFile(), "info.ser");

            if (downloadInfo.exists() == false) {
                try {
                    FileUtils.deleteDirectory(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                continue;
            }

            DownloadInfo info = FileSerializer.read(downloadInfo.getAbsolutePath());

            if (info != null) {
                summary.put(info.onlinePlayable.getId(), info);
                this.createItemFromDownloadInfo(info);
            }
            else {
                //TODO: consider delete folder
            }
        }

        File rootDir = new File(DOWNLOAD_BASE_PATH);
        File summaryFile = new File(rootDir, "summary.ser");
        FileSerializer.write(summary, summaryFile.getAbsolutePath());

        long elapsedTime = System.currentTimeMillis() - currentMillis;
        Log.d(TAG, "Sync segmentDuration: " + elapsedTime + "ms, items: " + assets.length);
    }
}
