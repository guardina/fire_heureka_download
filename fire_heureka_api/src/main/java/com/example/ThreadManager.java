package com.example;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.LoggerFactory;

public class ThreadManager {

    private ExecutorService executor;
    private int numberOfThreads; 
    private ExitOnErrorLogger logger;
    
    public ThreadManager(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        this.executor = Executors.newFixedThreadPool(numberOfThreads);
        this.logger = new ExitOnErrorLogger(LoggerFactory.getLogger(ThreadManager.class));
    }


    public void submitTask(String praxisName, Runnable task) {
        executor.submit(() -> {
            Instant start = Instant.now();
            logger.info("Praxis " + praxisName + " download started -->  " + start);

            task.run();

            Instant end = Instant.now();
            logger.info("Praxis " + praxisName + " donwload finished -->  " + end + " (Duration: " + Duration.between(start, end).toSeconds() + " s)");
        });
    }

    public void submitTasks(Map<String, Runnable> tasks) {
        for (Map.Entry<String, Runnable> entry : tasks.entrySet()) {
            submitTask(entry.getKey(), entry.getValue());
        }
    }


    public void shutdown() {
        try {
            executor.shutdown(); 
    
            while (!executor.isTerminated()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.error("Shutdown interrupted. Exiting. " + e);
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            logger.info("All tasks completed successfully.");
        } catch (Exception e) {
            logger.error("Exception during shutdown: "  + e);
        }
    }


    public boolean isTerminated() {
        return executor.isTerminated();
    }

    public int getNumberOfThreads() {
        return this.numberOfThreads;
    }
}