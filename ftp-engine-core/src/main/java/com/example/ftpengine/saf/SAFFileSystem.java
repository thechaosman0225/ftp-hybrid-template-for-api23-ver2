package com.example.ftpengine.saf;

import android.content.Context;
import android.net.Uri;

import com.example.ftpengine.IFtpFileSystem;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * SAF-backed FTP FileSystem (FTP-client compatible version)
 */
public class SAFFileSystem implements IFtpFileSystem {

    private final Context context;
    private final Uri rootUri;

    public SAFFileSystem(Context context, Uri rootUri) {
        this.context = context.getApplicationContext();
        this.rootUri = rootUri;
    }

    private SAFFileObject getFile(String path) {
        return new SAFFileObject(context, rootUri, normalize(path));
    }

    /* ===================== CORE ===================== */

    @Override
    public boolean exists(String path) {
        return getFile(path).exists();
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        return getFile(path).mkdir();
    }

    @Override
    public boolean delete(String path) throws IOException {
        return getFile(path).delete();
    }

    @Override
    public boolean rename(String from, String to) throws IOException {
        return getFile(from).renameTo(to);
    }

    /* ===================== LIST (FIXED FOR FTP CLIENTS) ===================== */

    @Override
public String[] list(String path) throws IOException {

    List<SAFFileObject> files =
            getFile(path).list();

    if (files == null) {
        return new String[0];
    }

    List<String> names = new ArrayList<>();

    for (SAFFileObject f : files) {

        if (f == null) continue;

        String p = f.getPath();

        if (p == null) continue;

        int idx = p.lastIndexOf('/');

        if (idx >= 0 && idx < p.length() - 1) {
            names.add(p.substring(idx + 1));
        } else {
            names.add(p);
        }
    }

    return names.toArray(new String[0]);
}

    /* ===================== READ / WRITE ===================== */

    @Override
    public byte[] readFile(String path) throws IOException {
        try (InputStream in = getFile(path).openInput();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buf = new byte[8192];
            int r;

            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }

            return out.toByteArray();
        }
    }

    @Override
    public void writeFile(String path, byte[] data) throws IOException {
        try (OutputStream out = getFile(path).openOutput(false)) {
            out.write(data);
            out.flush();
        }
    }

    /* ===================== HELPERS ===================== */

    private String normalize(String path) {
        if (path == null || path.isEmpty()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        return path.replaceAll("/+", "/");
    }

    private String extractName(String fullPath) {
        if (fullPath == null) return "";
        int idx = fullPath.lastIndexOf('/');
        return (idx >= 0) ? fullPath.substring(idx + 1) : fullPath;
    }
}
