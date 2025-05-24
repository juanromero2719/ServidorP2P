package com.mycompany.chatserverproject;

import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.List;
import java.util.Map;

public class ServerGUI implements ServerUI {
    private JTextArea logArea;
    private JFrame frame;
    private final ChatServer server;
    private final String ip;
    private final int clientPort;
    private final int peerPort;
    private JList<String> usersList;
    private DefaultListModel<String> usersModel;
    private JList<String> serversList;
    private DefaultListModel<String> serversModel;

    public ServerGUI(ChatServer server, String ip, int clientPort, int peerPort) {
        this.server = server;
        this.ip = ip;
        this.clientPort = clientPort;
        this.peerPort = peerPort;
        try {
            SwingUtilities.invokeAndWait(this::buildUI);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void buildUI() {
        frame = new JFrame("Chat Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);

        /* ---------- TOOLBAR CON BOTÓN ---------- */
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);

        JButton peerBtn = new JButton("Conectar servidor…");
        peerBtn.addActionListener(e -> {
            PeerConnectDialog dlg = new PeerConnectDialog(frame);
            if (dlg.isAccepted() && server instanceof ClusteredChatServer) {
                ((ClusteredChatServer) server)
                        .connectToPeer(dlg.getHost(), dlg.getPort());
            }
        });
        toolbar.add(peerBtn);

        /* ---------- PANEL DE INFORMACIÓN ---------- */
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        infoPanel.setBorder(BorderFactory.createEtchedBorder());
        infoPanel.add(new JLabel("IP: " + ip));
        infoPanel.add(new JLabel("|  Puerto Clientes: " + clientPort));
        infoPanel.add(new JLabel("|  Puerto Servidores: " + peerPort));
        infoPanel.add(new JLabel("|  Nombre: " + ((ClusteredChatServer) server).getServerId()));

        // Combina toolbar e infoPanel en un solo panel superior
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toolbar, BorderLayout.NORTH);
        topPanel.add(infoPanel, BorderLayout.SOUTH);
        frame.getContentPane().add(topPanel, BorderLayout.NORTH);

        /* ---------- PANEL IZQUIERDO (Usuarios / Servidores) ---------- */
        usersModel = new DefaultListModel<>();
        usersList = new JList<>(usersModel);
        JScrollPane usersPane = new JScrollPane(usersList);
        usersPane.setBorder(BorderFactory.createTitledBorder("Usuarios Conectados"));
        usersPane.setPreferredSize(new Dimension(200, 0));

        serversModel = new DefaultListModel<>();
        serversList = new JList<>(serversModel);
        JScrollPane serversPane = new JScrollPane(serversList);
        serversPane.setBorder(BorderFactory.createTitledBorder("Servidores"));
        serversPane.setPreferredSize(new Dimension(200, 0));

        JPanel leftPanel = new JPanel(new GridLayout(2, 1));
        leftPanel.add(usersPane);
        leftPanel.add(serversPane);

        /* ---------- PANEL DE LOGS ---------- */
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane logPane = new JScrollPane(logArea);
        logPane.setBorder(BorderFactory.createTitledBorder("Logs de Servidor"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, logPane);
        split.setDividerLocation(200);
        frame.getContentPane().add(split, BorderLayout.CENTER);

        /* ---------- BOTÓN DE INFORMES ---------- */
        JButton reportBtn = new JButton("Generar Informes");
        reportBtn.addActionListener(e -> generateReports());
        frame.getContentPane().add(reportBtn, BorderLayout.SOUTH);

        frame.setVisible(true);
    }

    @Override
    public void displayMessage(String message) {
        if (logArea != null) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(message + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        } else {
            System.err.println("Error: logArea es null. Mensaje: " + message);
        }
    }

    @Override
    public void initUI(Runnable reportAction) {
        // Método no utilizado con la versión actual de GUI 
    }

    private void generateReports() {
        StringBuilder report = new StringBuilder();
        report.append("=== Informes del Servidor ===\n\n");
        report.append("Usuarios Registrados:\n");
        try (Connection conn = server.getDb().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT username, email, ip_address FROM users")) {
            while (rs.next()) {
                report.append(String.format("Usuario: %s, Email: %s, IP: %s\n",
                        rs.getString("username"), rs.getString("email"), rs.getString("ip_address")));
            }
        } catch (SQLException e) {
            report.append("Error al obtener usuarios registrados: " + e.getMessage() + "\n");
        }
        report.append("\nUsuarios Conectados:\n");
        for (String username : server.getClients().keySet()) {
            report.append(username + "\n");
        }
        if (server.getClients().isEmpty()) {
            report.append("Ninguno\n");
        }
        JTextArea reportArea = new JTextArea(report.toString());
        reportArea.setEditable(false);
        JOptionPane.showMessageDialog(frame, new JScrollPane(reportArea), "Informes", JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public void updateOnlineUsers(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            usersModel.clear();
            for (String u : users) {
                usersModel.addElement(u);
            }
        });
    }

    @Override
    public void updateServerStatus(List<Map.Entry<String, String>> statuses) {
        SwingUtilities.invokeLater(() -> {
            serversModel.clear();
            for (Map.Entry<String, String> status : statuses) {
                serversModel.addElement(status.getKey() + " (" + status.getValue() + ")");
            }
        });
    }
    
    // implementación del nuevo método
    @Override
    public boolean requestPeerApproval(String peerId) {
        int res = JOptionPane.showConfirmDialog(frame,
            "El servidor «"+peerId+"» solicita conectarse.\n¿Aceptar?",
            "Nueva conexión P2P", JOptionPane.YES_NO_OPTION);
        return res == JOptionPane.YES_OPTION;
    }
}
