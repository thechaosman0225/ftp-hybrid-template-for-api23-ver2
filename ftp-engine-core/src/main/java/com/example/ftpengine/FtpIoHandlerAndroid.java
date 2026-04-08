package com.example.ftpengine;

import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IoSession;
import java.nio.charset.StandardCharsets;

public class FtpIoHandlerAndroid implements IoHandler {

    private final FtpCommandProcessor processor;

    public FtpIoHandlerAndroid(FtpCommandProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void sessionCreated(IoSession session) {
        // Create ONE context and reuse it everywhere
        session.setAttribute("ftpCtx", new FtpSessionContext());
    }

    @Override
    public void sessionOpened(IoSession session) {
        // ✅ FTP protocol requires this immediately
        try {
            session.write("220 FTP Server Ready\r\n"
                    .getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    @Override
    public void messageReceived(IoSession session, Object message) {
        if (!(message instanceof String)) return;

        String line = ((String) message).trim();
        FtpSessionContext ctx =
                (FtpSessionContext) session.getAttribute("ftpCtx");

        if (ctx == null) {
            session.close();
            return;
        }

        processor.handle(session, ctx, line);
    }

    @Override public void messageSent(IoSession session, Object message) {}
    @Override public void exceptionCaught(IoSession session, Throwable cause) {
        session.close();
    }
    @Override public void sessionClosed(IoSession session) {}
}
