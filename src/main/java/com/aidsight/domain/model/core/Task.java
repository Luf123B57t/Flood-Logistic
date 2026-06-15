package com.aidsight.domain.model.core;

import com.aidsight.domain.enums.TaskType;
import com.aidsight.domain.model.analysis.ChartData;

import java.util.List;

/**
 * Base abstract class for all analysis tasks.
 * <p>
 * Tasks define what kind of analysis should be performed on instances.
 * Each task implementation specifies its own Result type that contains
 * the analysis outcomes.
 * </p>
 * <p>
 * The {@link #getName()} and {@link #getDescription()} methods automatically
 * retrieve values from the corresponding {@link TaskType} enum.
 * </p>
 */
public abstract class Task {
    /**
     * Returns the name of this task.
     * <p>
     * Retrieves the display name from the corresponding {@link TaskType} enum.
     * Returns "No Name" if the task type is not found.
     * </p>
     *
     * @return the task name
     */
    public String getName() {
        TaskType type = TaskType.fromClass(this.getClass());
        return type != null ? type.getDisplayName() : "No Name";
    }

    /**
     * Returns a description of what this task does.
     * <p>
     * Retrieves the description from the corresponding {@link TaskType} enum.
     * Returns "No Description" if the task type is not found.
     * </p>
     *
     * @return the task description
     */
    public String getDescription() {
        TaskType type = TaskType.fromClass(this.getClass());
        return type != null ? type.getDescription() : "No Description";
    }

    /**
     * Result interface with self-referential generic type parameter.
     * This allows implementations to merge with their own specific type
     * instead of the base Result type, providing compile-time type safety.
     *
     * @param <T> The specific Result type (the implementing class itself)
     */
    public interface Result<T extends Result<T>> {
        /**
         * Merge this result with another result of the same type.
         * The generic type parameter ensures that only results of the same
         * concrete type can be merged together.
         *
         * @param other Another result of the same type to merge with this one
         */
        void merge(T other);

        /**
         * Get a formatted text report of the results.
         * @return Text report suitable for display
         */
        String getReport();

        /**
         * Get chart infrastructure as a list of ChartData objects.
         * Each ChartData contains complete configuration for one chart.
         * @return List of ChartData objects
         */
        List<ChartData> getChartData();
    }
}
