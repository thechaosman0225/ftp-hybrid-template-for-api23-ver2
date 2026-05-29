package com.example.ftpengine;

import org.apache.mina.core.session.IoSession;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * FileZilla-compatible Android FTP Command Processor (STABLE FIXED VERSION)
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

    /* ===================== REPLY ===================== */

    private void reply(IoSession session, String msg) {
        try {
            session.write(msg + "\r\n");
        } catch (Exception e) {
            session.closeNow();
        }
    }

    /* ===================== MAIN ===================== */

    public void handle(IoSession session, FtpSessionContext ctx, String line) {

        if (line == null || line.trim().isEmpty()) return;

        if (ctx.cwd == null) ctx.cwd = "/";

        String[] parts = line.trim().split(" ", 2);
        String cmd = parts[0].toUpperCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : null;

        boolean preLogin =
                cmd.equals("USER") || cmd.equals("PASS") ||
                cmd.equals("SYST") || cmd.equals("FEAT") ||
                cmd.equals("NOOP") || cmd.equals("QUIT") ||
                cmd.equals("OPTS") || cmd.equals("PWD");

        if (!ctx.loggedIn && !preLogin) {
            reply(session, "530 Please login with USER and PASS");
            return;
        }

        try {
            switch (cmd) {

                case "USER":
                    ctx.username = arg;
                    reply(session, "331 Username OK");
                    break;

                case "PASS":
                    if (users.authenticate(ctx.username, arg)) {
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
                    reply(session, "211-Features\r\n PASV\r\n UTF8\r\n211 End");
                    break;

                case "PWD":
                    reply(session, "257 \"" + ctx.cwd + "\"");
                    break;

                case "CWD":
                    changeDir(session, ctx, arg);
                    break;

                case "TYPE":
                    ctx.transferType = (arg != null) ? arg : "I";
                    reply(session, "200 Type set");
                    break;

                case "PASV":
                    enterPasv(session, ctx);
                    break;

                case "LIST":
                    list(session, ctx);
                    break;

                case "RETR":
                    retr(session, ctx, arg);
                    break;

                case "STOR":
                    stor(session, ctx, arg);
                    break;

                case "NOOP":
                    reply(session, "200 OK");
                    break;

                case "QUIT":
                    reply(session, "221 Bye");
                    session.closeNow();
                    break;

                default:
                    reply(session, "502 Not implemented");
            }

        } catch (Exception e) {
            e.printStackTrace();
            reply(session, "550 Internal server error");
        }
    }

    /* ===================== DIR ===================== */

    private void changeDir(IoSession session, FtpSessionContext ctx, String arg) {

        if (arg == null) {
            reply(session, "501 Missing directory");
            return;
        }

        String target = normalize(ctx.cwd + "/" + arg);

        if (fs.exists(target)) {
            ctx.cwd = target;
            reply(session, "250 OK");
        } else {
            reply(session, "550 Not found");
        }
    }

    private String normalize(String p) {
        if (p == null || p.isEmpty()) return "/";
        if (!p.startsWith("/")) p = "/" + p;
        return p.replaceAll("/+", "/");
    }

    /* ===================== PASV (FIXED CORE BUG) ===================== */

    private void enterPasv(IoSession session, FtpSessionContext ctx) {

        try {
            cleanup(ctx);

            ServerSocket ss = new ServerSocket(0);
            ss.setReuseAddress(true);

            ctx.passiveServerSocket = ss;
            ctx.pasvPort = ss.getLocalPort();
            ctx.passiveDataSocket = null;

            // IMPORTANT: blocking accept thread
            new Thread(() -> {
                try {
                    Socket s = ss.accept();
                    ctx.passiveDataSocket = s;
                } catch (Exception ignored) {}
            }).start();

            String[] ip = serverIp.split("\\.");

            reply(session,
                    "227 Entering Passive Mode (" +
                            ip[0] + "," + ip[1] + "," +
                            ip[2] + "," + ip[3] + "," +
                            (ctx.pasvPort / 256) + "," +
                            (ctx.pasvPort % 256) + ")");

        } catch (Exception e) {
            reply(session, "425 PASV failed");
        }
    }

    /* ===================== LIST (FIXED FLOW) ===================== */

    private void list(IoSession session, FtpSessionContext ctx) {

        try {
            reply(session, "150 Opening data connection");

            Socket data = waitData(ctx);

            if (data == null) {
                reply(session, "425 No data connection");
                return;
            }

            StringBuilder sb = new StringBuilder();

            for (String f : fs.list(ctx.cwd)) {
                if (f == null) continue;
                sb.append(f).append("\r\n");
            }

            data.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
            data.getOutputStream().flush();

            data.close();

            reply(session, "226 Done");

        } catch (Exception e) {
            reply(session, "550 LIST failed");
        } finally {
            cleanup(ctx);
        }
    }

    /* ===================== RETR ===================== */

    private void retr(IoSession session, FtpSessionContext ctx, String file) {

        try {
            if (file == null) {
                reply(session, "501 Missing file");
                return;
            }

            reply(session, "150 Opening data connection");

            Socket data = waitData(ctx);

            if (data == null) {
                reply(session, "425 No data connection");
                return;
            }

            byte[] bytes = fs.readFile(ctx.cwd + "/" + file);

            data.getOutputStream().write(bytes);
            data.getOutputStream().flush();
            data.close();

            reply(session, "226 Done");

        } catch (Exception e) {
            reply(session, "550 RETR failed");
        } finally {
            cleanup(ctx);
        }
    }

    /* ===================== STOR ===================== */

    private void stor(IoSession session, FtpSessionContext ctx, String file) {

        try {
            if (file == null) {
                reply(session, "501 Missing file");
                return;
            }

            reply(session, "150 Ready");

            Socket data = waitData(ctx);

            if (data == null) {
                reply(session, "425 No data connection");
                return;
            }

            byte[] buf = readFully(data.getInputStream());

            fs.writeFile(ctx.cwd + "/" + file, buf);

            data.close();

            reply(session, "226 Done");

        } catch (Exception e) {
            reply(session, "550 STOR failed");
        } finally {
            cleanup(ctx);
        }
    }

    /* ===================== FIXED WAIT (ROOT CAUSE OF YOUR BUG) ===================== */

    private Socket waitData(FtpSessionContext ctx) throws InterruptedException {

        for (int i = 0; i < 200; i++) {

            Socket s = ctx.passiveDataSocket;

            if (s != null && s.isConnected() && !s.isClosed()) {
                return s;
            }

            Thread.sleep(25);
        }

        return null;
    }

    /* ===================== CLEANUP ===================== */

    private void cleanup(FtpSessionContext ctx) {

        try {
            if (ctx.passiveDataSocket != null) ctx.passiveDataSocket.close();
        } catch (Exception ignored) {}

        try {
            if (ctx.passiveServerSocket != null) ctx.passiveServerSocket.close();
        } catch (Exception ignored) {}

        ctx.passiveDataSocket = null;
        ctx.passiveServerSocket = null;
        ctx.pasvPort = -1;
    }

    /* ===================== UTILS ===================== */

    private byte[] readFully(InputStream in) throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;

        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }

        return out.toByteArray();
    }

    public FtpUserManager getUserManager() {
        return users;
    }
}
