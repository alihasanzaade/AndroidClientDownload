package net.ericsson.emovs.download;

import android.app.Activity;
import android.content.Context;

import net.ericsson.emovs.utilities.models.EmpAsset;
import net.ericsson.emovs.utilities.emp.EMPRegistry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;


import static android.os.Environment.getExternalStorageDirectory;

/*
 * Copyright (c) 2017 Ericsson. All Rights Reserved
 *
 * This SOURCE CODE FILE, which has been provided by Ericsson as part
 * of an Ericsson software product for use ONLY by licensed users of the
 * product, includes CONFIDENTIAL and PROPRIETARY information of Ericsson.
 *
 * USE OF THIS SOFTWARE IS GOVERNED BY THE TERMS AND CONDITIONS OF
 * THE LICENSE STATEMENT AND LIMITED WARRANTY FURNISHED WITH
 * THE PRODUCT.
 */

@RunWith(RobolectricTestRunner.class)
public class DashDownloaderTest {

    //@Rule
    //public GrantPermissionRule mRuntimePermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.BLUETOOTH,Manifest.permission.RECORD_AUDIO);

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void manifestDurationTest() throws Exception {
        long timeSeconds = DashDownloader.getDuration("P0DT0H1M16.480S");
        Assert.assertEquals(timeSeconds, 76480);
    }

    @Test
    public void initTest() throws Exception {
        Activity activity = Robolectric.setupActivity(Activity.class);
        Context appContext = activity.getApplicationContext();

        EMPRegistry.bindApplicationContext(appContext);
        EmpAsset asset = new EmpAsset();
        asset.assetId = "test_12345";

        DownloadItemManager manager = new DownloadItemManager();
        Assert.assertTrue(DownloadItemManager.DOWNLOAD_BASE_PATH != null);
        Assert.assertTrue(manager.count(DownloadItem.State.COMPLETED) == 0);

        DownloadItem item = new DownloadItem(EMPRegistry.applicationContext(), asset, DownloadProperties.DEFAULT);
        DashDownloader downloader = new DashDownloader(item);

        Assert.assertFalse(downloader.isAlive());
        Assert.assertTrue(downloader.parent == item);
        Assert.assertTrue(downloader.errorMessage == null);

        downloader.init("https://psempdash.ebsd.ericsson.net/dash/DevGroup/EnigmaTV/5ff06bc9-dd05-4009-b002-29e51be5760e_enigma/5ff06bc9-dd05-4009-b002-29e51be5760e_enigma/vod.mpd", getExternalStorageDirectory().getPath(), DownloadProperties.DEFAULT);
        Assert.assertTrue(downloader.conf != null);
        Assert.assertFalse(downloader.isInterrupted());
    }

    @Test
    public void downloadSuccessTest() throws Exception {
        // TODO: find a way to store a static dash vod in azure a download that asset & give storage permission to android
        Activity activity = Robolectric.setupActivity(Activity.class);
        Context appContext = activity.getApplicationContext();

        EMPRegistry.bindApplicationContext(appContext);
        EmpAsset asset = new EmpAsset();
        asset.assetId = "test_12345";

        DownloadItemManager manager = new DownloadItemManager();
        Assert.assertTrue(DownloadItemManager.DOWNLOAD_BASE_PATH != null);
        Assert.assertTrue(manager.count(DownloadItem.State.COMPLETED) == 0);

        DownloadItem item = new DownloadItem(EMPRegistry.applicationContext(), asset, DownloadProperties.DEFAULT);
        DashDownloader downloader = new DashDownloader(item);

        Assert.assertFalse(downloader.isAlive());
        Assert.assertTrue(downloader.parent == item);
        Assert.assertTrue(downloader.errorMessage == null);

        downloader.init("https://psempdash.ebsd.ericsson.net/dash/DevGroup/EnigmaTV/peterz_test1_enigma/peterz_test1_enigma/vod.mpd?ownerId=devgroup&userToken=9f0b9603-7df4-49ef-982f-3e78c45eabaa&clientIp=192.36.29.123&signedAssetId=peterz_test1_enigma&playToken=CghkZXZncm91cBITcGV0ZXJ6X3Rlc3QxX2VuaWdtYRoNMTkyLjM2LjI5LjEyMyIkOWYwYjk2MDMtN2RmNC00OWVmLTk4MmYtM2U3OGM0NWVhYmFhKMXnwN75KzIAOgRGVk9EQghFbmlnbWFUVkoA%7Cx6BPfweXNpKQhWMw4B4JbxjFbFElx4Tpq85By2AnS%2BE%3D", getExternalStorageDirectory().getPath(), DownloadProperties.DEFAULT);

        Assert.assertTrue(downloader.conf != null);
        Assert.assertFalse(downloader.isInterrupted());

        downloader.start();
        Thread.sleep(5000);

        Assert.assertTrue("Error downloading manifest.".equals(downloader.getErrorMessage()));
        Assert.assertTrue(downloader.getErrorCode() == 6);
    }

    @Test
    public void downloadErrorDownloadingManifestTest() throws Exception {
        Activity activity = Robolectric.setupActivity(Activity.class);
        Context appContext = activity.getApplicationContext();

        EMPRegistry.bindApplicationContext(appContext);
        EmpAsset asset = new EmpAsset();
        asset.assetId = "test_12345";

        DownloadItemManager manager = new DownloadItemManager();
        Assert.assertTrue(DownloadItemManager.DOWNLOAD_BASE_PATH != null);
        Assert.assertTrue(manager.count(DownloadItem.State.COMPLETED) == 0);

        DownloadItem item = new DownloadItem(EMPRegistry.applicationContext(), asset, DownloadProperties.DEFAULT);
        DashDownloader downloader = new DashDownloader(item);

        Assert.assertFalse(downloader.isAlive());
        Assert.assertTrue(downloader.parent == item);
        Assert.assertTrue(downloader.errorMessage == null);

        downloader.init("https://my.wrong.manifest/vod.mpd", getExternalStorageDirectory().getPath(), DownloadProperties.DEFAULT);
        Assert.assertTrue(downloader.conf != null);
        Assert.assertFalse(downloader.isInterrupted());

        downloader.start();
        Thread.sleep(5000);

        Assert.assertTrue("Error downloading manifest.".equals(downloader.getErrorMessage()));
        Assert.assertTrue(downloader.getErrorCode() == 6);
    }
}