package com.mycompany.chatserverproject;

import com.mycompany.configloaderproject.ConfigLoader;
import com.mycompany.databaseconnectorproject.DatabaseConnection;
import com.mycompany.databaseconnectorproject.DatabaseConnector;
import java.net.InetAddress;

import java.util.Collections;

public class AppServerLauncher {

    public static void main(String[] args) {
        try {
            ConfigLoader cfg = new ConfigLoader();
            int defClientPort = Integer.parseInt(cfg.getProperty("port"));
            int defPeerPort   = Integer.parseInt(cfg.getProperty("peer_port"));
            int maxCon        = Integer.parseInt(cfg.getProperty("max_conexiones"));
            
            ServerConfigDialog dlg = new ServerConfigDialog(defClientPort, defPeerPort);
            if (!dlg.isAccepted()) return;
            
            String serverName = dlg.getServerName();
            
            String localIp = InetAddress.getLocalHost().getHostAddress();

            /* BBDD */
            String dbUrl  = cfg.getProperty("db_url");
            String dbUser = cfg.getProperty("db_user");
            String dbPass = cfg.getProperty("db_pass");
            DatabaseConnection db = new DatabaseConnector(dbUrl, dbUser, dbPass);

            /* Servidor (sin auto-conexión a peers) */
            ClusteredChatServer server = new ClusteredChatServer(
                    dlg.getClientPort(), maxCon, db, null,
                    serverName,
                    dlg.getPeerPort(), Collections.emptyList());

            ServerUI ui = new ServerGUI(server, localIp, dlg.getClientPort(), dlg.getPeerPort());
            server.setUI(ui);
            server.start();
        } catch (Exception ex) {
            System.err.println("❌ No se pudo arrancar el servidor: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
