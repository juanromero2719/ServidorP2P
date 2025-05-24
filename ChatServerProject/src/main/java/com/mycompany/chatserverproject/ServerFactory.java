/**
 *
 * @author home
 */

package com.mycompany.chatserverproject;

import com.mycompany.databaseconnectorproject.DatabaseConnection;
import java.util.Collections;

public class ServerFactory {
    /** Ahora crea siempre un ClusteredChatServer */
    public static ClusteredChatServer createServer(int port, int maxCon,
                                                   DatabaseConnection db, ServerUI ui,
                                                   String serverId, int peerPort) {
        return new ClusteredChatServer(port, maxCon, db, ui,
                                       serverId, peerPort, Collections.emptyList());
    }
}