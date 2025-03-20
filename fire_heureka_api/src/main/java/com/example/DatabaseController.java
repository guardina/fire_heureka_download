package com.example;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.LoggerFactory;



public class DatabaseController {
    private String url;
    private String user;
    private String password;
    private ExitOnErrorLogger logger;

    private static DatabaseController databaseController;

    public DatabaseController() {
        Properties properties = ConfigReader.getConfigReader().getProperties();
        this.url = properties.getProperty("db.url");
        this.user = properties.getProperty("db.user");
        this.password = properties.getProperty("db.password");
        this.logger = new ExitOnErrorLogger(LoggerFactory.getLogger(DatabaseController.class));
    }


    public static DatabaseController getDatabaseController() {
        if (databaseController == null) {
            databaseController = new DatabaseController();
        }
        return databaseController;
    }


    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }


    // Executes any SELECT query, specifying the columns to select
    public List<Map<String, String>> executeSelectQuery(String query, Object... params) {
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
        } catch (SQLException e) {
            logger.error("Database query failed: " + e.getMessage());
        }
        return results;
    }


    // SELECTs a single column from a table in the DB
    public String getEntry(String userId, String tableName, String columnName) {
        String query = "SELECT " + columnName + " FROM " + tableName + " WHERE user_id = ?";
        
        if (!query.equals("")) {
            try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            if (!userId.equals("")) {
                setParameters(stmt, userId);
            }


            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String token = rs.getString(columnName);
                    return token;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to retrieve " + columnName + " from database: " + e.getMessage());
        }
        }
        
        return null;
    }


    // Stores new access token, refresh token and token expiry in DB
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
        } catch (SQLException e) {
            logger.error("Failed to update token in database: " + e.getMessage());
        }
    }




    private void setParameters(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }
}