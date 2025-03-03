package com.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class App {

    private static Logger logger;
    private static FileManager fileManager;
    public static void main( String[] args ) {
    
        logger = new Logger("/home/debian/Desktop/json_fire5_parser/fire_heureka_api/logs");
        fileManager = new FileManager(logger);
        DatabaseController databaseController = new DatabaseController("fire_heureka_credentials", "debian", "password", logger);
        
        String query = "SELECT uc.username AS praxis_name, ut.user_id, ut.access_token FROM user_credentials uc JOIN user_tokens ut ON uc.id = ut.user_id;";

        List<Map<String, String>> results = databaseController.executeQuery(query, null);

        for (Map<String, String> row : results) {
            for (Map.Entry<String, String> entry : row.entrySet()) {
                System.out.print(entry.getKey() + ": " + entry.getValue() + " \n");
            }
            System.out.println();
        }



        ThreadManager threadManager = new ThreadManager(4);

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int taskId = i;
            tasks.add(() -> {
                try {
                    System.out.println("Task " + taskId + " is being processed by " + Thread.currentThread().getName());
                    Thread.sleep(2000);
                    System.out.println("Task " + taskId + " is complete.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        for (Runnable task : tasks) {
            threadManager.submitTask(task);
        }

        threadManager.shutdown();

        System.out.println("All tasks completed: " + threadManager.isTerminated());
    }

}