package com.capstone.adproject.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CourseEmailConstraintMigrationRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(CourseEmailConstraintMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public CourseEmailConstraintMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        migrateTable("student", "uk_student_email_course");
        migrateTable("lecturer", "uk_lecturer_email_course");
        migrateUsernameTable("student", "uk_student_username_course");
        migrateUsernameTable("lecturer", "uk_lecturer_username_course");
        migrateStudentResultOverrides();
    }

    private void migrateUsernameTable(String tableName, String targetCompositeIndexName) {
        try {
            if ("lecturer".equalsIgnoreCase(tableName)) {
                try {
                    jdbcTemplate.execute("ALTER TABLE `lecturer` DROP INDEX `idx_lecturer_username`");
                    logger.info("Explicitly dropped idx_lecturer_username on lecturer");
                } catch (Exception ignored) {
                }
            } else if ("student".equalsIgnoreCase(tableName)) {
                try {
                    jdbcTemplate.execute("ALTER TABLE `student` DROP INDEX `idx_student_username`");
                    logger.info("Explicitly dropped idx_student_username on student");
                } catch (Exception ignored) {
                }
            }
            dropLegacySingleColumnUniqueIndexes(tableName, "username", targetCompositeIndexName);
            ensureCompositeUniqueIndex(tableName, targetCompositeIndexName, "(`username`, `course_id`)");
        } catch (Exception ex) {
            logger.warn("Skipping username-constraint migration for table {}: {}", tableName, ex.getMessage());
        }
    }

    private void migrateStudentResultOverrides() {
        try {
            dropLegacySingleColumnUniqueIndexes("student_result_overrides", "student_id", "uk_override_student_course");
            jdbcTemplate.execute(
                    """
                    UPDATE `student_result_overrides` sro
                    JOIN `student` s ON sro.student_id = s.id
                    SET sro.course_id = s.course_id
                    WHERE sro.course_id IS NULL
                    """);
            jdbcTemplate.execute(
                    """
                    UPDATE `factor_override_history` foh
                    JOIN `student` s ON foh.student_id = s.id
                    SET foh.course_id = s.course_id
                    WHERE foh.course_id IS NULL
                    """);
            ensureCompositeUniqueIndex("student_result_overrides", "uk_override_student_course", "(`student_id`, `course_id`)");
        } catch (Exception ex) {
            logger.warn("Skipping override course-constraint migration: {}", ex.getMessage());
        }
    }

    private void migrateTable(String tableName, String targetCompositeIndexName) {
        try {
            dropLegacySingleColumnEmailUniqueIndexes(tableName, targetCompositeIndexName);
            ensureCompositeEmailCourseUniqueIndex(tableName, targetCompositeIndexName);
        } catch (Exception ex) {
            logger.warn("Skipping email-constraint migration for table {}: {}", tableName, ex.getMessage());
        }
    }

    private void dropLegacySingleColumnEmailUniqueIndexes(String tableName, String targetCompositeIndexName) {
        dropLegacySingleColumnUniqueIndexes(tableName, "email", targetCompositeIndexName);
    }

    private void dropLegacySingleColumnUniqueIndexes(String tableName, String columnName, String targetCompositeIndexName) {
        List<String> indexNames = jdbcTemplate.queryForList(
                """
                SELECT index_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                GROUP BY index_name
                HAVING MIN(non_unique) = 0
                   AND COUNT(*) = 1
                   AND SUM(CASE WHEN column_name = ? THEN 1 ELSE 0 END) = 1
                   AND index_name <> ?
                """,
                String.class,
                tableName,
                columnName,
                targetCompositeIndexName);

        for (String indexName : indexNames) {
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP INDEX `" + indexName + "`");
            logger.info("Dropped legacy single-column {} unique index {} on {}", columnName, indexName, tableName);
        }
    }

    private void ensureCompositeEmailCourseUniqueIndex(String tableName, String targetCompositeIndexName) {
        ensureCompositeUniqueIndex(tableName, targetCompositeIndexName, "(`email`, `course_id`)");
    }

    private void ensureCompositeUniqueIndex(String tableName, String targetCompositeIndexName, String columnsSql) {
        Integer existing = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """,
                Integer.class,
                tableName,
                targetCompositeIndexName);

        if (existing != null && existing > 0) {
            return;
        }

        jdbcTemplate.execute(
                "ALTER TABLE `" + tableName + "` ADD CONSTRAINT `" + targetCompositeIndexName
                        + "` UNIQUE " + columnsSql);
        logger.info("Created composite unique index {} on {}{}", targetCompositeIndexName, tableName, columnsSql);
    }
}
