package net.ericsson.emovs.download;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.ebs.android.exposure.entitlements.Entitlement;
import com.ebs.android.exposure.interfaces.IPlayable;
import com.ebs.android.exposure.metadata.builders.EmpBaseBuilder;
import com.ebs.android.exposure.models.EmpAsset;
import com.ebs.android.exposure.models.EmpOfflineAsset;
import com.ebs.android.utilities.FileSerializer;

import net.ericsson.emovs.download.interfaces.IDownload;
import net.ericsson.emovs.utilities.ContextRegistry;

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
        DOWNLOAD_BASE_PATH = getExternalStorageDirectory().getPath() + "/" + DOWNLOAD_FOLDER + "/" + ContextRegistry.get().getPackageName() + "/";
        this.downloadItems = new HashMap<>();
        this.maxConcurrentDownloads = DEFAULT_CONCURRENT_DOWNLOADS;
        this.assetsToDelete = new LinkedList<>();
    }

    public boolean createItem(IPlayable playable) {
        if (this.downloadItems.containsKey(playable.getId())) {
            return false;
        }
        DownloadItem item = new DownloadItem(ContextRegistry.get(), playable);
        this.downloadItems.put(playable.getId(), item);
        return true;
    }

    private void createItemFromDownloadInfo(DownloadInfo info) {
        if (this.downloadItems.containsKey(info.onlinePlayable.getId())) {
           return;
        }
        DownloadItem item = new DownloadItem(ContextRegistry.get(), info);
        this.downloadItems.put(info.onlinePlayable.getId(), item);
    }

    public int count(int state) {
        int n = 0;
        for(DownloadItem item : this.downloadItems.values()) {
            if (item.getState() == state) {
                n++;
            }
        }
        return n;
    }

    public boolean canStartNewDownload() {
        return count(DownloadItem.STATE_PAUSED) + count(DownloadItem.STATE_DOWNLOADING) < maxConcurrentDownloads;
    }

    public boolean hasAssetsToDelete() {
        return this.assetsToDelete.size() > 0;
    }

    public void downloadNext() {
        for (DownloadItem item : this.downloadItems.values()) {
            if (item.getState() == DownloadItem.STATE_QUEUED) {
                item.download();
                break;
            }
        }
    }

    public ArrayList<IDownload> getDownloads(int stateFilter) {
        ArrayList<IDownload> returnDownloads = new ArrayList<>();
        for (DownloadItem item : this.downloadItems.values()) {
            if (item.getState() == stateFilter) {
                returnDownloads.add(item);
            }
        }
        return returnDownloads;
    }

    public ArrayList<IDownload> getDownloads() {
        ArrayList<IDownload> returnDownloads = new ArrayList<>();
        for (DownloadItem item : this.downloadItems.values()) {
            returnDownloads.add(item);
        }
        return returnDownloads;
    }

    public void pause(IPlayable playable) {
        boolean contains = this.downloadItems.containsKey(playable.getId());
        if(contains == false) {
            return;
        }
        this.downloadItems.get(playable.getId()).pause();
    }

    public void resume(IPlayable playable) {
        boolean contains = this.downloadItems.containsKey(playable.getId());
        if(contains == false) {
            return;
        }
        this.downloadItems.get(playable.getId()).resume();
    }

    public void delete(IPlayable playable) {
        if (this.downloadItems.containsKey(playable.getId()) == false) {
            return;
        }
        this.downloadItems.get(playable.getId()).delete();
        this.assetsToDelete.add(playable.getId());
        updateSummary(playable.getId(), null);
    }

    public void retry(IPlayable playable) {
        boolean contains = this.downloadItems.containsKey(playable.getId());
        if(contains == false) {
            return;
        }
        this.downloadItems.get(playable.getId()).retry();
    }

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

    public static void saveJsonSummary() {
        JSONArray summaryJson = new JSONArray();
        for (DownloadInfo info : summary.values()) {
            JSONObject infoJson = new JSONObject();
            try {
                infoJson.put("state", info.state);
                infoJson.put("path", info.downloadPath);
                infoJson.put("progress", info.progress);
                infoJson.put("downloadedBytes", info.downloadedBytes);
                infoJson.put("uuid", info.uuid.toString());
                infoJson.put("online", info.onlinePlayable.getJson());
                infoJson.put("offline", info.offlinePlayable == null ? null : info.offlinePlayable.getJson());
                summaryJson.put(infoJson);

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        File rootDir = new File(DOWNLOAD_BASE_PATH);
        File summaryJsonFile = new File(rootDir, "summary.json");
        try {
            FileUtils.writeStringToFile(summaryJsonFile, summaryJson.toString(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

        Log.d(TAG, "Summary serialization duration: " + elapsedTime + "ms");
    }

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
                    info.state = infoJson.optInt("state", DownloadItem.STATE_FAILED);
                    info.downloadPath = infoJson.optString("path", "");
                    info.progress = infoJson.optDouble("progress", 0.0);
                    info.downloadedBytes = infoJson.optLong("downloadedBytes", 0);
                    info.uuid = UUID.fromString(infoJson.optString("uuid", UUID.randomUUID().toString()));

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
        Log.d(TAG, "Sync duration: " + elapsedTime + "ms, items: " + (summary == null ? 0 : summary.size()));
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
        Log.d(TAG, "Sync duration: " + elapsedTime + "ms, items: " + (summary == null ? 0 : summary.size()));
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
        Log.d(TAG, "Sync duration: " + elapsedTime + "ms, items: " + assets.length);
    }
}
