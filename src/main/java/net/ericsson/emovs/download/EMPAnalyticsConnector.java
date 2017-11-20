package net.ericsson.emovs.download;

import net.ericsson.emovs.analytics.EMPAnalyticsProvider;
import net.ericsson.emovs.analytics.EventParameters;
import net.ericsson.emovs.download.interfaces.IDownloadEventListener;
import net.ericsson.emovs.utilities.Entitlement;

import java.util.HashMap;

/**
 * Created by Joao Coelho on 2017-09-27.
 */

public class EMPAnalyticsConnector implements IDownloadEventListener {
    DownloadItem downloader;
    Entitlement entitlement;

    public EMPAnalyticsConnector(DownloadItem downloader) {
        this.downloader = downloader;
    }

    @Override
    public void onStart() {
        if (downloader == null || this.entitlement == null) {
            return;
        }
        String sessionId = this.entitlement.playSessionId;
        if (sessionId == null) {
            return;
        }

        HashMap<String, String> parameters = new HashMap<>();

        // TODO: missing play mode
        parameters.put(EventParameters.Created.PLAY_MODE, "VOD");
        parameters.put(EventParameters.Created.VERSION, downloader.getVersion());
        parameters.put(EventParameters.Created.PLAYER, "EMP.Android");

        EMPAnalyticsProvider.getInstance().created(sessionId, parameters);
        EMPAnalyticsProvider.getInstance().downloadStarted(sessionId, null);
    }

    @Override
    public void onPause() {
        if (downloader == null || this.entitlement == null) {
            return;
        }
        String sessionId = this.entitlement.playSessionId;
        if (sessionId == null) {
            return;
        }

        EMPAnalyticsProvider.getInstance().downloadPaused(sessionId, null);
    }

    @Override
    public void onResume() {
        if (downloader == null || this.entitlement == null) {
            return;
        }
        String sessionId = this.entitlement.playSessionId;
        if (sessionId == null) {
            return;
        }

        EMPAnalyticsProvider.getInstance().downloadResumed(sessionId, null);
    }

    @Override
    public void onEntitlement(Entitlement entitlement) {
        if (downloader == null) {
            return;
        }
        this.entitlement = entitlement;
        HashMap<String, String> parameters = new HashMap<>();

        if (entitlement.channelId != null) {
            parameters.put(EventParameters.HandshakeStarted.ASSET_ID, entitlement.channelId);
        }
        else if (entitlement.assetId != null) {
            parameters.put(EventParameters.HandshakeStarted.ASSET_ID, entitlement.assetId);
        }

        if (entitlement.programId != null) {
            parameters.put(EventParameters.HandshakeStarted.PROGRAM_ID, entitlement.programId);
        }
        EMPAnalyticsProvider.getInstance().handshakeStarted(this.entitlement.playSessionId, parameters);
    }

    @Override
    public void onError(int errorCode, String errorMessage) {
        if (downloader == null || this.entitlement == null) {
            return;
        }
        String sessionId = this.entitlement.playSessionId;
        if (sessionId == null) {
            return;
        }

        HashMap<String, String> parameters = new HashMap<>();
        parameters.put(EventParameters.Error.CODE, Integer.toString(errorCode));
        if (errorMessage != null && errorMessage.equals("") == false) {
            parameters.put(EventParameters.Error.MESSAGE, errorMessage);
        }

        EMPAnalyticsProvider.getInstance().downloadError(sessionId, parameters);
    }

    @Override
    public void onSuccess() {
        if (downloader == null) {
            return;
        }
        String sessionId = this.entitlement.playSessionId;
        if (sessionId == null) {
            return;
        }

        EMPAnalyticsProvider.getInstance().downloadCompleted(sessionId, null);
    }

    @Override
    public void onStop() {
        if (downloader == null || this.entitlement == null) {
            return;
        }
        String sessionId = this.entitlement.playSessionId;
        if (sessionId == null) {
            return;
        }

        EMPAnalyticsProvider.getInstance().downloadStopped(sessionId, null);
    }

    @Override
    public void onProgressUpdate(double progress) {
        EMPAnalyticsProvider.getInstance().refresh();
    }
}
