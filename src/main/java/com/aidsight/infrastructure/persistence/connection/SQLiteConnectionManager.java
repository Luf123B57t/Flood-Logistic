package com.aidsight.infrastructure.persistence.connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages SQLite database connections using the Singleton pattern per database path.
 * <p>
 * This class provides thread-safe access to SQLite database connections and ensures
 * that only one connection manager exists per database path. It handles connection
 * lifecycle including creation, retrieval, and proper cleanup.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * SQLiteConnectionManager manager = SQLiteConnectionManager.getInstance("database/database.db");
 * Connection conn = manager.getConnection();
 * // Use connection...
 * manager.close();
 * </pre>
 * </p>
 *
 * @author Generated Documentation
 * @version 1.0
 * @since 2025-11-27
 */
public class SQLiteConnectionManager {
    /**
     * Map storing singleton instances of connection managers, keyed by database path.
     */
    private static final Map<String, SQLiteConnectionManager> instances = new HashMap<>();

    /**
     * The active database connection.
     */
    private Connection connection;

    /**
     * The file system path to the SQLite database.
     */
    private final String dbPath;

    /**
     * Private constructor to enforce singleton pattern.
     * <p>
     * Creates a new connection manager and establishes an initial connection
     * to the specified database.
     * </p>
     *
     * @param dbPath the file system path to the SQLite database
     * @throws SQLException if a database access error occurs or the connection cannot be established
     */
    private SQLiteConnectionManager(String dbPath) throws SQLException {
        this.dbPath = dbPath;
        connect();
    }

    /**
     * Returns the singleton instance of the connection manager for the specified database path.
     * <p>
     * This method is thread-safe and ensures that only one connection manager exists per
     * database path. If an instance doesn't exist for the given path, a new one is created.
     * </p>
     *
     * @param dbPath the file system path to the SQLite database
     * @return the singleton SQLiteConnectionManager instance for the specified database path
     * @throws SQLException if a database access error occurs during instance creation
     */
    public static synchronized SQLiteConnectionManager getInstance(String dbPath) throws SQLException {
        if (!instances.containsKey(dbPath)) {
            instances.put(dbPath, new SQLiteConnectionManager(dbPath));
        }
        return instances.get(dbPath);
    }

    /**
     * Establishes a connection to the SQLite database.
     * <p>
     * Creates a new JDBC connection using the database path and enables auto-commit mode.
     * This method is called internally during initialization and when reconnecting.
     * </p>
     *
     * @throws SQLException if a database access error occurs or the connection cannot be established
     */
    private void connect() throws SQLException {
        String url = "jdbc:sqlite:" + dbPath;
        connection = DriverManager.getConnection(url);
        connection.setAutoCommit(true);

        // Enable foreign key constraints in SQLite
        try (var stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
    }

    /**
     * Returns the active database connection, creating a new one if necessary.
     * <p>
     * If the current connection is null or closed, this method automatically establishes
     * a new connection before returning it. This ensures that callers always receive a
     * valid, open connection.
     * </p>
     *
     * @return a valid, open Connection to the SQLite database
     * @throws SQLException if a database access error occurs or a new connection cannot be established
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connect();
        }
        return connection;
    }

    /**
     * Returns the file system path to the SQLite database managed by this instance.
     *
     * @return the database path as a string
     */
    public String getDbPath() {
        return dbPath;
    }

    /**
     * Closes the database connection and removes this instance from the singleton registry.
     * <p>
     * After calling this method, the connection is closed and the instance is removed from
     * the internal instances map. Future calls to getInstance() with the same database path
     * will create a new connection manager instance.
     * </p>
     *
     * @throws SQLException if a database access error occurs during connection closure
     */
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        instances.remove(dbPath);
    }

    /**
     * Closes all database connections and clears the singleton registry.
     * <p>
     * This static method iterates through all connection manager instances, closes their
     * connections, and clears the instances map. This is useful for application shutdown
     * to ensure all database connections are properly closed.
     * </p>
     *
     * @throws SQLException if a database access error occurs while closing any connection
     */
    public static synchronized void closeAll() throws SQLException {
        for (SQLiteConnectionManager manager : instances.values()) {
            if (manager.connection != null && !manager.connection.isClosed()) {
                manager.connection.close();
            }
        }
        instances.clear();
    }
}
