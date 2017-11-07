package net.ericsson.emovs.download;

import net.ericsson.emovs.exposure.interfaces.IPlayable;
import net.ericsson.emovs.exposure.models.EmpOfflineAsset;

import java.io.Serializable;
import java.util.UUID;

/**
 * Created by Joao Coelho on 2017-10-21.
 */

public class DownloadInfo implements Serializable {
    DownloadItem.State state;
    double progress;
    long downloadedBytes;
    String downloadPath;
    UUID uuid;
    IPlayable onlinePlayable;
    EmpOfflineAsset offlinePlayable;
}
