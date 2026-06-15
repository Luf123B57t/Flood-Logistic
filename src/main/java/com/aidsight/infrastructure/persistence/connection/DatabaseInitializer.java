package com.aidsight.infrastructure.persistence.connection;

import com.aidsight.domain.enums.InstanceType;
import com.aidsight.infrastructure.persistence.schema.SchemaProvider;
import com.aidsight.infrastructure.persistence.dao.SubjectDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages database schema initialization.
 * <p>
 * This class handles the creation of tables needed for the persistence layer.
 * It leverages the InstanceType enum and SchemaProvider interface to automatically
 * discover and create tables for all instance types, making it easy to add new
 * instance types without modifying this class.
 * </p>
 */
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    private final SQLiteConnectionManager connectionManager;

    /**
     * Creates a new DatabaseInitializer.
     *
     * @param connectionManager the connection manager for database access
     */
    public DatabaseInitializer(SQLiteConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    /**
     * Initializes the database schema by creating all required tables.
     * <p>
     * This method is idempotent - it can be called multiple times safely as it
     * uses CREATE TABLE IF NOT EXISTS statements. It automatically creates tables
     * for all InstanceTypes by using their SchemaProvider implementations.
     * </p>
     *
     * @throws SQLException if a database access error occurs
     */
    public void initializeSchema() throws SQLException {
        // Create subjects table first (as it's referenced by foreign keys)
        createSchemaFromProvider(new SubjectDAO(connectionManager));

        // Create tables for all instance types
        for (InstanceType instanceType : InstanceType.values()) {
            createSchemaFromProvider(instanceType.createDAO(connectionManager));
        }

        logger.info("Database schema initialization complete");
    }


    /**
     * Creates a table and indexes from a SchemaProvider.
     *
     * @param provider the schema provider
     * @throws SQLException if a database access error occurs
     */
    @SuppressWarnings("SqlSourceToSinkFlow") // SQL comes from trusted internal SchemaProvider implementations
    private void createSchemaFromProvider(Object provider) throws SQLException {
        if (!(provider instanceof SchemaProvider schemaProvider)) {
            logger.warn("Object {} does not implement SchemaProvider, skipping schema creation",
                       provider.getClass().getSimpleName());
            return;
        }

        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // Create table(s)
            for (String createTableSQL : schemaProvider.getCreateTableSQL()) {
                if (createTableSQL != null && !createTableSQL.isBlank()) {
                    stmt.execute(createTableSQL);
                    logger.debug("Created/verified table(s) for: {}", schemaProvider.getTableName());
                }
            }

            // Create indexes if provided
            for (String createIndexSQL : schemaProvider.getCreateIndexSQL()) {
                if (createIndexSQL != null && !createIndexSQL.isBlank()) {
                    stmt.execute(createIndexSQL);
                    logger.debug("Created/verified index for: {}", schemaProvider.getTableName());
                }
            }
        }
    }
}

