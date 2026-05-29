package com.example.ftp;

import android.content.Context;
import android.util.Log;

import com.example.ftpengine.FtpCommandProcessor;
import com.example.ftpengine.FtpUserManager;
import com.example.ftpengine.saf.SAFFileSystem;

import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public class FtpEngineHybrid {

    private static final String TAG = "FtpEngineHybrid";

    private final NioSocketAcceptor acceptor;
    private final FtpCommandProcessor processor;
    private final FtpUserManager userManager;
    private final String serverIp;

    public FtpEngineHybrid(Context context, SAFFileSystem safFs) {

        this.serverIp = resolveBestLanIp();
        this.userManager = new FtpUserManager();

        this.processor = new FtpCommandProcessor(
                safFs,
                userManager,
                serverIp
        );

        this.acceptor = new NioSocketAcceptor();

        // ================= MINA CONFIG =================
        this.acceptor.getFilterChain().addLast(
                "codec",
                new ProtocolCodecFilter(
                        new TextLineCodecFactory(StandardCharsets.UTF_8)
                )
        );

        this.acceptor.setHandler(new FtpIoHandlerAndroid(processor));

        // ================= STABILITY =================
        this.acceptor.getSessionConfig().setReuseAddress(true);
        this.acceptor.getSessionConfig().setTcpNoDelay(true);
        this.acceptor.getSessionConfig().setKeepAlive(true);

        // IMPORTANT FIX: improves FTP burst handling
        this.acceptor.getSessionConfig().setReadBufferSize(64 * 1024);

        Log.i(TAG, "FTP Engine initialized on IP: " + serverIp);
    }

    public void start(int port) throws Exception {

        acceptor.bind(new InetSocketAddress("0.0.0.0", port));

        Log.i(TAG, "===================================");
        Log.i(TAG, " FTP SERVER READY");
        Log.i(TAG, " Control: ftp://" + serverIp + ":" + port);
        Log.i(TAG, "===================================");
    }

    public void stop() {
        try {
            acceptor.unbind();
            acceptor.dispose(true);
            Log.i(TAG, "FTP server stopped");
        } catch (Exception e) {
            Log.e(TAG, "Stop error", e);
        }
    }

    public FtpUserManager getUserManager() {
        return userManager;
    }

    // =========================================================
    // ROBUST LAN IP DETECTOR (FILEZILLA SAFE)
    // =========================================================

    private String resolveBestLanIp() {

        try {
            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            String fallback = null;

            while (interfaces.hasMoreElements()) {

                NetworkInterface ni = interfaces.nextElement();

                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;

                Enumeration<InetAddress> addresses = ni.getInetAddresses();

                while (addresses.hasMoreElements()) {

                    InetAddress addr = addresses.nextElement();

                    if (!(addr instanceof Inet4Address)) continue;
                    if (addr.isLoopbackAddress()) continue;

                    String ip = addr.getHostAddress();

                    // PRIORITY LAN MATCH
                    if (ip.startsWith("192.168.")
                            || ip.startsWith("10.")
                            || ip.startsWith("172.")) {
                        Log.i(TAG, "Selected LAN IP: " + ip);
                        return ip;
                    }

                    // fallback candidate
                    fallback = ip;
                }
            }

            if (fallback != null) {
                Log.w(TAG, "Using fallback IP: " + fallback);
                return fallback;
            }

        } catch (Exception e) {
            Log.e(TAG, "IP detection failed", e);
        }

        Log.w(TAG, "Falling back to 127.0.0.1 (NOT LAN accessible)");
        return "127.0.0.1";
    }
}
