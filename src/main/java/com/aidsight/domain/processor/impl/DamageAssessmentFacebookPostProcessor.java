package com.aidsight.domain.processor.impl;

import com.aidsight.domain.model.instance.FacebookPostInstance;
import com.aidsight.domain.model.task.DamageAssessmentTask;
import com.aidsight.domain.processor.Processor;

/**
 * Processor implementation for damage assessment tasks on Facebook post instances.
 * <p>
 * This processor analyzes Facebook posts and their associated comments to assess
 * damage. Currently, this is a placeholder implementation that returns empty results.
 * </p>
 * <p>
 * Future implementations may analyze comment content for damage-related information.
 * </p>
 *
 * @see DamageAssessmentTask
 * @see FacebookPostInstance
 */
public class DamageAssessmentFacebookPostProcessor implements Processor<DamageAssessmentTask, FacebookPostInstance, DamageAssessmentTask.Result> {
    /**
     * Processes a damage assessment task on a Facebook post instance.
     * <p>
     * This is currently a placeholder implementation that returns an empty result.
     * </p>
     *
     * @param task the damage assessment task to execute
     * @param instance the Facebook post instance to analyze
     * @return an empty damage assessment result
     */
    @Override
    public DamageAssessmentTask.Result process(DamageAssessmentTask task, FacebookPostInstance instance) {
        // no-op right now, just return placeholder data
        return new DamageAssessmentTask.Result();
    }
}
