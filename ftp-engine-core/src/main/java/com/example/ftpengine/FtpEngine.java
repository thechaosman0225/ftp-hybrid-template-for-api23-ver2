package com.example.ftpengine;

import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.AndroidNioSocketAcceptor;

import java.net.InetSocketAddress;

/**
 * FTP engine using the Android-compatible NIO acceptor.
 */
public class FtpEngine {

    private final AndroidNioSocketAcceptor acceptor;
    private final FtpCommandProcessor processor;

    public FtpEngine(IFtpFileSystem fs) {
        this.processor = new FtpCommandProcessor(fs, new FtpUserManager());

        IoHandler handler = new FtpIoHandlerAndroid(processor);

        // Android-compatible acceptor
        this.acceptor = new AndroidNioSocketAcceptor(handler);
    }

    /**
     * Start FTP server.
     * IMPORTANT: bind to 0.0.0.0 so other devices can connect.
     */
    public void start(int port) throws Exception {
        acceptor.bind(new InetSocketAddress("0.0.0.0", port));
        System.out.println("FtpEngine started on port " + port);
    }

    /**
     * Stop FTP server.
     */
    public void stop() {
        if (acceptor != null) {
            acceptor.shutdown(); // clean shutdown
        }
        System.out.println("FtpEngine stopped");
    }

    public FtpCommandProcessor getProcessor() {
        return processor;
    }
}