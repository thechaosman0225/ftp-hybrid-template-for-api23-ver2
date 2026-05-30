package com.example.ftpengine;

import org.apache.mina.core.session.IoSession;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class FtpCommandProcessor {

    private final IFtpFileSystem fs;
    private final FtpUserManager users;
    private final String serverIp;

    public FtpCommandProcessor(IFtpFileSystem fs, FtpUserManager users, String serverIp) {
        this.fs = fs;
        this.users = users;
        this.serverIp = (serverIp == null || serverIp.isEmpty())
                ? "127.0.0.1"
                : serverIp;
    }

    /* ===================== REPLY ===================== */

    private void reply(IoSession s, String msg) {
        try {
            s.write(msg + "\r\n");
        } catch (Exception e) {
            s.closeNow();
        }
    }

    /* ===================== MAIN HANDLER ===================== */

    public void handle(IoSession s, FtpSessionContext c, String line) {

        if (line == null) return;

        line = line.trim();
        if (line.isEmpty()) return;

        if (c.cwd == null) c.cwd = "/";

        String[] p = line.split(" ", 2);
        String cmd = p[0].toUpperCase(Locale.ROOT);
        String arg = p.length > 1 ? p[1].trim() : null;

        boolean preLogin =
                cmd.equals("USER") || cmd.equals("PASS") ||
                cmd.equals("SYST") || cmd.equals("FEAT") ||
                cmd.equals("NOOP") || cmd.equals("QUIT") ||
                cmd.equals("PWD");

        if (!c.loggedIn && !preLogin) {
            reply(s, "530 Login required");
            return;
        }

        switch (cmd) {

            /* ================= AUTH ================= */

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

            /* ================= BASIC ================= */

            case "SYST":
                reply(s, "215 UNIX");
                break;

            case "FEAT":
                reply(s,
                        "211-Features\r\n" +
                        " PASV\r\n" +
                        " UTF8\r\n" +
                        "211 End");
                break;

            case "NOOP":
                reply(s, "200 OK");
                break;

            case "QUIT":
                reply(s, "221 BYE");
                s.closeNow();
                break;

            /* ================= FILE SYSTEM ================= */

            case "PWD":
                reply(s, "257 \"" + c.cwd + "\"");
                break;

            case "CWD":
                if (arg != null && fs.exists(arg)) {
                    c.cwd = normalize(c.cwd + "/" + arg);
                    reply(s, "250 OK");
                } else {
                    reply(s, "550 NO DIR");
                }
                break;

            case "TYPE":
                c.type = (arg == null) ? "I" : arg.toUpperCase(Locale.ROOT);
                reply(s, "200 Type set to " + c.type);
                break;

            /* ================= PASSIVE MODE ================= */

            case "PASV":
                enterPassiveMode(s, c);
                break;

            /* ================= DATA COMMANDS ================= */

            case "LIST":
                list(s, c);
                break;

            case "RETR":
                try {
                    retr(s, c, arg);
                } catch (Exception e) {
                    e.printStackTrace();
                    reply(s, "550 RETR failed");
                }
                break;

            case "STOR":
                try {
                    stor(s, c, arg);
                } catch (Exception e) {
                    e.printStackTrace();
                    reply(s, "550 STOR failed");
                }
                break;

            default:
                reply(s, "502 NOT IMPL");
        }
    }

    /* ===================== PASV ===================== */

    private void enterPassiveMode(IoSession s, FtpSessionContext c) {

        try {
            cleanup(c);

            ServerSocket ss = new ServerSocket(0);
            ss.setReuseAddress(true);

            c.passiveServerSocket = ss;
            c.pasvPort = ss.getLocalPort();

            Thread t = new Thread(() -> {
                try {
                    Socket socket = ss.accept();
                    socket.setKeepAlive(true);
                    socket.setTcpNoDelay(true);
                    c.passiveDataSocket = socket;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            t.setDaemon(true);
            t.start();

            String[] ip = ipFix(serverIp);

            reply(s,
                    "227 Entering Passive Mode (" +
                            ip[0] + "," + ip[1] + "," + ip[2] + "," + ip[3] + "," +
                            (c.pasvPort / 256) + "," +
                            (c.pasvPort % 256) + ")"
            );

        } catch (Exception e) {
            reply(s, "425 PASV FAIL");
        }
    }

    /* ===================== LIST ===================== */

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

            d.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
            d.getOutputStream().flush();
            d.close();

            reply(s, "226 DONE");

        } catch (Exception e) {
            e.printStackTrace();
            reply(s, "550 LIST FAILED");
        } finally {
            cleanup(c);
        }
    }

    /* ===================== RETR ===================== */

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
        cleanup(c);
    }

    /* ===================== STOR ===================== */

    private void stor(IoSession s, FtpSessionContext c, String f) throws Exception {

        reply(s, "150 OPEN");

        Socket d = wait(c);
        if (d == null) {
            reply(s, "425 NO DATA");
            return;
        }

        InputStream in = d.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] buf = new byte[8192];
        int r;

        while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
        }

        fs.writeFile(c.cwd + "/" + f, out.toByteArray());

        d.close();

        reply(s, "226 DONE");
        cleanup(c);
    }

    /* ===================== WAIT ===================== */

    private Socket wait(FtpSessionContext c) {

        for (int i = 0; i < 120; i++) {
            if (c.passiveDataSocket != null) {
                return c.passiveDataSocket;
            }

            try {
                Thread.sleep(25);
            } catch (InterruptedException ignored) {}
        }

        return null;
    }

    /* ===================== CLEANUP ===================== */

    private void cleanup(FtpSessionContext c) {

        try {
            if (c.passiveDataSocket != null) c.passiveDataSocket.close();
        } catch (Exception ignored) {}

        try {
            if (c.passiveServerSocket != null) c.passiveServerSocket.close();
        } catch (Exception ignored) {}

        c.passiveDataSocket = null;
        c.passiveServerSocket = null;
        c.pasvPort = -1;
    }

    /* ===================== HELPERS ===================== */

    private String normalize(String p) {
        if (p == null) return "/";
        return p.replace("//", "/");
    }

    private String[] ipFix(String ip) {
        if (ip == null || !ip.contains(".")) {
            return new String[]{"127", "0", "0", "1"};
        }
        return ip.split("\\.");
    }
}
