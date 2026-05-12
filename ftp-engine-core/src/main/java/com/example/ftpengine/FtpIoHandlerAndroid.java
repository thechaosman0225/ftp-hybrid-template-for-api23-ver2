package com.example.ftpengine;

import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;

public class FtpIoHandlerAndroid extends IoHandlerAdapter {

    @Override
    public void sessionCreated(IoSession session) {
        // Session created
    }

    @Override
    public void sessionOpened(IoSession session) {
        // Session opened
    }

    @Override
    public void sessionClosed(IoSession session) {
        // Session closed
    }

    @Override
    public void sessionIdle(IoSession session, IdleStatus status) {
        // Close idle sessions to save battery / sockets
        session.closeNow();
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause) {
        cause.printStackTrace();
        session.closeNow();
    }

    @Override
    public void messageReceived(IoSession session, Object message) {
        // FTP engine will process commands here later
    }

    @Override
    public void messageSent(IoSession session, Object message) {
        // Optional logging
    }
}
