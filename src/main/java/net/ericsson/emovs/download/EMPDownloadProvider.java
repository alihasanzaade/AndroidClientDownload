package net.ericsson.emovs.download;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import com.ebs.android.exposure.interfaces.IPlayable;
import com.ebs.android.utilities.ServiceUtils;

import net.ericsson.emovs.download.interfaces.IDownload;
import net.ericsson.emovs.utilities.ContextRegistry;

import java.util.ArrayList;

/**
 * Created by Joao Coelho on 2017-10-05.
 */

public class EMPDownloadProvider {
    private static final String TAG = EMPDownloadProvider.class.toString();

    Intent downloadServiceIntent;

    private static class EMPDownloadProviderHolder {
        private final static EMPDownloadProvider sInstance = new EMPDownloadProvider();
    }

    public static EMPDownloadProvider getInstance() {
        return EMPDownloadProviderHolder.sInstance;
    }

    protected EMPDownloadProvider() {
    }

    public void add(IPlayable playable) throws Exception {
        boolean ret = DownloadItemManager.getInstance().createItem(playable);
        if (ret) {
            startService();
        }
    }

    public void pause(IPlayable playable) {
        DownloadItemManager.getInstance().pause(playable);
    }

    public void resume(IPlayable playable) {
        DownloadItemManager.getInstance().resume(playable);
    }

    public void retry(IPlayable playable) {
        DownloadItemManager.getInstance().retry(playable);
    }

    public void delete(IPlayable playable) {
        DownloadItemManager.getInstance().delete(playable);
        try {
            startService();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ArrayList<IDownload> getDownloads() {
        return DownloadItemManager.getInstance().getDownloads();
    }

    public ArrayList<IDownload> getDownloads(DownloadItem.State stateFilter) {
        return DownloadItemManager.getInstance().getDownloads(stateFilter);
    }

    public void startService() throws Exception {
        if (ContextRegistry.get() == null) {
            throw  new Exception("APP_NOT_BOUND_TO_DOWNLOADER_PROVIDER");
        }
        if (isDownloadServiceRunning() == false) {
            this.downloadServiceIntent = new Intent(ContextRegistry.get(), EMPDownloadService.class);
            this.downloadServiceIntent.setAction(EMPDownloadService.class.getName());
            ContextRegistry.get().startService(downloadServiceIntent);
        }
    }

    public void stopService() {
        if (isDownloadServiceRunning() == true) {
            ContextRegistry.get().stopService(this.downloadServiceIntent);
        }
    }

    public boolean isDownloadServiceRunning() {
        if (ContextRegistry.get() == null) {
            return false;
        }
        return ServiceUtils.isServiceRunning(ContextRegistry.get(), EMPDownloadService.class);
    }

}
