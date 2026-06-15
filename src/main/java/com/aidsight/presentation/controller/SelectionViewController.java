package com.aidsight.presentation.controller;

import com.aidsight.application.controller.MainController;
import com.aidsight.domain.enums.InstanceType;
import com.aidsight.domain.model.core.Subject;
import com.aidsight.application.service.SubjectService;
import com.aidsight.infrastructure.config.DatabaseConfig;
import com.aidsight.infrastructure.persistence.connection.SQLiteConnectionManager;
import com.aidsight.infrastructure.persistence.dao.SubjectDAO;
import com.aidsight.presentation.util.DialogUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * Controller for the subject selection view.
 * <p>
 * This controller manages the subject selection screen where users can choose
 * an existing subject for analysis, add new subjects, or delete existing ones.
 * It handles loading subjects from the database and managing user interactions.
 * </p>
 */
public class SelectionViewController {
    private static final Logger logger = LoggerFactory.getLogger(SelectionViewController.class);

    @FXML
    private ComboBox<Subject> subjectComboBox;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private Label taskCountLabel;

    @FXML
    private Label instanceCountLabel;

    @FXML
    private Button startAnalysisButton;

    @FXML
    private Button addSubjectButton;

    @FXML
    private Button deleteSubjectButton;

    private Subject selectedSubject;
    private MainController mainController;

    /**
     * Sets the main controller reference.
     * <p>
     * This method is called to inject the main controller dependency after
     * the view is loaded.
     * </p>
     *
     * @param mainController the application's main controller
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * Initializes the controller after FXML injection.
     * <p>
     * This method is automatically called by JavaFX after the FXML file is loaded.
     * It ensures the database is initialized and loads all available subjects.
     * </p>
     */
    @FXML
    public void initialize() {
        try {
            // Ensure database is initialized
            SubjectService.initialize();
            loadSubjects();
        } catch (SQLException e) {
            logger.error("Error initializing: {}", e.getMessage(), e);
        }
    }

    /**
     * Loads all subjects from the database and populates the subject dropdown.
     * <p>
     * This method retrieves all subjects from the database and registers all
     * available instance types for each subject to ensure proper type handling.
     * </p>
     */
    private void loadSubjects() {
        try {
            SQLiteConnectionManager connectionManager = SQLiteConnectionManager.getInstance("database/database.db");
            SubjectDAO subjectDAO = new SubjectDAO(connectionManager);
            List<Subject> subjects = subjectDAO.getAll();

            // Register all possible instance types for each subject
            for (Subject subject : subjects) {
                for (InstanceType instanceType : InstanceType.values()) {
                    subject.addInstanceType(instanceType.getInstanceClass());
                }
            }

            subjectComboBox.setItems(FXCollections.observableArrayList(subjects));
        } catch (SQLException e) {
            logger.error("Error loading subjects: {}", e.getMessage(), e);
            subjectComboBox.setItems(FXCollections.observableArrayList(List.of()));
        }
    }

    /**
     * Handles subject selection from the dropdown menu.
     * <p>
     * This method is invoked when the user selects a subject from the dropdown.
     * It updates the display with the subject's description and instance/task counts,
     * and enables or disables action buttons based on the selection.
     * </p>
     */
    @FXML
    protected void onSubjectSelected() {
        selectedSubject = subjectComboBox.getValue();

        if (selectedSubject != null) {
            // Update description
            descriptionArea.setText(selectedSubject.getDescription());

            // Update counts - use countInstances() to avoid loading all infrastructure
            taskCountLabel.setText("Tasks: " + selectedSubject.getTasks().size());
            instanceCountLabel.setText("Instances: " + selectedSubject.countInstances());

            // Enable buttons
            startAnalysisButton.setDisable(false);
            deleteSubjectButton.setDisable(false);
        } else {
            descriptionArea.clear();
            taskCountLabel.setText("Tasks: -");
            instanceCountLabel.setText("Instances: -");
            startAnalysisButton.setDisable(true);
            deleteSubjectButton.setDisable(true);
        }
    }

    /**
     * Handles Start Analysis button click - transitions to analysis view.
     * <p>
     * Loads the analysis view, passes the selected subject and main controller,
     * and transitions to the analysis dashboard.
     * </p>
     */
    @FXML
    protected void onStartAnalysisClick() {
        if (selectedSubject == null) {
            return;
        }

        Stage stage = (Stage) startAnalysisButton.getScene().getWindow();
        mainController.getNavigationManager().navigateToAnalysis(stage, selectedSubject);
    }

