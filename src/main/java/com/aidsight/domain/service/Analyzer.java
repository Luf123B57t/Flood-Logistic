package com.aidsight.domain.service;

import com.aidsight.domain.model.core.Instance;
import com.aidsight.domain.processor.Processor;
import com.aidsight.domain.enums.ProcessorType;
import com.aidsight.domain.model.core.Task;
import com.aidsight.infrastructure.external.python.SentimentAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Analyzer class that processes tasks against instances using centralized processors.
 * <p>
 * This class executes analysis by looking up processors from the central ProcessorType registry.
 * It processes each task against each instance, merging results from the same task type.
 * </p>
 * <p>
 * Processors are defined in the ProcessorType enum, which maintains a centralized mapping
 * of task-instance combinations to their respective processors.
 * </p>
 */
public class Analyzer {
    private static final Logger logger = LoggerFactory.getLogger(Analyzer.class);

    /**
     * Helper method to merge two results of the same type.
     * <p>
     * Uses unchecked cast since we know at runtime the results are of compatible types.
     * The type parameter R ensures type safety at the merge level.
     * </p>
     *
     * @param <R> the result type
     * @param target the target result to merge into
     * @param source the source result to merge from
     */
    @SuppressWarnings("unchecked")
    private <R extends Task.Result<R>> void mergeResults(Task.Result<?> target, Task.Result<?> source) {
        ((Task.Result<R>) target).merge((R) source);
    }

    /**
     * Analyzes all tasks against all instances using centralized processors.
     * <p>
     * This is a synchronous method that processes each task against each instance,
     * merging results from the same task type. For each task-instance pair, it looks
     * up the appropriate processor from ProcessorType and executes it. Results from
     * the same task are merged together.
     * </p>
     * <p>
     * The SentimentAnalyzer is started before processing and stopped after completion
     * to ensure proper resource management.
     * </p>
     *
     * @param tasks the list of tasks to execute
     * @param instances the list of instances to process
     * @return an AnalysisResult containing merged results for each task type
     * @throws RuntimeException if the sentiment analysis process crashes or fails to start
     */
    @SuppressWarnings("unchecked")
    public AnalysisResult analyze(List<? extends Task> tasks, List<? extends Instance> instances) {
        SentimentAnalyzer analyzer = SentimentAnalyzer.getInstance();

        try {
            // Start sentiment analyzer before processing
            logger.info("Starting sentiment analyzer...");
            analyzer.start();
        } catch (IOException e) {
            logger.error("Failed to start sentiment analyzer", e);
            throw new RuntimeException("Failed to start sentiment analyzer: " + e.getMessage(), e);
        }

        try {
            AnalysisResult results = new AnalysisResult();
            for (Task task : tasks) {
                Task.Result<?> merged = null;
                for (Instance inst : instances) {
                    Class<? extends Task> taskClass = task.getClass();
                    Class<? extends Instance> instanceClass = inst.getClass();
                    Processor<Task, Instance, Task.Result<?>> p = (Processor<Task, Instance, Task.Result<?>>) ProcessorType.getProcessor(taskClass, instanceClass);
                    if (p == null)
                        continue;
                    Task.Result<?> r = p.process(task, inst);
                    if (r == null)
                        continue;
                    if (merged == null)
                        merged = r;
                    else
                        mergeResults(merged, r);
                }
                if (merged != null)
                    results.put(task.getClass(), merged);
            }
            return results;
        } finally {
            // Always stop sentiment analyzer when analysis completes or fails
            logger.info("Stopping sentiment analyzer...");
            analyzer.stop();
        }
    }

    /**
     * Container for analysis results, mapping task classes to their results.
     * <p>
     * This class provides a type-safe way to store and retrieve results from different
     * analysis tasks, using the task class as the key.
     * </p>
     */
    public static class AnalysisResult {
        private final Map<Class<? extends Task>, Task.Result<?>> resultMap = new HashMap<>();

        /**
         * Stores a result for a specific task class.
         *
         * @param <T> the task type
         * @param taskClass the class of the task
         * @param result the result to store
         */
        public <T extends Task> void put(Class<T> taskClass, Task.Result<?> result) {
            resultMap.put(taskClass, result);
        }

        /**
         * Retrieves the result for a specific task class.
         *
         * @param <T> the task type
         * @param <R> the result type
         * @param taskClass the class of the task
         * @return the result associated with the task class, or null if not found
         */
        @SuppressWarnings("unchecked")
        public <T extends Task, R extends Task.Result<R>> R get(Class<T> taskClass) {
            return (R) resultMap.get(taskClass);
        }

    }
}
