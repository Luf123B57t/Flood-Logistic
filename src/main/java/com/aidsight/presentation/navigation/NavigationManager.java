package com.aidsight.presentation.navigation;

import com.aidsight.application.controller.MainController;
import com.aidsight.presentation.config.GUIConfig;
import com.aidsight.domain.model.core.Subject;
import com.aidsight.presentation.MainApplication;
import com.aidsight.presentation.controller.AddSubjectViewController;
import com.aidsight.presentation.controller.AnalysisViewController;
import com.aidsight.presentation.controller.HomeViewController;
import com.aidsight.presentation.controller.SelectionViewController;
import com.aidsight.presentation.controller.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Manages navigation between different views in the application.
 * <p>
 * This class centralizes view transition logic, separating it from business logic
 * in controllers. It handles loading FXML files, creating scenes, passing controller
 * references, and updating stage properties.
 * </p>
 */
public class NavigationManager {
    private static final Logger logger = LoggerFactory.getLogger(NavigationManager.class);

    private final MainController mainController;

    /**
     * Creates a new NavigationManager.
     *
     * @param mainController the main controller to pass to view controllers
     */
    public NavigationManager(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * Navigates to the home view.
     * <p>
     * This method loads the home FXML view, creates a scene, injects the main
     * controller, and transitions the stage to display the home screen.
     * </p>
     *
     * @param stage the stage to transition to the home view
     */
    public void navigateToHome(Stage stage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("/com/aidsight/fxml/home-view.fxml"));
            Scene scene = GUIConfig.createScene(fxmlLoader);

            HomeViewController controller = fxmlLoader.getController();
            controller.setMainController(mainController);

            stage.setTitle(GUIConfig.TITLE_HOME);
            stage.setScene(scene);

        } catch (IOException e) {
            logger.error("Error navigating to home view: {}", e.getMessage(), e);
        }
    }

    /**
     * Navigates to the selection view.
     * <p>
     * This method loads the selection FXML view, creates a scene, injects the main
     * controller, and transitions the stage to display the subject selection screen.
     * </p>
     *
     * @param stage the stage to transition to the selection view
     */
    public void navigateToSelection(Stage stage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("/com/aidsight/fxml/selection-view.fxml"));
            Scene scene = GUIConfig.createScene(fxmlLoader);

            SelectionViewController controller = fxmlLoader.getController();
            controller.setMainController(mainController);

            stage.setTitle(GUIConfig.TITLE_SELECTION);
            stage.setScene(scene);

        } catch (IOException e) {
            logger.error("Error navigating to selection view: {}", e.getMessage(), e);
        }
    }

    /**
     * Navigates to the add subject view.
     * <p>
     * This method loads the add subject FXML view, creates a scene, injects the main
     * controller, and transitions the stage to display the subject creation screen.
     * </p>
     *
     * @param stage the stage to transition to the add subject view
     */
    public void navigateToAddSubject(Stage stage) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("/com/aidsight/fxml/add-subject-view.fxml"));
            Scene scene = GUIConfig.createScene(fxmlLoader);

            AddSubjectViewController controller = fxmlLoader.getController();
            controller.setMainController(mainController);

            stage.setTitle("Add New Subject");
            stage.setScene(scene);

        } catch (IOException e) {
            logger.error("Error navigating to add subject view: {}", e.getMessage(), e);
        }
    }

    /**
     * Navigates to the analysis view for a specific subject.
     * <p>
     * This method loads the analysis FXML view, creates a scene, injects the main
     * controller and subject, and transitions the stage to display the analysis
     * dashboard. The stage title is set to include the subject name.
     * </p>
     *
     * @param stage the stage to transition to the analysis view
     * @param subject the subject to be analyzed
     */
    public void navigateToAnalysis(Stage stage, Subject subject) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(MainApplication.class.getResource("/com/aidsight/fxml/analysis-view.fxml"));
            Scene scene = GUIConfig.createScene(fxmlLoader);

            AnalysisViewController controller = fxmlLoader.getController();
            controller.setMainController(mainController);
            controller.setSubject(subject);

            stage.setTitle(GUIConfig.TITLE_ANALYSIS_PREFIX + subject.getName());
            stage.setScene(scene);

        } catch (IOException e) {
            logger.error("Error navigating to analysis view: {}", e.getMessage(), e);
        }
    }
}

