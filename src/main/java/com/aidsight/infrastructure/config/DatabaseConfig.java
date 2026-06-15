package com.aidsight.infrastructure.config;

/**
 * Configuration class for database parameters.
 * Provides centralized control over database connection settings
 * including paths, connection pooling, and timeout settings.
 */
public final class DatabaseConfig {
    /**
     * Path to the SQLite database file.
     * Default: "database/database.db"
     */
    private static String databasePath = "database/database.db";

    /**
     * Private constructor to prevent instantiation.
     */
    private DatabaseConfig() {}

    // Getters
    public static String getDatabasePath() {
        return databasePath;
    }

    // Setters for runtime configuration
    public static void setDatabasePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Database path cannot be null or empty");
        }
        databasePath = path;
    }
}

