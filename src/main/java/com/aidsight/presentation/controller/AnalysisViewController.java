package com.aidsight.presentation.controller;

import com.aidsight.application.controller.MainController;
import com.aidsight.domain.model.analysis.ChartData;
import com.aidsight.domain.model.core.Instance;
import com.aidsight.domain.model.core.Subject;
import com.aidsight.domain.model.core.Task;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller for the analysis view that displays tasks, instances, and analysis results.
 * <p>
 * This controller manages the analysis dashboard where users can:
 * </p>
 * <ul>
 * <li>View subject details and associated tasks</li>
 * <li>Browse loaded instances</li>
 * <li>Initiate analysis operations</li>
 * <li>View analysis results in charts and reports</li>
 * </ul>
 * <p>
 * The view dynamically generates task result views based on the task types
 * and displays results using charts and textual reports.
 * </p>
 */
public class AnalysisViewController {
    private static final Logger logger = LoggerFactory.getLogger(AnalysisViewController.class);

    public VBox leftPanel;
    public VBox rightPanel;
    @FXML
    private Label subjectNameLabel;

    @FXML
    private Button backButton;

    @FXML
    private Button analyzeButton;
    @FXML
    private VBox taskButtonsContainer;
    @FXML
    private VBox instanceListContainer;
    @FXML
    private StackPane contentArea;
    @FXML
    private VBox defaultView;
    private Subject subject;
    private MainController mainController;
    private final Map<Class<? extends Task>, TaskResultView> taskResultViews = new HashMap<>();
    private final Map<Class<? extends Task>, Button> taskButtons = new HashMap<>();
    private ProgressIndicator progressIndicator;

    /**
     * Initializes the controller after FXML injection.
     * <p>
     * This method is automatically called by JavaFX after the FXML file is loaded.
     * It creates and configures the progress indicator for analysis operations.
     * </p>
     */
    @FXML
    public void initialize() {
        // Create progress indicator (initially hidden)
        progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
    }

    /**
     * Sets the main controller reference.
     * <p>
     * This method is called to inject the main controller dependency after
     * the view is loaded. The main controller is used for executing analysis
     * operations and navigation.
     * </p>
     *
     * @param mainController the application's main controller
     */
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    /**
     * Sets the subject to be analyzed and initializes the view components.
     * <p>
     * This method loads the subject's instances if not already loaded,
     * initializes task buttons and result views, and populates the instance list.
     * </p>
     *
     * @param subject the subject containing tasks and instances for analysis
     */
    public void setSubject(Subject subject) {
        this.subject = subject;
        // Load instances if not already loaded
        if (subject.getInstances().isEmpty() && subject.getId() != null) {
            subject.loadInstances();
            logger.debug("Loaded {} instances for analysis", subject.getInstances().size());
        }
        subjectNameLabel.setText(subject.getName());
        // Initialize task buttons
        initializeTaskButtons();
        // Initialize instance list
        initializeInstanceList();
    }
    /**
     * Initializes task buttons in the left panel.
     * <p>
     * This method creates a button for each task in the subject and associates
     * each button with a result view. Buttons are initially disabled until
     * analysis is performed.
     * </p>
     */
    private void initializeTaskButtons() {
        taskButtonsContainer.getChildren().clear();
        taskButtons.clear();
        taskResultViews.clear();
        for (Task task : subject.getTasks()) {
            Class<? extends Task> taskClass = task.getClass();
            // Create button
            Button button = new Button(getTaskDisplayName(task));
            button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("task-button");
            button.setOnAction(_ -> showTaskResult(taskClass));
            button.setDisable(true); // Disabled until analysis is run
            taskButtons.put(taskClass, button);
            taskButtonsContainer.getChildren().add(button);
            // Create result view
            TaskResultView resultView = new TaskResultView(task);
            taskResultViews.put(taskClass, resultView);
        }
    }
    /**
     * Initializes the instance list in the right panel.
     * <p>
     * This method groups instances by their name and displays them with
     * their count. It provides a summary view of the data available for analysis.
     * </p>
     */
    private void initializeInstanceList() {
        instanceListContainer.getChildren().clear();

        // Group instances by their name and count them
        Map<String, Long> instanceCounts = new HashMap<>();
        for (Instance instance : subject.getInstances()) {
            String name = instance.getName();
            instanceCounts.put(name, instanceCounts.getOrDefault(name, 0L) + 1);
        }

        // Display each instance name with its count
        for (Map.Entry<String, Long> entry : instanceCounts.entrySet()) {
            String name = entry.getKey();
            long count = entry.getValue();

            String displayText = name + " (" + count + ")";

            Label instanceLabel = new Label(displayText);
            instanceLabel.setWrapText(true);
            instanceLabel.setMaxWidth(Double.MAX_VALUE);
            instanceLabel.getStyleClass().add("instance-label");
            instanceLabel.setPadding(new Insets(8, 10, 8, 10));
            instanceListContainer.getChildren().add(instanceLabel);
        }
    }
    /**
     * Retrieves the display name for a task.
     * <p>
     * This method extracts the user-friendly name from a task for display
     * in the UI.
     * </p>
     *
     * @param task the task to get the display name for
     * @return the task's display name
     */
    private String getTaskDisplayName(Task task) {
        return task.getName();
    }

