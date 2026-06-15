package com.aidsight.domain.enums;

import com.aidsight.domain.model.core.Instance;
import com.aidsight.domain.model.task.SentimentTrackerTask;
import com.aidsight.domain.processor.Processor;
import com.aidsight.domain.model.core.Task;
import com.aidsight.domain.model.task.DamageAssessmentTask;
import com.aidsight.domain.processor.impl.DamageAssessmentFacebookPostProcessor;

import java.util.*;
import java.util.function.Supplier;

/**
 * Enumeration of available task types.
 * <p>
 * This enum provides a centralized registry of all available task types
 * that can be added to subjects for analysis. Each task type includes:
 * </p>
 * <ul>
 * <li>Task class and factory method</li>
 * <li>Processor mappings for different instance types</li>
 * <li>Metadata including identifier, display name, and description</li>
 * </ul>
 * <p>
 * To add a new task type, create a new enum constant with the required task details
 * and register the appropriate processor mappings in the static initializer block.
 * The trace-back mechanism from task class to TaskType is automatically established.
 * </p>
 */
public enum TaskType {
    DAMAGE_ASSESSMENT(
        "damage-assessment",
        "Damage Assessment",
        "Assess the extent and predominant type of damage",
        DamageAssessmentTask.class
    ),
    SENTIMENT_TRACKER(
        "sentiment-tracker",
        "Sentiment Tracker",
        "Track sentiment over time",
        SentimentTrackerTask.class
    );

    private final String id;
    private final String displayName;
    private final String description;
    private final Class<? extends Task> taskClass;

    // Map to store processor factories for each task-instance combination
    private final Map<Class<? extends Instance>, Supplier<Processor<?, ?, ?>>> processorMap;

    // Static reverse lookup map: Task class -> TaskType
    private static final Map<Class<? extends Task>, TaskType> CLASS_TO_TYPE_MAP = new HashMap<>();

    static {
        // Build reverse lookup map automatically
        for (TaskType type : values()) {
            CLASS_TO_TYPE_MAP.put(type.taskClass, type);
        }

        // Register processor mappings for each task
        DAMAGE_ASSESSMENT.registerProcessor(
            InstanceType.FACEBOOK_POST.getInstanceClass(),
            DamageAssessmentFacebookPostProcessor::new
        );
    }

    TaskType(String id, String displayName, String description, Class<? extends Task> taskClass) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.taskClass = taskClass;
        this.processorMap = new HashMap<>();
    }

    /**
     * Registers a processor factory for a specific instance type.
     * Called during static initialization.
     *
     * @param instanceClass the instance class this processor handles
     * @param processorFactory the factory to create processor instances
     */
    private void registerProcessor(Class<? extends Instance> instanceClass,
                                   Supplier<Processor<?, ?, ?>> processorFactory) {
        processorMap.put(instanceClass, processorFactory);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Class<? extends Task> getTaskClass() {
        return taskClass;
    }

    /**
     * Creates a new instance of the task.
     *
     * @return a new Task instance
     */
    public Task createTask() {
        try {
            return taskClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create task instance for " + displayName, e);
        }
    }

    /**
     * Creates a processor for the given instance type.
     *
     * @param instanceClass the instance class
     * @return a new Processor instance, or null if no processor is registered
     */
    public Processor<?, ?, ?> createProcessor(Class<? extends Instance> instanceClass) {
        Supplier<Processor<?, ?, ?>> factory = processorMap.get(instanceClass);
        return factory != null ? factory.get() : null;
    }

    /**
     * Gets all instance types that this task can process.
     *
     * @return set of instance classes
     */
    public Set<Class<? extends Instance>> getSupportedInstanceTypes() {
        return new HashSet<>(processorMap.keySet());
    }

    /**
     * Gets all available task types.
     *
     * @return list of all task types
     */
    public static List<TaskType> getAll() {
        return Arrays.asList(values());
    }

    /**
     * Finds a task type by its ID.
     *
     * @param id the task type ID
     * @return the TaskType, or null if not found
     */
    public static TaskType fromId(String id) {
        for (TaskType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Finds a task type by its class (trace-back mechanism).
     * <p>
     * This allows you to get the TaskType enum from a Task class,
     * enabling access to all related information (processors, etc.)
     * </p>
     *
     * @param taskClass the task class
     * @return the TaskType, or null if not found
     */
    public static TaskType fromClass(Class<? extends Task> taskClass) {
        return CLASS_TO_TYPE_MAP.get(taskClass);
    }
}

