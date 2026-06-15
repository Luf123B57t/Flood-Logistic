package com.aidsight.infrastructure.persistence.dao;

import com.aidsight.domain.model.core.Subject;
import com.aidsight.domain.model.core.Task;
import com.aidsight.domain.enums.TaskType;
import com.aidsight.infrastructure.persistence.connection.SQLiteConnectionManager;
import com.aidsight.infrastructure.persistence.schema.SchemaProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Subject entities.
 * <p>
 * This DAO manages the persistence of Subject objects, including their associated
 * tasks. Tasks are stored as TaskType IDs for refactor safety and type safety.
 * </p>
 */
public record SubjectDAO(SQLiteConnectionManager connectionManager) implements DAO<Subject, Integer>, SchemaProvider {

    private static final Logger logger = LoggerFactory.getLogger(SubjectDAO.class);
    private static final String TABLE_NAME = "subjects";
    private static final String TASKS_TABLE_NAME = "subject_tasks";

    @Override
    public Optional<Subject> get(Integer id) throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " WHERE id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Subject subject = extractFromResultSet(rs);
                    loadTasks(subject);
                    return Optional.of(subject);
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public List<Subject> getAll() throws SQLException {
        String sql = "SELECT * FROM " + TABLE_NAME + " ORDER BY created_at DESC";
        List<Subject> subjects = new ArrayList<>();

        try (Connection conn = connectionManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Subject subject = extractFromResultSet(rs);
                subjects.add(subject);
            }
        }

        // Load tasks for all subjects after the ResultSet is closed
        for (Subject subject : subjects) {
            loadTasks(subject);
        }

        return subjects;
    }

    @Override
    public Subject save(Subject entity) throws SQLException {
        String sql = "INSERT INTO " + TABLE_NAME + " (name, description, created_at) VALUES (?, ?, ?)";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, entity.getName());
            stmt.setString(2, entity.getDescription());
            // Format datetime as SQLite expects: 'YYYY-MM-DD HH:MM:SS'
            String createdAtStr = entity.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            stmt.setString(3, createdAtStr);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Creating subject failed, no rows affected.");
            }

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    entity.setId(generatedKeys.getInt(1));
                } else {
                    throw new SQLException("Creating subject failed, no ID obtained.");
                }
            }

            // Save associated tasks
            saveTasks(entity);
        }

        return entity;
    }

    @Override
    public boolean update(Subject entity) throws SQLException {
        if (entity.getId() == null) {
            throw new IllegalArgumentException("Cannot update entity without an ID");
        }

        String sql = "UPDATE " + TABLE_NAME + " SET name = ?, description = ? WHERE id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, entity.getName());
            stmt.setString(2, entity.getDescription());
            stmt.setInt(3, entity.getId());

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                // Delete and re-save tasks
                deleteTasks(entity.getId());
                saveTasks(entity);
                return true;
            }

            return false;
        }
    }

    @Override
    public boolean delete(Integer id) throws SQLException {
        // Tasks are automatically deleted via CASCADE
        String sql = "DELETE FROM " + TABLE_NAME + " WHERE id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        }
    }

    /**
     * Extracts a Subject from a ResultSet (without tasks).
     *
     * @param rs the ResultSet positioned at the row to extract
     * @return a Subject object without tasks loaded
     * @throws SQLException if a database access error occurs
     */
    private Subject extractFromResultSet(ResultSet rs) throws SQLException {
        Integer id = rs.getInt("id");
        String name = rs.getString("name");
        String description = rs.getString("description");
        String createdAtStr = rs.getString("created_at");

        // SQLite stores datetime as 'YYYY-MM-DD HH:MM:SS', replace space with 'T' for ISO format
        LocalDateTime createdAt = LocalDateTime.parse(createdAtStr.replace(' ', 'T'));

        return new Subject(id, name, description, new ArrayList<>(), new ArrayList<>(), createdAt);
    }

    /**
     * Loads tasks for a subject from the subject_tasks table.
     *
     * @param subject the subject to load tasks for
     * @throws SQLException if a database access error occurs
     */
    private void loadTasks(Subject subject) throws SQLException {
        if (subject.getId() == null) {
            return;
        }

        String sql = "SELECT task_type_id FROM " + TASKS_TABLE_NAME +
                     " WHERE subject_id = ? ORDER BY task_order ASC";

        List<Task> tasks = new ArrayList<>();

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, subject.getId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String taskTypeId = rs.getString("task_type_id");
                    TaskType taskType = TaskType.fromId(taskTypeId);
                    if (taskType != null) {
                        tasks.add(taskType.createTask());
                    } else {
                        logger.warn("Unknown task type ID: {}", taskTypeId);
                    }
                }
            }
        }

        subject.setTasks(tasks);
    }

    /**
     * Saves tasks for a subject to the subject_tasks table.
     *
     * @param subject the subject whose tasks should be saved
     * @throws SQLException if a database access error occurs
     */
    private void saveTasks(Subject subject) throws SQLException {
        if (subject.getId() == null) {
            throw new IllegalStateException("Cannot save tasks for a subject without an ID");
        }

        String sql = "INSERT INTO " + TASKS_TABLE_NAME +
                     " (subject_id, task_type_id, task_order) VALUES (?, ?, ?)";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            int order = 0;
            for (Task task : subject.getTasks()) {
                TaskType taskType = TaskType.fromClass(task.getClass());
                if (taskType == null) {
                    logger.warn("Unknown task class: {}, skipping", task.getClass().getName());
                    continue;
                }
                stmt.setInt(1, subject.getId());
                stmt.setString(2, taskType.getId());
                stmt.setInt(3, order++);
                stmt.addBatch();
            }

            stmt.executeBatch();
        }
    }

    /**
     * Deletes all tasks for a subject.
     *
     * @param subjectId the subject ID whose tasks should be deleted
     * @throws SQLException if a database access error occurs
     */
    private void deleteTasks(Integer subjectId) throws SQLException {
        String sql = "DELETE FROM " + TASKS_TABLE_NAME + " WHERE subject_id = ?";

        try (Connection conn = connectionManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, subjectId);
            stmt.executeUpdate();
        }
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
            CREATE TABLE IF NOT EXISTS subjects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT,
                created_at TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS subject_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_id INTEGER NOT NULL,
                task_type_id TEXT NOT NULL,
                task_order INTEGER NOT NULL,
                FOREIGN KEY (subject_id) REFERENCES subjects(id) ON DELETE CASCADE
            )
            """
        );
    }

    @Override
    public List<String> getCreateIndexSQL() {
        return List.of(
            """
            CREATE INDEX IF NOT EXISTS idx_subjects_created_at
            ON subjects(created_at)
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_subject_tasks_subject_id
            ON subject_tasks(subject_id)
            """
        );
    }
}