    /**
     * Handles the Back button click event to return to the selection view.
     * <p>
     * This method navigates back to the subject selection screen.
     * </p>
     */
    @FXML
    protected void onBackButtonClick() {
        Stage stage = (Stage) backButton.getScene().getWindow();
        mainController.getNavigationManager().navigateToSelection(stage);
    }

    /**
     * Handles the Analyze button click event to execute analysis operations.
     * <p>
     * This method initiates asynchronous analysis of the subject's tasks against
     * its instances. During analysis, the UI is updated to show progress and
     * buttons are disabled. Upon completion, results are displayed and task
     * buttons are enabled.
     * </p>
     */
    @FXML
    protected void onAnalyzeButtonClick() {
        // Disable analyze button
        analyzeButton.setDisable(true);
        analyzeButton.setText("Analyzing...");
        // Disable task buttons
        taskButtons.values().forEach(btn -> btn.setDisable(true));
        // Show progress
        showProgress();
        // Run analysis asynchronously
        mainController.analyze(subject).thenAccept(result -> {
            // Update UI on JavaFX Application Thread
            Platform.runLater(() -> {
                // Update all task result views
                for (var entry : taskResultViews.entrySet()) {
                    Class<? extends Task> taskClass = entry.getKey();
                    TaskResultView view = entry.getValue();
                    Task.Result<?> taskResult = result.get(taskClass);
                    if (taskResult != null) {
                        view.updateWithResult(taskResult);
                    }
                }
                // Re-enable buttons
                analyzeButton.setDisable(false);
                analyzeButton.setText("Analyze");
                taskButtons.values().forEach(btn -> btn.setDisable(false));
                hideProgress();
                // Auto-select first task
                if (!taskButtons.isEmpty()) {
                    Class<? extends Task> firstTask = subject.getTasks().getFirst().getClass();
                    showTaskResult(firstTask);
                }
            });
        }).exceptionally(throwable -> {
            // Handle errors
            Platform.runLater(() -> {
                analyzeButton.setDisable(false);
                analyzeButton.setText("Analyze (Error)");
                hideProgress();
                logger.error("Analysis failed: {}", throwable.getMessage(), throwable);
            });
            return null;
        });
    }
    /**
     * Displays the result view for a specific task.
     * <p>
     * This method updates the content area to show the results for the selected
     * task and highlights the corresponding task button in the sidebar.
     * </p>
     *
     * @param taskClass the class of the task whose results should be displayed
     */
    private void showTaskResult(Class<? extends Task> taskClass) {
        TaskResultView view = taskResultViews.get(taskClass);
        if (view != null) {
            contentArea.getChildren().clear();
            contentArea.getChildren().add(view);
            // Update button styles
            taskButtons.forEach((tc, btn) -> {
                // Always remove the class first to prevent duplicates
                btn.getStyleClass().remove("task-button-selected");
                if (tc.equals(taskClass)) {
                    btn.getStyleClass().add("task-button-selected");
                }
            });
        }
    }

