package com.aidsight.presentation.controller;

import com.aidsight.application.controller.MainController;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.Stage;

/**
 * Controller for the home screen.
 * <p>
 * This controller manages the home view which serves as the entry point
 * of the application, allowing users to start the analysis workflow.
 * </p>
 */
public class HomeViewController {
    @FXML
    private Button startButton;

    private MainController mainController;

    /**
     * Sets the main controller reference.
     * <p>
     * This method is called after the view is loaded to inject the main controller dependency.
     * </p>
     *
     * @param mainController the application's main controller
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * Handles Start button click - transitions to selection view.
     * <p>
     * When the user clicks the start button, this method navigates to the
     * subject selection screen.
     * </p>
     */
    @FXML
    protected void onStartButtonClick() {
        Stage stage = (Stage) startButton.getScene().getWindow();
        mainController.getNavigationManager().navigateToSelection(stage);
    }
}
