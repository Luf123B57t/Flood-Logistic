package com.aidsight.presentation;

import com.aidsight.application.controller.MainController;
import com.aidsight.application.service.SubjectService;
import com.aidsight.infrastructure.config.DatabaseConfig;
import com.aidsight.presentation.config.GUIConfig;
import com.aidsight.infrastructure.persistence.connection.SQLiteConnectionManager;
import com.aidsight.presentation.controller.HomeViewController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;

/**
 * Main JavaFX application class that initializes and starts the GUI.
 * <p>
 * This class extends JavaFX Application and is responsible for setting up
 * the primary stage, creating the main controller, and loading the initial view.
 * </p>
 */
public class MainApplication extends Application {

    private static final Logger logger = LoggerFactory.getLogger(MainApplication.class);
    private MainController mainController;

    /**
     * Starts the JavaFX application and sets up the primary stage.
     * <p>
     * This method creates the main controller, loads the home view,
     * and displays the primary window.
     * </p>
     *
     * @param stage the primary stage for this application
     * @throws IOException if the FXML file cannot be loaded
     */
    @Override
    public void start(Stage stage) throws IOException {
        // Initialize database and register DAOs
        try {
            SubjectService.initialize();
        } catch (SQLException e) {
            logger.error("Failed to initialize database: {}", e.getMessage(), e);
            // Continue anyway - errors will be shown when trying to load subjects
        }

        // Create the main controller at application startup
        mainController = new MainController();

        // Load the home view first
        FXMLLoader fxmlLoader = new FXMLLoader();
        fxmlLoader.setLocation(MainApplication.class.getResource("/com/aidsight/fxml/home-view.fxml"));
        Scene scene = GUIConfig.createScene(fxmlLoader);

        // Pass the main controller to the home view controller
        HomeViewController controller = fxmlLoader.getController();
        controller.setMainController(mainController);

        stage.setTitle(GUIConfig.TITLE_HOME);
        stage.setScene(scene);

        // Set up window close handler to ensure clean shutdown
        stage.setOnCloseRequest(_ -> {
            logger.info("Application shutdown requested");
            // Additional cleanup can be added here if needed
        });

        stage.show();
    }

    /**
     * Called when the application is stopping.
     * Performs cleanup operations.
     */
    @Override
    public void stop() {
        logger.info("Application stopping - performing cleanup");

        // Terminate all active scraping threads first
        if (mainController != null) {
            mainController.terminateAllScrapingThreads();
        }

        // Close database connections
        try {
            SQLiteConnectionManager connectionManager = SQLiteConnectionManager.getInstance(DatabaseConfig.getDatabasePath());
            connectionManager.close();
            logger.info("Database connections closed");
        } catch (Exception e) {
            logger.error("Error closing database connections", e);
        }

        logger.info("Application stopped");

        // Force exit to ensure all threads (including Selenium threads) are terminated
        // Use a separate thread to avoid blocking the JavaFX shutdown
        new Thread(() -> {
            try {
                Thread.sleep(100); // Give JavaFX time to finish cleanup
            } catch (InterruptedException e) {
                // Ignore
            }
            logger.info("Forcing application exit to terminate any remaining threads");
            System.exit(0);
        }).start();
    }
}
