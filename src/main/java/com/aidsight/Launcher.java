package com.aidsight;

import com.aidsight.presentation.MainApplication;
import javafx.application.Application;

/**
 * Application entry point that launches the JavaFX application.
 * <p>
 * This class serves as the main entry point for the application and delegates
 * to the JavaFX Application class for proper initialization.
 * </p>
 *
 * @author Dao Minh Tam
 * @version 1.0
 */
public class Launcher {
    /**
     * Main method that starts the JavaFX application.
     *
     * @param args command line arguments passed to the application
     */
    public static void main(String[] args) {
        Application.launch(MainApplication.class, args);
    }
}
