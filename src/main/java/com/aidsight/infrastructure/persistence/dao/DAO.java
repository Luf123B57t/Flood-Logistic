package com.aidsight.infrastructure.persistence.dao;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Generic Data Access Object interface for CRUD operations.
 * @param <T> The entity type
 * @param <ID> The primary key type
 */
public interface DAO<T, ID> {

    /**
     * Retrieves an entity by its primary key.
     * @param id the primary key
     * @return an Optional containing the entity if found, or empty if not found
     * @throws SQLException if a database access error occurs
     */
    Optional<T> get(ID id) throws SQLException;

    /**
     * Retrieves all entities from the database.
     * @return a list of all entities
     * @throws SQLException if a database access error occurs
     */
    List<T> getAll() throws SQLException;

    /**
     * Saves a new entity to the database.
     * @param entity the entity to save
     * @return the saved entity with generated ID (if applicable)
     * @throws SQLException if a database access error occurs
     */
    T save(T entity) throws SQLException;

    /**
     * Updates an existing entity in the database.
     * @param entity the entity to update
     * @return true if the update was successful, false otherwise
     * @throws SQLException if a database access error occurs
     */
    boolean update(T entity) throws SQLException;

    /**
     * Deletes an entity by its primary key.
     * @param id the primary key of the entity to delete
     * @return true if the deletion was successful, false otherwise
     * @throws SQLException if a database access error occurs
     */
    boolean delete(ID id) throws SQLException;
}

