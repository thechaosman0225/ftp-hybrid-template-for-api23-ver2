package com.example.ftpsample;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.ftp.FtpEngineHybrid;
import com.example.ftp.AndroidUtils;
import com.example.ftpengine.FtpUserManager;
import com.example.ftpengine.saf.SAFFileSystem;

public class MainActivity extends AppCompatActivity {

    private SAFFileSystem safFs;
    private FtpEngineHybrid ftpEngine;
    private LogUtils logger;
    private FtpUserManager userManager;

    private ActivityResultLauncher<android.content.Intent> folderPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView txtLog = findViewById(R.id.txtLog);
        ScrollView scrollView = findViewById(R.id.scrollView);
        logger = new LogUtils(txtLog, scrollView);

        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnAddUser = findViewById(R.id.btnAddUser);

        Button btnChooseFolder = findViewById(R.id.btnChooseFolder);
        Button btnStart = findViewById(R.id.btnStartServer);
        Button btnStop = findViewById(R.id.btnStopServer);

        // Folder picker launcher
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null) {
                            AndroidUtils.takePersistablePermission(this, treeUri);
                            safFs = new SAFFileSystem(this, treeUri);
                            logger.log("Selected folder: " + treeUri.getPath());
                        }
                    }
                });

        btnChooseFolder.setOnClickListener(v -> folderPickerLauncher.launch(AndroidUtils.requestSAFRootFolder()));

        btnStart.setOnClickListener(v -> {
            if (safFs == null) {
                Toast.makeText(this, "Please choose a folder first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ftpEngine != null) {
                Toast.makeText(this, "FTP Server already running", Toast.LENGTH_SHORT).show();
                return;
            }

            ftpEngine = new FtpEngineHybrid(this, safFs);
            userManager = ftpEngine.getProcessor().getUserManager(); // get the user manager
            logger.log("Default account: admin / admin");

            new Thread(() -> {
                try {
                    ftpEngine.start(2121);
                    runOnUiThread(() -> logger.log("FTP Server started on port 2121"));
                } catch (Exception e) {
                    runOnUiThread(() -> logger.log("Failed to start FTP Server: " + e.getMessage()));
                    e.printStackTrace();
                }
            }).start();
        });

        btnStop.setOnClickListener(v -> {
            if (ftpEngine != null) {
                new Thread(() -> {
                    ftpEngine.stop();
                    runOnUiThread(() -> logger.log("FTP Server stopped"));
                    ftpEngine = null;
                    userManager = null;
                }).start();
            } else {
                Toast.makeText(this, "FTP Server is not running", Toast.LENGTH_SHORT).show();
            }
        });

        // Add new user account
        btnAddUser.setOnClickListener(v -> {
            if (userManager == null) {
                Toast.makeText(this, "Start the FTP server first", Toast.LENGTH_SHORT).show();
                return;
            }

            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Username or password cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            userManager.addUser(username, password);
            logger.log("Added new user: " + username);
            etUsername.setText("");
            etPassword.setText("");
        });
    }
}