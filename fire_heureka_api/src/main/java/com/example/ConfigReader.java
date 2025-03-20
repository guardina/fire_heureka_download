package com.example;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigReader {
    private static ConfigReader configReader;
    private static Properties properties;

    public ConfigReader() {
        properties = new Properties();
        try (FileInputStream input = new FileInputStream("fire_heureka_api/src/main/resources/config.properties")) {
            properties.load(input);
        } catch (IOException e) {
            System.out.println("Error while loading properties: " + e.getMessage());
        }
    }

    public static ConfigReader getConfigReader() {
        if (configReader == null) {
            configReader = new ConfigReader();
        }
        return configReader;
    }

    public Properties getProperties(){
        return properties;
    }

}
