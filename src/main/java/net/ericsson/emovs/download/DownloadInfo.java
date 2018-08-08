package net.ericsson.emovs.download;

import net.ericsson.emovs.utilities.interfaces.IPlayable;
import net.ericsson.emovs.utilities.models.EmpOfflineAsset;

import java.io.Serializable;
import java.util.UUID;

/**
 * Created by Joao Coelho on 2017-10-21.
 */

public class DownloadInfo implements Serializable {
    public DownloadItem.State state;
    public double progress;
    public long downloadedBytes;
    public String downloadPath;
    public UUID uuid;
    public IPlayable onlinePlayable;
    public EmpOfflineAsset offlinePlayable;
    public DownloadProperties properties;
}
