package com.example.ftpengine;

import java.net.ServerSocket;
import java.net.Socket;

/**
 * Holds FTP session state for each connected client.
 * FIXED: proper passive mode lifecycle management.
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

    /* ===================== PASSIVE MODE (FIXED) ===================== */

    // FIX: MUST store server socket (not only client socket)
    public ServerSocket passiveServerSocket = null;

    // client connection accepted from ServerSocket
    public Socket passiveDataSocket = null;

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

        /* PASSIVE cleanup (FIXED) */
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
