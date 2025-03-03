package com.example;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Logger {
    private String logDir;

    public Logger (String logDir) {
        this.logDir = logDir;

        File dir = new File(logDir);
        if (!dir.exists()) {
            dir.mkdir();
        }
    }

    private void log(String message, String level) {
        String filename = logDir + "/" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log";
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logMessage = String.format("[%s] [%s] %s%n", timestamp, level, message);

        try (PrintWriter out = new PrintWriter(new FileWriter(filename, true))) {
            out.print(logMessage);
        } catch (IOException e) {
            System.err.println("Failed to write log: " + e.getMessage());
        }
    }


    public void info(String message) {
        log(message, "INFO");
    }

    public void error(String message) {
        log(message, "ERROR");
    }

    public void debug(String message) {
        log(message, "DEBUG");
    }
}