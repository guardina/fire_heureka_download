package com.example;

import org.slf4j.Logger;


// Acts exactly like the slf4j logger, but when it prints an error it stops the program
public class ExitOnErrorLogger{
    private final Logger logger;

    public ExitOnErrorLogger(Logger logger){
        this.logger = logger;
    }

    public void info(String message) {
        logger.info(message);
    }

    public void error(String message) {
        logger.error(message);
        System.exit(1);
    }

    public void debug(String message) {
        logger.debug(message);
    }

    public void warn(String message) {
        logger.warn(message);
    }
}