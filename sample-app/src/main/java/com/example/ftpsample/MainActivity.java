package com.example.ftpsample;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.ftp.FtpEngineHybrid;
import com.example.ftpengine.FtpFileSystem;
import com.example.ftpengine.FtpUserManager;
import com.example.ftpengine.IFtpFileSystem;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MainActivity for FTP Server with FtpFileSystem backend.
 * 
 * Updated to:
 * - Use external storage (/storage/emulated/0/FTP) instead of app-private directory
 * - Request necessary storage permissions
 * - Support Android FTP clients (RCKit, WiFi File Transfer, etc.)
 */
public class MainActivity extends AppCompatActivity {

    private static final int STORAGE_PERMISSION_REQUEST = 42;

    private FtpEngineHybrid ftpEngine;
    private FtpUserManager userManager;
    private File ftpRoot;

    // FIX 1: Single-thread executor serialises start/stop and keeps them off
    // the main thread. A bare new Thread() per click allowed a slow start and
    // a fast stop to race and leave MINA half-open on API 23.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText etUsername   = findViewById(R.id.etUsername);
        EditText etPassword   = findViewById(R.id.etPassword);
        Button btnStart       = findViewById(R.id.btnStartServer);
        Button btnStop        = findViewById(R.id.btnStopServer);
        Button btnAddUser     = findViewById(R.id.btnAddUser);
        TextView txtLog       = findViewById(R.id.txtLog);
        TextView txtRootPath  = findViewById(R.id.txtRootPath);
        ScrollView scrollView = findViewById(R.id.scrollView);

        LogUtils logger = new LogUtils(txtLog, scrollView);

        // Check and request storage permissions
        if (!hasStoragePermissions()) {
            requestStoragePermissions();
            logger.log("Storage permissions requested. Please grant them to continue.");
            return;
        }

        // Initialize FTP root directory (external storage)
        ftpRoot = StorageUtils.getFtpDirectory(this);

        // Display FTP root path
        if (txtRootPath != null) {
            txtRootPath.setText("FTP Root: " + ftpRoot.getAbsolutePath());
        }

        /* ===================== START SERVER ===================== */

        btnStart.setOnClickListener(v -> {

            if (!hasStoragePermissions()) {
                Toast.makeText(this, "Storage permissions required", Toast.LENGTH_SHORT).show();
                requestStoragePermissions();
                return;
            }

            if (ftpEngine != null) {
                Toast.makeText(this, "FTP server already running", Toast.LENGTH_SHORT).show();
                return;
            }

            // FIX 2: Engine construction and start() both go to the executor.
            // Original code built FtpEngineHybrid on the main thread before
            // handing start() to a new Thread. On API 23, FtpEngineHybrid's
            // constructor resolves the SAF Uri and sets up MINA's NioProcessor
            // — both of which can stall long enough to trigger a silent kill.
            setServerButtonsEnabled(btnStart, btnStop, false);

            executor.submit(() -> {
                try {
                    IFtpFileSystem ftpFs = new FtpFileSystem(ftpRoot);
                    FtpEngineHybrid engine = new FtpEngineHybrid(this, ftpFs);
                    engine.start(2121);

                    // Only promote to field after a successful start so that
                    // the stop button and addUser guard (ftpEngine != null)
                    // never see a half-initialised engine.
                    ftpEngine   = engine;
                    userManager = engine.getUserManager();

                    runOnUiThread(() -> {
                        logger.log("FTP server started on port 2121");
                        logger.log("Root directory: " + ftpRoot.getAbsolutePath());
                        logger.log("Ready for FTP clients (FileZilla, RCKit, etc.)");
                        setServerButtonsEnabled(btnStart, btnStop, true);
                    });

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        logger.log("Start failed: " + e.getMessage());
                        e.printStackTrace();
                        setServerButtonsEnabled(btnStart, btnStop, true);
                    });
                }
            });
        });

        /* ===================== STOP SERVER ===================== */

        btnStop.setOnClickListener(v -> {

            if (ftpEngine == null) {
                Toast.makeText(this, "FTP server is not running", Toast.LENGTH_SHORT).show();
                return;
            }

            // FIX 3: Null out the fields before submitting so a second tap
            // while the executor is still stopping can't queue a duplicate
            // stop. The engine reference is captured in a local for the lambda.
            FtpEngineHybrid engineToStop = ftpEngine;
            ftpEngine   = null;
            userManager = null;

            setServerButtonsEnabled(btnStart, btnStop, false);

            executor.submit(() -> {
                try {
                    engineToStop.stop();
                } catch (Exception ignored) {}

                runOnUiThread(() -> {
                    logger.log("FTP server stopped");
                    setServerButtonsEnabled(btnStart, btnStop, true);
                });
            });
        });

        /* ===================== ADD USER ===================== */

        btnAddUser.setOnClickListener(v -> {

            if (ftpEngine == null || userManager == null) {
                Toast.makeText(this, "Start the FTP server first", Toast.LENGTH_SHORT).show();
                return;
            }

            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Username and password required", Toast.LENGTH_SHORT).show();
                return;
            }

            userManager.addUser(username, password);

            logger.log("Added FTP user: " + username);

            etUsername.setText("");
            etPassword.setText("");
        });
    }

    /**
     * Check if we have required storage permissions.
     */
    private boolean hasStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ requires MANAGE_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-10 requires READ/WRITE_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Pre-Android 6 doesn't require runtime permissions
    }

    /**
     * Request storage permissions from the user.
     */
    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.MANAGE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST
            );
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    STORAGE_PERMISSION_REQUEST
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permissions granted, reinitialize
                ftpRoot = StorageUtils.getFtpDirectory(this);
                TextView txtRootPath = findViewById(R.id.txtRootPath);
                if (txtRootPath != null) {
                    txtRootPath.setText("FTP Root: " + ftpRoot.getAbsolutePath());
                }
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions denied. App may not work properly.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // FIX 4: Shut down the executor and engine when the activity is destroyed.
    // Without this, MINA's selector threads outlive the activity. On re-launch
    // they hold port 2121 open and the new start() throws AddressInUseException
    // — a second silent crash on API 23.
    @Override
    protected void onDestroy() {
        super.onDestroy();
        FtpEngineHybrid engineToStop = ftpEngine;
        ftpEngine   = null;
        userManager = null;
        if (engineToStop != null) {
            executor.submit(() -> {
                try { engineToStop.stop(); } catch (Exception ignored) {}
            });
        }
        executor.shutdown();
    }

    // Helper — keeps both server buttons in a consistent enabled state while
    // background work is in flight so the user can't double-tap Start or Stop.
    private void setServerButtonsEnabled(Button btnStart, Button btnStop, boolean enabled) {
        btnStart.setEnabled(enabled);
        btnStop.setEnabled(enabled);
    }
}
