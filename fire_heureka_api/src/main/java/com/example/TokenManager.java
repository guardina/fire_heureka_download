package com.example;

public class TokenManager {
    String userId;
    DatabaseController databaseController;

    public TokenManager(String userId, DatabaseController databaseController) {
        this.userId = userId;
        this.databaseController = databaseController;
    }

    public String getAccessToken() {
        databaseController.getToken(userId, "access");
        return "";
    }
}