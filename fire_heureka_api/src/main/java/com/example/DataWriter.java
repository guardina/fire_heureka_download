package com.example;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;

import org.slf4j.LoggerFactory;

public class DataWriter {
    private FileWriter writer;
    private ExitOnErrorLogger logger;
    private FileManager fileManager;

    private String filePath;

    // Creates a new folder whose name is the download's date. Creates the file whose name is the name of the praxis (in our system)
    public DataWriter(String praxisName) {
        this.logger = new ExitOnErrorLogger(LoggerFactory.getLogger(DataWriter.class));
        this.fileManager = FileManager.getFileManager();
        String todayDate = LocalDate.now().toString();
        this.filePath = "fire_heureka_api/src/main/full_download/" + todayDate + "/" + praxisName + ".json";
        setFileWriter();
    }


    public void setFileWriter() {
        fileManager.createFile(filePath);
        try {
            this.writer = new FileWriter(filePath, false);
        } catch (IOException e) {
            logger.error("File " + filePath + " not found");
        }
    }


    public void writeOnFile(String text) {
        try {
            writer.write(text);
            writer.flush();
        } catch (IOException e) {
            logger.error("Error while writing on file: " + e.getMessage());
        }
    }
}