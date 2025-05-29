package com.mycompany.chatserverproject;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/** Acepta conexiones entrantes de otros servidores */
class PeerServerAcceptor implements Runnable {
    private final int peerPort;
    private final ClusteredChatServer server;

    PeerServerAcceptor(int peerPort, ClusteredChatServer server) {
        this.peerPort = peerPort;
        this.server   = server;
    }

    @Override
    public void run() {
        try (ServerSocket ss = new ServerSocket(peerPort)) {
            System.out.println("Escuchando pares en puerto " + peerPort);
            while (true) {
                Socket s = ss.accept();
                PeerHandler ph = new PeerHandler(s, server);
                new Thread(ph).start();          // esperamos HELLO
            }
        } catch (IOException e) {
            System.out.println("PeerServerAcceptor error: " + e.getMessage());
        }
    }

    
}
