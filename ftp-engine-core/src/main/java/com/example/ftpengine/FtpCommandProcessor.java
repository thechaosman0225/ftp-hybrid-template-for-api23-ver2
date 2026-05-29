package com.example.ftpengine;

import org.apache.mina.core.session.IoSession;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * FileZilla-compatible Android FTP Command Processor (STABLE CORE)
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

    /* ===================== FTP REPLY ===================== */

    private void reply(IoSession session, String msg) {
        try {
            session.write(msg + "\r\n");
        } catch (Exception e) {
            session.closeNow();
        }
    }

    /* ===================== MAIN HANDLER ===================== */

    public void handle(IoSession session, FtpSessionContext ctx, String line) {

        if (line == null) return;
        line = line.trim();
        if (line.isEmpty()) return;

        if (ctx.cwd == null) ctx.cwd = "/";

        String[] parts = line.split(" ", 2);
        String cmd = parts[0].toUpperCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : null;

        boolean preLoginAllowed =
                cmd.equals("USER") ||
                cmd.equals("PASS") ||
                cmd.equals("SYST") ||
                cmd.equals("FEAT") ||
                cmd.equals("NOOP") ||
                cmd.equals("QUIT") ||
                cmd.equals("OPTS") ||
                cmd.equals("PWD");

        if (!ctx.loggedIn && !preLoginAllowed) {
            reply(session, "530 Please login with USER and PASS");
            return;
        }

        try {
            switch (cmd) {

                /* ================= AUTH ================= */

                case "USER":
                    ctx.username = arg;
                    reply(session, "331 Username OK, need password");
                    break;

                case "PASS":
                    if (ctx.username != null &&
                            users.authenticate(ctx.username, arg)) {
                        ctx.loggedIn = true;
                        reply(session, "230 Login successful");
                    } else {
                        reply(session, "530 Login incorrect");
                    }
                    break;

                /* ================= SYSTEM ================= */

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

                case "NOOP":
                    reply(session, "200 OK");
                    break;

                case "QUIT":
                    reply(session, "221 Goodbye");
                    session.closeNow();
                    break;

                /* ================= FILE SYSTEM ================= */

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

                /* ================= PASSIVE MODE ================= */

                case "PASV":
                    enterPassiveMode(session, ctx);
                    break;

                /* ================= DATA COMMANDS ================= */

                case "LIST":
                    handleList(session, ctx);
                    break;

                case "RETR":
                    handleRetr(session, ctx, arg);
                    break;

                case "STOR":
                    handleStor(session, ctx, arg);
                    break;

                default:
                    reply(session, "502 Command not implemented");
            }

        } catch (Exception e) {
            e.printStackTrace();
            reply(session, "550 Internal server error");
        }
    }

    /* ================= DIRECTORY ================= */

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

    /* ================= PASSIVE MODE (FIXED) ================= */

   private void enterPassiveMode(IoSession session, FtpSessionContext ctx) {

    try {

        cleanupData(ctx);

        ctx.passiveServerSocket = new ServerSocket(0);

        ctx.passiveServerSocket.setReuseAddress(true);

        ctx.pasvPort =
                ctx.passiveServerSocket.getLocalPort();

        ctx.passiveDataSocket = null;

        Thread acceptThread = new Thread(() -> {

            try {

                Socket socket =
                        ctx.passiveServerSocket.accept();

                socket.setKeepAlive(true);
                socket.setTcpNoDelay(true);

                ctx.passiveDataSocket = socket;

                System.out.println(
                        "[FTP] Passive client connected: "
                                + socket.getRemoteSocketAddress()
                );

            } catch (Exception e) {

                System.out.println(
                        "[FTP] PASV accept failed: "
                                + e.getMessage()
                );
            }
        });

        acceptThread.setDaemon(true);
        acceptThread.start();

        String[] ip = serverIp.split("\\.");

        int p1 = ctx.pasvPort / 256;
        int p2 = ctx.pasvPort % 256;

        reply(session,
                "227 Entering Passive Mode (" +
                        ip[0] + "," +
                        ip[1] + "," +
                        ip[2] + "," +
                        ip[3] + "," +
                        p1 + "," +
                        p2 + ")");

    } catch (Exception e) {

        e.printStackTrace();

        reply(session,
                "425 Can't open passive connection");
    }
}

    /* ================= DATA OPS ================= */

    private void handleList(IoSession session,
                        FtpSessionContext ctx) throws Exception {

    reply(session, "150 Opening data connection");

    Socket data = waitForData(ctx);

    if (data == null) {
        reply(session, "425 No data connection");
        return;
    }

    try {

        String path = ctx.cwd;

        if (path == null || path.isEmpty()) {
            path = "/";
        }

        String[] files = fs.list(path);

        StringBuilder sb = new StringBuilder();

        if (files != null) {

            for (String f : files) {

                if (f == null) continue;

                // FileZilla-compatible UNIX listing
                sb.append("-rw-r--r-- 1 owner group ")
                        .append("0 ")
                        .append("Jan 1 00:00 ")
                        .append(f)
                        .append("\r\n");
            }
        }

        byte[] out =
                sb.toString().getBytes(StandardCharsets.UTF_8);

        data.getOutputStream().write(out);
        data.getOutputStream().flush();

        reply(session, "226 Transfer complete");

    } catch (Exception e) {

        e.printStackTrace();

        reply(session, "550 Failed to list directory");

    } finally {

        try {
            data.close();
        } catch (Exception ignored) {}

        cleanupData(ctx);
    }
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

        cleanupData(ctx);

        reply(session, "226 Transfer complete");
    }

    /* ================= SAFE DATA HANDLING ================= */

   private Socket waitForData(FtpSessionContext ctx)
        throws InterruptedException {

    for (int i = 0; i < 400; i++) {

        Socket socket = ctx.passiveDataSocket;

        if (socket != null
                && socket.isConnected()
                && !socket.isClosed()) {

            return socket;
        }

        Thread.sleep(25);
    }

    return null;
}

    private void cleanupData(FtpSessionContext ctx) {

    try {
        if (ctx.passiveDataSocket != null) {
            ctx.passiveDataSocket.close();
        }
    } catch (Exception ignored) {}

    try {
        if (ctx.passiveServerSocket != null) {
            ctx.passiveServerSocket.close();
        }
    } catch (Exception ignored) {}

    ctx.passiveDataSocket = null;
    ctx.passiveServerSocket = null;
    ctx.pasvPort = -1;
}

    private byte[] readFully(InputStream in) throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;

        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }

        return out.toByteArray();
    }

    /* ================= PUBLIC ================= */

    public FtpUserManager getUserManager() {
        return users;
    }
}
