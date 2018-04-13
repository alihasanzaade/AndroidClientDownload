package net.ericsson.emovs.download.drm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.drm.DrmSession;
import com.google.android.exoplayer2.drm.OfflineLicenseHelper;
import com.google.android.exoplayer2.drm.UnsupportedDrmException;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Util;

import java.io.IOException;

import static com.google.android.exoplayer2.ExoPlayerLibraryInfo.TAG;

/**
 * This class is responsible for downloading and storing a Widevine license in SharedStorage
 *
 * Created by Joao Coelho on 2017-09-21.
 */
public class WidevineDownloadLicenseManager {
    private final String EMP_WIDEVINE_KEYSTORE = "EMP_WIDEVINE_KEYSTORE";
    private final String KEY_OFFLINE_MEDIA_ID  = "OFFLINE_KEY_";

    private Context ctx;

    public WidevineDownloadLicenseManager(Context ctx) {
        this.ctx = ctx;
    }

    /**
     * Stores a downloaded license in the SharedStorage
     *
     * @param mediaId
     * @param offlineAssetKeyId
     */
    public void store(String mediaId, byte[] offlineAssetKeyId) {
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.putString(KEY_OFFLINE_MEDIA_ID + mediaId, Base64.encodeToString (offlineAssetKeyId, Base64.DEFAULT));
        editor.commit();
    }

    /**
     * Downloads a license from a specific location
     *
     * @param licenseUrl
     * @param initDataBase64
     * @return
     */
    public byte[] download(String licenseUrl, String initDataBase64) {
        if (Util.SDK_INT < 18) {
            return null;
        }

        try {
            // GenericDrmCallback customDrmCallback = new GenericDrmCallback(buildHttpDataSourceFactory(true), licenseUrl);
            OfflineLicenseHelper offlineLicenseHelper = OfflineLicenseHelper.newWidevineInstance(licenseUrl, buildHttpDataSourceFactory(true));

            byte[] initData = null;
            if (initDataBase64 != null) {
                initData = Base64.decode(initDataBase64, Base64.DEFAULT);
            }
            //byte[] offlineAssetKeyId = offlineLicenseHelper.downloadLicense(new DrmInitData(new DrmInitData.SchemeData(C.WIDEVINE_UUID, null, "mimeType", initData)));
            byte[] offlineAssetKeyId = offlineLicenseHelper.downloadLicense(new DrmInitData(new DrmInitData.SchemeData(C.WIDEVINE_UUID, "video/mp4", initData, false)));

            if (offlineAssetKeyId == null) {
                return null;
            }

            Pair<Long, Long> remainingTime = offlineLicenseHelper.getLicenseDurationRemainingSec(offlineAssetKeyId);

            Log.e(TAG, "Widevine license : " + Base64.encodeToString (offlineAssetKeyId, Base64.DEFAULT));

            if (remainingTime == null) {
                return null;
            }

            Log.e(TAG, "Widevine license remaining seconds: " + remainingTime.first);
            Log.e(TAG, "Widevine playback remaining seconds: " + remainingTime.second);

            if (remainingTime.first == 0 || remainingTime.second == 0) {
                return null;
            }

            return offlineAssetKeyId;
        }
        catch (UnsupportedDrmException | DrmSession.DrmSessionException e1) {
            e1.printStackTrace();
        }

        return null;
    }

    private HttpDataSource.Factory buildHttpDataSourceFactory(boolean useBandwidthMeter) {
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        return new DefaultHttpDataSourceFactory(Util.getUserAgent(this.ctx, "EMP-Player"), useBandwidthMeter ? bandwidthMeter : null);
    }

    private SharedPreferences getSharedPreferences() {
        return this.ctx.getSharedPreferences(EMP_WIDEVINE_KEYSTORE, Context.MODE_PRIVATE);
    }
}
