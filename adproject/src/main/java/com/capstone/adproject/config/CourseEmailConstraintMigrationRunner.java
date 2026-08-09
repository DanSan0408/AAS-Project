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
        List<String> indexNames = jdbcTemplate.queryForList(
                """
                SELECT index_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                GROUP BY index_name
                HAVING MIN(non_unique) = 0
                   AND COUNT(*) = 1
                   AND SUM(CASE WHEN column_name = 'email' THEN 1 ELSE 0 END) = 1
                   AND index_name <> ?
                """,
                String.class,
                tableName,
                targetCompositeIndexName);

        for (String indexName : indexNames) {
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP INDEX `" + indexName + "`");
            logger.info("Dropped legacy single-column email unique index {} on {}", indexName, tableName);
        }
    }

    private void ensureCompositeEmailCourseUniqueIndex(String tableName, String targetCompositeIndexName) {
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
                        + "` UNIQUE (`email`, `course_id`)");
        logger.info("Created composite unique index {} on {}(email, course_id)", targetCompositeIndexName, tableName);
    }
}
