package com.mycompany.chatserverproject;

import javax.swing.*;
import java.awt.*;
import java.net.InetAddress;

/** Diálogo para elegir puertos antes de arrancar el servidor. */
class ServerConfigDialog extends JDialog {
    private final JTextField ipField;
    private final JTextField clientPortField;
    private final JTextField peerPortField;
    private boolean accepted = false;

    ServerConfigDialog(int defaultClient, int defaultPeer) {
        super((Frame) null, "Configurar Servidor", true);
        String localIp = "0.0.0.0";
        try { localIp = InetAddress.getLocalHost().getHostAddress(); } catch (Exception ignored) {}

        ipField = new JTextField(localIp);
        ipField.setEditable(false);
        clientPortField = new JTextField(String.valueOf(defaultClient));
        peerPortField   = new JTextField(String.valueOf(defaultPeer));

        buildUI();
        setVisible(true);
    }

    private void buildUI() {
        JPanel form = new JPanel(new GridLayout(3, 2, 5, 5));
        form.add(new JLabel("IP del Servidor:"));
        form.add(ipField);
        form.add(new JLabel("Puerto para Clientes:"));
        form.add(clientPortField);
        form.add(new JLabel("Puerto entre Servidores:"));
        form.add(peerPortField);

        JButton ok  = new JButton("Iniciar");
        JButton can = new JButton("Cancelar");
        ok.addActionListener(e -> {
            if (validatePorts()) {
                accepted = true;
                dispose();
            }
        });
        can.addActionListener(e -> dispose());

        JPanel btns = new JPanel();
        btns.add(ok); btns.add(can);

        getContentPane().add(form, BorderLayout.CENTER);
        getContentPane().add(btns, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);
    }

    private boolean validatePorts() {
        try {
            int c = Integer.parseInt(clientPortField.getText().trim());
            int p = Integer.parseInt(peerPortField.getText().trim());
            if (c < 1 || c > 65535 || p < 1 || p > 65535 || c == p) throw new NumberFormatException();
            return true;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Puertos inválidos (1-65535 y distintos).",
                    "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    boolean isAccepted()           { return accepted; }
    int     getClientPort()        { return Integer.parseInt(clientPortField.getText().trim()); }
    int     getPeerPort()          { return Integer.parseInt(peerPortField.getText().trim()); }
}