    /**
     * Displays the progress indicator in the content area.
     * <p>
     * This method hides the default view and shows a progress spinner while
     * analysis is being performed.
     * </p>
     */
    private void showProgress() {
        defaultView.setVisible(false);
        progressIndicator.setVisible(true);
        if (!contentArea.getChildren().contains(progressIndicator)) {
            contentArea.getChildren().add(progressIndicator);
        }
    }

    /**
     * Hides the progress indicator after analysis completion.
     * <p>
     * This method removes the progress spinner from view, allowing results
     * to be displayed.
     * </p>
     */
    private void hideProgress() {
        progressIndicator.setVisible(false);
    }

    /**
     * A custom component that displays task analysis results dynamically.
     * <p>
     * This view provides a scrollable container for displaying analysis results
     * including multiple charts and textual reports. It supports dynamic chart
     * generation based on the ChartData provided by task results.
     * </p>
     * <p>
     * The view automatically handles different chart types (line charts and bar charts)
     * and axis types (numeric, date, and datetime).
     * </p>
     */
    public static class TaskResultView extends VBox {
        private final VBox chartsContainer;
        private final TextArea detailsArea;
    
        /**
         * Constructs a new TaskResultView for displaying results of the specified task.
         * <p>
         * This constructor creates the UI structure including a title label,
         * scrollable charts container, and a details text area.
         * </p>
         *
         * @param task the task whose results will be displayed in this view
         */
        public TaskResultView(Task task) {
            this.setSpacing(10);
    
            // Title (stays at top, not scrolled)
            Label titleLabel = new Label(task.getDescription());
            titleLabel.getStyleClass().add("task-result-title");
    
            // Container for multiple charts
            chartsContainer = new VBox(10);
    
            // Create details area
            detailsArea = new TextArea();
            detailsArea.setEditable(false);
            detailsArea.setWrapText(true);
            detailsArea.setPrefRowCount(8);
            detailsArea.setMaxHeight(200);
            detailsArea.setPromptText("No infrastructure available. Click 'Analyze' to generate results.");
    
            // Create scrollable content container
            VBox scrollableContent = new VBox(15);
            scrollableContent.getChildren().addAll(chartsContainer, detailsArea);
    
            // Wrap content in ScrollPane
            ScrollPane scrollPane = new ScrollPane(scrollableContent);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(false);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setPannable(true);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);
    
            // Add components (title stays fixed, content scrolls)
            this.getChildren().addAll(titleLabel, scrollPane);
        }
    
        /**
         * Updates the view with task analysis results.
         * <p>
         * This method populates the view with the provided results, including:
         * </p>
         * <ul>
         * <li>Textual report in the details area</li>
         * <li>Charts generated from ChartData objects</li>
         * </ul>
         * <p>
         * Existing charts are cleared before new ones are added.
         * </p>
         *
         * @param result the task result containing data and charts to display
         */
        public void updateWithResult(Task.Result<?> result) {
            // Clear existing charts
            chartsContainer.getChildren().clear();
    
            // Update details text using getReport()
            detailsArea.setText(result.getReport());
    
            // Update charts using getChartData() - now returns List<ChartData>
            List<ChartData> allChartData = result.getChartData();
    
            for (ChartData chartData : allChartData) {
                // Create a new chart for each ChartData (can be LineChart or BarChart)
                XYChart<?, Number> chart = createChart(chartData);
    
                chartsContainer.getChildren().add(chart);
            }
        }
    
