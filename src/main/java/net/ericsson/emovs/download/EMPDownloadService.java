package net.ericsson.emovs.download;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.ebs.android.utilities.RunnableThread;


/**
 * Created by Joao Coelho on 2017-10-05.
 */

public class EMPDownloadService extends Service {
    private static final String TAG = EMPDownloadService.class.toString();

    static final int NOTIFICATION_ID = 543;
    //private static boolean isRunning;
    private DownloadItemManager manager;

    public EMPDownloadService() {
        super();
        //isRunning = false;
        this.manager = new DownloadItemManager();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        new RunnableThread(new Runnable() {
            @Override
            public void run() {
                start();
            }
        }).start();
        this.showNotification();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        //isRunning = false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        // START_REDELIVER_INTENT ??
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }


    //public static boolean isRunning() {
    //    return isRunning;
    //}


    private void start() {
        Log.d(TAG, "Starting service.");
        //isRunning = true;
        run();
    }

    private void stop() {
        Log.d(TAG, "Stopping service.");
        stopForeground(true);
        stopSelf();
        //isRunning = false;
    }

    private void run() {
//        this.manager.syncWithStorage();
        for (;;) {
            try {
                //if (isRunning == false) {
                //    break;
                //}
                if(DownloadItemManager.getInstance().hasAssetsToDelete()) {
                    new RunnableThread(new Runnable() {
                        @Override
                        public void run() {
                            DownloadItemManager.getInstance().flushRemovedAssets();
                        }
                    }).start();
                }
                if (DownloadItemManager.getInstance().count(DownloadItem.STATE_QUEUED) > 0) {
                    if (DownloadItemManager.getInstance().canStartNewDownload()) {
                        Log.d(TAG, "Downloading next asset...");
                        DownloadItemManager.getInstance().downloadNext();
                    }
                    else {
                        Thread.sleep(100);
                        Log.d(TAG, "Waiting for download to finish...");
                        continue;
                    }
                }
                else if (DownloadItemManager.getInstance().count(DownloadItem.STATE_DOWNLOADING) == 0 &&
                         DownloadItemManager.getInstance().count(DownloadItem.STATE_PAUSED) == 0) {
                    stop();
                    break;
                }
                else {
                    Log.d(TAG, "Waiting for download to finish...");
                    Thread.sleep(100);
                }
            }
            catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private void showNotification() {
//        Notification notification = new NotificationCompat.Builder(this)
//                .setContentTitle("DownloadItem")
//                .setTicker(getResources().getString(R.string.app_name))
//                .setContentText("Downloading...")
//                .setSmallIcon(R.drawable.my_icon)
//                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
//                .setContentIntent(contentPendingIntent)
//                .setOngoing(true)
//                .setDeleteIntent(contentPendingIntent)  // if needed
//                .build();
//
//        // NO_CLEAR makes the notification stay when the user performs a "delete all" command
//        notification.flags = notification.flags | Notification.FLAG_NO_CLEAR;
//        startForeground(NOTIFICATION_ID, notification);
    }
}
