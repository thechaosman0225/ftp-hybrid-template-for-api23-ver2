package com.example.ftpsample;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.example.ftpengine.FtpEngineHybrid;
import com.example.ftpengine.filesystem.SafFileSystem;
import com.example.ftpengine.logging.AndroidFtpLogger;
import com.example.ftpengine.user.FtpUserManager;

public class MainActivity extends AppCompatActivity {

    private FtpEngineHybrid ftpEngine;
    private SafFileSystem safFs;
    private FtpUserManager userManager;
    private AndroidFtpLogger logger;

    private TextView txtStatus;

    // SAF Folder picker
    private final ActivityResultLauncher<Intent> folderPicker =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Uri treeUri = result.getData().getData();
                            getContentResolver().takePersistableUriPermission(
                                    treeUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            );

                            safFs = new SafFileSystem(this, treeUri);
                            logger.log("SAF root selected: " + treeUri);
                            txtStatus.setText("Folder selected ✔");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        logger = new AndroidFtpLogger(this);

        Button btnPickFolder = findViewById(R.id.btnPickFolder);
        Button btnStart = findViewById(R.id.btnStartServer);
        Button btnStop = findViewById(R.id.btnStopServer);
        txtStatus = findViewById(R.id.txtStatus);

        txtStatus.setText("Server stopped");

        // 📂 Pick SAF Folder
        btnPickFolder.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            folderPicker.launch(intent);
        });

        // ▶ START FTP SERVER
        btnStart.setOnClickListener(v -> {

            if (safFs == null) {
                Toast.makeText(this, "Please choose a folder first", Toast.LENGTH_SHORT).show();
                return;
            }

            if (ftpEngine != null) {
                Toast.makeText(this, "FTP server already running", Toast.LENGTH_SHORT).show();
                return;
            }

            // ⭐ FIX: constructor throws Exception → must catch
            try {
                ftpEngine = new FtpEngineHybrid(this, safFs);
                userManager = ftpEngine.getUserManager();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "FTP init failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }

            new Thread(() -> {
                try {
                    ftpEngine.start(1024);
                    runOnUiThread(() -> {
                        txtStatus.setText("Server running on port 1024");
                        logger.log("FTP server started on port 1024");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        txtStatus.setText("Start failed");
                        logger.log("Failed to start server: " + e.getMessage());
                    });
                }
            }).start();
        });

        // ⏹ STOP FTP SERVER
        btnStop.setOnClickListener(v -> {
            if (ftpEngine == null) {
                Toast.makeText(this, "Server not running", Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                try {
                    ftpEngine.stop();
                    ftpEngine = null;

                    runOnUiThread(() -> {
                        txtStatus.setText("Server stopped");
                        logger.log("FTP server stopped");
                    });

                } catch (Exception e) {
                    runOnUiThread(() ->
                            logger.log("Stop failed: " + e.getMessage())
                    );
                }
            }).start();
        });
    }
}
