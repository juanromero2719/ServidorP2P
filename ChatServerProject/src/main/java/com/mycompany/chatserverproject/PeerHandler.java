package com.mycompany.chatserverproject;

import java.io.*;
import java.net.Socket;

class PeerHandler implements Runnable {
    private final Socket socket;
    private final ClusteredChatServer server;
    private final CryptoService crypto = new CryptoService();
    private BufferedReader in;
    private PrintWriter out;
    private String peerId = "UNKNOWN";
    private volatile boolean running = true;

    PeerHandler(Socket socket, ClusteredChatServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    void hello(String myId) {
        sendRaw("HELLO:" + myId);
    }

    private void sendRaw(String plain) {
        out.println(crypto.encrypt(plain));
    }

    void sendPeerMessage(String plain) {
        out.println(crypto.encrypt(plain));
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                String decoded = crypto.decrypt(line);
                if (decoded.startsWith("HELLO:")) {
                    peerId = decoded.substring(6);
                    server.onHello(peerId, this);
                    continue;
                }
                server.handlePeerMessage(decoded);
            }
        } catch (IOException ignored) {
        } finally {
            stop();
        }
    }

    void stop() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        server.peerDisconnected(peerId);
    }

    @Override
    public String toString() {
        return peerId;
    }
    
    void close() { stop(); }
}