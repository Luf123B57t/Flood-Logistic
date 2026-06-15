package com.aidsight.infrastructure.persistence.dao;

import com.aidsight.domain.model.core.Instance;
import com.aidsight.infrastructure.persistence.schema.SchemaProvider;

import java.sql.SQLException;
import java.util.List;

/**
 * Extended DAO interface for Instance entities that belong to subjects.
 * <p>
 * This interface adds subject-specific operations to the base DAO functionality,
 * allowing instances to be queried and managed based on their subject relationships.
 * It also extends SchemaProvider to enable automatic schema initialization.
 * </p>
 *
 * @param <T> The instance entity type extending Instance
 * @param <ID> The primary key type
 */
public interface InstanceDAO<T extends Instance, ID> extends DAO<T, ID>, SchemaProvider {

    /**
     * Retrieves all instances associated with a specific subject.
     *
     * @param subjectId the subject ID to filter by
     * @return a list of instances belonging to the specified subject
     * @throws SQLException if a database access error occurs
     */
    List<T> getAllBySubjectId(Integer subjectId) throws SQLException;

    /**
     * Counts the number of instances associated with a specific subject.
     *
     * @param subjectId the subject ID to count instances for
     * @return the number of instances belonging to the specified subject
     * @throws SQLException if a database access error occurs
     */
    int countBySubjectId(Integer subjectId) throws SQLException;

    /**
     * Deletes all instances associated with a specific subject.
     * <p>
     * Note: This operation may be restricted by foreign key constraints
     * depending on the database schema configuration.
     * </p>
     *
     * @param subjectId the subject ID whose instances should be deleted
     * @return the number of instances deleted
     * @throws SQLException if a database access error occurs
     */
    int deleteBySubjectId(Integer subjectId) throws SQLException;

    /**
     * Saves a new instance associated with a specific subject.
     *
     * @param entity the instance to save
     * @param subjectId the subject ID to associate the instance with
     * @return the saved instance with generated ID (if applicable)
     * @throws SQLException if a database access error occurs
     */
    T saveWithSubject(T entity, Integer subjectId) throws SQLException;

    /**
     * Saves multiple instances associated with a specific subject in a batch operation.
     * <p>
     * This method provides better performance than calling saveWithSubject multiple times
     * by using batch SQL operations.
     * </p>
     *
     * @param entities the list of instances to save
     * @param subjectId the subject ID to associate all instances with
     * @return the list of saved instances with generated IDs
     * @throws SQLException if a database access error occurs
     */
    List<T> saveAllWithSubject(List<T> entities, Integer subjectId) throws SQLException;

}

