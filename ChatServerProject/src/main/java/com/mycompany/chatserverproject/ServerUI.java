package com.mycompany.chatserverproject;

import java.util.List;
import java.util.Map;

public interface ServerUI {
    void displayMessage(String message);
    void initUI(Runnable reportAction);
    void updateOnlineUsers(List<String> users);
    void updateServerStatus(List<Map.Entry<String, String>> statuses);
}