package net.ericsson.emovs.download;

import android.content.Intent;

import net.ericsson.emovs.utilities.interfaces.IPlayable;
import net.ericsson.emovs.utilities.system.ServiceUtils;

import net.ericsson.emovs.download.interfaces.IDownload;
import net.ericsson.emovs.utilities.emp.EMPRegistry;

import java.util.ArrayList;

/**
 * Use this class to queue assets for download and check current download status and overall download management
 *
 * Created by Joao Coelho on 2017-10-05.
 */
public class EMPDownloadProvider {
    private static final String TAG = EMPDownloadProvider.class.toString();

    Intent downloadServiceIntent;

    private static class EMPDownloadProviderHolder {
        private final static EMPDownloadProvider sInstance = new EMPDownloadProvider();
    }

    /**
     * @return
     */
    public static EMPDownloadProvider getInstance() {
        return EMPDownloadProviderHolder.sInstance;
    }

    protected EMPDownloadProvider() {
    }

    /**
     * Queue a playable for download: currently only EmpAsset objects are supported
     *
     * @param playable
     * @throws Exception
     */
    public void add(IPlayable playable) throws Exception {
        boolean ret = DownloadItemManager.getInstance().createItem(playable);
        if (ret) {
            startService();
        }
    }

    /**
     * Pause an ongoing download for a specific asset
     *
     * @param playable
     */
    public void pause(IPlayable playable) {
        DownloadItemManager.getInstance().pause(playable);
    }

    /**
     * Resume a paused download for a specific asset
     *
     * @param playable
     */
    public void resume(IPlayable playable) {
        DownloadItemManager.getInstance().resume(playable);
    }

    /**
     * Immediatly retry a failed download
     *
     * @param playable
     */
    public void retry(IPlayable playable) {
        DownloadItemManager.getInstance().retry(playable);
    }

    /**
     * Delete a failed, downloaded or downloading asset
     *
     * @param playable
     */
    public void delete(IPlayable playable) {
        DownloadItemManager.getInstance().delete(playable);
        try {
            startService();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets a list of all download entries regardless of the state
     * @return
     */
    public ArrayList<IDownload> getDownloads() {
        return DownloadItemManager.getInstance().getDownloads();
    }

    /**
     * Gets a list of the download entries filtered by a specific state
     *
     * @param stateFilter
     * @return
     */
    public ArrayList<IDownload> getDownloads(DownloadItem.State stateFilter) {
        return DownloadItemManager.getInstance().getDownloads(stateFilter);
    }

    /**
     * Call this method to force start the background download service
     *
     * @throws Exception
     */
    public void startService() throws Exception {
        if (EMPRegistry.applicationContext() == null) {
            throw  new Exception("APP_NOT_BOUND_TO_DOWNLOADER_PROVIDER");
        }
        if (isDownloadServiceRunning() == false) {
            this.downloadServiceIntent = new Intent(EMPRegistry.applicationContext(), EMPDownloadService.class);
            this.downloadServiceIntent.setAction(EMPDownloadService.class.getName());
            EMPRegistry.applicationContext().startService(downloadServiceIntent);
        }
    }

    /**
     * Call this method to force stop the background download service
     */
    public void stopService() {
        if (isDownloadServiceRunning() == true) {
            EMPRegistry.applicationContext().stopService(this.downloadServiceIntent);
        }
    }

    /**
     * Helper method that informs if background download service is already running
     * @return
     */
    public boolean isDownloadServiceRunning() {
        if (EMPRegistry.applicationContext() == null) {
            return false;
        }
        return ServiceUtils.isServiceRunning(EMPRegistry.applicationContext(), EMPDownloadService.class);
    }

}
