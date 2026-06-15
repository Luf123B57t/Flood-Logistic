package com.aidsight.infrastructure.persistence.dao;

import com.aidsight.domain.model.core.Instance;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for mapping instance types to their corresponding DAO implementations.
 * <p>
 * This singleton class provides a centralized mechanism to register and retrieve
 * InstanceDAO implementations based on instance class types. It enables the Subject
 * class to dynamically load instances of different types without tight coupling to
 * specific DAO implementations.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * InstanceDAORegistry registry = InstanceDAORegistry.getInstance();
 * registry.register(FacebookPostInstance.class, facebookPostDAO);
 * InstanceDAO dao = registry.getDAO(FacebookPostInstance.class);
 * </pre>
 * </p>
 */
public class InstanceDAORegistry {
    private static InstanceDAORegistry instance;
    private final Map<Class<? extends Instance>, InstanceDAO<?, ?>> daoMap;

    /**
     * Private constructor to enforce singleton pattern.
     */
    private InstanceDAORegistry() {
        this.daoMap = new HashMap<>();
    }

    /**
     * Returns the singleton instance of the registry.
     *
     * @return the InstanceDAORegistry singleton instance
     */
    public static synchronized InstanceDAORegistry getInstance() {
        if (instance == null) {
            instance = new InstanceDAORegistry();
        }
        return instance;
    }

    /**
     * Registers an InstanceDAO implementation for a specific instance type.
     * <p>
     * If a DAO is already registered for the given type, it will be replaced.
     * </p>
     *
     * @param instanceType the instance class type
     * @param dao the DAO implementation for the instance type
     * @param <T> the instance type
     * @param <ID> the primary key type
     */
    public <T extends Instance, ID> void register(Class<T> instanceType, InstanceDAO<T, ID> dao) {
        daoMap.put(instanceType, dao);
    }

    /**
     * Registers an InstanceDAO implementation for a specific instance type (raw version).
     * <p>
     * This method is used for automatic registration from InstanceType enum where
     * the exact type parameters cannot be inferred at compile time.
     * </p>
     *
     * @param instanceType the instance class type
     * @param dao the DAO implementation for the instance type
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerRaw(Class<? extends Instance> instanceType, InstanceDAO<?, ?> dao) {
        daoMap.put(instanceType, dao);
    }

    /**
     * Retrieves the DAO implementation for a specific instance type.
     *
     * @param instanceType the instance class type
     * @param <T> the instance type
     * @param <ID> the primary key type
     * @return an Optional containing the DAO if registered, or empty if not found
     */
    @SuppressWarnings("unchecked")
    public <T extends Instance, ID> Optional<InstanceDAO<T, ID>> getDAO(Class<T> instanceType) {
        return Optional.ofNullable((InstanceDAO<T, ID>) daoMap.get(instanceType));
    }

    /**
     * Checks if a DAO is registered for the given instance type.
     *
     * @param instanceType the instance class type
     * @return true if a DAO is registered, false otherwise
     */
    public boolean isRegistered(Class<? extends Instance> instanceType) {
        return daoMap.containsKey(instanceType);
    }

    /**
     * Removes the DAO registration for a specific instance type.
     *
     * @param instanceType the instance class type
     */
    public void unregister(Class<? extends Instance> instanceType) {
        daoMap.remove(instanceType);
    }

    /**
     * Clears all registered DAOs.
     * <p>
     * This is primarily useful for testing purposes.
     * </p>
     */
    public void clear() {
        daoMap.clear();
    }
}

