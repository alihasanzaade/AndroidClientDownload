package net.ericsson.emovs.download.interfaces;

/**
 * Created by Joao Coelho on 2017-09-27.
 */

public interface IDownloadEventListener {
    void onStart();
    void onProgressUpdate(double progress);
    void onPause();
    void onResume();
    void onCancel();
    void onFinish();

    // TODO: refactor old IDownloadCallback methods
    void onEntitlement(Object entitlement);
    void onError(int errorCode, String errorMessage);
    void onSuccess();
}
