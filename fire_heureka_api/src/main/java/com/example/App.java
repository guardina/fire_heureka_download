package com.example;

import java.util.List;
import java.util.Map;

public class App {

    private static Logger logger;
    private static FileManager fileManager;
    private static DatabaseController databaseController;
    private static HeurekaClient heurekaClient;
    public static void main( String[] args ) {
    
        logger = new Logger("/home/alex/Desktop/json_fire5_parser/fire_heureka_download/logs");
        fileManager = new FileManager(logger);
        databaseController = new DatabaseController("fire_heureka_credentials", "alex", "password", logger);
        heurekaClient = new HeurekaClient();

        MyVault vault = new MyVault();
        System.out.println(vault.getClientId());


        
        String query = "SELECT username, id FROM user_credentials;";

        List<Map<String, String>> results = databaseController.executeSelectQuery(query, null);

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

            TokenManager tokenManager = new TokenManager(userId, databaseController);
            System.out.println(tokenManager.getAccessToken());

            System.out.println("USERNAME: " + praxisName + ", USER_ID: " + userId);
            
        }


    }

}