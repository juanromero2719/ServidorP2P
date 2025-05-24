/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.mycompany.chatserverproject;

import javax.swing.*;
import java.awt.*;
/**
 *
 * @author juanr
 */
public class PeerConnectDialog extends JDialog {

    private final JTextField hostField = new JTextField("127.0.0.1", 15);
    private final JTextField portField = new JTextField("6000", 6);
    private boolean accepted = false;

    public PeerConnectDialog(Frame owner) {
        super(owner, "Conectar a otro Servidor", true);
        buildUI();
        pack();
        setLocationRelativeTo(owner);
        setVisible(true);
    }

    private void buildUI() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5,5,5,5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("IP / Host:"), gbc);
        gbc.gridx = 1;
        panel.add(hostField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Puerto:"), gbc);
        gbc.gridx = 1;
        panel.add(portField, gbc);

        JButton ok = new JButton("Conectar");
        ok.addActionListener(e -> {
            accepted = true;
            dispose();
        });
        JButton cancel = new JButton("Cancelar");
        cancel.addActionListener(e -> dispose());

        JPanel btns = new JPanel();
        btns.add(ok);
        btns.add(cancel);

        getContentPane().add(panel, BorderLayout.CENTER);
        getContentPane().add(btns, BorderLayout.SOUTH);
    }

    // --- getters ----------------------------------------------------------
    public boolean isAccepted() { return accepted; }

    public String getHost() { return hostField.getText().trim(); }

    public int getPort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            return 6000; // valor por defecto
        }
    }
}
