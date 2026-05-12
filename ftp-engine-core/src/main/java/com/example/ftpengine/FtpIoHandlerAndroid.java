package com.example.ftpengine;

import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;

public class FtpIoHandlerAndroid implements IoHandler {

    private final FtpCommandProcessor processor;

    public FtpIoHandlerAndroid(FtpCommandProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void sessionCreated(IoSession session) {}

    @Override
    public void sessionOpened(IoSession session) {
        FtpSessionContext ctx = new FtpSessionContext();
        session.setAttribute("ctx", ctx);
        session.write("220 Android FTP Server Ready\r\n".getBytes());
    }

    @Override
    public void sessionClosed(IoSession session) {}

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) {}

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        session.close();
    }

    @Override
    public void messageReceived(IoSession session, Object message) {
        String line = message.toString().trim();
        FtpSessionContext ctx = (FtpSessionContext) session.getAttribute("ctx");
        processor.handle(session, ctx, line);
    }

    @Override
    public void messageSent(IoSession session, Object message) {}

    // ⭐ NEW METHOD required by latest MINA
    @Override
    public void inputClosed(IoSession session) {
        session.closeNow();
    }
}
