package com.example.ftpengine;

import org.apache.mina.core.session.IoSession;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class FtpCommandProcessor {

    private final IFtpFileSystem fs;
    private final FtpUserManager users;
    private final String ip;

    public FtpCommandProcessor(IFtpFileSystem fs, FtpUserManager users, String ip) {
        this.fs = fs;
        this.users = users;
        this.ip = ip;
    }

    private void reply(IoSession s, String m) {
        s.write(m + "\r\n");
    }

    public void handle(IoSession s, FtpSessionContext c, String line) {

        if (c.cwd == null) c.cwd = "/";

        String[] p = line.split(" ", 2);
        String cmd = p[0].toUpperCase(Locale.ROOT);
        String arg = p.length > 1 ? p[1] : null;

        boolean pre =
                cmd.equals("USER") || cmd.equals("PASS") ||
                cmd.equals("SYST") || cmd.equals("FEAT") ||
                cmd.equals("NOOP") || cmd.equals("QUIT") ||
                cmd.equals("PWD");

        if (!c.loggedIn && !pre) {
            reply(s, "530 Login required");
            return;
        }

        switch (cmd) {

            case "USER":
                c.username = arg;
                reply(s, "331 OK");
                break;

            case "PASS":
                if (users.authenticate(c.username, arg)) {
                    c.loggedIn = true;
                    reply(s, "230 OK");
                } else {
                    reply(s, "530 FAIL");
                }
                break;

            case "SYST":
                reply(s, "215 UNIX");
                break;

            case "PWD":
                reply(s, "257 \"" + c.cwd + "\"");
                break;

            case "CWD":
                if (fs.exists(arg)) {
                    c.cwd = normalize(c.cwd + "/" + arg);
                    reply(s, "250 OK");
                } else reply(s, "550 NO DIR");
                break;

            case "TYPE":
                reply(s, "200 OK");
                break;

            case "PASV":
                openPasv(s, c);
                break;

            case "LIST":
                list(s, c);
                break;

            case "RETR":
                retr(s, c, arg);
                break;

            case "STOR":
                stor(s, c, arg);
                break;

            case "QUIT":
                reply(s, "221 BYE");
                s.closeNow();
                break;

            default:
                reply(s, "502 NOT IMPL");
        }
    }

    private void openPasv(IoSession s, FtpSessionContext c) {

        try {
            if (c.passiveServerSocket != null)
                c.passiveServerSocket.close();

            ServerSocket ss = new ServerSocket(0);
            ss.setReuseAddress(true);

            c.passiveServerSocket = ss;
            c.pasvPort = ss.getLocalPort();

            new Thread(() -> {
                try {
                    c.passiveDataSocket = ss.accept();
                } catch (Exception ignored) {}
            }).start();

            String[] ip = this.ip.split("\\.");

            reply(s,
                    "227 Entering Passive Mode (" +
                            ip[0]+","+ip[1]+","+ip[2]+","+ip[3]+"," +
                            (c.pasvPort / 256) + "," +
                            (c.pasvPort % 256) + ")"
            );

        } catch (Exception e) {
            reply(s, "425 PASV FAIL");
        }
    }

    private void list(IoSession s, FtpSessionContext c) {

        try {
            reply(s, "150 OPEN");

            Socket d = wait(c);
            if (d == null) {
                reply(s, "425 NO DATA");
                return;
            }

            StringBuilder sb = new StringBuilder();

            for (String f : fs.list(c.cwd)) {
                sb.append(f).append("\r\n");
            }

            d.getOutputStream().write(sb.toString().getBytes());
            d.close();

            reply(s, "226 DONE");

        } catch (Exception e) {
            reply(s, "550 FAIL");
        }
    }

    private void retr(IoSession s, FtpSessionContext c, String f) throws Exception {

        reply(s, "150 OPEN");

        Socket d = wait(c);
        if (d == null) {
            reply(s, "425 NO DATA");
            return;
        }

        byte[] data = fs.readFile(c.cwd + "/" + f);

        d.getOutputStream().write(data);
        d.close();

        reply(s, "226 DONE");
    }

    private void stor(IoSession s, FtpSessionContext c, String f) throws Exception {

        reply(s, "150 OPEN");

        Socket d = wait(c);
        if (d == null) {
            reply(s, "425 NO DATA");
            return;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = d.getInputStream();

        byte[] buf = new byte[8192];
        int r;

        while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
        }

        fs.writeFile(c.cwd + "/" + f, out.toByteArray());

        d.close();

        reply(s, "226 DONE");
    }

    private Socket wait(FtpSessionContext c) throws InterruptedException {

        for (int i = 0; i < 400; i++) {
            if (c.passiveDataSocket != null && c.passiveDataSocket.isConnected())
                return c.passiveDataSocket;

            Thread.sleep(25);
        }
        return null;
    }

    private String normalize(String p) {
        return p.replace("//", "/");
    }
}
