package net.ericsson.emovs.download;

import android.app.Activity;
import android.content.Context;

import net.ericsson.emovs.download.drm.WidevineDownloadLicenseManager;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

/**
 * Created by Benjamin on 2017-12-15.
 */

@RunWith(RobolectricTestRunner.class)
public class WidevineDownloadLicenseManagerTest {

    @Test
    public void testStore() throws Exception {
        Activity activity = Robolectric.setupActivity(Activity.class);
        Context appContext = activity.getApplicationContext();

        WidevineDownloadLicenseManager licenseManager = new WidevineDownloadLicenseManager(appContext);
        String keyId = "00000";
        String mediaId = "12345";
        licenseManager.store(mediaId, keyId.getBytes());
    }
}
