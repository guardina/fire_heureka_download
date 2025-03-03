package com.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
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


    // INSERT, UPDATE, DELETE
    public void executeUpdate(String query, Object... params) {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            Connection conn = connect();
            PreparedStatement stmt = conn.prepareStatement(query);
            
            if (params != null) {
                setParameters(stmt, params);
            }
            
            logger.info("Executed update: " + query);
        } catch (SQLException e) {
            e.printStackTrace();
            logger.error("Database update failed: " + e.getMessage());
        } catch (ClassNotFoundException c) {
            c.printStackTrace();
            logger.error("ClassNotFoundException: " + c.getMessage());
        }
    }


    // SELECT 
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
}