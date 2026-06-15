package com.aidsight.infrastructure.persistence.dao.impl;

import com.aidsight.infrastructure.persistence.connection.SQLiteConnectionManager;
import com.aidsight.domain.model.instance.FacebookPostInstance;
import com.aidsight.infrastructure.persistence.dao.InstanceDAO;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Data Access Object for FacebookPostInstance that interacts with SQLite database.
 * Implements InstanceDAO to support subject-based instance management.
 */
public record FacebookPostDAO(SQLiteConnectionManager connectionManager) implements InstanceDAO<FacebookPostInstance, Integer> {

    private static final String TABLE_NAME = "facebook_posts";
    private static final String COMMENTS_TABLE_NAME = "facebook_post_comments";

    @Override
    public Optional<FacebookPostInstance> get(Integer id) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    FacebookPostInstance post = extractFromResultSet(rs);
                    loadComments(conn, post);
                    return Optional.of(post);
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public List<FacebookPostInstance> getAll() throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME;
        List<FacebookPostInstance> posts = new ArrayList<>();

        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                FacebookPostInstance post = extractFromResultSet(rs);
                loadComments(conn, post);
                posts.add(post);
            }
        }

        return posts;
    }

    @Override
    public FacebookPostInstance save(FacebookPostInstance entity) {
        throw new UnsupportedOperationException("Use saveWithSubject() to save Facebook posts with a subject association");
    }

    @Override
    public boolean update(FacebookPostInstance entity) throws SQLException {
        if (entity.getId() == 0) {
            throw new IllegalArgumentException("Cannot update entity without an ID");
        }

        String sql = "UPDATE " + TABLE_NAME + " SET " +
                "url = ?, content = ?, posted_date = ?, reactions_count = ? " +
                "WHERE id = ?";

        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                setStatementParameters(stmt, entity);
                stmt.setInt(5, entity.getId());

                int affectedRows = stmt.executeUpdate();

                if (affectedRows > 0) {
                    // Update comments
                    saveComments(conn, entity);
                    conn.commit();
                    return true;
                } else {
                    conn.rollback();
                    return false;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public boolean delete(Integer id) throws SQLException {
        String deleteCommentsSql = "DELETE FROM " + COMMENTS_TABLE_NAME + " WHERE post_id = ?";
        String deletePostSql = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";

        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // First delete comments
                try (PreparedStatement stmt = conn.prepareStatement(deleteCommentsSql)) {
                    stmt.setInt(1, id);
                    stmt.executeUpdate();
                }

                // Then delete the post
                try (PreparedStatement stmt = conn.prepareStatement(deletePostSql)) {
                    stmt.setInt(1, id);
                    int affectedRows = stmt.executeUpdate();

                    conn.commit();
                    return affectedRows > 0;
                }
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Extracts a FacebookPostInstance from a ResultSet.
     *
     * @param rs the ResultSet positioned at the row to extract
     * @return a FacebookPostInstance object
     * @throws SQLException if a database access error occurs
     */
    private FacebookPostInstance extractFromResultSet(ResultSet rs) throws SQLException {
        FacebookPostInstance post = new FacebookPostInstance();

        post.setId(rs.getInt("id"));
        post.setUrl(rs.getString("url"));
        post.setContent(rs.getString("content"));

        // Comments will be loaded separately
        post.setComments(new ArrayList<>());

        // Handle date
        String dateString = rs.getString("posted_date");
        if (dateString != null && !dateString.isEmpty()) {
            try {
                // Try to parse as Unix timestamp (milliseconds)
                long timestamp = Long.parseLong(dateString);
                post.setPostedDate(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate());
            } catch (NumberFormatException e) {
                // If not a timestamp, try to parse as ISO date string
                post.setPostedDate(LocalDate.parse(dateString));
            }
        }

        // Parse reactions count array
        String reactionsStr = rs.getString("reactions_count");
        if (reactionsStr != null && !reactionsStr.isEmpty()) {
            post.setReactionsCount(parseReactionsCount(reactionsStr));
        } else {
            post.setReactionsCount(new int[FacebookPostInstance.Reaction.values().length]);
        }

        return post;
    }

    /**
     * Sets the parameters in a PreparedStatement from a FacebookPostInstance.
     *
     * @param stmt   the PreparedStatement to populate
     * @param entity the FacebookPostInstance containing the values
     * @throws SQLException if a database access error occurs
     */
    private void setStatementParameters(PreparedStatement stmt, FacebookPostInstance entity) throws SQLException {
        setStatementParameters(stmt, entity, 1);
    }

    /**
     * Sets the parameters in a PreparedStatement from a FacebookPostInstance.
     * This overload allows specifying a starting parameter index.
     *
     * @param stmt   the PreparedStatement to populate
     * @param entity the FacebookPostInstance containing the values
     * @param startIndex the starting parameter index
     * @throws SQLException if a database access error occurs
     */
    private void setStatementParameters(PreparedStatement stmt, FacebookPostInstance entity, int startIndex) throws SQLException {
        stmt.setString(startIndex, entity.getUrl());
        stmt.setString(startIndex + 1, entity.getContent());

        // Convert LocalDate to SQL Date
        if (entity.getPostedDate() != null) {
            stmt.setDate(startIndex + 2, Date.valueOf(entity.getPostedDate()));
        } else {
            stmt.setNull(startIndex + 2, Types.DATE);
        }

        // Serialize reactions count array
        String reactionsStr = serializeReactionsCount(entity.getReactionsCount());
        stmt.setString(startIndex + 3, reactionsStr);
    }

    /**
     * Loads comments for a FacebookPostInstance from the database.
     *
     * @param conn the database connection
     * @param post the FacebookPostInstance to load comments for
     * @throws SQLException if a database access error occurs
     */
    private void loadComments(Connection conn, FacebookPostInstance post) throws SQLException {
        String sql = "SELECT comment_text FROM " + COMMENTS_TABLE_NAME + " WHERE post_id = ? ORDER BY id";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, post.getId());

            try (ResultSet rs = stmt.executeQuery()) {
                List<String> comments = new ArrayList<>();
                while (rs.next()) {
                    comments.add(rs.getString("comment_text"));
                }
                post.setComments(comments);
            }
        }
    }

    /**
     * Saves comments for a FacebookPostInstance to the database.
     * Deletes existing comments and inserts new ones.
     *
     * @param conn the database connection
     * @param post the FacebookPostInstance to save comments for
     * @throws SQLException if a database access error occurs
     */
    private void saveComments(Connection conn, FacebookPostInstance post) throws SQLException {
        // First, delete existing comments
        String deleteSql = "DELETE FROM " + COMMENTS_TABLE_NAME + " WHERE post_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setInt(1, post.getId());
            stmt.executeUpdate();
        }

        // Then, insert new comments
        if (post.getComments() != null && !post.getComments().isEmpty()) {
            String insertSql = "INSERT INTO " + COMMENTS_TABLE_NAME + " (post_id, comment_text) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                for (String comment : post.getComments()) {
                    stmt.setInt(1, post.getId());
                    stmt.setString(2, comment);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        }
    }

    /**
     * Parses a reactions count string into an array.
     *
     * @param reactionsStr the serialized reactions string
     * @return array of reaction counts
     */
    private int[] parseReactionsCount(String reactionsStr) {
        if (reactionsStr == null || reactionsStr.isEmpty()) {
            return new int[FacebookPostInstance.Reaction.values().length];
        }

        String[] parts = reactionsStr.split(",");
        int[] counts = new int[FacebookPostInstance.Reaction.values().length];

        for (int i = 0; i < Math.min(parts.length, counts.length); i++) {
            try {
                counts[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                counts[i] = 0;
            }
        }

        return counts;
    }

    /**
     * Serializes a reactions count array into a string.
     *
     * @param reactionsCount the array of reaction counts
     * @return serialized string
     */
    private String serializeReactionsCount(int[] reactionsCount) {
        if (reactionsCount == null || reactionsCount.length == 0) {
            return "";
        }
        return Arrays.stream(reactionsCount)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
    }

    // InstanceDAO interface methods

    @Override
    public List<FacebookPostInstance> getAllBySubjectId(Integer subjectId) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE subject_id = ?";
        List<FacebookPostInstance> posts = new ArrayList<>();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, subjectId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    FacebookPostInstance post = extractFromResultSet(rs);
                    loadComments(conn, post);
                    posts.add(post);
                }
            }
        }

        return posts;
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
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // First delete comments for all posts with this subject_id
                String deleteCommentsSql = "DELETE FROM " + COMMENTS_TABLE_NAME +
                                          " WHERE post_id IN (SELECT id FROM " + TABLE_NAME + " WHERE subject_id = ?)";
                try (PreparedStatement stmt = conn.prepareStatement(deleteCommentsSql)) {
                    stmt.setInt(1, subjectId);
                    stmt.executeUpdate();
                }

                // Then delete the posts
                String deletePostsSql = "DELETE FROM " + TABLE_NAME + " WHERE subject_id = ?";
                int deletedCount;
                try (PreparedStatement stmt = conn.prepareStatement(deletePostsSql)) {
                    stmt.setInt(1, subjectId);
                    deletedCount = stmt.executeUpdate();
                }

                conn.commit();
                return deletedCount;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public FacebookPostInstance saveWithSubject(FacebookPostInstance entity, Integer subjectId) throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " " +
                "(subject_id, url, content, posted_date, reactions_count) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, subjectId);
                setStatementParameters(stmt, entity, 2);

                int affectedRows = stmt.executeUpdate();

                if (affectedRows == 0) {
                    throw new SQLException("Creating Facebook post failed, no rows affected.");
                }

                try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        entity.setId(generatedKeys.getInt(1));
                    } else {
                        throw new SQLException("Creating Facebook post failed, no ID obtained.");
                    }
                }

                // Save comments
                saveComments(conn, entity);

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }

        return entity;
    }

    @Override
    public List<FacebookPostInstance> saveAllWithSubject(List<FacebookPostInstance> entities, Integer subjectId) throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " " +
                "(subject_id, url, content, posted_date, reactions_count) " +
                "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                // Insert each post individually to ensure proper ID retrieval
                for (FacebookPostInstance entity : entities) {
                    stmt.setInt(1, subjectId);
                    setStatementParameters(stmt, entity, 2);

                    int affectedRows = stmt.executeUpdate();

                    if (affectedRows == 0) {
                        throw new SQLException("Creating Facebook post failed, no rows affected.");
                    }

                    // Get the generated ID for this specific post
                    try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            entity.setId(generatedKeys.getInt(1));
                        } else {
                            throw new SQLException("Creating Facebook post failed, no ID obtained.");
                        }
                    }

                    // Save comments for this post now that we have a valid ID
                    saveComments(conn, entity);
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
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
        return Arrays.asList(
            """
            CREATE TABLE IF NOT EXISTS facebook_posts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_id INTEGER NOT NULL,
                url TEXT,
                content TEXT,
                posted_date TEXT,
                reactions_count TEXT,
                FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS facebook_post_comments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                post_id INTEGER NOT NULL,
                comment_text TEXT,
                FOREIGN KEY (post_id) REFERENCES facebook_posts(id) ON DELETE CASCADE
            )
            """
        );
    }

    @Override
    public List<String> getCreateIndexSQL() {
        return Arrays.asList(
            """
            CREATE INDEX IF NOT EXISTS idx_facebook_posts_subject_id
            ON facebook_posts(subject_id)
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_facebook_post_comments_post_id
            ON facebook_post_comments(post_id)
            """
        );
    }
}

