package com.example.ftpsample;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.ftp.FtpEngineHybrid;
import com.example.ftpengine.FtpFileSystem;
import com.example.ftpengine.IFtpFileSystem;

import java.io.File;

/**
 * Foreground FTP Server Service
 * Keeps the FTP server alive in the background.
 * 
 * Updated to use external storage (/storage/emulated/0/FTP) instead of app-private directory.
 * Compatible with Android FTP clients (RCKit, WiFi File Transfer, FileZilla, etc.)
 */
public class FtpServerService extends Service {

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

            // Initialize FTP filesystem with external storage directory
            // This makes files accessible to FTP clients without root access
            // Path: /storage/emulated/0/FTP or similar
            File ftpRoot = StorageUtils.getFtpDirectory(this);
            
            if (!ftpRoot.canRead() || !ftpRoot.canWrite()) {
                throw new RuntimeException("Cannot access FTP directory: " + ftpRoot.getAbsolutePath());
            }

            IFtpFileSystem ftpFs = new FtpFileSystem(ftpRoot);

            // Start FTP engine
            ftpEngine = new FtpEngineHybrid(this, ftpFs);
            ftpEngine.start(2121);

            // Start foreground notification
            startForeground(
                    NOTIFICATION_ID,
                    buildNotification("FTP Server at: " + ftpRoot.getAbsolutePath())
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
