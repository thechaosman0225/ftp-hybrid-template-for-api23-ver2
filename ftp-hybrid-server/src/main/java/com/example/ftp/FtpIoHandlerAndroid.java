package com.example.ftp;

import com.example.ftpengine.FtpCommandProcessor;
import com.example.ftpengine.FtpSessionContext;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;

import java.nio.charset.StandardCharsets;

public class FtpIoHandlerAndroid extends IoHandlerAdapter {

    private static final String CTX_KEY = "ftpCtx";

    private final FtpCommandProcessor processor;

    public FtpIoHandlerAndroid(FtpCommandProcessor processor) {
        this.processor = processor;
    }

    /* ================= SESSION ================= */

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

        // ✅ FIX: Send greeting directly (DO NOT pass to processor)
        write(session, "220 Android FTP Server Ready");
    }

    @Override
    public void sessionClosed(IoSession session) {
        FtpSessionContext ctx = getCtx(session);

        if (ctx != null) ctx.reset();

        System.out.println("[FTP] Session closed: "
                + session.getId()
                + " from " + session.getRemoteAddress());
    }

    /* ================= MESSAGE ================= */

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
            write(session, "500 Internal server error");
        }
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        cause.printStackTrace();
        write(session, "500 Internal server error");
    }

    /* ================= CORE FIX ================= */

    private void write(IoSession session, String msg) {
        try {
            session.write(msg + "\r\n");
        } catch (Exception e) {
            session.closeNow();
        }
    }

    /* ================= DECODER ================= */

    private String decode(Object message) {
        try {
            if (message instanceof String) return (String) message;

            if (message instanceof IoBuffer) {
                IoBuffer buf = (IoBuffer) message;
                byte[] bytes = new byte[buf.remaining()];
                buf.get(bytes);
                return new String(bytes, StandardCharsets.UTF_8);
            }

            if (message instanceof byte[]) {
                return new String((byte[]) message, StandardCharsets.UTF_8);
            }

            return null;

        } catch (Exception e) {
            return null;
        }
    }

    /* ================= UTIL ================= */

    private FtpSessionContext getCtx(IoSession session) {
        return (FtpSessionContext) session.getAttribute(CTX_KEY);
    }
}
