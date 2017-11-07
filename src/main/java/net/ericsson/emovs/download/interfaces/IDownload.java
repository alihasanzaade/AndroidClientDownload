package net.ericsson.emovs.download.interfaces;

import com.ebs.android.exposure.interfaces.IPlayable;
import com.ebs.android.exposure.models.EmpOfflineAsset;

import net.ericsson.emovs.download.DownloadItem;

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
    void addEventListener(IDownloadEventListener listener);
}
