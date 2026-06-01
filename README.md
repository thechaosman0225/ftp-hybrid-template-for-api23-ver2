# FTP Hybrid Server Template

An Android FTP server template built on **Apache MINA 2.x** with a pluggable filesystem backend.
Tested with FileZilla; supports passive mode, binary transfers, and multiple filesystem implementations:
**FtpFileSystem** (java.io.File) and **SAFFileSystem** (Android Storage Access Framework).

---

## 🆕 Recent Changes (v2.0)

This version has been **refactored to use FtpFileSystem backend** for simpler integration as an Android library dependency:

- ✅ **Removed SAF dependency** from the sample app — uses app's `getFilesDir()` directly
- ✅ **Flexible backend support** — `FtpEngineHybrid` now accepts `IFtpFileSystem` interface
- ✅ **Simplified embedding** — no folder picker or persistable URIs needed
- ✅ **Backward compatible** — still supports SAFFileSystem if you need it

### Migration from v1.x to v2.0

If you were using the old SAF-based version:

**Before (v1.x):**
```java
SAFFileSystem fs = new SAFFileSystem(context, treeUri);
FtpEngineHybrid engine = new FtpEngineHybrid(context, fs);
```

**After (v2.0):**
```java
// Option 1: File-based (recommended for simplicity)
File ftpRoot = context.getFilesDir();
IFtpFileSystem fs = new FtpFileSystem(ftpRoot);
FtpEngineHybrid engine = new FtpEngineHybrid(context, fs);

// Option 2: Still supports SAF if needed
Uri treeUri = ...; // from folder picker
IFtpFileSystem fs = new SAFFileSystem(context, treeUri);
FtpEngineHybrid engine = new FtpEngineHybrid(context, fs);
```

---

## Module Structure

```
ftp-hybrid-template/
├── ftp-engine-core/      # FTP protocol logic, filesystem abstractions, user management
├── ftp-hybrid-server/    # Connects MINA networking to ftp-engine-core
└── sample-app/           # Working Android app — FTP server UI with file-based backend
```

---

## Requirements

| Tool | Version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer |
| Android SDK | API 23 (Android 6.0) minimum |
| Java | 17 |
| Gradle | 8.x |

---

## Key Dependencies

Both `ftp-engine-core` and `ftp-hybrid-server` pull Apache MINA directly from
Maven Central — no local patched JAR or AAR is required.

```groovy
// ftp-engine-core/build.gradle and ftp-hybrid-server/build.gradle
implementation "org.apache.mina:mina-core:2.0.21"
```

The `mina-android-patched` module referenced in older versions of this template
has been **removed**. MINA 2.0.21 from Maven Central works on Android API 23+
without modification when the following packaging exclusions are present (already
configured in both modules):

```groovy
packagingOptions {
    resources {
        excludes += [
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
            "META-INF/ASL2.0"
        ]
    }
}
```

---

## Getting Started

### 1. Clone

```bash
git clone https://github.com/thechaosman0225/ftp-hybrid-template-for-api23-ver2.git
cd ftp-hybrid-template-for-api23-ver2
```

### 2. Open in Android Studio

File → Open → select the cloned folder. Let Gradle sync finish.

### 3. Run the sample app

Connect a physical device or start an emulator (API 23+), select
`sample-app` as the run configuration, and press Run.

---

## Using the Sample App

The sample app now uses a **file-based FTP server** (no folder picker needed):

1. **Start Server** — immediately starts the FTP server on port `2121`
   - FTP root is automatically set to the app's private directory
   - Directory path is displayed in the UI
2. **Add User** — enter a username and password, then tap "Add User"
3. **Connect** from FileZilla (or any FTP client):
   - Host: your device's local IP address (shown in logcat)
   - Port: `2121`
   - Protocol: FTP (plain, no TLS)
   - Logon type: Normal
   - Enter the username and password you added

### UI Layout

