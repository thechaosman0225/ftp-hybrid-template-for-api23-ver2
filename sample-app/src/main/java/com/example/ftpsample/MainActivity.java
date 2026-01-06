package com.example.ftpsample;

import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.ftp.FtpEngineHybrid;
import com.example.ftp.AndroidUtils;
import com.example.ftpengine.saf.SAFFileSystem;

public class MainActivity extends AppCompatActivity {

    private SAFFileSystem safFs;
    private FtpEngineHybrid ftpEngine;
    private ActivityResultLauncher<android.content.Intent> folderPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);

        Button btnChooseFolder = findViewById(R.id.btnChooseFolder);
        Button btnStart = findViewById(R.id.btnStartServer);
        Button btnStop = findViewById(R.id.btnStopServer);
        Button btnAddUser = findViewById(R.id.btnAddUser);

        TextView txtLog = findViewById(R.id.txtLog);
        ScrollView scrollView = findViewById(R.id.scrollView);

        LogUtils logger = new LogUtils(txtLog, scrollView);

        // SAF folder picker
        folderPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        if (treeUri != null) {
                            AndroidUtils.takePersistablePermission(this, treeUri);
                            safFs = new SAFFileSystem(this, treeUri);
                            logger.log("Selected FTP root: " + treeUri.getPath());
                        }
                    }
                });

        btnChooseFolder.setOnClickListener(v ->
                folderPickerLauncher.launch(AndroidUtils.requestSAFRootFolder())
        );

        btnStart.setOnClickListener(v -> {
            if (safFs == null) {
                Toast.makeText(this, "Please choose a folder first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ftpEngine != null) {
                Toast.makeText(this, "FTP server already running", Toast.LENGTH_SHORT).show();
                return;
            }

            ftpEngine = new FtpEngineHybrid(this, safFs);

            new Thread(() -> {
                try {
                    ftpEngine.start(2121);
                    runOnUiThread(() ->
                            logger.log("FTP server started on port 2121")
                    );
                } catch (Exception e) {
                    runOnUiThread(() ->
                            logger.log("Failed to start server: " + e.getMessage())
                    );
                }
            }).start();
        });

        btnStop.setOnClickListener(v -> {
            if (ftpEngine == null) {
                Toast.makeText(this, "FTP server is not running", Toast.LENGTH_SHORT).show();
                return;
            }

            new Thread(() -> {
                ftpEngine.stop();
                ftpEngine = null;
                runOnUiThread(() -> logger.log("FTP server stopped"));
            }).start();
        });

        // ✅ Add FTP user via ENGINE API (safe)
        btnAddUser.setOnClickListener(v -> {
            if (ftpEngine == null) {
                Toast.makeText(this, "Start the FTP server first", Toast.LENGTH_SHORT).show();
                return;
            }

            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Username and password required", Toast.LENGTH_SHORT).show();
                return;
            }

            ftpEngine.addUser(username, password);
            logger.log("Added FTP user: " + username);

            etUsername.setText("");
            etPassword.setText("");
        });
    }
}
