package net.ericsson.emovs.download;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import net.ericsson.emovs.download.interfaces.IDownload;
import net.ericsson.emovs.utilities.models.EmpAsset;
import net.ericsson.emovs.utilities.emp.EMPRegistry;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


import java.util.ArrayList;

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

//@RunWith(AndroidJUnit4.class)
public class DownloadItemManagerTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void assetSelectionTest() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();
        EMPRegistry.bindApplicationContext(appContext);

        DownloadItemManager manager = new DownloadItemManager();

        EmpAsset asset = new EmpAsset();
        asset.assetId = "test_12345";

        manager.createItem(asset);
        Assert.assertTrue(manager.count(DownloadItem.State.QUEUED) == 1);
        manager.createItem(asset);
        Assert.assertTrue(manager.count(DownloadItem.State.QUEUED) == 1);
        ArrayList<IDownload> downloads = manager.getDownloads(DownloadItem.State.QUEUED);
        Assert.assertTrue(downloads.size() == 1 && downloads.get(0).getOnlinePlayable().getId() == asset.getId());
    }

    @Test
    public void canStartDownloadTest() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();
        EMPRegistry.bindApplicationContext(appContext);

        DownloadItemManager manager = new DownloadItemManager();

        EmpAsset asset = new EmpAsset();
        asset.assetId = "test_12345";

        manager.createItem(asset);
        Assert.assertTrue(manager.canStartNewDownload());
    }

    @Test
    public void assetDeletionTest() throws Exception {
        Context appContext = InstrumentationRegistry.getTargetContext();
        EMPRegistry.bindApplicationContext(appContext);

        DownloadItemManager manager = new DownloadItemManager();

        EmpAsset asset = new EmpAsset();
        asset.assetId = "test_12345";

        manager.createItem(asset);
        Assert.assertTrue(manager.count(DownloadItem.State.QUEUED) == 1);
        manager.createItem(asset);
        Assert.assertTrue(manager.count(DownloadItem.State.QUEUED) == 1);
        ArrayList<IDownload> downloads = manager.getDownloads(DownloadItem.State.QUEUED);
        Assert.assertTrue(downloads.size() == 1 && downloads.get(0).getOnlinePlayable().getId() == asset.getId());
        manager.delete(asset);
        Assert.assertTrue(manager.count(DownloadItem.State.QUEUED) == 1);
        manager.flushRemovedAssets();
        Assert.assertTrue(manager.count(DownloadItem.State.QUEUED) == 0);
    }

}