```
┌─────────────────────────────────────┐
│  FTP Root Directory               │
│  /data/data/.../files            │
└─────────────────────────────────────┘
    
  [Start Server] [Stop Server]

┌─────────────────────────────────────┐
│  Server Log                        │
│  (Shows connection and status)    │
└─────────────────────────────────────┘

  Add FTP User
  [Username input]
  [Password input]
  [Add User Button]
```

---

## Embedding the Server in Your Own App

### Option 1: File-Based Backend (Recommended)

```java
import com.example.ftp.FtpEngineHybrid;
import com.example.ftpengine.FtpFileSystem;
import com.example.ftpengine.IFtpFileSystem;

// In your Activity or Service:
File ftpRoot = getFilesDir(); // or any File path you choose

// Create filesystem
IFtpFileSystem fs = new FtpFileSystem(ftpRoot);

// Create and start engine
FtpEngineHybrid ftpEngine = new FtpEngineHybrid(this, fs);

// Add users
ftpEngine.getUserManager().addUser("admin", "password");

// Start (run off the main thread)
new Thread(() -> {
    try {
        ftpEngine.start(2121);
    } catch (Exception e) {
        e.printStackTrace();
    }
}).start();

// Stop when done
ftpEngine.stop();
```

### Option 2: SAF Backend (If You Need Scoped Storage)

```java
import com.example.ftpengine.saf.SAFFileSystem;

// Get a SAF tree Uri from ACTION_OPEN_DOCUMENT_TREE
Uri treeUri = ...; // from ActivityResultLauncher

// Take persistable permission
getContentResolver().takePersistableUriPermission(
    treeUri,
    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
);

// Create filesystem
IFtpFileSystem fs = new SAFFileSystem(this, treeUri);

// Create and start engine (same as Option 1 from here)
FtpEngineHybrid ftpEngine = new FtpEngineHybrid(this, fs);
ftpEngine.getUserManager().addUser("admin", "password");

new Thread(() -> ftpEngine.start(2121)).start();
```

### Long-Running Service

For production use, wrap the engine in a foreground Service:

```java
// FtpServerService.java
public class FtpServerService extends Service {
    private FtpEngineHybrid ftpEngine;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            File ftpRoot = getFilesDir();
            IFtpFileSystem fs = new FtpFileSystem(ftpRoot);
            
            ftpEngine = new FtpEngineHybrid(this, fs);
            ftpEngine.getUserManager().addUser("admin", "password");
            ftpEngine.start(2121);
            
            // Start foreground notification
            startForeground(1, buildNotification("FTP running..."));
        } catch (Exception e) {
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (ftpEngine != null) ftpEngine.stop();
        super.onDestroy();
    }
}
```

See `FtpServerService.java` in `sample-app` for a complete working example.

---

## Architecture

### Filesystem Abstraction

```
FTP Client (FileZilla)
        │  TCP
        ▼
  FtpIoHandlerAndroid     ← MINA IoHandler (ftp-hybrid-server)
        │
        ▼
  FtpCommandProcessor     ← Parses FTP commands (ftp-engine-core)
        │
        ▼
  IFtpFileSystem          ← Filesystem interface (ftp-engine-core)
        │
   ┌────┴────┐
   │         │
FtpFileSystem  SAFFileSystem
(java.io.File) (Android SAF)
```

This abstraction allows you to swap filesystem implementations without changing the FTP engine.
You can even implement your own `IFtpFileSystem` for cloud storage, encrypted filesystems, etc.

### Key Classes

