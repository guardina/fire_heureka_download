package com.example;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ThreadManager {

    private ExecutorService executor;
    private int numberOfThreads; 
    
    public ThreadManager(int numberOfThreads) {
        this.numberOfThreads = numberOfThreads;
        this.executor = Executors.newFixedThreadPool(numberOfThreads);
    }


    public void submitTask(Runnable task) {
        executor.submit(task);
    }

    public <T> List<Future<T>> submitTasks(List<Callable<T>> tasks) {
        try {
            return executor.invokeAll(tasks);
        } catch (InterruptedException ie) {
            ie.printStackTrace();
        }
        return null;
    }


    public void shutdown() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }


    public boolean isTerminated() {
        return executor.isTerminated();
    }

    public int getNumberOfThreads() {
        return this.numberOfThreads;
    }
}