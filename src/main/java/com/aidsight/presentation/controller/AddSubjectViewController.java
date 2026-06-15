package com.aidsight.presentation.controller;

import com.aidsight.application.controller.MainController;
import com.aidsight.infrastructure.config.ScraperConfig;
import com.aidsight.infrastructure.scraping.manager.ScraperManager;
import com.aidsight.domain.enums.InstanceType;
import com.aidsight.domain.model.core.Subject;
import com.aidsight.domain.model.core.Task;
import com.aidsight.domain.enums.TaskType;
import com.aidsight.presentation.util.DialogUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller for the Add Subject view.
 * <p>
 * This controller manages the subject creation workflow, including:
 * </p>
 * <ul>
 * <li>Configuration of scraping parameters and data sources</li>
 * <li>Selection of analysis tasks to perform</li>
 * <li>Execution of web scraping operations</li>
 * <li>Saving subjects and scraped data to the database</li>
 * </ul>
 * <p>
 * The controller handles both unified and per-source scraping configurations,
 * manages scraping thread lifecycle, and provides progress feedback to users.
 * </p>
 */
public class AddSubjectViewController {
    private static final Logger logger = LoggerFactory.getLogger(AddSubjectViewController.class);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @FXML
    private TextField subjectNameField;

    @FXML
    private TextArea subjectDescriptionArea;

    @FXML
    private VBox tasksCheckBoxContainer;

    @FXML
    private VBox scrapersCheckBoxContainer;

    @FXML
    private CheckBox headlessModeCheckBox;

    @FXML
    private TextField keywordsField;

    @FXML
    private CheckBox useSameCountCheckBox;

    @FXML
    private HBox unifiedCountContainer;

    @FXML
    private Spinner<Integer> numPostsSpinner;

    @FXML
    private VBox individualCountsContainer;

    @FXML
    private ProgressBar progressBar;

    @FXML
    private TextArea progressLogArea;

    @FXML
    private Button scrapeButton;

    @FXML
    private Button saveButton;

    @FXML
    private Button cancelButton;

    private MainController mainController;
    private final ScraperManager scraperManager = new ScraperManager();
    private ScraperManager.ScrapeResult scrapeResult = null;
    private boolean scrapingInProgress = false;
    private Thread scrapingThread = null;

    // Map to hold dynamically created checkboxes
    private final Map<InstanceType, CheckBox> scraperCheckBoxes = new HashMap<>();
    private final Map<TaskType, CheckBox> taskCheckBoxes = new HashMap<>();

    // Map to hold individual scraper spinners
    private final Map<InstanceType, Spinner<Integer>> scraperSpinners = new HashMap<>();

    /**
     * Sets the main controller reference.
     * <p>
     * This method is called to inject the main controller dependency after
     * the view is loaded. The main controller is used for navigation and
     * thread management.
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
     * It performs initial setup including:
     * </p>
     * <ul>
     * <li>Configuring spinners and checkboxes</li>
     * <li>Creating dynamic UI elements for scrapers and tasks</li>
     * <li>Setting up event listeners</li>
     * <li>Initializing progress logging</li>
     * </ul>
     */
    @FXML
    public void initialize() {
        // Initialize spinner with default values
        SpinnerValueFactory<Integer> valueFactory =
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000, 10);
        numPostsSpinner.setValueFactory(valueFactory);

        // Initialize headless mode checkbox with current config value
        headlessModeCheckBox.setSelected(ScraperConfig.isHeadlessMode());

        // Dynamically create checkboxes for each scraper type
        createScraperCheckBoxes();

        // Dynamically create checkboxes for each task type
        createTaskCheckBoxes();

        // Setup post count mode toggle
        setupPostCountControls();

        // Clear progress log
        progressLogArea.clear();
        logProgress("System initialized. Ready to scrape infrastructure.");

