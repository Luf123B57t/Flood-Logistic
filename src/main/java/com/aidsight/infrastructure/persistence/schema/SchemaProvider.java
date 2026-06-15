package com.aidsight.infrastructure.persistence.schema;

import java.util.Collections;
import java.util.List;

/**
 * Interface for DAOs that can provide their table schema definitions.
 * <p>
 * This interface allows DAOs to encapsulate their table creation logic,
 * making it easier to add new instance types without modifying the
 * DatabaseInitializer class.
 * </p>
 */
public interface SchemaProvider {

    /**
     * Returns a list of SQL statements to create the table(s) for this DAO.
     * <p>
     * Should use "CREATE TABLE IF NOT EXISTS" to be idempotent.
     * Some DAOs may need to create multiple related tables (e.g., main table and junction/child tables).
     * </p>
     *
     * @return list of SQL statements to create the table(s)
     */
    List<String> getCreateTableSQL();

    /**
     * Returns a list of SQL statements to create indexes for this table.
     * <p>
     * Should use "CREATE INDEX IF NOT EXISTS" to be idempotent.
     * May return empty list if no indexes are needed.
     * </p>
     *
     * @return list of SQL statements to create indexes
     */
    default List<String> getCreateIndexSQL() {
        return Collections.emptyList();
    }

    /**
     * Returns the name of the table managed by this DAO.
     *
     * @return the table name
     */
    String getTableName();
}

