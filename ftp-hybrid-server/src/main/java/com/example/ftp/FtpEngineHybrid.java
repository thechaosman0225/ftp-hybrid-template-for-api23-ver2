package com.example.ftp;

import android.content.Context;
import android.util.Log;

import com.example.ftpengine.FtpCommandProcessor;
import com.example.ftpengine.FtpUserManager;
import com.example.ftpengine.saf.SAFFileSystem;

import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

public class FtpEngineHybrid {

    private final NioSocketAcceptor acceptor;
    private final FtpCommandProcessor processor;
    private final FtpUserManager userManager;
    private final String serverIp;

    public FtpEngineHybrid(Context context, SAFFileSystem fs) {

        this.serverIp = detectIp();
        this.userManager = new FtpUserManager();

        this.processor = new FtpCommandProcessor(fs, userManager, serverIp);

        this.acceptor = new NioSocketAcceptor();

        // ❌ IMPORTANT: DO NOT USE TextLineCodec for FTP
        // it breaks CRLF + buffering in FileZilla

        this.acceptor.setHandler(new FtpIoHandlerAndroid(processor));

        this.acceptor.getSessionConfig().setReuseAddress(true);
        this.acceptor.getSessionConfig().setTcpNoDelay(true);
    }

    public void start(int port) throws Exception {
        acceptor.bind(new InetSocketAddress("0.0.0.0", port));
        Log.i("FTP", "FTP running on " + serverIp + ":" + port);
    }

    public void stop() {
        acceptor.unbind();
        acceptor.dispose(true);
    }

    public FtpUserManager getUserManager() {
        return userManager;
    }

    private String detectIp() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;

                for (Enumeration<InetAddress> addrs = ni.getInetAddresses(); addrs.hasMoreElements();) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}

        return "127.0.0.1";
    }
}
