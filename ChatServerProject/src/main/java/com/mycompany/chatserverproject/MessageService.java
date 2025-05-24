/**
 *
 * @author Estudiante_MCA
 */

package com.mycompany.chatserverproject;

import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;

public interface MessageService {
    void registerClient(String username, PrintWriter out);
    void broadcast(String message, String sender);
    void sendToChannel(String channel, String message, String sender, byte[] file);
    void joinChannel(String channel, PrintWriter out);
    void removeClient(PrintWriter out);
    Map<String, PrintWriter> getClients();
}