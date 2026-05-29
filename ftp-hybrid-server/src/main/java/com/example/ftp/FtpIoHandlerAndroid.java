package com.example.ftp;

import com.example.ftpengine.FtpCommandProcessor;
import com.example.ftpengine.FtpSessionContext;

import org.apache.mina.core.buffer.IoBuffer;
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

    /* ===================== SESSION ===================== */

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

        // ✅ FIX: Proper FTP greeting MUST be sent as a reply, NOT processed as a command
        safeReply(session, ctx, "220 Welcome to Android FTP Server");
    }

    @Override
    public void sessionClosed(IoSession session) {

        FtpSessionContext ctx = getCtx(session);

        if (ctx != null) {
            ctx.reset();
        }

        System.out.println("[FTP] Session closed: "
                + session.getId()
                + " from " + session.getRemoteAddress());
    }

    /* ===================== MESSAGE ===================== */

    @Override
    public void messageReceived(IoSession session, Object message) {

        FtpSessionContext ctx = getCtx(session);
        if (ctx == null) return;

        String line = decode(message);

        if (line == null) return;

        line = line.replace("\r", "").trim();

        if (line.isEmpty()) return;

        System.out.println("[FTP] CMD: " + line);

        try {
            processor.handle(session, ctx, line);
        } catch (Exception e) {
            e.printStackTrace();
            safeReply(session, ctx, "500 Internal server error");
        }
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {

        cause.printStackTrace();

        System.out.println("[FTP] Exception: "
                + session.getRemoteAddress());

        safeReply(session, getCtx(session), "500 Internal server error");
    }

    @Override
    public void messageSent(IoSession session, Object message) {
        // optional debug
    }

    /* ===================== HELPERS ===================== */

    private FtpSessionContext getCtx(IoSession session) {
        return (FtpSessionContext) session.getAttribute(CTX_KEY);
    }

    /**
     * FIX: Correct MINA decoding (supports IoBuffer properly)
     */
    private String decode(Object message) {

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

            System.out.println("[FTP] Unknown message type: "
                    + message.getClass().getName());

            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * FIX: Safe reply wrapper (never crashes session thread)
     */
    private void safeReply(IoSession session, FtpSessionContext ctx, String msg) {
        try {
            processor.handle(session, ctx, msg);
        } catch (Exception e) {
            System.out.println("[FTP] Failed to send reply: " + msg);
        }
    }
}
