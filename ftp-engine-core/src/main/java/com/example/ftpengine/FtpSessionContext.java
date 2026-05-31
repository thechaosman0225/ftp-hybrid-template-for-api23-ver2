package com.example.ftpengine;

import java.net.ServerSocket;
import java.net.Socket;

/**
 * Holds FTP session state for each connected client.
 *
 * FIX: passiveDataSocket is now volatile so that the background thread's
 * write in openPasv() is immediately visible to the polling loop in
 * waitForData(). Without volatile, the JVM/Android runtime is free to
 * cache the field in a register, meaning waitForData() could spin all
 * 200 iterations seeing null even after the accept thread has assigned
 * the socket — causing the spurious "550 LIST failed" error.
 */
public class FtpSessionContext {

    public boolean loggedIn = false;
    public String username = null;
    public String cwd = "/";
    public String transferType = "I"; // I = binary, A = ASCII

    /* ===================== ACTIVE MODE ===================== */
    public String dataHost = null;
    public int dataPort = -1;
    public Socket activeDataSocket = null;

    /* ===================== PASSIVE MODE ===================== */

    // Must store the server socket so it can be closed on cleanup.
    public ServerSocket passiveServerSocket = null;

    // FIX: volatile ensures the write by the accept-thread in openPasv()
    // is visible to the polling loop in waitForData() on the main thread.
    public volatile Socket passiveDataSocket = null;

    public int pasvPort = -1;

    /* ===================== RESET ===================== */

    public void reset() {
        loggedIn = false;
        username = null;
        cwd = "/";
        transferType = "I";

        dataHost = null;
        dataPort = -1;

        /* ACTIVE cleanup */
        if (activeDataSocket != null) {
            try { activeDataSocket.close(); } catch (Exception ignored) {}
            activeDataSocket = null;
        }

        /* PASSIVE cleanup */
        if (passiveDataSocket != null) {
            try { passiveDataSocket.close(); } catch (Exception ignored) {}
            passiveDataSocket = null;
        }

        if (passiveServerSocket != null) {
            try { passiveServerSocket.close(); } catch (Exception ignored) {}
            passiveServerSocket = null;
        }

        pasvPort = -1;
    }
}
