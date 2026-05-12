package com.example.ftp;

import com.example.ftpengine.FtpCommandProcessor;
import com.example.ftpengine.FtpSessionContext;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;

import java.nio.charset.StandardCharsets;

/**
 * Android-compatible MINA IoHandler for FTP control connection.
 * FIXED: safe message decoding + no silent failures + proper FTP flow support.
 */
public class FtpIoHandlerAndroid extends IoHandlerAdapter {

    private static final String CTX_KEY = "ftpCtx";

    private final FtpCommandProcessor processor;

    public FtpIoHandlerAndroid(FtpCommandProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void sessionCreated(IoSession session) {
        session.setAttribute(CTX_KEY, new FtpSessionContext());

        System.out.println("[FTP] Session created: "
                + session.getId()
                + " from " + session.getRemoteAddress());
    }

    @Override
    public void sessionOpened(IoSession session) {
        FtpSessionContext ctx = (FtpSessionContext) session.getAttribute(CTX_KEY);

        try {
            System.out.println("[FTP] Session opened: " + session.getId());

            // Proper FTP greeting
            processor.handle(session, ctx, "220 Welcome to Android FTP Server");

        } catch (Exception e) {
            e.printStackTrace();
            safeReply(session, ctx, "421 Service not available");
        }
    }

    @Override
    public void sessionClosed(IoSession session) {
        FtpSessionContext ctx = (FtpSessionContext) session.getAttribute(CTX_KEY);

        if (ctx != null) ctx.reset();

        System.out.println("[FTP] Session closed: "
                + session.getId()
                + " from " + session.getRemoteAddress());
    }

    @Override
    public void messageReceived(IoSession session, Object message) {

        FtpSessionContext ctx = (FtpSessionContext) session.getAttribute(CTX_KEY);

        String line = decodeMessage(message);

        if (line == null || line.trim().isEmpty()) {
            System.out.println("[FTP] Empty/unsupported message from "
                    + session.getRemoteAddress());
            return;
        }

        line = line.replace("\r", "").trim();

        System.out.println("[FTP] CMD from "
                + session.getRemoteAddress()
                + ": " + line);

        try {
            processor.handle(session, ctx, line);
        } catch (Exception e) {
            e.printStackTrace();
            safeReply(session, ctx, "500 Internal server error");
        }
    }

    @Override
    public void messageSent(IoSession session, Object message) {
        // optional debug
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        cause.printStackTrace();

        System.out.println("[FTP] Exception on session "
                + session.getId()
                + " from " + session.getRemoteAddress());

        FtpSessionContext ctx = (FtpSessionContext) session.getAttribute(CTX_KEY);
        safeReply(session, ctx, "500 Internal server error");
    }

    /**
     * Safe message decoder for MINA (handles multiple transport formats)
     */
    private String decodeMessage(Object message) {

        try {
            if (message instanceof String) {
                return (String) message;
            }

            if (message instanceof IoBuffer) {
                IoBuffer buffer = (IoBuffer) message;
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                return new String(bytes, StandardCharsets.UTF_8);
            }

            if (message instanceof byte[]) {
                return new String((byte[]) message, StandardCharsets.UTF_8);
            }

            System.out.println("[FTP] Unknown message type: " + message.getClass());
            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Ensures FTP reply is always sent even on error
     */
    private void safeReply(IoSession session, FtpSessionContext ctx, String msg) {
        try {
            processor.handle(session, ctx, msg);
        } catch (Exception ignored) {
            System.out.println("[FTP] Failed to send reply: " + msg);
        }
    }
}
