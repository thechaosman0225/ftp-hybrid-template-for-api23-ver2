package com.example.ftpsample;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;

/**
 * Utility class for handling external storage paths across Android versions.
 * 
 * Supports:
 * - Android 5.0-10: Direct /storage/emulated/0 access
 * - Android 11+: MANAGE_EXTERNAL_STORAGE permission
 */
public class StorageUtils {

    /**
     * Get the external storage root directory (readable by FTP clients).
     * 
     * On most devices: /storage/emulated/0
     * Fallback to app's external cache dir if needed
     */
    public static File getExternalStorageRoot(Context context) {
        // Try primary external storage (most common path)
        File externalDir = Environment.getExternalStorageDirectory();
        if (externalDir != null && externalDir.canRead() && externalDir.canWrite()) {
            return externalDir;
        }

        // Fallback to external files directory
        File fallback = context.getExternalFilesDir(null);
        if (fallback != null && fallback.canRead() && fallback.canWrite()) {
            return fallback;
        }

        // Last resort: app's cache directory
        File cache = context.getExternalCacheDir();
        if (cache != null && cache.canRead() && cache.canWrite()) {
            return cache;
        }

        // If all else fails, use internal storage
        return context.getFilesDir();
    }

    /**
     * Get a user-accessible FTP directory in external storage.
     * Creates "FTP" directory in the user's external storage root.
     */
    @TargetApi(Build.VERSION_CODES.R)
    public static File getFtpDirectory(Context context) {
        File storageRoot = getExternalStorageRoot(context);
        File ftpDir = new File(storageRoot, "FTP");
        
        if (!ftpDir.exists()) {
            ftpDir.mkdirs();
        }
        
        return ftpDir;
    }

    /**
     * Check if we have write permission to external storage.
     */
    public static boolean hasExternalStoragePermission(Context context) {
        File externalDir = Environment.getExternalStorageDirectory();
        if (externalDir == null) return false;
        
        return externalDir.canRead() && externalDir.canWrite();
    }

    /**
     * Get human-readable storage path for display.
     */
    public static String getStoragePathForDisplay(File storageRoot) {
        if (storageRoot == null) return "Unknown";
        return storageRoot.getAbsolutePath();
    }
}