        /**
         * Creates a chart from ChartData specifications.
         * <p>
         * This method determines the appropriate chart type (line or bar) and
         * axis types (numeric or categorical) based on the data provided in
         * the ChartData object. It automatically detects date/datetime data
         * and creates categorical axes for them.
         * </p>
         *
         * @param chartData the chart configuration and data
         * @return a configured XYChart ready for display
         */
        private XYChart<?, Number> createChart(ChartData chartData) {
            List<?> xAxisData = chartData.getXAxisData();
    
            // Detect x-axis infrastructure type
            boolean isLocalDate = false;
            boolean isLocalDateTime = false;
    
            if (xAxisData != null && !xAxisData.isEmpty()) {
                Object firstElement = xAxisData.getFirst();
                if (firstElement instanceof LocalDate) {
                    isLocalDate = true;
                } else if (firstElement instanceof LocalDateTime) {
                    isLocalDateTime = true;
                }
            }
    
            // Create chart based on detected type and chart type
            if (isLocalDate || isLocalDateTime) {
                return createDateTimeChart(chartData);
            } else {
                return createNumberChart(chartData);
            }
        }
    
        /**
         * Configures common properties for all chart types.
         * <p>
         * This method applies standard settings including title, legend visibility,
         * animation, and size constraints to ensure consistent chart appearance.
         * </p>
         *
         * @param <X> the type of the X-axis values
         * @param <Y> the type of the Y-axis values
         * @param chart the chart to configure
         * @param chartData the chart data containing configuration values
         */
        private <X, Y> void configureChart(XYChart<X, Y> chart, ChartData chartData) {
            chart.setTitle(chartData.getTitle());
            chart.setLegendVisible(true);
            chart.setAnimated(true);
            chart.setMinHeight(400);
            chart.setPrefHeight(450);
        }

        /**
         * Creates a chart with categorical (String) x-axis for date or datetime data.
         * <p>
         * This method formats date and datetime values into readable strings and
         * creates a chart with a CategoryAxis for the x-axis. The chart type
         * (line or bar) is determined by the ChartData configuration.
         * </p>
         *
         * @param chartData the chart configuration and data
         * @return a chart with String x-axis and Number y-axis
         */
        private XYChart<String, Number> createDateTimeChart(ChartData chartData) {
            CategoryAxis xAxis = new CategoryAxis();
            xAxis.setLabel(chartData.getXAxisLabel());

            XYChart<String, Number> chart = getStringNumberXYChart(chartData, xAxis);

            configureChart(chart, chartData);

            // Get X-axis infrastructure
            List<?> xValues = chartData.getXAxisData();
    
            // Add all series to this chart
            for (var entry : chartData.getSeriesData().entrySet()) {
                addDateTimeSeriesToChart(chart, entry.getKey(), xValues, entry.getValue());
            }
    
            return chart;
        }

        /**
         * Creates a chart instance with String x-axis and Number y-axis.
         * <p>
         * This helper method constructs either a LineChart or BarChart based on
         * the chart type specified in the ChartData configuration.
         * </p>
         *
         * @param chartData the chart configuration
         * @param xAxis the configured category axis for the x-axis
         * @return a chart with String x-axis and Number y-axis
         */
        private static XYChart<String, Number> getStringNumberXYChart(ChartData chartData, CategoryAxis xAxis) {
            NumberAxis yAxis = new NumberAxis();
            yAxis.setLabel(chartData.getYAxisLabel());
            yAxis.setAutoRanging(true);
            yAxis.setForceZeroInRange(false);

            // Create either LineChart or BarChart based on chartType
            XYChart<String, Number> chart;
            if (chartData.getChartType() == ChartData.ChartType.BAR) {
                chart = new BarChart<>(xAxis, yAxis);
            } else {
                chart = new LineChart<>(xAxis, yAxis);
            }
            return chart;
        }

        /**
         * Creates a chart with numeric (Number) x-axis for numerical data.
         * <p>
         * This method creates a chart with a NumberAxis for the x-axis. The chart type
         * (line or bar) is determined by the ChartData configuration. All series from
         * the ChartData are added to the chart.
         * </p>
         *
         * @param chartData the chart configuration and data
         * @return a chart with Number x-axis and Number y-axis
         */
        private XYChart<Number, Number> createNumberChart(ChartData chartData) {
            NumberAxis xAxis = new NumberAxis();
            xAxis.setLabel(chartData.getXAxisLabel());
            xAxis.setAutoRanging(true);
            xAxis.setForceZeroInRange(false);

            XYChart<Number, Number> chart = getNumberNumberXYChart(chartData, xAxis);

            configureChart(chart, chartData);

            // Get X-axis infrastructure if available
            List<?> xValues = chartData.getXAxisData();
    
            // Add all series to this chart
            for (var entry : chartData.getSeriesData().entrySet()) {
                addNumberSeriesToChart(chart, entry.getKey(), xValues, entry.getValue());
            }
    
            return chart;
        }

