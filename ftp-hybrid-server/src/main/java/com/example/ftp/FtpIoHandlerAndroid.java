package com.example.ftp;

import com.example.ftpengine.FtpCommandProcessor;
import com.example.ftpengine.FtpSessionContext;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;

import java.nio.charset.StandardCharsets;

public class FtpIoHandlerAndroid extends IoHandlerAdapter {

    private static final String CTX_KEY = "ftpCtx";
    private final FtpCommandProcessor processor;

    public FtpIoHandlerAndroid(FtpCommandProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void sessionCreated(IoSession session) {
        session.setAttribute(CTX_KEY, new FtpSessionContext());
    }

    @Override
    public void sessionOpened(IoSession session) {

        FtpSessionContext ctx = getCtx(session);

        // ✅ MUST be first message after connect
        session.write("220 Android FTP Server Ready\r\n");
    }

    @Override
    public void messageReceived(IoSession session, Object message) {

        FtpSessionContext ctx = getCtx(session);
        if (ctx == null) return;

        String line = message.toString().trim();
        if (line.isEmpty()) return;

        try {
            processor.handle(session, ctx, line);
        } catch (Exception e) {
            session.write("500 Internal server error\r\n");
        }
    }

    private FtpSessionContext getCtx(IoSession session) {
        return (FtpSessionContext) session.getAttribute(CTX_KEY);
    }
}
