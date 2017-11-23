package net.ericsson.emovs.download.interfaces;


import net.ericsson.emovs.utilities.entitlements.Entitlement;

/**
 * Implement this interface if you want to create a listener for your downloads
 *
 * Created by Joao Coelho on 2017-09-27.
 */
public interface IDownloadEventListener {
    /**
     * Fired when downlad starts
     */
    void onStart();

    /**
     * Fired when progress is updated
     * @param progress
     */
    void onProgressUpdate(double progress);

    /**
     * Fired when download is paused
     */
    void onPause();

    /**
     * Fired when download is resumed
     */
    void onResume();

    /**
     * Fired when an entitlemend is ready for a specific download entry
     * @param entitlement
     */
    void onEntitlement(Entitlement entitlement);

    /**
     * Fired when an error occurs
     * @param errorCode
     * @param errorMessage
     */
    void onError(int errorCode, String errorMessage);

    /**
     * Fired when download completes successfully
     */
    void onSuccess();

    /**
     * Fired when download is stopped
     */
    void onStop();
}
