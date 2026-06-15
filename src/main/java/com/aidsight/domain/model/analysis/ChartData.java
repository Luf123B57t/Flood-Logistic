package com.aidsight.domain.model.analysis;

import java.util.*;

/**
 * Represents chart infrastructure with full configuration including title, axis labels, series infrastructure, and chart type.
 * Supports different x-axis infrastructure types: Number, LocalDate, or LocalDateTime.
 * The x-axis infrastructure type is automatically detected from the provided infrastructure.
 * Supports different chart types: LINE and BAR.
 *
 * <p>Example usage with LocalDateTime x-axis:
 * <pre>{@code
 * List<LocalDateTime> timestamps = new ArrayList<>();
 * List<Double> values = new ArrayList<>();
 *
 * LocalDateTime now = LocalDateTime.now();
 * for (int i = 0; i < 24; i++) {
 *     timestamps.add(now.minusHours(24 - i));
 *     values.add(Math.random() * 100);
 * }
 *
 * ChartData chart = new ChartData.Builder("Temperature Over Time")
 *     .xAxisLabel("Date & Time")
 *     .yAxisLabel("Temperature (°C)")
 *     .xAxisData(timestamps)
 *     .addSeries("Sensor 1", values)
 *     .build();
 * }</pre>
 *
 * <p>Example usage with LocalDate x-axis:
 * <pre>{@code
 * List<LocalDate> dates = new ArrayList<>();
 * List<Double> sales = new ArrayList<>();
 *
 * LocalDate today = LocalDate.now();
 * for (int i = 0; i < 7; i++) {
 *     dates.add(today.minusDays(6 - i));
 *     sales.add(Math.random() * 1000);
 * }
 *
 * ChartData chart = new ChartData.Builder("Daily Sales")
 *     .xAxisLabel("Date")
 *     .yAxisLabel("Sales ($)")
 *     .xAxisData(dates)
 *     .addSeries("Revenue", sales)
 *     .build();
 * }</pre>
 *
 * <p>Example usage with Number x-axis:
 * <pre>{@code
 * List<Double> xValues = Arrays.asList(1.0, 2.0, 3.0, 4.0);
 * List<Double> cpuData = Arrays.asList(45.2, 52.1, 48.9, 61.3);
 * List<Double> memoryData = Arrays.asList(2048.5, 2156.3, 2201.7, 2298.1);
 *
 * ChartData chart = new ChartData.Builder("System Resources")
 *     .xAxisLabel("Time Index")
 *     .yAxisLabel("Usage")
 *     .xAxisData(xValues)
 *     .addSeries("CPU Usage (%)", cpuData)
 *     .addSeries("Memory Usage (MB)", memoryData)
 *     .build();
 * }</pre>
 */
public class ChartData {

    /**
     * Enum representing the type of chart to display.
     */
    public enum ChartType {
        /** Line chart - infrastructure points connected by lines */
        LINE,
        /** Bar chart - infrastructure displayed as vertical or horizontal bars */
        BAR
    }

    private final String title;
    private final String xAxisLabel;
    private final String yAxisLabel;
    private final List<?> xAxisData;  // Can be List<Number>, List<LocalDate>, or List<LocalDateTime>
    private final Map<String, List<? extends Number>> seriesData;
    private final ChartType chartType;


    /**
     * Builder for creating ChartData instance.
     */
    public static class Builder {
        private final String title;
        private String xAxisLabel = "X Axis";
        private String yAxisLabel = "Y Axis";
        private List<?> xAxisData = null;  // Can be List<Number>, List<LocalDate>, or List<LocalDateTime>
        private final Map<String, List<? extends Number>> seriesData = new LinkedHashMap<>();
        private ChartType chartType = ChartType.LINE;  // Default to LINE chart

        /**
         * Creates a new builder with the specified chart title.
         *
         * @param title the title for the chart
         */
        public Builder(String title) {
            this.title = title;
        }

        /**
         * Sets the x-axis label.
         *
         * @param label the label text
         * @return this builder for method chaining
         */
        public Builder xAxisLabel(String label) {
            this.xAxisLabel = label;
            return this;
        }

        /**
         * Sets the y-axis label.
         *
         * @param label the label text
         * @return this builder for method chaining
         */
        public Builder yAxisLabel(String label) {
            this.yAxisLabel = label;
            return this;
        }

        /**
         * Sets x-axis infrastructure. Accepts List of Number, LocalDate, or LocalDateTime.
         *
         * @param data the x-axis infrastructure points
         * @return this builder for method chaining
         */
        public Builder xAxisData(List<?> data) {
            this.xAxisData = data;
            return this;
        }

        /**
         * Adds a single infrastructure series to the chart.
         *
         * @param seriesName the name of the series (shown in legend)
         * @param data the y-axis values for this series
         * @return this builder for method chaining
         */
        public Builder addSeries(String seriesName, List<? extends Number> data) {
            this.seriesData.put(seriesName, data);
            return this;
        }

        /**
         * Adds multiple infrastructure series to the chart at once.
         *
         * @param series a map of series names to their infrastructure
         * @return this builder for method chaining
         */
        public Builder addSeries(Map<String, ? extends List<? extends Number>> series) {
            this.seriesData.putAll(series);
            return this;
        }

        /**
         * Sets the chart type (LINE or BAR). Defaults to LINE if not specified.
         *
         * @param type the chart type
         * @return this builder for method chaining
         */
        public Builder chartType(ChartType type) {
            this.chartType = type;
            return this;
        }

        /**
         * Builds and returns the ChartData instance.
         *
         * @return a new ChartData instance with the configured values
         */
        public ChartData build() {
            return new ChartData(this);
        }
    }

    private ChartData(Builder builder) {
        this.title = builder.title;
        this.xAxisLabel = builder.xAxisLabel;
        this.yAxisLabel = builder.yAxisLabel;
        this.xAxisData = builder.xAxisData;
        this.seriesData = new LinkedHashMap<>(builder.seriesData);
        this.chartType = builder.chartType;
    }

    /**
     * Returns the chart title.
     *
     * @return the chart title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the x-axis label.
     *
     * @return the x-axis label
     */
    public String getXAxisLabel() {
        return xAxisLabel;
    }

    /**
     * Returns the y-axis label.
     *
     * @return the y-axis label
     */
    public String getYAxisLabel() {
        return yAxisLabel;
    }

    /**
     * Returns the x-axis infrastructure which can be List of Number, LocalDate, or LocalDateTime.
     * Returns null if no x-axis infrastructure was provided (auto-indexing).
     *
     * @return the x-axis infrastructure, or null if not set
     */
    public List<?> getXAxisData() {
        return xAxisData;
    }

    /**
     * Returns the series infrastructure mapping series names to their y-values.
     *
     * @return map of series names to their infrastructure
     */
    public Map<String, List<? extends Number>> getSeriesData() {
        return seriesData;
    }

    /**
     * Returns the chart type (LINE or BAR).
     *
     * @return the chart type
     */
    public ChartType getChartType() {
        return chartType;
    }
}

