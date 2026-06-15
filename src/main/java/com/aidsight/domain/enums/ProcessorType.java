package com.aidsight.domain.enums;

import com.aidsight.domain.model.core.Instance;
import com.aidsight.domain.model.instance.NewspaperArticleInstance;
import com.aidsight.domain.model.task.SentimentTrackerTask;
import com.aidsight.domain.processor.Processor;
import com.aidsight.domain.model.core.Task;
import com.aidsight.domain.processor.impl.DamageAssessmentFacebookPostProcessor;
import com.aidsight.domain.model.task.DamageAssessmentTask;
import com.aidsight.domain.model.instance.FacebookPostInstance;
import com.aidsight.domain.processor.impl.DamageAssessmentNewspaperArticleProcessor;
import com.aidsight.domain.processor.impl.SentimentTrackerFacebookPostProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Enumeration of all processor types.
 * <p>
 * This enum provides a centralized registry of all processor mappings
 * for task-instance combinations. Each processor type defines:
 * </p>
 * <ul>
 * <li>Task class that the processor handles</li>
 * <li>Instance class that the processor operates on</li>
 * <li>Processor factory for creating processor instances</li>
 * </ul>
 * <p>
 * To add a new processor, create a new enum constant specifying the task class,
 * instance class, and processor factory. The central processor map is automatically
 * populated during static initialization, making the processor available to the analyzer.
 * </p>
 */
public enum ProcessorType {
    DAMAGE_ASSESSMENT_FACEBOOK_POST(
        DamageAssessmentTask.class,
        FacebookPostInstance.class,
        DamageAssessmentFacebookPostProcessor::new
    ),
    DAMAGE_ASSESSMENT_NEWSPAPER_ARTICLE(
        DamageAssessmentTask.class,
        NewspaperArticleInstance.class,
        DamageAssessmentNewspaperArticleProcessor::new
    ),
    SENTIMENT_TRACKER_FACEBOOK_POST(
        SentimentTrackerTask.class,
        FacebookPostInstance.class,
        SentimentTrackerFacebookPostProcessor::new
    );

    private final Class<? extends Task> taskClass;
    private final Class<? extends Instance> instanceClass;
    private final Supplier<Processor<?, ?, ?>> processorFactory;

    // Central processor map: (TaskClass, InstanceClass) -> ProcessorFactory
    private static final Map<Pair<Class<? extends Task>, Class<? extends Instance>>, Supplier<Processor<?, ?, ?>>> PROCESSOR_MAP = new HashMap<>();

    static {
        // Build the central processor map automatically from enum values
        for (ProcessorType type : values()) {
            Pair<Class<? extends Task>, Class<? extends Instance>> key =
                new Pair<>(type.taskClass, type.instanceClass);
            PROCESSOR_MAP.put(key, type.processorFactory);
        }
    }

    /**
     * Constructor for ProcessorType.
     *
     * @param taskClass the task class this processor handles
     * @param instanceClass the instance class this processor operates on
     * @param processorFactory factory to create processor instances
     */
    ProcessorType(Class<? extends Task> taskClass,
                  Class<? extends Instance> instanceClass,
                  Supplier<Processor<?, ?, ?>> processorFactory) {
        this.taskClass = taskClass;
        this.instanceClass = instanceClass;
        this.processorFactory = processorFactory;
    }

    /**
     * Gets the task class for this processor type.
     *
     * @return the task class
     */
    public Class<? extends Task> getTaskClass() {
        return taskClass;
    }

    /**
     * Gets the instance class for this processor type.
     *
     * @return the instance class
     */
    public Class<? extends Instance> getInstanceClass() {
        return instanceClass;
    }

    /**
     * Creates a new processor instance.
     *
     * @return a new Processor instance
     */
    public Processor<?, ?, ?> createProcessor() {
        return processorFactory.get();
    }

    /**
     * Gets a processor for the given task-instance combination from the central map.
     *
     * @param taskClass the task class
     * @param instanceClass the instance class
     * @return a new Processor instance, or null if no processor is registered
     */
    public static Processor<?, ?, ?> getProcessor(Class<? extends Task> taskClass,
                                                   Class<? extends Instance> instanceClass) {
        Pair<Class<? extends Task>, Class<? extends Instance>> key = new Pair<>(taskClass, instanceClass);
        Supplier<Processor<?, ?, ?>> factory = PROCESSOR_MAP.get(key);
        return factory != null ? factory.get() : null;
    }

    /**
     * Checks if a processor exists for the given task-instance combination.
     *
     * @param taskClass the task class
     * @param instanceClass the instance class
     * @return true if a processor is registered, false otherwise
     */
    public static boolean hasProcessor(Class<? extends Task> taskClass,
                                       Class<? extends Instance> instanceClass) {
        Pair<Class<? extends Task>, Class<? extends Instance>> key = new Pair<>(taskClass, instanceClass);
        return PROCESSOR_MAP.containsKey(key);
    }

    /**
     * Utility class to represent a pair of values.
     * <p>
     * This record provides a simple immutable container for two related values of
     * potentially different types. It implements proper equals and hashCode methods
     * for use in collections.
     * </p>
     *
     * @param <A> the type of the first element
     * @param <B> the type of the second element
     * @param first the first element of the pair
     * @param second the second element of the pair
     */
    public static record Pair<A, B>(A first, B second) {
        /**
         * Checks equality based on the values of both elements.
         * <p>
         * Two pairs are equal if both their first and second elements are equal.
         * This method uses pattern matching for instanceof to safely extract values.
         * </p>
         *
         * @param o the object to compare with
         * @return true if both pairs contain equal elements, false otherwise
         */
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Pair<?, ?>(Object otherFirst, Object otherSecond)))
                return false;
            return Objects.equals(first, otherFirst) && Objects.equals(second, otherSecond);
        }
    }
}

