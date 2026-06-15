package com.aidsight.infrastructure.persistence.dao.impl;

import com.aidsight.infrastructure.persistence.connection.SQLiteConnectionManager;
import com.aidsight.domain.model.instance.NewspaperArticleInstance;
import com.aidsight.infrastructure.persistence.dao.InstanceDAO;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for NewspaperArticleInstance that interacts with SQLite database.
 * Implements InstanceDAO to support subject-based instance management.
 */
public record NewspaperArticleDAO(SQLiteConnectionManager connectionManager) implements InstanceDAO<NewspaperArticleInstance, Integer> {

    private static final String TABLE_NAME = "newspaper_articles";

    @Override
    public Optional<NewspaperArticleInstance> get(Integer id) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(extractFromResultSet(rs));
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public List<NewspaperArticleInstance> getAll() throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME;
        List<NewspaperArticleInstance> articles = new ArrayList<>();

        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                articles.add(extractFromResultSet(rs));
            }
        }

        return articles;
    }

    @Override
    public NewspaperArticleInstance save(NewspaperArticleInstance entity) {
        throw new UnsupportedOperationException("Use saveWithSubject() to save newspaper articles with a subject association");
    }

    @Override
    public boolean update(NewspaperArticleInstance entity) throws SQLException {
        if (entity.getId() == 0) {
            throw new IllegalArgumentException("Cannot update entity without an ID");
        }

        String sql = "UPDATE " + TABLE_NAME + " SET " +
                "url = ?, author = ?, content = ?, posted_date = ? " +
                "WHERE id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setStatementParameters(stmt, entity);
            stmt.setInt(5, entity.getId());

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    @Override
    public boolean delete(Integer id) throws SQLException {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Extracts a NewspaperArticleInstance from a ResultSet.
     *
     * @param rs the ResultSet positioned at the row to extract
     * @return a NewspaperArticleInstance object
     * @throws SQLException if a database access error occurs
     */
    private NewspaperArticleInstance extractFromResultSet(ResultSet rs) throws SQLException {
        NewspaperArticleInstance article = new NewspaperArticleInstance();

        article.setId(rs.getInt("id"));
        article.setUrl(rs.getString("url"));
        article.setAuthor(rs.getString("author"));
        article.setContent(rs.getString("content"));

        // Handle date
        String dateString = rs.getString("posted_date");
        if (dateString != null && !dateString.isEmpty()) {
            try {
                // Try to parse as Unix timestamp (milliseconds)
                long timestamp = Long.parseLong(dateString);
                article.setPostedDate(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate());
            } catch (NumberFormatException e) {
                // If not a timestamp, try to parse as ISO date string
                article.setPostedDate(LocalDate.parse(dateString));
            }
        }

        return article;
    }

    /**
     * Sets the parameters in a PreparedStatement from a NewspaperArticleInstance.
     *
     * @param stmt   the PreparedStatement to populate
     * @param entity the NewspaperArticleInstance containing the values
     * @throws SQLException if a database access error occurs
     */
    private void setStatementParameters(PreparedStatement stmt, NewspaperArticleInstance entity) throws SQLException {
        setStatementParameters(stmt, entity, 1);
    }

    /**
     * Sets the parameters in a PreparedStatement from a NewspaperArticleInstance.
     * This overload allows specifying a starting parameter index.
     *
     * @param stmt   the PreparedStatement to populate
     * @param entity the NewspaperArticleInstance containing the values
     * @param startIndex the starting parameter index
     * @throws SQLException if a database access error occurs
     */
    private void setStatementParameters(PreparedStatement stmt, NewspaperArticleInstance entity, int startIndex) throws SQLException {
        stmt.setString(startIndex, entity.getUrl());
        stmt.setString(startIndex + 1, entity.getAuthor());
        stmt.setString(startIndex + 2, entity.getContent());

        // Convert LocalDate to SQL Date
        if (entity.getPostedDate() != null) {
            stmt.setDate(startIndex + 3, Date.valueOf(entity.getPostedDate()));
        } else {
            stmt.setNull(startIndex + 3, Types.DATE);
        }
    }

    // InstanceDAO interface methods

    @Override
    public List<NewspaperArticleInstance> getAllBySubjectId(Integer subjectId) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE subject_id = ?";
        List<NewspaperArticleInstance> articles = new ArrayList<>();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, subjectId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    articles.add(extractFromResultSet(rs));
                }
            }
        }

        return articles;
    }

    @Override
    public int countBySubjectId(Integer subjectId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE subject_id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, subjectId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }

        return 0;
    }

    @Override
    public int deleteBySubjectId(Integer subjectId) throws SQLException {
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE subject_id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, subjectId);
            return stmt.executeUpdate();
        }
    }

    @Override
    public NewspaperArticleInstance saveWithSubject(NewspaperArticleInstance entity, Integer subjectId) throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " " +
                "(subject_id, url, author, content, posted_date) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, subjectId);
            setStatementParameters(stmt, entity, 2);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating newspaper article failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    entity.setId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating newspaper article failed, no ID obtained.");
                }
            }
        }

        return entity;
    }

    @Override
    public List<NewspaperArticleInstance> saveAllWithSubject(List<NewspaperArticleInstance> entities, Integer subjectId) throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " " +
                "(subject_id, url, author, content, posted_date) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            for (NewspaperArticleInstance entity : entities) {
                stmt.setInt(1, subjectId);
                setStatementParameters(stmt, entity, 2);
                stmt.addBatch();
            }

            stmt.executeBatch();

            // Get generated IDs
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                int index = 0;
                while (generatedKeys.next() && index < entities.size()) {
                    entities.get(index).setId(generatedKeys.getInt(1));
                    index++;
                }
            }
        }

        return entities;
    }

    // SchemaProvider interface methods

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public List<String> getCreateTableSQL() {
        return List.of(
            """
            CREATE TABLE IF NOT EXISTS newspaper_articles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_id INTEGER NOT NULL,
                url TEXT,
                author TEXT,
                content TEXT,
                posted_date TEXT,
                FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE
            )
            """
        );
    }

    @Override
    public List<String> getCreateIndexSQL() {
        return List.of(
            """
            CREATE INDEX IF NOT EXISTS idx_newspaper_articles_subject_id
            ON newspaper_articles(subject_id)
            """
        );
    }
}

