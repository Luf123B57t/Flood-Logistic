package com.aidsight.application.service;

import com.aidsight.domain.enums.InstanceType;
import com.aidsight.domain.model.core.Subject;
import com.aidsight.infrastructure.config.DatabaseConfig;
import com.aidsight.infrastructure.persistence.connection.DatabaseInitializer;
import com.aidsight.infrastructure.persistence.connection.SQLiteConnectionManager;
import com.aidsight.infrastructure.persistence.dao.InstanceDAORegistry;
import com.aidsight.infrastructure.persistence.dao.SubjectDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

/**
 * Manager class to initialize the database and register instance DAOs.
 * <p>
 * This manager provides methods to ensure the database is properly initialized
 * and handles DAO registry initialization for instance type management.
 * </p>
 */
public class SubjectService {

    private static final Logger logger = LoggerFactory.getLogger(SubjectService.class);
    private static boolean initialized = false;

    /**
     * Initializes the database schema and registers DAOs.
     * <p>
     * This method should be called once at application startup to ensure
     * the database is ready and all instance DAOs are registered.
     * </p>
     *
     * @throws SQLException if a database access error occurs
     */
    public static synchronized void initialize() throws SQLException {
        if (initialized) {
            return;
        }

        SQLiteConnectionManager connectionManager = SQLiteConnectionManager.getInstance(DatabaseConfig.getDatabasePath());

        // Initialize database schema
        DatabaseInitializer initializer = new DatabaseInitializer(connectionManager);
        initializer.initializeSchema();


        // Register instance DAOs
        registerDAOs(connectionManager);

        // Log existing subjects
        SubjectDAO subjectDAO = new SubjectDAO(connectionManager);
        List<Subject> existingSubjects = subjectDAO.getAll();
        if (existingSubjects.isEmpty()) {
            logger.info("Database is empty, no subjects to load");
        } else {
            logger.info("Found {} existing subject(s) in database", existingSubjects.size());
        }

        initialized = true;
        logger.info("SubjectService initialization complete");
    }

    /**
     * Registers all instance DAO implementations with the registry.
     * Uses InstanceType enum to automatically register all DAOs.
     *
     * @param connectionManager the connection manager for database access
     */
    private static void registerDAOs(SQLiteConnectionManager connectionManager) {
        InstanceDAORegistry registry = InstanceDAORegistry.getInstance();

        // Automatically register all instance types using InstanceType enum
        for (InstanceType type : InstanceType.values()) {
            registry.registerRaw(type.getInstanceClass(), type.createDAO(connectionManager));
            logger.debug("Registered DAO for: {}", type.getDisplayName());
        }

        logger.info("Registered {} DAO(s) automatically from InstanceType enum", InstanceType.values().length);
    }
}
