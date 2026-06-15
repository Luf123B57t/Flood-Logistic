package com.aidsight.domain.model.core;

import com.aidsight.infrastructure.persistence.dao.InstanceDAORegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Subject contains a collection of tasks and instances for analysis.
 * <p>
 * A subject represents a complete analysis scenario with a set of tasks to perform
 * and a set of instances to analyze. Subjects can be persisted to and loaded from
 * the database, and support multiple instance types through the InstanceDAO registry.
 * </p>
 */
public class Subject {
    private static final Logger logger = LoggerFactory.getLogger(Subject.class);

    private Integer id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private List<Task> tasks;
    private List<Instance> instances;
    private final Set<Class<? extends Instance>> instanceTypes;

    /**
     * Creates a new Subject with the specified properties.
     *
     * @param name the name of this subject
     * @param description a detailed description of what this subject analyzes
     * @param tasks the list of tasks to perform on the instances
     * @param instances the list of instances to analyze
     */
    public Subject(String name, String description, List<Task> tasks, List<Instance> instances) {
        this.name = name;
        this.description = description;
        this.tasks = new ArrayList<>(tasks);
        this.instances = new ArrayList<>(instances);
        this.instanceTypes = new HashSet<>();
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Creates a new Subject with ID and creation timestamp (for database loading).
     *
     * @param id the database ID
     * @param name the name of this subject
     * @param description a detailed description of what this subject analyzes
     * @param tasks the list of tasks to perform on the instances
     * @param instances the list of instances to analyze
     * @param createdAt the creation timestamp
     */
    public Subject(Integer id, String name, String description, List<Task> tasks, List<Instance> instances, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.tasks = new ArrayList<>(tasks);
        this.instances = new ArrayList<>(instances);
        this.instanceTypes = new HashSet<>();
        this.createdAt = createdAt;
    }

    // Getters
    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Task> getTasks() {
        return new ArrayList<>(tasks);
    }

    public List<Instance> getInstances() {
        return new ArrayList<>(instances);
    }

    // Setters
    public void setId(Integer id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = new ArrayList<>(tasks);
    }

    public void setInstances(List<Instance> instances) {
        this.instances = new ArrayList<>(instances);
    }

    /**
     * Registers an instance type to be loaded for this subject.
     * <p>
     * Instance types must be registered before calling loadInstances().
     * </p>
     *
     * @param instanceType the instance class to register
     */
    public void addInstanceType(Class<? extends Instance> instanceType) {
        this.instanceTypes.add(instanceType);
    }

    /**
     * Loads all instances for this subject from the database using registered DAOs.
     * <p>
     * This method queries each registered instance type's DAO and loads instances
     * associated with this subject's ID. The subject must have an ID set before calling.
     * </p>
     *
     * @throws IllegalStateException if the subject has no ID
     */
    public void loadInstances() {
        if (id == null) {
            throw new IllegalStateException("Cannot load instances for a subject without an ID");
        }

        instances.clear();
        InstanceDAORegistry registry = InstanceDAORegistry.getInstance();

        for (Class<? extends Instance> instanceType : instanceTypes) {
            registry.getDAO(instanceType).ifPresent(dao -> {
                try {
                    List<? extends Instance> typeInstances = dao.getAllBySubjectId(id);
                    instances.addAll(typeInstances);
                } catch (SQLException e) {
                    logger.error("Error loading instances of type {}: {}", instanceType.getSimpleName(), e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Counts all instances for this subject without loading them into memory.
     * <p>
     * This method is more efficient than loading all instances when you only need the count.
     * It queries each registered instance type's DAO and sums the counts.
     * The subject must have an ID set before calling.
     * </p>
     *
     * @return the total number of instances associated with this subject
     * @throws IllegalStateException if the subject has no ID
     */
    public int countInstances() {
        if (id == null) {
            throw new IllegalStateException("Cannot count instances for a subject without an ID");
        }

        int totalCount = 0;
        InstanceDAORegistry registry = InstanceDAORegistry.getInstance();

        for (Class<? extends Instance> instanceType : instanceTypes) {
            var daoOptional = registry.getDAO(instanceType);
            if (daoOptional.isPresent()) {
                try {
                    totalCount += daoOptional.get().countBySubjectId(id);
                } catch (SQLException e) {
                    logger.error("Error counting instances of type {}: {}", instanceType.getSimpleName(), e.getMessage(), e);
                }
            }
        }

        return totalCount;
    }

    /**
     * Gets instances filtered by a specific type.
     *
     * @param instanceType the instance class to filter by
     * @param <T> the instance type
     * @return a list of instances of the specified type
     */
    @SuppressWarnings("unchecked")
    public <T extends Instance> List<T> getInstancesByType(Class<T> instanceType) {
        List<T> result = new ArrayList<>();
        for (Instance instance : instances) {
            if (instanceType.isInstance(instance)) {
                result.add((T) instance);
            }
        }
        return result;
    }

    /**
     * Returns the name of this subject.
     *
     * @return the subject name
     */
    @Override
    public String toString() {
        return name;
    }
}
