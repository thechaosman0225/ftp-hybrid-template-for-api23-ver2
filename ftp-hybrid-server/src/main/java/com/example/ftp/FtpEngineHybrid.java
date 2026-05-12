package com.example.ftp;

import android.content.Context;
import android.util.Log;

import com.example.ftpengine.FtpCommandProcessor;
import com.example.ftpengine.FtpUserManager;
import com.example.ftpengine.saf.SAFFileSystem;

import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.core.service.IoHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class FtpEngineHybrid {

    private static final String TAG = "FtpEngineHybrid";

    private final NioSocketAcceptor acceptor;
    private final FtpCommandProcessor processor;
    private final FtpUserManager userManager;

    public FtpEngineHybrid(Context context, SAFFileSystem safFs) {

        this.userManager = new FtpUserManager();
        this.processor = new FtpCommandProcessor(safFs, userManager);

        IoHandler handler = new FtpIoHandlerAndroid(processor);

        this.acceptor = new NioSocketAcceptor();
        this.acceptor.setHandler(handler);
    }

    public void start(int port) throws Exception {

        acceptor.bind(new InetSocketAddress(
                InetAddress.getByName("0.0.0.0"), port));

        Enumeration<NetworkInterface> interfaces =
                NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            Enumeration<InetAddress> addresses = ni.getInetAddresses();

            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();

                if (!addr.isLoopbackAddress()
                        && addr.getHostAddress().contains(".")) {

                    Log.i(TAG,
                            "FTP reachable at "
                                    + addr.getHostAddress()
                                    + ":" + port);
                }
            }
        }

        Log.i(TAG, "FTP server started on port " + port);
    }

    public void stop() {
        acceptor.unbind();
        acceptor.dispose();
        Log.i(TAG, "FTP server stopped");
    }

    public FtpUserManager getUserManager() {
        return userManager;
    }
}
