package com.mycompany.chatserverproject;

import com.mycompany.databaseconnectorproject.DatabaseConnection;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClusteredChatServer extends ChatServer {
    private final Set<String> remoteUsers = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, String> serverStatus = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Set<String>> serverFiles = Collections.synchronizedMap(new HashMap<>());
    private final String serverId;
    private final int peerPort;
    private final List<PeerInfo> peersToConnect;
    private final Map<String, PeerHandler> activePeers = new ConcurrentHashMap<>();
    private final Set<String> processedIds = Collections.synchronizedSet(new HashSet<>());
    private ServerUI ui;
    private final Map<PrintWriter, String> writerToUser = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> remoteUserDetails = Collections.synchronizedMap(new HashMap<>());

    public ClusteredChatServer(int port, int maxCon, DatabaseConnection db, ServerUI ignoredUi, String serverId, int peerPort, List<PeerInfo> peers) {
        super(port, maxCon, db, ignoredUi);
        this.serverId = serverId;
        this.peerPort = peerPort;
        this.peersToConnect = peers;
        for (PeerInfo peer : peers) {
            serverStatus.put(peer.toString(), "DISCONNECTED");
            serverFiles.put(peer.toString(), new HashSet<>());
            remoteUserDetails.put(peer.toString(), new HashMap<>());
        }
        serverStatus.put(serverId, "CONNECTED");
        serverFiles.put(serverId, new HashSet<>());
        remoteUserDetails.put(serverId, new HashMap<>());
    }

    @Override
    public void start() {
        new Thread(new PeerServerAcceptor(peerPort, this), "PeerAcceptor").start();
//        new Timer("PeerConnector", true).scheduleAtFixedRate(
//            new TimerTask() {
//                @Override
//                public void run() {
//                    tryConnectToPeers();
//                }
//            }, 0, 10_000
//        );
        super.start();
    }

    private void tryConnectToPeers() {
        for (PeerInfo info : peersToConnect) {
            if (activePeers.containsKey(info.toString())) continue;
            try {
                Socket s = new Socket(info.getHost(), info.getPort());
                PeerHandler ph = new PeerHandler(s, this);
                registerPeer(info.toString(), ph);
                new Thread(ph).start();
                ph.hello(serverId);
                super.log("Conectado (saliente) a peer " + info);
                updateServerStatus(info.toString(), "CONNECTED");
                broadcastServerStatus();
                syncFilesWithPeer(ph);
                syncUsersWithPeer(ph);
            } catch (Exception ex) {
                super.log("No se pudo conectar con peer " + info + ": " + ex.getMessage());
                updateServerStatus(info.toString(), "DISCONNECTED");
            }
        }
    }
    
    private void tryConnectSinglePeer(PeerInfo info) {
        if (activePeers.containsKey(info.toString())) return;
        try {
            Socket s = new Socket(info.getHost(), info.getPort());
            PeerHandler ph = new PeerHandler(s, this);
            new Thread(ph).start();
            ph.hello(serverId);
            super.log("Conectado (saliente) a peer " + info);
        } catch (Exception ex) {
            super.log("No se pudo conectar con peer " + info + ": " + ex.getMessage());
            updateServerStatus(info.toString(), "DISCONNECTED");
        }
    }

    void registerPeer(String key, PeerHandler ph) {
        activePeers.put(key, ph);
        updateServerStatus(key, "CONNECTED");
        broadcastServerStatus();
    }

    void peerDisconnected(String key) {
        activePeers.remove(key);
        updateServerStatus(key, "DISCONNECTED");
        broadcastServerStatus();
        super.log("Peer desconectado: " + key);
    }

    private void updateServerStatus(String serverKey, String status) {
        serverStatus.put(serverKey, status);
        if (ui != null) {
            ui.updateServerStatus(new ArrayList<>(serverStatus.entrySet()));
        }
    }

    private void broadcastServerStatus() {
        StringBuilder statusMsg = new StringBuilder("SERVER_STATUS:");
        for (Map.Entry<String, String> entry : serverStatus.entrySet()) {
            statusMsg.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
        }
        String msg = statusMsg.length() > "SERVER_STATUS:".length() ? statusMsg.substring(0, statusMsg.length() - 1) : "SERVER_STATUS:none";
        broadcastToPeers(msg);
    }

    private void syncFilesWithPeer(PeerHandler ph) {
        Set<String> localFiles = serverFiles.getOrDefault(serverId, new HashSet<>());
        String fileList = String.join(",", localFiles);
        String id = UUID.randomUUID().toString();
        String frame = "PEER_MSG:" + id + ":" + serverId + ":FILE_SNAPSHOT:" + fileList;
        ph.sendPeerMessage(frame);
        for (String fileName : localFiles) {
            try (Connection conn = getDb().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT file FROM messages WHERE file_name = ?")) {
                stmt.setString(1, fileName);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    byte[] fileData = rs.getBytes("file");
                    String fileMsg = "FILE_SYNC:" + fileName + ":" + Base64.getEncoder().encodeToString(fileData);
                    ph.sendPeerMessage("PEER_MSG:" + UUID.randomUUID().toString() + ":" + serverId + ":" + fileMsg);
                }
            } catch (SQLException e) {
                super.log("Error al sincronizar archivo " + fileName + ": " + e.getMessage());
            }
        }
    }

    private void syncUsersWithPeer(PeerHandler ph) {
        List<Map<String, String>> users = getRegisteredUsers();
        StringBuilder userData = new StringBuilder();
        for (Map<String, String> user : users) {
            userData.append(user.get("username")).append("|")
                    .append(user.get("email")).append("|")
                    .append(user.get("ip_address")).append("|")
                    .append(user.get("photo") != null ? user.get("photo") : "none").append(",");
        }
        String userList = userData.length() > 0 ? userData.substring(0, userData.length() - 1) : "none";
        String id = UUID.randomUUID().toString();
        String frame = "PEER_MSG:" + id + ":" + serverId + ":USER_DB_SYNC:" + userList;
        ph.sendPeerMessage(frame);
    }

    private void broadcastToPeers(String plain) {
        String id = UUID.randomUUID().toString();
        String frame = "PEER_MSG:" + id + ":" + serverId + ":" + plain;
        for (PeerHandler ph : activePeers.values()) {
            ph.sendPeerMessage(frame);
        }
        processedIds.add(id);
    }

    @Override
    public synchronized void addClient(String username, PrintWriter out) {
        super.addClient(username, out);
        writerToUser.put(out, username);
        ui.updateOnlineUsers(getAllUsers());
        broadcastToPeers("USER_JOIN:" + username);
        syncFilesWithClient(out);
        for (PeerHandler peer : activePeers.values()) {
            syncUsersWithPeer(peer);
        }
    }

    @Override
    public synchronized void removeClient(PrintWriter out) {
        String user = writerToUser.remove(out);
        super.removeClient(out);
        ui.updateOnlineUsers(getAllUsers());
        if (user != null) {
            broadcastToPeers("USER_LEFT:" + user);
        }
    }

    @Override
    public synchronized void sendToUser(String username, String message, byte[] file) {
        String[] spl = message.split(":", 2);
        String sender = spl[0];
        String text = spl.length > 1 ? spl[1] : "";
        if (username.contains("@")) {
            String[] parts = username.split("@");
            String targetUser = parts[0];
            String targetServer = parts[1];
            if (!targetServer.equals(serverId)) {
                broadcastToPeers("USER_MSG:" + targetUser + ":" + sender + ":" + text + ":" + (file != null ? Base64.getEncoder().encodeToString(file) : "null"));
                super.log("(P2P-OUT) " + sender + " → " + username + " : " + text);
                return;
            }
            username = targetUser;
        }
        super.sendToUser(username, message, file);
        broadcastToPeers("USER_MSG:" + username + ":" + sender + ":" + text + ":" + (file != null ? Base64.getEncoder().encodeToString(file) : "null"));
        super.log("(P2P-OUT) " + sender + " → " + username + " : " + text);
    }

    @Override
    public synchronized void sendToChannel(String channel, String message, String sender, byte[] file) {
        super.sendToChannel(channel, message, sender, file);
        broadcastToPeers("CHANNEL_MSG:" + channel + ":" + sender + ":" + message + ":" + (file != null ? Base64.getEncoder().encodeToString(file) : "null"));
    }

    @Override
    public synchronized void createChannel(String channelName, String creator) {
        super.createChannel(channelName, creator);
        broadcastToPeers("CHANNEL_CREATE:" + channelName + ":" + creator);
    }

    @Override
    public synchronized void storeFile(byte[] file, String fileName, String destination, String sender) {
        super.storeFile(file, fileName, destination, sender);
        serverFiles.computeIfAbsent(serverId, k -> new HashSet<>()).add(fileName);
        broadcastToPeers("FILE_SYNC:" + fileName + ":" + Base64.getEncoder().encodeToString(file));
        super.log("Archivo sincronizado: " + fileName);
    }

    void handlePeerMessage(String frame) {
        if (!frame.startsWith("PEER_MSG:")) return;
        String[] parts = frame.split(":", 5);
        if (parts.length < 5) return;
        String id = parts[1];
        String src = parts[2];
        String tipo = parts[3];
        String payload = parts[4];
        if (processedIds.contains(id) || src.equals(serverId)) return;
        processedIds.add(id);
        String userAtServer = payload.contains(":") ? payload.split(":")[0] + "@" + src : payload + "@" + src;
        switch (tipo) {
            case "USER_JOIN":
                remoteUsers.add(userAtServer);
                super.log("Usuario conectado: " + userAtServer);
                ui.updateOnlineUsers(getAllUsers());
                sendOnlineUsersToAll();
                break;
            case "USER_LEFT":
                remoteUsers.remove(userAtServer);
                super.log("Usuario desconectado: " + userAtServer);
                ui.updateOnlineUsers(getAllUsers());
                sendOnlineUsersToAll();
                break;
            case "USER_MSG":
                String[] um = payload.split(":", 4);
                if (um.length < 4) break;
                String rawDest = um[0];
                String dest = rawDest.contains("@") ? rawDest.split("@")[0] : rawDest;
                String sender = um[1].contains("@") ? um[1] : um[1] + "@" + src;
                String text = um[2];
                byte[] file = "null".equals(um[3]) ? null : Base64.getDecoder().decode(um[3]);
                super.log("(PEER) USER_MSG " + sender + " → " + dest + " : " + text);
                if (getClients().containsKey(dest)) {
                    super.sendToUser(dest, sender + ":" + text, file);
                } else {
                    broadcastToPeers("USER_MSG:" + dest + ":" + sender + ":" + text + ":" + (file != null ? Base64.getEncoder().encodeToString(file) : "null"));
                }
                break;
            case "CHANNEL_MSG":
                String[] cm = payload.split(":", 5);
                if (cm.length < 5) break;
                String canal = cm[0], snd = cm[1], txt = cm[2];
                byte[] f = "null".equals(cm[3]) ? null : Base64.getDecoder().decode(cm[3]);
                super.sendToChannel(canal, txt, snd, f);
                break;
            case "CHANNEL_CREATE":
                String[] cc = payload.split(":", 2);
                if (cc.length == 2) super.createChannel(cc[0], cc[1]);
                break;
            case "USER_SNAPSHOT":
                if (!payload.equals("")) {
                    for (String u : payload.split(",")) {
                        remoteUsers.add(u + "@" + src);
                    }
                }
                ui.updateOnlineUsers(getAllUsers());
                sendOnlineUsersToAll();
                break;
            case "USER_DB_SYNC":
                Map<String, String> userDetails = remoteUserDetails.computeIfAbsent(src, k -> new HashMap<>());
                userDetails.clear();
                if (!payload.equals("none")) {
                    for (String user : payload.split(",")) {
                        String[] fields = user.split("\\|");
                        if (fields.length >= 4) {
                            try (Connection conn = getDb().getConnection();
                                 PreparedStatement stmt = conn.prepareStatement(
                                     "INSERT IGNORE INTO users (username, email, ip_address, photo) VALUES (?, ?, ?, ?)")) {
                                stmt.setString(1, fields[0]);
                                stmt.setString(2, fields[1]);
                                stmt.setString(3, fields[2]);
                                stmt.setString(4, fields[3].equals("none") ? null : fields[3]);
                                stmt.executeUpdate();
                                userDetails.put(fields[0], fields[1] + "|" + fields[2] + "|" + fields[3]);
                            } catch (SQLException e) {
                                super.log("Error al sincronizar usuario " + fields[0] + " desde " + src + ": " + e.getMessage());
                            }
                        }
                    }
                }
                super.log("Usuarios sincronizados desde " + src + ": " + userDetails.keySet());
                break;
            case "FILE_SNAPSHOT":
                Set<String> files = serverFiles.computeIfAbsent(src, k -> new HashSet<>());
                files.clear();
                if (!payload.equals("")) {
                    Collections.addAll(files, payload.split(","));
                }
                super.log("Actualizada lista de archivos de " + src + ": " + files);
                break;
            case "FILE_SYNC":
                String[] fs = payload.split(":", 2);
                if (fs.length < 2) break;
                String fileName = fs[0];
                byte[] fileData = Base64.getDecoder().decode(fs[1]);
                storeFile(fileData, fileName, "remote_" + src, src);
                serverFiles.computeIfAbsent(src, k -> new HashSet<>()).add(fileName);
                super.log("Archivo sincronizado desde " + src + ": " + fileName);
                break;
            case "SERVER_STATUS":
                String[] statuses = payload.equals("none") ? new String[0] : payload.split(",");
                for (String status : statuses) {
                    String[] kv = status.split("=");
                    if (kv.length == 2) {
                        serverStatus.put(kv[0], kv[1]);
                    }
                }
                if (ui != null) {
                    ui.updateServerStatus(new ArrayList<>(serverStatus.entrySet()));
                }
                super.log("Estado de servidores actualizado: " + serverStatus);
                break;
            case "SERVER_JOIN":
                if (!serverStatus.containsKey(payload)) {
                    serverStatus.put(payload, "CONNECTED");
                    serverFiles        .computeIfAbsent(payload, k -> new HashSet<>());
                    remoteUserDetails  .computeIfAbsent(payload, k -> new HashMap<>());
                    if (ui != null) ui.updateServerStatus(
                             new ArrayList<>(serverStatus.entrySet()));
                    super.log("Servidor se unió: " + payload);
                }
                break;
        }
    }

    @Override
    public synchronized void sendOnlineUsers(PrintWriter out) {
        String users = getAllUsers().isEmpty() ? "none" : String.join(",", getAllUsers());
        out.println(encrypt("ONLINE_USERS:" + users));
    }

    @Override
    public synchronized void sendOnlineUsersToAll() {
        String users = getAllUsers().isEmpty() ? "none" : String.join(",", getAllUsers());
        String enc = encrypt("ONLINE_USERS:" + users);
        for (PrintWriter c : getClients().values()) {
            c.println(enc);
        }
        log("(P2P) lista de usuarios en línea actualizada: " + users);
    }

    private List<String> getAllUsers() {
        List<String> all = new ArrayList<>(getClients().keySet());
        all.addAll(remoteUsers);
        return all;
    }

    private void syncFilesWithClient(PrintWriter out) {
        for (Map.Entry<String, Set<String>> entry : serverFiles.entrySet()) {
            for (String fileName : entry.getValue()) {
                try (Connection conn = getDb().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT file FROM messages WHERE file_name = ?")) {
                    stmt.setString(1, fileName);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        byte[] fileData = rs.getBytes("file");
                        String fileMsg = "FILE|" + super.getUsername(out) + "|server|" + fileName + "|" + Base64.getEncoder().encodeToString(fileData);
                        out.println(encrypt(fileMsg));
                    }
                } catch (SQLException e) {
                    super.log("Error al sincronizar archivo con cliente: " + fileName + ": " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void setUI(ServerUI ui) {
        super.setUI(ui);
        this.ui = ui;
        ui.updateServerStatus(new ArrayList<>(serverStatus.entrySet()));
    }

    private void sendSnapshotToPeer(PeerHandler ph) {
        String users = String.join(",", getClients().keySet());
        String id = UUID.randomUUID().toString();
        String frame = "PEER_MSG:" + id + ":" + serverId + ":USER_SNAPSHOT:" + users;
        ph.sendPeerMessage(frame);
        syncFilesWithPeer(ph);
        syncUsersWithPeer(ph);
        ph.sendPeerMessage("PEER_MSG:" + UUID.randomUUID().toString() + ":" + serverId + ":SERVER_JOIN:" + serverId);
    }
  
    public void connectToPeer(String host, int port) {
        tryConnectSinglePeer(new PeerInfo(host, port));
    }
    
    void onHello(String peerId, PeerHandler ph) {
        // Elimina cualquier entrada provisional como "/192.168.1.10:6000"
        serverStatus.remove(ph.toString());
        serverFiles.remove(ph.toString());
        remoteUserDetails.remove(ph.toString());

        // Verifica si se acepta la conexión desde la UI
        if (ui == null || ui.requestPeerApproval(peerId)) {
            registerPeer(peerId, ph);  // registra correctamente con el nombre del servidor
            ph.sendPeerMessage("PEER_MSG:" + UUID.randomUUID() + ":" + serverId + ":SERVER_JOIN:" + serverId);
        } else {
            ph.close();  // cierra la conexión si se rechaza
        }
    }
    
    public String getServerId() {
        return serverId;
    }
}