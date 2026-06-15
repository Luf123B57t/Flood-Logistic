package com.aidsight.presentation.config;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.io.IOException;
import java.util.Objects;

/**
 * Centralized GUI configuration for the application.
 * <p>
 * This class contains constants for window dimensions, application titles,
 * and provides utility methods for creating properly configured JavaFX scenes
 * with consistent styling.
 * </p>
 */
public final class GUIConfig {
    /**
     * Default window width in pixels.
     */
    public static final double WIDTH = 1600.0;

    /**
     * Default window height in pixels.
     */
    public static final double HEIGHT = 900.0;

    /**
     * Application name.
     */
    public static final String APP_NAME = "OOP App";

    /**
     * Title for the home view.
     */
    public static final String TITLE_HOME = APP_NAME;

    /**
     * Title for the selection view.
     */
    public static final String TITLE_SELECTION = "Select Analysis Subject";

    /**
     * Title prefix for the analysis view.
     * The subject name will be appended to this prefix.
     */
    public static final String TITLE_ANALYSIS_PREFIX = "Analysis Dashboard - ";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private GUIConfig() {}

    /**
     * Creates a Scene from a pre-configured FXMLLoader and applies the application stylesheet.
     * <p>
     * This method loads the FXML content, creates a scene with standard dimensions,
     * and automatically applies the application's CSS stylesheet.
     * </p>
     *
     * @param loader the FXMLLoader configured with the desired FXML file
     * @return a Scene with the loaded content and applied styling
     * @throws IOException if the FXML file cannot be loaded or the stylesheet cannot be found
     */
    public static Scene createScene(FXMLLoader loader) throws IOException {
        Parent root = loader.load();
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        // Attach stylesheet located next to this class's package resources
        scene.getStylesheets().add(
                Objects.requireNonNull(GUIConfig.class.getResource("/com/aidsight/styles/styles.css")).toExternalForm()
        );
        return scene;
    }
}