        /**
         * Creates a chart instance with Number x-axis and Number y-axis.
         * <p>
         * This helper method constructs either a LineChart or BarChart based on
         * the chart type specified in the ChartData configuration.
         * </p>
         *
         * @param chartData the chart configuration
         * @param xAxis the configured number axis for the x-axis
         * @return a chart with Number x-axis and Number y-axis
         */
        private static XYChart<Number, Number> getNumberNumberXYChart(ChartData chartData, NumberAxis xAxis) {
            NumberAxis yAxis = new NumberAxis();
            yAxis.setLabel(chartData.getYAxisLabel());
            yAxis.setAutoRanging(true);
            yAxis.setForceZeroInRange(false);

            // Create either LineChart or BarChart based on chartType
            XYChart<Number, Number> chart;
            if (chartData.getChartType() == ChartData.ChartType.BAR) {
                chart = new BarChart<>(xAxis, yAxis);
            } else {
                chart = new LineChart<>(xAxis, yAxis);
            }
            return chart;
        }

        /**
         * Adds a data series to a datetime chart with String x-axis.
         * <p>
         * This method creates a chart series from the provided data, formatting
         * date and datetime values into readable strings. If x-values are not
         * provided or don't match the y-values count, indices are used instead.
         * </p>
         *
         * @param chart the chart to add the series to
         * @param seriesName the name of the series for the legend
         * @param xValues the x-axis values (LocalDate or LocalDateTime objects)
         * @param yValues the y-axis numeric values
         */
        private void addDateTimeSeriesToChart(XYChart<String, Number> chart, String seriesName,
                                              List<?> xValues, List<? extends Number> yValues) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(seriesName);
    
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
            if (xValues != null && xValues.size() == yValues.size()) {
                for (int i = 0; i < yValues.size(); i++) {
                    String xLabel;
                    Object xValue = xValues.get(i);
    
                    if (xValue instanceof LocalDate) {
                        xLabel = ((LocalDate) xValue).format(dateFormatter);
                    } else if (xValue instanceof LocalDateTime) {
                        xLabel = ((LocalDateTime) xValue).format(dateTimeFormatter);
                    } else {
                        xLabel = xValue.toString();
                    }
    
                    series.getData().add(new XYChart.Data<>(xLabel, yValues.get(i)));
                }
            } else {
                // Use indices as X values
                for (int i = 0; i < yValues.size(); i++) {
                    series.getData().add(new XYChart.Data<>(String.valueOf(i), yValues.get(i)));
                }
            }
    
            chart.getData().add(series);
        }
    
        /**
         * Adds an infrastructure series to a numeric chart (with Number x-axis).
         * <p>
         * Pairs x and y values to create infrastructure points. If x-values are not provided
         * or not numeric, uses indices as x-values.
         * </p>
         *
         * @param chart the chart to add the series to
         * @param seriesName the name of the series (shown in legend)
         * @param xValues the x-axis numeric values (or null for auto-indexing)
         * @param yValues the y-axis numeric values
         */
        private void addNumberSeriesToChart(XYChart<Number, Number> chart, String seriesName,
                                            List<?> xValues, List<? extends Number> yValues) {
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(seriesName);
    
            if (xValues != null && xValues.size() == yValues.size()) {
                for (int i = 0; i < yValues.size(); i++) {
                    Number xValue = (xValues.get(i) instanceof Number) ? (Number) xValues.get(i) : i;
                    series.getData().add(new XYChart.Data<>(xValue, yValues.get(i)));
                }
            } else {
                // Use indices as X values
                for (int i = 0; i < yValues.size(); i++) {
                    series.getData().add(new XYChart.Data<>(i, yValues.get(i)));
                }
            }
    
            chart.getData().add(series);
        }
    }
}
