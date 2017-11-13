package net.ericsson.emovs.download.interfaces;

import net.ericsson.emovs.download.DownloadItem;
import net.ericsson.emovs.exposure.interfaces.IPlayable;
import net.ericsson.emovs.exposure.models.EmpOfflineAsset;

/**
 * Created by Joao Coelho on 2017-10-19.
 */

public interface IDownload {
    EmpOfflineAsset getDownloadedAsset();
    IPlayable getOnlinePlayable();
    DownloadItem.State getState();
    int getErrorCode();
    String getErrorMessage();
    long getMediaEstimatedSize();
    long getDownloadedSize();
    double getProgress();
    void addEventListener(String key, IDownloadEventListener listener);
    void removeEventListener(String key);
}
