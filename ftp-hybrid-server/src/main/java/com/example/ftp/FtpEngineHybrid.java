package com.example.ftp;

import android.content.Context;
import android.util.Log;

import com.example.ftpengine.FtpCommandProcessor;
import com.example.ftpengine.FtpUserManager;
import com.example.ftpengine.IFtpFileSystem;

import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * FTP Engine Hybrid - Main entry point for the FTP server.
 * 
 * Updated to accept IFtpFileSystem interface instead of SAFFileSystem,
 * allowing support for both SAFFileSystem and FtpFileSystem backends.
 */
public class FtpEngineHybrid {

    private static final String TAG = "FtpEngineHybrid";

    private final NioSocketAcceptor acceptor;
    private final FtpCommandProcessor processor;
    private final FtpUserManager userManager;
    private final String serverIp;

    /**
     * Initialize FTP Engine with any IFtpFileSystem implementation.
     * 
     * @param context Android context
     * @param fs Filesystem implementation (can be SAFFileSystem, FtpFileSystem, or custom)
     */
    public FtpEngineHybrid(Context context, IFtpFileSystem fs) {

        this.serverIp = resolveIp();
        this.userManager = new FtpUserManager();
        this.processor = new FtpCommandProcessor(fs, userManager, serverIp);

        this.acceptor = new NioSocketAcceptor();

        TextLineCodecFactory codec =
                new TextLineCodecFactory(StandardCharsets.UTF_8);

        acceptor.getFilterChain().addLast("codec",
                new ProtocolCodecFilter(codec));

        acceptor.setHandler(new FtpIoHandlerAndroid(processor));

        acceptor.getSessionConfig().setReuseAddress(true);
        acceptor.getSessionConfig().setTcpNoDelay(true);
        acceptor.getSessionConfig().setKeepAlive(true);
    }

    public void start(int port) throws Exception {
        acceptor.bind(new InetSocketAddress("0.0.0.0", port));
        Log.i(TAG, "FTP started: ftp://" + serverIp + ":" + port);
    }

    public void stop() {
        acceptor.unbind();
        acceptor.dispose(true);
    }

    public FtpUserManager getUserManager() {
        return userManager;
    }

    private String resolveIp() {
        try {
            Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces();

            while (ifs.hasMoreElements()) {
                NetworkInterface ni = ifs.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;

                Enumeration<InetAddress> addrs = ni.getInetAddresses();

                while (addrs.hasMoreElements()) {
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
