package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseController {
    private String url;
    private String user;
    private String password;
    private Logger logger;

    public DatabaseController(String database, String user, String password, Logger logger) {
        this.url = "jdbc:mysql://127.0.0.1:3306/" + database;
        this.user = user;
        this.password = password;
        this.logger = logger;
    }


    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }



    public List<Map<String, String>> executeQuery(String query, Object... params) {
        List<Map<String, String>> results = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {
             
            if (params != null) {
                setParameters(stmt, params);
            }

            ResultSet rs = stmt.executeQuery();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            List<String> columnNames = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                columnNames.add(metaData.getColumnLabel(i));
            }

            while (rs.next()) {
                Map<String, String> rowMap = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    rowMap.put(columnNames.get(i - 1), rs.getString(i));
                }
                results.add(rowMap);
            }
            logger.info("Executed query: " + query);
        } catch (SQLException e) {
            logger.error("Database query failed: " + e.getMessage());
        }
        return results;
    }


    
    private void setParameters(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }



    public String getToken(String userId, String tokenType) {
        String query = "";
        if (tokenType.equals("access")) {
            query = "SELECT access_token FROM user_tokens WHERE user_id = ?";
        } else if (tokenType.equals("refresh")) {
            query = "SELECT refresh_token FROM user_tokens WHERE user_id = ?";
        }
        
        if (!query.equals("")) {
            try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            if (!userId.equals("")) {
                setParameters(stmt, userId);
            }


            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String token = rs.getString(tokenType + "_token");
                    logger.info("Retrieved access token for user: " + userId);
                    return token;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve access token from database: " + e.getMessage());
        }
        }
        
        return null;
    }



    public void updateToken(String accessToken, String refreshToken, String tokenExpiry, String userId) {
        LocalDateTime today = LocalDateTime.now();
        LocalDateTime newTokenExpiry = today.plusSeconds(Integer.parseInt(tokenExpiry));
        String updateTokenQuery = "UPDATE user_tokens SET access_token = ?, refresh_token = ?, token_expiry = ?, updated_at = ? WHERE user_id = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(updateTokenQuery)) {

            if (!userId.equals("")) {
                setParameters(stmt, accessToken, refreshToken, newTokenExpiry, today, userId);
            }


            stmt.executeUpdate();
            logger.info("Successfully updated token!");
        } catch (SQLException e) {
            logger.error("Failed to update token in database: " + e.getMessage());
        }
    }
}