package com.example;

import java.io.File;
import java.io.IOException;

public class FileManager {

    private Logger logger;

    public FileManager(Logger logger) {
        this.logger = logger;
    }


    // Creates the specified file in the specified directory
    
    public boolean createFile(String directoryPath, String fileName) {
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                logger.info("Successfully created the folder " + directoryPath);
                return true;
            } else {
                logger.error("Failed to create directory.");
                return false;
            }
        }
    
        File file = new File(directoryPath + "/" + fileName);
        if (!file.exists()) {
            try {
                if (file.createNewFile()) {
                    logger.info("File created: " + fileName);
                    return true;
                } else {
                    logger.error("Failed to create file.");
                    return false;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            logger.info("File already exists: " + fileName);
        }
    
        return true;
    }
}