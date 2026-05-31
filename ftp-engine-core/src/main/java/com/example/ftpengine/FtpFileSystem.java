package com.example.ftpengine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;

public class FtpFileSystem implements IFtpFileSystem {

    private final File root;

    public FtpFileSystem(File rootDir) {
        this.root = rootDir;
        if (!root.exists()) root.mkdirs();
    }

    private File resolve(String path) {
        if (path == null || path.isEmpty()) path = "/";
        if (path.startsWith("/")) path = path.substring(1);
        File f = new File(root, path);
        try {
            String r = root.getCanonicalPath();
            String c = f.getCanonicalPath();
            if (!c.startsWith(r)) return root;
        } catch (Exception ignored) {}
        return f;
    }

    @Override
    public boolean exists(String path) {
        return resolve(path).exists();
    }

    // FIX: implement isDirectory() so FtpCommandProcessor can emit the
    // correct 'd' vs '-' prefix in LIST responses.
    @Override
    public boolean isDirectory(String path) {
        return resolve(path).isDirectory();
    }

    @Override
    public boolean mkdir(String path) throws IOException {
        return resolve(path).mkdirs();
    }

    @Override
    public boolean delete(String path) throws IOException {
        return resolve(path).delete();
    }

    @Override
    public boolean rename(String from, String to) throws IOException {
        File o = resolve(from);
        File n = resolve(to);
        return o.renameTo(n);
    }

    @Override
    public String[] list(String path) throws IOException {
        String[] items = resolve(path).list();
        if (items == null) return new String[0];
        Arrays.sort(items);
        return items;
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        File f = resolve(path);
        FileInputStream in = new FileInputStream(f);
        byte[] data = new byte[(int) f.length()];
        in.read(data);
        in.close();
        return data;
    }

    @Override
    public void writeFile(String path, byte[] data) throws IOException {
        File f = resolve(path);
        File parent = f.getParentFile();
        if (!parent.exists()) parent.mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        out.write(data);
        out.flush();
        out.close();
    }
}
