package com.example;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

public class App {

    private static DatabaseController databaseController;
    public static void main( String[] args ) {

        /*Encrypter test = new Encrypter();
        test.runTest();*/
    
        ExitOnErrorLogger logger = new ExitOnErrorLogger(LoggerFactory.getLogger(App.class));
        logger.info("Started Download");
        databaseController = DatabaseController.getDatabaseController();
        
        String query = "SELECT uc.username, uc.id \n" + //
                        "FROM user_credentials uc\n" + //
                        "INNER JOIN user_tokens ut ON uc.id = ut.user_id\n" + //
                        "WHERE ut.access_token IS NOT NULL";

        List<Map<String, String>> results = databaseController.executeSelectQuery(query);

        Map<String, Runnable> tasks = new HashMap<>();

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
            
            tasks.put(userId, new FullDownloadTask(userId, praxisName));
        }

        ThreadManager threadManager = new ThreadManager(Runtime.getRuntime().availableProcessors());
        threadManager.submitTasks(tasks);

        threadManager.shutdown();

    }


    public static class FullDownloadTask implements Runnable {
        HeurekaClient heurekaClient;

        public FullDownloadTask(String userId, String praxisName) {
            DataWriter writer = new DataWriter(praxisName);
            TokenManager tokenManager = new TokenManager(userId);
            this.heurekaClient = new HeurekaClient(userId, tokenManager, writer);
            tokenManager.setHeurekaClient(heurekaClient);
        }
    
        @Override
        public void run() {
            heurekaClient.configureHeureka();
            heurekaClient.fullDownload();
        }
    }
}


