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

                /* usamos la dirección remota como clave temporal hasta recibir HELLO */
                String key = s.getRemoteSocketAddress().toString();
                server.registerPeer(key, ph);

                new Thread(ph).start();
            }
        } catch (IOException e) {
            System.out.println("PeerServerAcceptor error: " + e.getMessage());
        }
    }
}
