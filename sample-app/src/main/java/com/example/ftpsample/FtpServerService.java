package com.example.ftpsample;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.ftp.FtpEngineHybrid;
import com.example.ftp.AndroidUtils;
import com.example.ftpengine.saf.SAFFileSystem;

/**
 * Foreground FTP Server Service
 * Keeps the FTP server alive in the background.
 */
public class FtpServerService extends Service {

    public static final String EXTRA_TREE_URI = "tree_uri";

    private static final String CHANNEL_ID = "ftp_server_channel";
    private static final int NOTIFICATION_ID = 1001;

    private FtpEngineHybrid ftpEngine;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        try {

            // SAF folder URI from MainActivity
            String uriString = intent.getStringExtra(EXTRA_TREE_URI);

            if (uriString == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            Uri treeUri = Uri.parse(uriString);

            // Permission already granted by MainActivity
            SAFFileSystem safFs = new SAFFileSystem(this, treeUri);

            // Start FTP engine
            ftpEngine = new FtpEngineHybrid(this, safFs);
            ftpEngine.start(2121);

            // Start foreground notification
            startForeground(
                    NOTIFICATION_ID,
                    buildNotification("FTP Server running on port 2121")
            );

        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        if (ftpEngine != null) {
            ftpEngine.stop();
            ftpEngine = null;
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /* ========================================================= */
    /* Notification Helpers                                      */
    /* ========================================================= */

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "FTP Server",
                    NotificationManager.IMPORTANCE_LOW
            );

            channel.setDescription("Foreground FTP Server");

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String text) {

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Android FTP Server")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .build();
    }
}
