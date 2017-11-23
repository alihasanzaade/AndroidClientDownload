package net.ericsson.emovs.download.interfaces;

import net.ericsson.emovs.download.DownloadItem;
import net.ericsson.emovs.utilities.interfaces.IPlayable;
import net.ericsson.emovs.utilities.models.EmpOfflineAsset;

/**
 * This interface exposes a public api for the developer to query important thing regarding a download
 *
 * Created by Joao Coelho on 2017-10-19.
 */
public interface IDownload {
    /**
     * Returns the downloaded asset object that can be used with EMPPlayer
     *
     * @return
     */
    EmpOfflineAsset getDownloadedAsset();

    /**
     * Returns a reference to the online asset that was downloaded
     *
     * @return
     */
    IPlayable getOnlinePlayable();

    /**
     * Returns the state of a download
     *
     * @return
     */
    DownloadItem.State getState();

    /**
     * Returns an error code of a failed download
     *
     * @return
     */
    int getErrorCode();

    /**
     * Returns the error message of a failed download
     *
     * @return
     */
    String getErrorMessage();

    /**
     * Returns estimated media size (bytes)
     *
     * @return
     */
    long getMediaEstimatedSize();

    /**
     * Returns current downloaded size (bytes)
     *
     * @return
     */
    long getDownloadedSize();

    /**
     * Returns current download progress
     *
     * @return
     */
    double getProgress();

    /**
     * Registers a download event listener
     *
     * @param key
     * @param listener
     */
    void addEventListener(String key, IDownloadEventListener listener);

    /**
     * Removes a download event listener from a key
     *
     * @param key
     */
    void removeEventListener(String key);
}
