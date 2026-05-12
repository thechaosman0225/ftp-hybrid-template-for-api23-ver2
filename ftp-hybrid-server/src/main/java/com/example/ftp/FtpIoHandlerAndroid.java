package com.example.ftp;

import com.example.ftpengine.FtpCommandProcessor;
import com.example.ftpengine.FtpSessionContext;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;

import java.nio.charset.StandardCharsets;

/**
 * Android-compatible IoHandler for FTP control connection.
 */
public class FtpIoHandlerAndroid extends IoHandlerAdapter {

    private final FtpCommandProcessor processor;

    public FtpIoHandlerAndroid(FtpCommandProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void sessionCreated(IoSession session) {
        session.setAttribute("ftpCtx", new FtpSessionContext());
        System.out.println("[FTP] Session created: " + session.getId()
                + " from " + session.getRemoteAddress());
    }

    @Override
    public void sessionOpened(IoSession session) {
        FtpSessionContext ctx =
                (FtpSessionContext) session.getAttribute("ftpCtx");

        try {
            System.out.println("[FTP] Session opened: " + session.getId());
            processor.handle(session, ctx, "220 Welcome to Android FTP Server");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void sessionClosed(IoSession session) {
        FtpSessionContext ctx =
                (FtpSessionContext) session.getAttribute("ftpCtx");

        if (ctx != null) ctx.reset();

        System.out.println("[FTP] Session closed: " + session.getId()
                + " from " + session.getRemoteAddress());
    }

    @Override
    public void messageReceived(IoSession session, Object message) {

        if (!(message instanceof byte[])) return;

        byte[] bytes = (byte[]) message;

        FtpSessionContext ctx =
                (FtpSessionContext) session.getAttribute("ftpCtx");

        try {
            String line = new String(bytes, StandardCharsets.UTF_8);
            String[] commands = line.split("\r?\n");

            for (String cmdLine : commands) {
                cmdLine = cmdLine.trim();

                if (!cmdLine.isEmpty()) {
                    System.out.println("[FTP] Cmd from "
                            + session.getRemoteAddress()
                            + ": " + cmdLine);

                    processor.handle(session, ctx, cmdLine);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            try {
                processor.handle(session, ctx, "500 Internal server error");
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void messageSent(IoSession session, Object message) {
        // optional logging
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        cause.printStackTrace();

        System.out.println("[FTP] Exception on session "
                + session.getId()
                + " from " + session.getRemoteAddress());

        FtpSessionContext ctx =
                (FtpSessionContext) session.getAttribute("ftpCtx");

        try {
            processor.handle(session, ctx, "500 Internal server error");
        } catch (Exception ignored) {}
    }
}
