package com.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TokenManager {
    String userId;
    DatabaseController databaseController;
    private final Object lock = new Object();

    public TokenManager(String userId, DatabaseController databaseController) {
        this.userId = userId;
        this.databaseController = databaseController;
    }


        
    public String getAccessToken() {
        synchronized (lock) {
            checkAndUpdate();
        }
        return databaseController.getEntry(userId, "user_tokens", "access_token");
    }


    // Method that checks if the token has expired. If that's the case, it updates it
    private void checkAndUpdate() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime tokenExpiry = LocalDateTime.parse(databaseController.getEntry(userId, "user_tokens", "token_expiry"), formatter);
  
        if (tokenExpiry.isBefore(now)) {
            refreshAccessToken();
        }
    }


    private void refreshAccessToken() {
        databaseController.updateToken("new_access_to", "new_refresh_to", "299", userId);
    }
}