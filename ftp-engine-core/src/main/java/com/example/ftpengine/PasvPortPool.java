package com.example.ftpengine;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

public class PasvPortPool {

    private final int minPort;
    private final int maxPort;
    private int next;
    private final Set<Integer> used = new HashSet<>();

    public PasvPortPool(int minPort, int maxPort) {
        this.minPort = minPort;
        this.maxPort = maxPort;
        this.next = minPort;
    }

    public synchronized ServerSocket openSocket() throws IOException {
        for (int i = 0; i <= (maxPort - minPort); i++) {

            int port = next++;
            if (next > maxPort) next = minPort;

            if (used.contains(port)) continue;

            try {
                ServerSocket socket = new ServerSocket(port);
                used.add(port);
                return socket;
            } catch (IOException ignored) {}
        }

        throw new IOException("No free PASV ports");
    }

    public synchronized void release(int port) {
        used.remove(port);
    }
}