| Class | Module | Purpose |
|---|---|---|
| `FtpEngineHybrid` | ftp-hybrid-server | Entry point — wires MINA to the command processor, accepts `IFtpFileSystem` |
| `FtpCommandProcessor` | ftp-engine-core | Handles FTP commands (USER, PASS, PASV, LIST, RETR, STOR, etc.) |
| `FtpSessionContext` | ftp-engine-core | Per-connection state (cwd, transfer type, passive socket) |
| `IFtpFileSystem` | ftp-engine-core | **Interface** — implement this to add custom filesystem backends |
| `FtpFileSystem` | ftp-engine-core | **Default** — java.io.File implementation (recommended for simplicity) |
| `SAFFileSystem` | ftp-engine-core | **Alternative** — Android SAF implementation (for scoped storage) |
| `FtpUserManager` | ftp-engine-core | In-memory username/password store |

---

## Filesystem Comparison

Choose based on your use case:

| Feature | FtpFileSystem | SAFFileSystem |
|---------|---------------|---------------|
| **Storage Backend** | `java.io.File` | Android SAF URIs |
| **Setup Complexity** | ✅ Simple (just a File path) | ⚠️ Complex (folder picker + permissions) |
| **Use Case** | Default, app-private storage | Scoped storage, user-selected folders |
| **Performance** | ✅ Direct I/O (faster) | ⚠️ Content provider overhead (slower) |
| **Storage Paths** | Any `File` path | Only SAF-granted URIs |
| **Recommended For** | Dependency libraries, internal use | User-accessible storage on API 30+ |

---

## Known Limitations

- **No TLS/FTPS** — plain FTP only. FileZilla will show "Insecure server" warning; this is expected.
- **Passive mode only** — active mode (PORT command) is not implemented.
- **In-memory user store** — users are lost when the server stops. Persist them yourself if needed.
- **Full file buffering** — RETR/STOR reads/writes the entire file into a `byte[]`. This works
  well for typical files but will cause `OutOfMemoryError` on very large files. Stream directly
  to/from the socket for production use.
- **No MLSD/SIZE** — newer FTP clients prefer these commands for accurate file sizes and
  timestamps. `LIST` in Unix `ls -l` format is used instead.

---

## Troubleshooting

### "Address already in use" exception

If the FTP server won't start after a crash, the port may still be bound. Solutions:
- Wait 30-60 seconds and try again (TIME_WAIT timeout)
- Restart the device
- Use `FIX 4` pattern in `MainActivity.java` — ensure `executor.shutdown()` is called in `onDestroy()`

### Slow file transfers

If transfers are slow:
- Check network connectivity between client and device
- Use `tcpdump` or Wireshark to verify FTP commands are being sent correctly
- Consider streaming file I/O instead of buffering entire files (modify `readFile()`/`writeFile()`)

### Cannot connect from FileZilla

Verify:
1. Device and client are on the same network
2. Device's IP address is correct (check `logcat` for "FTP started: ftp://...")
3. Port 2121 is not blocked by a firewall
4. Username and password were added before connecting

---

## Development & Contributing

### Project Structure

- **ftp-engine-core** — Pure Java, no Android dependencies. Can be used in non-Android projects.
- **ftp-hybrid-server** — Android-specific (uses `android.util.Log`). Contains MINA integration.
- **sample-app** — Full Android app demonstrating embedding the FTP server.

### Building Without Android

To test just the FTP engine (without Android):
```bash
cd ftp-engine-core
./gradlew test
```

### Custom Filesystem Implementation

To add your own filesystem backend (e.g., cloud storage, encryption):

```java
public class MyCustomFileSystem implements IFtpFileSystem {
    @Override
    public boolean exists(String path) { /* ... */ }
    
    @Override
    public boolean isDirectory(String path) { /* ... */ }
    
    @Override
    public byte[] readFile(String path) throws IOException { /* ... */ }
    
    @Override
    public void writeFile(String path, byte[] data) throws IOException { /* ... */ }
    
    // Implement other methods...
}

// Use it:
IFtpFileSystem fs = new MyCustomFileSystem();
FtpEngineHybrid engine = new FtpEngineHybrid(context, fs);
```

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

---

## Support

For issues, questions, or suggestions, please open a GitHub Issue or check the sample app for working examples.
