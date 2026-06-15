package com.aidsight.domain.processor;

import com.aidsight.domain.model.core.Instance;
import com.aidsight.domain.model.core.Task;

/**
 * Processor interface for combining tasks with instances to produce results.
 * <p>
 * Processors define how a specific task should be executed on a specific instance type.
 * Each processor implementation handles one task-instance combination and produces
 * the appropriate result.
 * </p>
 *
 * @param <P> the task type this processor handles
 * @param <I> the instance type this processor operates on
 * @param <R> the result type this processor produces
 */
public interface Processor<P extends Task, I extends Instance, R extends Task.Result<?>> {
    /**
     * Processes the given task on the given instance and produces a result.
     *
     * @param task the task to execute
     * @param instance the instance to process
     * @return the result of processing, or null if no result is produced
     */
    R process(P task, I instance);
}