    /**
     * Handles Add Subject button click - transitions to add subject view.
     * <p>
     * Loads the add subject view and transitions to it.
     * </p>
     */
    @FXML
    protected void onAddSubjectClick() {
        Stage stage = (Stage) addSubjectButton.getScene().getWindow();
        mainController.getNavigationManager().navigateToAddSubject(stage);
    }

    /**
     * Handles Delete Subject button click - deletes the selected subject after confirmation.
     * <p>
     * Shows a confirmation dialog before deletion. If confirmed, deletes the subject
     * from the database and refreshes the subject list.
     * </p>
     */
    @FXML
    protected void onDeleteSubjectClick() {
        if (selectedSubject == null) {
            return;
        }

        // Build confirmation message
        String message = "Are you sure you want to delete this subject?\n\n" +
            "This will permanently delete:\n" +
            "• Subject: " + selectedSubject.getName() + "\n" +
            "• " + selectedSubject.getTasks().size() + " task(s)\n" +
            "• " + selectedSubject.countInstances() + " instance(s)\n\n" +
            "This action cannot be undone.";

        // Show confirmation dialog
        boolean confirmed = DialogUtil.showConfirmation(
            "Confirm Delete",
            "Delete Subject: " + selectedSubject.getName(),
            message
        );

        if (confirmed) {
            deleteSelectedSubject();
        }
    }

    /**
     * Performs the deletion of the currently selected subject from the database.
     * <p>
     * This method attempts to delete the subject and displays appropriate success
     * or error messages. On successful deletion, it refreshes the subject list
     * and clears the selection.
     * </p>
     */
    private void deleteSelectedSubject() {
        try {
            SQLiteConnectionManager connectionManager = SQLiteConnectionManager.getInstance("database/database.db");
            SubjectDAO subjectDAO = new SubjectDAO(connectionManager);

            boolean deleted = subjectDAO.delete(selectedSubject.getId());

            if (deleted) {
                logger.info("Successfully deleted subject: {} (ID: {})",
                    selectedSubject.getName(), selectedSubject.getId());

                // Show success message
                DialogUtil.showInfo("Subject Deleted",
                    "Subject '" + selectedSubject.getName() + "' has been successfully deleted.");

                // Refresh the subject list
                refreshSubjectList();

                // Clear selection
                selectedSubject = null;
                subjectComboBox.setValue(null);
                descriptionArea.clear();
                taskCountLabel.setText("Tasks: -");
                instanceCountLabel.setText("Instances: -");
                startAnalysisButton.setDisable(true);
                deleteSubjectButton.setDisable(true);

            } else {
                logger.warn("Failed to delete subject: {} (ID: {})",
                    selectedSubject.getName(), selectedSubject.getId());
                DialogUtil.showError("Delete Failed", "Failed to delete the subject. It may have already been deleted.");
            }

        } catch (SQLException e) {
            logger.error("Error deleting subject: {}", e.getMessage(), e);
            DialogUtil.showError("Database Error", "An error occurred while deleting the subject: " + e.getMessage());
        }
    }

    /**
     * Refreshes the subject list from the database.
     * <p>
     * This method reloads all subjects from the database and updates the dropdown.
     * It is typically called after adding or deleting a subject.
     * </p>
     */
    private void refreshSubjectList() {
        try {
            SQLiteConnectionManager connectionManager = SQLiteConnectionManager.getInstance(DatabaseConfig.getDatabasePath());
            SubjectDAO subjectDAO = new SubjectDAO(connectionManager);
            List<Subject> subjects = subjectDAO.getAll();

            // Register all possible instance types for each subject
            for (Subject subject : subjects) {
                for (InstanceType instanceType : InstanceType.values()) {
                    subject.addInstanceType(instanceType.getInstanceClass());
                }
            }

            subjectComboBox.setItems(FXCollections.observableArrayList(subjects));

        } catch (SQLException e) {
            logger.error("Error refreshing subjects: {}", e.getMessage(), e);
            DialogUtil.showError("Database Error", "Failed to refresh subject list: " + e.getMessage());
        }
    }
}
