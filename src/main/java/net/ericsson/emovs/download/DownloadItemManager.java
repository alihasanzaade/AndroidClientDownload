package net.ericsson.emovs.download;

import android.app.Application;
import android.content.Context;

import com.ebs.android.exposure.entitlements.Entitlement;
import com.ebs.android.exposure.interfaces.IPlayable;
import com.ebs.android.utilities.FileSerializer;

import net.ericsson.emovs.download.interfaces.IDownload;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashMap;

import static android.os.Environment.getExternalStorageDirectory;

/**
 * Created by Joao Coelho on 2017-10-05.
 */

public class DownloadItemManager {
    private static final String TAG = DownloadItemManager.class.toString();

    // TODO: download folder should be specific for each app
    public static String DOWNLOAD_BASE_PATH = getExternalStorageDirectory().getPath() + "/empdownloads/";

    private final int DEFAULT_CONCURRENT_DOWNLOADS = 2;

    private Context app;
    private HashMap<String, DownloadItem> downloadItems;
    private int maxConcurrentDownloads;

    private static class DownloadItemManagerHolder {
        private final static DownloadItemManager sInstance = new DownloadItemManager();
    }

    public static DownloadItemManager getInstance() {
        return DownloadItemManagerHolder.sInstance;
    }

    public static void bind(Context app) {
        getInstance().app = app;
    }

    protected DownloadItemManager() {
        this.downloadItems = new HashMap<>();
        this.maxConcurrentDownloads = DEFAULT_CONCURRENT_DOWNLOADS;
    }

    public boolean createItem(IPlayable playable) {
        if (this.downloadItems.containsKey(playable.getId())) {
            return false;
        }
        DownloadItem item = new DownloadItem(this.app);
        item.setOnlinePlayable(playable);
        this.downloadItems.put(playable.getId(), item);
        return true;
    }

    private void createItemFromDownloadInfo(DownloadInfo info) {
        DownloadItem item = new DownloadItem(this.app, info);
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
        return count(DownloadItem.STATE_DOWNLOADING) < maxConcurrentDownloads;
    }

    public void downloadNext() {
        for (DownloadItem item : this.downloadItems.values()) {
            if (item.getState() == DownloadItem.STATE_QUEUED) {
                item.download();
                break;
            }
        }
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

    public void retry(IPlayable playable) {
        boolean contains = this.downloadItems.containsKey(playable.getId());
        if(contains == false) {
            return;
        }
        // TODO: should queue and not download immediately
        this.downloadItems.get(playable.getId()).retry();
    }

    public void syncWithStorage() {
        //TODO: make this async or create summary that includes all assets
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
                //TODO: consider delete folder
                continue;
            }

            DownloadInfo info = FileSerializer.read(downloadInfo.getAbsolutePath());

            if (info != null) {
                this.createItemFromDownloadInfo(info);
            }
            else {
                //TODO: consider delete folder
            }
        }
        // TODO: start potential downloads that were interrupted by service shutdown
    }

    //TODO: implement listener to onDownloadListUpdate

}