        // Setup window close handler (needs to be done after scene is set)
        Platform.runLater(this::setupWindowCloseHandler);
    }

    /**
     * Dynamically creates checkboxes for each available scraper type.
     * <p>
     * This method generates UI checkboxes based on the available instance types
     * that support scraping. Each checkbox is pre-selected by default and includes
     * a listener to manage the enabled state of corresponding spinners.
     * </p>
     */
    private void createScraperCheckBoxes() {
        if (scrapersCheckBoxContainer == null) {
            logger.warn("scrapersCheckBoxContainer is null - checkboxes will not be created");
            return;
        }

        scrapersCheckBoxContainer.getChildren().clear();
        scraperCheckBoxes.clear();

        for (InstanceType instanceType : InstanceType.getAll()) {
            // Only show scrapable instance types
            if (instanceType.isScrapable()) {
                CheckBox checkBox = new CheckBox(instanceType.getDisplayName());
                checkBox.getStyleClass().add("checkbox");
                // Select by default
                checkBox.setSelected(true);

                // Add listener to update individual spinner state
                checkBox.selectedProperty().addListener((_, _, newVal) ->
                        updateIndividualSpinnerState(instanceType, newVal));

                scraperCheckBoxes.put(instanceType, checkBox);
                scrapersCheckBoxContainer.getChildren().add(checkBox);
            }
        }
    }

    /**
     * Sets up the post count controls and toggles between unified and individual modes.
     * <p>
     * This method configures the UI to allow users to either specify a single post
     * count for all scrapers (unified mode) or different counts for each scraper
     * (individual mode). It creates spinners and sets up listeners to manage
     * visibility of the appropriate controls.
     * </p>
     */
    private void setupPostCountControls() {
        // Create individual spinners for each scraper
        createIndividualSpinners();

        // Add listener to toggle between unified and individual modes
        useSameCountCheckBox.selectedProperty().addListener((_, _, newVal) -> {
            if (newVal) {
                // Show unified, hide individual
                unifiedCountContainer.setVisible(true);
                unifiedCountContainer.setManaged(true);
                individualCountsContainer.setVisible(false);
                individualCountsContainer.setManaged(false);
            } else {
                // Hide unified, show individual
                unifiedCountContainer.setVisible(false);
                unifiedCountContainer.setManaged(false);
                individualCountsContainer.setVisible(true);
                individualCountsContainer.setManaged(true);
            }
        });
    }

    /**
     * Creates individual spinners for each scraper type.
     * <p>
     * This method generates a spinner control for each scrapable instance type,
     * allowing users to specify different post counts for each data source.
     * Each spinner is initially configured based on its corresponding checkbox state.
     * </p>
     */
    private void createIndividualSpinners() {
        if (individualCountsContainer == null) {
            logger.warn("individualCountsContainer is null - spinners will not be created");
            return;
        }

        individualCountsContainer.getChildren().clear();
        scraperSpinners.clear();

        for (InstanceType instanceType : InstanceType.getAll()) {
            if (instanceType.isScrapable()) {
                HBox spinnerRow = new HBox(10);
                spinnerRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

                Label label = new Label(instanceType.getDisplayName() + ":");
                label.setMinWidth(150);
                label.getStyleClass().add("form-hint");

                Spinner<Integer> spinner = new Spinner<>();
                SpinnerValueFactory<Integer> valueFactory =
                    new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000, 10);
                spinner.setValueFactory(valueFactory);
                spinner.setEditable(true);
                spinner.setPrefWidth(150);
                spinner.getStyleClass().add("form-spinner");

                Label postsLabel = new Label("posts");
                postsLabel.getStyleClass().add("form-hint");

                spinnerRow.getChildren().addAll(label, spinner, postsLabel);

                scraperSpinners.put(instanceType, spinner);
                individualCountsContainer.getChildren().add(spinnerRow);

                // Set initial state based on checkbox
                CheckBox checkBox = scraperCheckBoxes.get(instanceType);
                if (checkBox != null) {
                    spinner.setDisable(!checkBox.isSelected());
                }
            }
        }
    }

    /**
     * Updates the enabled state of an individual spinner based on its checkbox selection.
     * <p>
     * This method is invoked when a scraper checkbox is toggled. It enables or
     * disables the corresponding spinner to reflect whether that data source
     * should be included in the scraping operation.
     * </p>
     *
     * @param instanceType the instance type whose spinner should be updated
     * @param enabled true to enable the spinner, false to disable it
     */
    private void updateIndividualSpinnerState(InstanceType instanceType, boolean enabled) {
        Spinner<Integer> spinner = scraperSpinners.get(instanceType);
        if (spinner != null) {
            spinner.setDisable(!enabled);
        }
    }

    /**
     * Dynamically creates checkboxes for each available task type.
     * <p>
     * This method generates UI checkboxes based on the available task types.
     * Each checkbox is pre-selected by default and includes a tooltip with
     * the task's description.
     * </p>
     */
    private void createTaskCheckBoxes() {
        if (tasksCheckBoxContainer == null) {
            logger.warn("tasksCheckBoxContainer is null - checkboxes will not be created");
            return;
        }

        tasksCheckBoxContainer.getChildren().clear();
        taskCheckBoxes.clear();

        for (TaskType taskType : TaskType.getAll()) {
            CheckBox checkBox = new CheckBox(taskType.getDisplayName());
            checkBox.getStyleClass().add("checkbox");
            // Select by default
            checkBox.setSelected(true);
            taskCheckBoxes.put(taskType, checkBox);
            tasksCheckBoxContainer.getChildren().add(checkBox);

            // Add tooltip with task description
            Tooltip tooltip = new Tooltip(taskType.getDescription());
            checkBox.setTooltip(tooltip);
        }
    }

    /**
     * Sets up the window close handler to manage scraping interruption.
     * <p>
     * This method configures the window close event to prompt the user for
     * confirmation if scraping is in progress. If confirmed, the scraping
     * thread is terminated before the window closes.
     * </p>
     */
    private void setupWindowCloseHandler() {
        if (cancelButton.getScene() != null && cancelButton.getScene().getWindow() != null) {
            Stage stage = (Stage) cancelButton.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                if (scrapingInProgress) {
                    // Show confirmation dialog
                    boolean confirmed = DialogUtil.showConfirmationOkCancel(
                        "Confirm Exit",
                        "Scraping in Progress",
                        "Scraping is currently in progress. Are you sure you want to exit?"
                    );

                    if (confirmed) {
                        // User confirmed - terminate the scraping thread
                        terminateScrapingThread();
                    } else {
                        // User cancelled - consume the event to prevent window from closing
                        event.consume();
                    }
                }
            });
        }
    }

    /**
     * Handles the Scrape button click event to initiate the scraping process.
     * <p>
     * This method performs the following operations:
     * </p>
     * <ol>
     * <li>Validates user input</li>
     * <li>Disables controls during scraping</li>
     * <li>Collects scraping configuration from UI controls</li>
     * <li>Starts asynchronous scraping with progress callbacks</li>
     * </ol>
     * <p>
     * The scraping operation runs in a background thread to avoid blocking the UI.
     * Progress updates are delivered via callbacks and displayed in the progress area.
     * </p>
     */
    @FXML
    protected void onScrapeClick() {
        // Validate input
        if (!validateInput()) {
            return;
        }


        // Disable controls during scraping
        setControlsEnabled(false);
        scrapingInProgress = true;

        // Clear previous infrastructure
        scrapeResult = null;
        progressBar.setProgress(0);
        progressLogArea.clear();
        logProgress("Starting scraping process...");

        // Get configuration
        String keywords = keywordsField.getText().trim();
        int numPosts = numPostsSpinner.getValue();
        boolean headlessMode = headlessModeCheckBox.isSelected();
        boolean useSameCount = useSameCountCheckBox.isSelected();

        // Collect enabled scrapers from checkboxes
        Set<InstanceType> enabledScrapers = new HashSet<>();
        for (Map.Entry<InstanceType, CheckBox> entry : scraperCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                enabledScrapers.add(entry.getKey());
            }
        }

        // Build post counts map based on mode
        Map<InstanceType, Integer> postCountsPerScraper = new HashMap<>();
        if (!useSameCount) {
            // Use individual counts
            for (Map.Entry<InstanceType, Spinner<Integer>> entry : scraperSpinners.entrySet()) {
                InstanceType instanceType = entry.getKey();
                if (enabledScrapers.contains(instanceType)) {
                    postCountsPerScraper.put(instanceType, entry.getValue().getValue());
                }
            }
        }
        // If useSameCount is true, postCountsPerScraper will be empty and config will use numPosts

        // Create scrape configuration
        ScraperManager.ScrapeConfig config = new ScraperManager.ScrapeConfig(
            keywords, numPosts, postCountsPerScraper, enabledScrapers, headlessMode
        );

        // Create progress callback
        ScraperManager.ScraperProgressCallback progressCallback = new ScraperManager.ScraperProgressCallback() {
            @Override
            public void onProgress(double progress, String message) {
                Platform.runLater(() -> {
                    progressBar.setProgress(progress);
                    logProgress(message);
                });
            }

            @Override
            public void onComplete(ScraperManager.ScrapeResult result) {
                scrapeResult = result;
                Platform.runLater(() -> {
                    logProgress("===== Scraping Complete =====");
                    logProgress("Total items scraped: " + scrapeResult.getTotalCount());
                    setControlsEnabled(true);
                    saveButton.setDisable(false);
                    scrapeButton.setDisable(true);
                    scrapingInProgress = false;
                });
            }

            @Override
            public void onError(String errorMessage) {
                Platform.runLater(() -> {
                    logProgress("ERROR: " + errorMessage);
                    DialogUtil.showError("Scraping Error", "An error occurred during scraping: " + errorMessage);
                    setControlsEnabled(true);
                    scrapingInProgress = false;
                });
            }
        };

        // Create thread registration callback
        ScraperManager.ThreadRegistrationCallback threadCallback = new ScraperManager.ThreadRegistrationCallback() {
            @Override
            public void onThreadCreated(Thread thread) {
                if (mainController != null) {
                    mainController.registerScrapingThread(thread);
                }
            }

            @Override
            public void onThreadComplete(Thread thread) {
                if (mainController != null) {
                    mainController.unregisterScrapingThread(thread);
                }
                scrapingThread = null;
            }
        };

        // Start scraping in background thread using ScraperManager
        scrapingThread = scraperManager.startScrapingAsync(config, progressCallback, threadCallback);
    }


    /**
     * Handles the Save button click event to persist the subject to the database.
     * <p>
     * This method validates that data has been scraped, collects the subject
     * details and selected tasks, and saves everything to the database. Upon
     * successful save, it displays a confirmation message and navigates back
     * to the selection view.
     * </p>
     */
    @FXML
    protected void onSaveClick() {
        if (scrapeResult == null || scrapeResult.isEmpty()) {
            DialogUtil.showError("No Data", "Please scrape infrastructure before saving the subject.");
            return;
        }

        String name = subjectNameField.getText().trim();
        String description = subjectDescriptionArea.getText().trim();

        // Collect selected tasks
        List<Task> selectedTasks = new ArrayList<>();
        for (Map.Entry<TaskType, CheckBox> entry : taskCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedTasks.add(entry.getKey().createTask());
            }
        }

        try {
            logProgress("Saving subject to database...");

            // Save subject and instances using ScraperManager
            Subject savedSubject = scraperManager.saveSubject(name, description, selectedTasks, scrapeResult.getScrapedData());

            logProgress("Subject saved with ID: " + savedSubject.getId());
            logProgress("Successfully saved subject with " + scrapeResult.getTotalCount() + " total instances");

            // Show success and return to selection view
            DialogUtil.showInfo("Success", "Subject '" + name + "' has been created successfully with " +
                    scrapeResult.getTotalCount() + " instances.");

            Stage stage = (Stage) saveButton.getScene().getWindow();
            mainController.getNavigationManager().navigateToSelection(stage);

        } catch (SQLException e) {
            logger.error("Error saving subject", e);
            logProgress("ERROR: Failed to save subject: " + e.getMessage());
            DialogUtil.showError("Database Error", "Failed to save subject: " + e.getMessage());
        }
    }

    /**
     * Handles the Cancel button click event to return to the selection view.
     * <p>
     * If scraping is in progress, this method prompts the user for confirmation
     * before terminating the scraping operation and navigating away.
     * </p>
     */
    @FXML
    protected void onCancelClick() {
        if (scrapingInProgress) {
            boolean confirmed = DialogUtil.showConfirmationOkCancel(
                "Confirm Cancel",
                "Scraping in Progress",
                "Scraping is currently in progress. Are you sure you want to cancel?"
            );

            if (!confirmed) {
                return;
            }

            // User confirmed cancellation - terminate the scraping thread
            terminateScrapingThread();
        }

        Stage stage = (Stage) cancelButton.getScene().getWindow();
        mainController.getNavigationManager().navigateToSelection(stage);
    }

    /**
     * Terminates the currently running scraping thread.
     * <p>
     * This method delegates thread termination to the MainController for
     * consistent handling. If the MainController is unavailable, it falls
     * back to local termination using thread interruption.
     * </p>
     */
    private void terminateScrapingThread() {
        Thread threadToTerminate = scrapingThread;
        if (threadToTerminate != null && threadToTerminate.isAlive()) {
            logger.info("Requesting scraping thread termination...");

            // Use the centralized termination method from MainController
            if (mainController != null) {
                mainController.terminateScrapingThread(threadToTerminate);
            } else {
                // Fallback if mainController is not available
                logger.warn("MainController not available, using local termination");
                threadToTerminate.interrupt();
                try {
                    threadToTerminate.join(2000);
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting for scraping thread to terminate");
                    Thread.currentThread().interrupt();
                }
            }

            scrapingThread = null;
            scrapingInProgress = false;
        }
    }


    /**
     * Validates all input fields before initiating scraping or saving.
     * <p>
     * This method checks that:
     * </p>
     * <ul>
     * <li>Subject name is not empty</li>
     * <li>Subject description is not empty</li>
     * <li>At least one task is selected</li>
     * <li>At least one scraper is selected</li>
     * <li>Search keywords are provided</li>
     * </ul>
     *
     * @return true if all validation checks pass, false otherwise
     */
    private boolean validateInput() {
        if (subjectNameField.getText().trim().isEmpty()) {
            DialogUtil.showError("Validation Error", "Please enter a subject name.");
            return false;
        }

        if (subjectDescriptionArea.getText().trim().isEmpty()) {
            DialogUtil.showError("Validation Error", "Please enter a subject description.");
            return false;
        }

        // Check if at least one task is selected
        boolean anyTaskSelected = taskCheckBoxes.values().stream()
            .anyMatch(CheckBox::isSelected);

        if (!anyTaskSelected) {
            DialogUtil.showError("Validation Error", "Please select at least one analysis task.");
            return false;
        }

        // Check if at least one scraper is selected
        boolean anyScraperSelected = scraperCheckBoxes.values().stream()
            .anyMatch(CheckBox::isSelected);

        if (!anyScraperSelected) {
            DialogUtil.showError("Validation Error", "Please select at least one platform to scrape from.");
            return false;
        }

        if (keywordsField.getText().trim().isEmpty()) {
            DialogUtil.showError("Validation Error", "Please enter search keywords.");
            return false;
        }

        return true;
    }

    /**
     * Enables or disables all input controls.
     * <p>
     * This method is used to prevent user interaction with controls during
     * scraping operations. It manages the enabled state of all form controls
     * including text fields, checkboxes, spinners, and buttons.
     * </p>
     *
     * @param enabled true to enable controls, false to disable them
     */
    private void setControlsEnabled(boolean enabled) {
        subjectNameField.setDisable(!enabled);
        subjectDescriptionArea.setDisable(!enabled);

        // Enable/disable all task checkboxes
        for (CheckBox checkBox : taskCheckBoxes.values()) {
            checkBox.setDisable(!enabled);
        }

        // Enable/disable all scraper checkboxes
        for (CheckBox checkBox : scraperCheckBoxes.values()) {
            checkBox.setDisable(!enabled);
        }

        headlessModeCheckBox.setDisable(!enabled);
        keywordsField.setDisable(!enabled);
        useSameCountCheckBox.setDisable(!enabled);
        numPostsSpinner.setDisable(!enabled);

        // Enable/disable individual spinners based on both enabled state and checkbox state
        for (Map.Entry<InstanceType, Spinner<Integer>> entry : scraperSpinners.entrySet()) {
            InstanceType instanceType = entry.getKey();
            Spinner<Integer> spinner = entry.getValue();
            CheckBox checkBox = scraperCheckBoxes.get(instanceType);

            // Spinner is disabled if controls are disabled OR if its checkbox is unchecked
            spinner.setDisable(!enabled || (checkBox != null && !checkBox.isSelected()));
        }

        scrapeButton.setDisable(!enabled);
        cancelButton.setDisable(!enabled && scrapingInProgress);
    }

    /**
     * Logs a progress message with timestamp to the progress text area.
     * <p>
     * This method formats messages with a timestamp prefix and appends them
     * to the progress log area for user visibility during scraping operations.
     * </p>
     *
     * @param message the progress message to log
     */
    private void logProgress(String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        String logMessage = "[" + timestamp + "] " + message + "\n";
        progressLogArea.appendText(logMessage);
    }
}

