package com.example.ftp;

import com.example.ftpengine.*;

import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;

import java.nio.charset.StandardCharsets;

public class FtpIoHandlerAndroid extends IoHandlerAdapter {

    private static final String CTX = "ftp_ctx";
    private final FtpCommandProcessor processor;

    public FtpIoHandlerAndroid(FtpCommandProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void sessionCreated(IoSession session) {
        session.setAttribute(CTX, new FtpSessionContext());
    }

    @Override
    public void sessionOpened(IoSession session) {
        FtpSessionContext ctx = get(session);

        // IMPORTANT: MUST be direct reply, not processor
        write(session, "220 Android FTP Ready");
    }

    @Override
    public void messageReceived(IoSession session, Object msg) {

        FtpSessionContext ctx = get(session);
        String line = decode(msg);

        if (line == null || line.isEmpty()) return;

        processor.handle(session, ctx, line.trim());
    }

    private String decode(Object msg) {
        try {
            if (msg instanceof String) return (String) msg;

            if (msg instanceof IoBuffer) {
                IoBuffer b = (IoBuffer) msg;
                byte[] d = new byte[b.remaining()];
                b.get(d);
                return new String(d, StandardCharsets.UTF_8);
            }

            if (msg instanceof byte[]) {
                return new String((byte[]) msg, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}

        return null;
    }

    private FtpSessionContext get(IoSession s) {
        return (FtpSessionContext) s.getAttribute(CTX);
    }

    private void write(IoSession s, String msg) {
        s.write(msg + "\r\n");
    }
}
