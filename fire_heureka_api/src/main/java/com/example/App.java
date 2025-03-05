package com.example;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class App {

    private static Logger logger;
    private static FileManager fileManager;
    public static void main( String[] args ) {
    
        logger = new Logger("/home/alex/Desktop/json_fire5_parser/fire_heureka_download/logs");
        fileManager = new FileManager(logger);
        DatabaseController databaseController = new DatabaseController("fire_heureka_credentials", "alex", "password", logger);
        HeurekaClient heurekaClient = new HeurekaClient();




        
        String query = "SELECT username, id FROM user_credentials;";

        List<Map<String, String>> results = databaseController.executeQuery(query, null);

        for (Map<String, String> row : results) {
            String userId = "";
            String praxisName = "";
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (entry.getKey().equals("id")) {
                    userId = entry.getValue();
                } else if (entry.getKey().equals("username")) {
                    praxisName = entry.getValue();
                }
            }

            System.out.println("USERNAME: " + praxisName + ", USER_ID: " + userId);

            if (!userId.equals("")) {
                databaseController.updateToken("new_access_t", "new_refresh_t", "299", userId);
            }

            
        }


    }

}