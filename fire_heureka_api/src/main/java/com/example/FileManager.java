package com.example;

import java.io.File;
import java.io.IOException;

import org.slf4j.LoggerFactory;

public class FileManager {

    private ExitOnErrorLogger logger;
    private static FileManager fileManager;

    private FileManager() {
        this.logger = new ExitOnErrorLogger(LoggerFactory.getLogger(FileManager.class));
    }


    public static FileManager getFileManager() {
        if (fileManager == null) {
            fileManager = new FileManager();
        }
        return fileManager;
    }


    // Creates the specified directories
    public boolean createDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                return true;
            } else {
                logger.error("Failed to create directory " + directoryPath);
                return false;
            }
        }
        return true;
    }


    // Creates the specified file in the specified directory
    public boolean createFile(String fullFilePath) {
        File file = new File(fullFilePath);
        File directory = file.getParentFile();
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                return true;
            } else {
                logger.error("Failed to create directory");
                return false;
            }
        }

        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    return true;
                } else {
                    logger.error("Failed to create file");
                    return false;
                }
            } catch (IOException e) {
                logger.error("IOException raised: " + e.getMessage());
            }
        }
    
        return true;
    }
}