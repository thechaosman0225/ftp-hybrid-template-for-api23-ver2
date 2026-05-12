package com.example.ftpengine;

import org.apache.mina.core.session.IoSession;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Android FTP Command Processor (FILEZILLA-COMPATIBLE VERSION)
 * Passive mode only.
 */
public class FtpCommandProcessor {

    private final IFtpFileSystem fs;
    private final FtpUserManager users;
    private final String serverIp;

    public FtpCommandProcessor(IFtpFileSystem fs,
                               FtpUserManager users,
                               String serverIp) {
        this.fs = fs;
        this.users = users;
        this.serverIp = (serverIp != null && serverIp.contains("."))
                ? serverIp
                : "127.0.0.1";
    }

    /* ===================== Reply ===================== */

    private void reply(IoSession session, String msg) {
        try {
            session.write(msg + "\r\n");
        } catch (Exception e) {
            session.closeNow();
        }
    }

    /* ===================== COMMAND ENTRY ===================== */

    public void handle(IoSession session, FtpSessionContext ctx, String line) {
        if (line == null || line.trim().isEmpty()) return;

        if (ctx.cwd == null) ctx.cwd = "/";

        String[] parts = line.trim().split(" ", 2);
        String cmd = parts[0].toUpperCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : null;

        boolean preLoginAllowed =
                cmd.equals("USER") || cmd.equals("PASS") ||
                cmd.equals("SYST") || cmd.equals("FEAT") ||
                cmd.equals("NOOP") || cmd.equals("QUIT") ||
                cmd.equals("OPTS") || cmd.equals("PWD");

        if (!ctx.loggedIn && !preLoginAllowed) {
            reply(session, "530 Please login with USER and PASS");
            return;
        }

        try {
            switch (cmd) {

                case "USER":
                    ctx.username = arg;
                    reply(session, "331 Username OK, need password");
                    break;

                case "PASS":
                    if (ctx.username != null && users.authenticate(ctx.username, arg)) {
                        ctx.loggedIn = true;
                        reply(session, "230 Login successful");
                    } else {
                        reply(session, "530 Login incorrect");
                    }
                    break;

                case "SYST":
                    reply(session, "215 UNIX Type: L8");
                    break;

                case "FEAT":
                    reply(session,
                            "211-Features\r\n" +
                            " PASV\r\n" +
                            " UTF8\r\n" +
                            "211 End");
                    break;

                case "OPTS":
                    reply(session, "200 OK");
                    break;

                case "PWD":
                    reply(session, "257 \"" + ctx.cwd + "\"");
                    break;

                case "CWD":
                    changeDirectory(session, ctx, arg);
                    break;

                case "TYPE":
                    ctx.transferType = (arg != null) ? arg : "I";
                    reply(session, "200 Type set to " + ctx.transferType);
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
                    session.closeNow();
                    break;

                default:
                    reply(session, "502 Command not implemented");
            }

        } catch (Exception e) {
            e.printStackTrace();
            reply(session, "550 Internal server error");
        }
    }

    /* ===================== DIRECTORY ===================== */

    private void changeDirectory(IoSession session, FtpSessionContext ctx, String arg) {
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

    /* ===================== PASV (FINAL SAFE VERSION) ===================== */

    private void handlePasv(IoSession session, FtpSessionContext ctx) {
        try {
            ServerSocket serverSocket = new ServerSocket(0);

            ctx.passiveServerSocket = serverSocket;
            ctx.pasvPort = serverSocket.getLocalPort();

            new Thread(() -> {
                try {
                    ctx.passiveDataSocket = serverSocket.accept();
                } catch (Exception ignored) {}
            }).start();

            String[] ipParts = serverIp.split("\\.");

            int p1 = ctx.pasvPort / 256;
            int p2 = ctx.pasvPort % 256;

            reply(session,
                    "227 Entering Passive Mode (" +
                            ipParts[0] + "," + ipParts[1] + "," +
                            ipParts[2] + "," + ipParts[3] + "," +
                            p1 + "," + p2 + ")");

        } catch (Exception e) {
            reply(session, "425 Can't open passive connection");
        }
    }

    /* ===================== DATA OPS ===================== */

    private void handleList(IoSession session, FtpSessionContext ctx) throws Exception {
        reply(session, "150 Opening data connection");

        Socket data = waitForData(ctx);
        if (data == null) {
            reply(session, "425 No data connection");
            return;
        }

        StringBuilder sb = new StringBuilder();

        for (String f : fs.list(ctx.cwd)) {
            if (f != null) {
                sb.append(f).append("\r\n");
            }
        }

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
            reply(session, "425 No data connection");
            return;
        }

        byte[] bytes = fs.readFile(ctx.cwd + "/" + filename);

        data.getOutputStream().write(bytes);
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
            reply(session, "425 No data connection");
            return;
        }

        byte[] buffer = readFully(data.getInputStream());
        fs.writeFile(ctx.cwd + "/" + filename, buffer);

        data.close();
        clearData(ctx);

        reply(session, "226 Transfer complete");
    }

    /* ===================== UTIL ===================== */

    private byte[] readFully(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;

        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }

        return out.toByteArray();
    }

    private Socket waitForData(FtpSessionContext ctx) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (ctx.passiveDataSocket != null) return ctx.passiveDataSocket;
            Thread.sleep(50);
        }
        return null;
    }

    private void clearData(FtpSessionContext ctx) {
        try {
            if (ctx.passiveServerSocket != null) {
                ctx.passiveServerSocket.close();
            }
        } catch (Exception ignored) {}

        ctx.passiveServerSocket = null;
        ctx.passiveDataSocket = null;
    }

    public FtpUserManager getUserManager() {
        return users;
    }
}
