package net.ericsson.emovs.download.interfaces;

import com.ebs.android.exposure.entitlements.Entitlement;

/**
 * Created by Joao Coelho on 2017-09-27.
 */

public interface IDownloadEventListener {
    void onStart();
    void onProgressUpdate(double progress);
    void onPause();
    void onResume();
    void onEntitlement(Entitlement entitlement);
    void onError(int errorCode, String errorMessage);
    void onSuccess();
}
