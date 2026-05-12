package com.example.ftp;

import com.example.ftpengine.FtpCommandProcessor;
import com.example.ftpengine.FtpSessionContext;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;

import java.nio.charset.StandardCharsets;

/**
 * Stable Android FTP IoHandler (MINA-safe + FileZilla compatible)
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
        FtpSessionContext ctx = getCtx(session);

        System.out.println("[FTP] Session opened: " + session.getId());

        processor.handle(session, ctx,
                "220 Welcome to Android FTP Server");
    }

    @Override
    public void sessionClosed(IoSession session) {
        FtpSessionContext ctx = getCtx(session);

        if (ctx != null) ctx.reset();

        System.out.println("[FTP] Session closed: "
                + session.getId()
                + " from " + session.getRemoteAddress());
    }

    @Override
    public void messageReceived(IoSession session, Object message) {

        FtpSessionContext ctx = getCtx(session);

        String line = decode(message);

        if (line == null || line.trim().isEmpty()) return;

        line = line.replace("\r", "").trim();

        System.out.println("[FTP] CMD: " + line);

        try {
            processor.handle(session, ctx, line);
        } catch (Exception e) {
            e.printStackTrace();
            sendError(session, ctx);
        }
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        cause.printStackTrace();
        sendError(session, getCtx(session));
    }

    @Override
    public void messageSent(IoSession session, Object message) {
        // optional debug
    }

    /* ===================== Helpers ===================== */

    private FtpSessionContext getCtx(IoSession session) {
        return (FtpSessionContext) session.getAttribute(CTX_KEY);
    }

    private String decode(Object message) {
        try {
            if (message instanceof String) {
                return (String) message;
            }

            if (message instanceof byte[]) {
                return new String((byte[]) message, StandardCharsets.UTF_8);
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    private void sendError(IoSession session, FtpSessionContext ctx) {
        try {
            processor.handle(session, ctx,
                    "500 Internal server error");
        } catch (Exception ignored) {
        }
    }
}
