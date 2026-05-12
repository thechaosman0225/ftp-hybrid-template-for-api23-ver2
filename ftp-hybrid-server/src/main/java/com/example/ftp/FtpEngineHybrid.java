package com.example.ftp;

import android.content.Context;
import android.util.Log;

import com.example.ftpengine.FtpCommandProcessor;
import com.example.ftpengine.FtpUserManager;
import com.example.ftpengine.saf.SAFFileSystem;

import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public class FtpEngineHybrid {

    private static final String TAG = "FtpEngineHybrid";

    private final NioSocketAcceptor acceptor;
    private final FtpCommandProcessor processor;
    private final FtpUserManager userManager;
    private final String serverIp;

    public FtpEngineHybrid(Context context, SAFFileSystem safFs) throws Exception {

        this.serverIp = getLocalIpAddress(); // ⭐ REAL LAN IP
        Log.i(TAG, "Detected LAN IP: " + serverIp);

        this.userManager = new FtpUserManager();
        this.processor = new FtpCommandProcessor(safFs, userManager, serverIp);

        this.acceptor = new NioSocketAcceptor();

        // Fix telnet / text protocol handling
        this.acceptor.getFilterChain().addLast(
                "codec",
                new ProtocolCodecFilter(
                        new TextLineCodecFactory(StandardCharsets.UTF_8)
                )
        );

        // FTP handler
        this.acceptor.setHandler(new FtpIoHandlerAndroid(processor));

        this.acceptor.getSessionConfig().setReuseAddress(true);
    }

    public void start(int port) throws Exception {
        acceptor.bind(new InetSocketAddress("0.0.0.0", port));
        Log.i(TAG, "FTP server started at " + serverIp + ":" + port);
    }

    public void stop() {
        acceptor.unbind();
        acceptor.dispose(true);
        Log.i(TAG, "FTP server stopped");
    }

    public FtpUserManager getUserManager() {
        return userManager;
    }

    /* ================= LAN IP DETECTOR ================= */

    private String getLocalIpAddress() throws Exception {
        Enumeration<NetworkInterface> interfaces =
                NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();

            if (!ni.isUp() || ni.isLoopback()) continue;

            Enumeration<InetAddress> addresses = ni.getInetAddresses();

            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();

                if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                    return addr.getHostAddress();
                }
            }
        }

        // Fallback (should never happen on WiFi)
        return "127.0.0.1";
    }
}
