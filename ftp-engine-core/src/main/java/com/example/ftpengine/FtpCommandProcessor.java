package com.example.ftpengine;

import org.apache.mina.core.session.IoSession;

import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Fully patched minimal FTP command processor for Android.
 * Passive mode only (ACTIVE disabled) with public access to user manager.
 */
public class FtpCommandProcessor {

    private final IFtpFileSystem fs;
    private final FtpUserManager users;

    public FtpCommandProcessor(IFtpFileSystem fs, FtpUserManager users) {
        this.fs = fs;
        this.users = users;
    }

    /* ===================== Core Reply ===================== */
    private void reply(IoSession session, String msg) {
        try {
            session.write((msg + "\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            try { session.close(); } catch (Exception ignored) {}
        }
    }

    /* ===================== Command Entry ===================== */
    public void handle(IoSession session, FtpSessionContext ctx, String line) {
        if (line == null) return;

        String[] parts = line.split(" ", 2);
        String cmd = parts[0].toUpperCase(Locale.ROOT);
        String arg = (parts.length > 1 ? parts[1] : null);

        // Enforce login
        if (!ctx.loggedIn &&
                !(cmd.equals("USER") || cmd.equals("PASS") || cmd.equals("QUIT"))) {
            reply(session, "530 Please login with USER and PASS");
            return;
        }

        try {
            switch (cmd) {
                case "USER":
                    ctx.username = arg;
                    reply(session, "331 User name okay, need password");
                    break;

                case "PASS":
                    if (users.authenticate(ctx.username, arg)) {
                        ctx.loggedIn = true;
                        reply(session, "230 User logged in, proceed");
                    } else {
                        reply(session, "530 Login incorrect");
                    }
                    break;

                case "SYST":
                    reply(session, "215 UNIX Type: L8");
                    break;

                case "FEAT":
                    reply(session, "211-Features\r\n PASV\r\n UTF8\r\n211 End");
                    break;

                case "PWD":
                    reply(session, "257 \"" + ctx.cwd + "\" is current directory");
                    break;

                case "CWD":
                    changeDirectory(session, ctx, arg);
                    break;

                case "TYPE":
                    reply(session, "200 Type set to " + arg);
                    break;

                case "PORT":
                    reply(session, "502 Active mode not supported, use PASV");
                    break;

                case "PASV":
                    handlePasv(session, ctx);
                    break;

                case "LIST":
                    handleList(session, ctx);
                    break;

                case "RETR":
                    handleRetr(session, ctx, arg);
                    break;

                case "STOR":
                    handleStor(session, ctx, arg);
                    break;

                case "NOOP":
                    reply(session, "200 OK");
                    break;

                case "QUIT":
                    reply(session, "221 Goodbye");
                    session.close();
                    break;

                default:
                    reply(session, "502 Command not implemented");
            }
        } catch (Exception e) {
            reply(session, "550 Internal server error");
        }
    }

    /* ===================== Helpers ===================== */
    private void changeDirectory(IoSession session, FtpSessionContext ctx, String arg) throws Exception {
        if (arg == null) {
            reply(session, "501 Missing directory");
            return;
        }

        String target = normalize(ctx.cwd + "/" + arg);
        if (fs.exists(target)) {
            ctx.cwd = target;
            reply(session, "250 Directory changed");
        } else {
            reply(session, "550 Directory not found");
        }
    }

    private String normalize(String p) {
        if (p == null || p.isEmpty()) return "/";
        if (!p.startsWith("/")) p = "/" + p;
        return p.replaceAll("/+", "/");
    }

    /* ===================== Passive Mode ===================== */
    private void handlePasv(IoSession session, FtpSessionContext ctx) {
        try {
            ServerSocket ss = new ServerSocket(0);
            ctx.pasvPort = ss.getLocalPort();

            new Thread(() -> {
                try {
                    ctx.passiveDataSocket = ss.accept();
                } catch (Exception ignored) {}
            }).start();

            String ip = session.getLocalAddress()
                    .toString().replace("/", "").split(":")[0];

            String[] parts = ip.split("\\.");
            int p1 = ctx.pasvPort / 256;
            int p2 = ctx.pasvPort % 256;

            reply(session,
                    "227 Entering Passive Mode (" +
                            parts[0] + "," + parts[1] + "," +
                            parts[2] + "," + parts[3] + "," +
                            p1 + "," + p2 + ")"
            );
        } catch (Exception e) {
            reply(session, "425 Can't open passive connection");
        }
    }

    /* ===================== Data Commands ===================== */
    private void handleList(IoSession session, FtpSessionContext ctx) throws Exception {
        reply(session, "150 Opening data connection");

        Socket data = waitForData(ctx);
        if (data == null) {
            reply(session, "425 Can't open data connection");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String f : fs.list(ctx.cwd)) sb.append(f).append("\r\n");

        data.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
        data.close();
        clearData(ctx);

        reply(session, "226 Transfer complete");
    }

    private void handleRetr(IoSession session, FtpSessionContext ctx, String filename) throws Exception {
        if (filename == null) {
            reply(session, "501 Missing filename");
            return;
        }

        reply(session, "150 Opening data connection");

        Socket data = waitForData(ctx);
        if (data == null) {
            reply(session, "425 Can't open data connection");
            return;
        }

        byte[] fileData = fs.readFile(ctx.cwd + "/" + filename);
        data.getOutputStream().write(fileData);
        data.close();
        clearData(ctx);

        reply(session, "226 Transfer complete");
    }

    private void handleStor(IoSession session, FtpSessionContext ctx, String filename) throws Exception {
        if (filename == null) {
            reply(session, "501 Missing filename");
            return;
        }

        reply(session, "150 Opening data connection");

        Socket data = waitForData(ctx);
        if (data == null) {
            reply(session, "425 Can't open data connection");
            return;
        }

        byte[] buffer = data.getInputStream().readAllBytes();
        fs.writeFile(ctx.cwd + "/" + filename, buffer);
        data.close();
        clearData(ctx);

        reply(session, "226 Transfer complete");
    }

    /* ===================== Data Utilities ===================== */
    private Socket waitForData(FtpSessionContext ctx) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (ctx.passiveDataSocket != null) return ctx.passiveDataSocket;
            Thread.sleep(100);
        }
        return null;
    }

    private void clearData(FtpSessionContext ctx) {
        ctx.passiveDataSocket = null;
        ctx.activeDataSocket = null;
        ctx.dataHost = null;
        ctx.dataPort = 0;
    }

    /* ===================== Public Access ===================== */
    /**
     * Allow external classes (e.g., MainActivity) to access user manager.
     */
    public FtpUserManager getUserManager() {
        return users;
    }
}