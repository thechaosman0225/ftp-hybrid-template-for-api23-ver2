package com.example.ftp;

import android.content.Context;
import android.util.Log;

import com.example.ftpengine.FtpCommandProcessor;
import com.example.ftpengine.FtpUserManager;
import com.example.ftpengine.saf.SAFFileSystem;

import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.LineDelimiter;
import org.apache.mina.filter.codec.textline.TextLineCodecFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
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

        this.processor =
                new FtpCommandProcessor(fs, userManager, serverIp);

        this.acceptor = new NioSocketAcceptor();

        // ✅ REQUIRED FOR FTP TEXT COMMANDS
        TextLineCodecFactory codec =
                new TextLineCodecFactory(StandardCharsets.UTF_8);

        codec.setDecoderDelimiter(LineDelimiter.CRLF);
        codec.setEncoderDelimiter(LineDelimiter.CRLF);

        acceptor.getFilterChain().addLast(
                "codec",
                new ProtocolCodecFilter(codec)
        );

        acceptor.setHandler(
                new FtpIoHandlerAndroid(processor)
        );

        acceptor.getSessionConfig().setReuseAddress(true);
        acceptor.getSessionConfig().setTcpNoDelay(true);
        acceptor.getSessionConfig().setKeepAlive(true);
    }

    public void start(int port) throws Exception {
        acceptor.bind(new InetSocketAddress("0.0.0.0", port));

        Log.i("FTP", "FTP running at " + serverIp + ":" + port);
    }

    public void stop() {

        try {
            acceptor.unbind();
            acceptor.dispose(true);
        } catch (Exception ignored) {
        }
    }

    public FtpUserManager getUserManager() {
        return userManager;
    }

    private String detectIp() {

        try {

            Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {

                NetworkInterface ni = interfaces.nextElement();

                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }

                Enumeration<InetAddress> addresses =
                        ni.getInetAddresses();

                while (addresses.hasMoreElements()) {

                    InetAddress addr = addresses.nextElement();

                    if (addr instanceof Inet4Address
                            && !addr.isLoopbackAddress()) {

                        return addr.getHostAddress();
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return "127.0.0.1";
    }
}
