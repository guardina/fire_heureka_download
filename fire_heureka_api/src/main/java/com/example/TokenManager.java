package com.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;


/*
 * The TokenManager class manages the token access for the application. It represents a bridge between application
 * and DB when it comes to tokens. It is the class that check whether the used token is still valid and asks for a 
 * new one if it is not
 */

public class TokenManager {
    private String userId;
    private DatabaseController databaseController;
    private HeurekaClient heurekaClient;

    private final Object lock = new Object();

    public TokenManager(String userId) {
        this.userId = userId;
        this.databaseController = DatabaseController.getDatabaseController();
    }

    // Returns access token from DB
    public String getAccessToken() {
        synchronized (lock) {
            checkAndUpdate();
        }
        return databaseController.getEntry(userId, "user_tokens", "access_token");
    }


    // Returns refresh token from DB
    public String getRefreshToken() {
        return databaseController.getEntry(userId, "user_tokens", "refresh_token");
    }


    // Checks if the token has expired. If that's the case, it updates it
    private void checkAndUpdate() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String tokenTime = databaseController.getEntry(userId, "user_tokens", "token_expiry");
        
        if (tokenTime != null) {
            LocalDateTime tokenExpiry = LocalDateTime.parse(tokenTime, formatter);
  
            if (tokenExpiry.isBefore(now)) {
                refreshAccessToken();
            }
        }
    }


    // Asks for a new access token, refresh token and stores them in DB
    private void refreshAccessToken() {
        Map<String, String> newTokens = heurekaClient.getNewToken();
        if (newTokens != null) {
            databaseController.updateToken(newTokens.get("access_token"), newTokens.get("refresh_token"), newTokens.get("expires_in"), userId);
        }
    }



    public void setHeurekaClient(HeurekaClient heurekaClient) {
        this.heurekaClient = heurekaClient;
    }
}