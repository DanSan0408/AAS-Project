package com.capstone.adproject.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Migration runner to detect and clean up legacy orphan columns in MySQL.
 * 
 * Specifically addresses the case where earlier versions created 'submittedAt' (camelCase)
 * with NOT NULL and no default value. When the entities were updated to @Column(name = "submitted_at"),
 * Hibernate ddl-auto=update added 'submitted_at' but left 'submittedAt' in place,
 * causing "Field 'submittedAt' doesn't have a default value" errors on INSERT.
 */
@Component
@Order(1)
public class LegacyColumnCleanupMigrationRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LegacyColumnCleanupMigrationRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public LegacyColumnCleanupMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        cleanLegacyColumn("marks", "submittedAt", "submitted_at");
        cleanLegacyColumn("assessment_comments", "submittedAt", "submitted_at");
    }

    private void cleanLegacyColumn(String tableName, String legacyColumn, String currentColumn) {
        try {
            // Check if legacy column exists in MySQL table
            Integer legacyColCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                legacyColumn
            );

            if (legacyColCount == null || legacyColCount == 0) {
                return;
            }

            logger.info("Found legacy column '{}' in table '{}'. Initiating migration...", legacyColumn, tableName);

            // Check if current (snake_case) column exists
            Integer currentColCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                currentColumn
            );

            if (currentColCount != null && currentColCount > 0) {
                // Both columns exist: copy any non-null legacy values if current is null, then drop or nullify legacy
                try {
                    jdbcTemplate.execute(
                        "UPDATE `" + tableName + "` SET `" + currentColumn + "` = `" + legacyColumn + "` " +
                        "WHERE `" + currentColumn + "` IS NULL AND `" + legacyColumn + "` IS NOT NULL"
                    );
                } catch (Exception e) {
                    logger.debug("Could not copy legacy data from {} to {}: {}", legacyColumn, currentColumn, e.getMessage());
                }

                try {
                    jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP COLUMN `" + legacyColumn + "`");
                    logger.info("Successfully dropped legacy column '{}' from table '{}'.", legacyColumn, tableName);
                } catch (Exception dropEx) {
                    logger.warn("Could not drop column '{}' from '{}', making it nullable with default NULL: {}", 
                        legacyColumn, tableName, dropEx.getMessage());
                    jdbcTemplate.execute("ALTER TABLE `" + tableName + "` MODIFY COLUMN `" + legacyColumn + "` DATETIME NULL DEFAULT NULL");
                }
            } else {
                // Only legacy column exists: make it nullable with default NULL so it does not block inserts
                jdbcTemplate.execute("ALTER TABLE `" + tableName + "` MODIFY COLUMN `" + legacyColumn + "` DATETIME NULL DEFAULT NULL");
                logger.info("Modified legacy column '{}' in table '{}' to be NULLABLE with DEFAULT NULL.", legacyColumn, tableName);
            }
        } catch (Exception ex) {
            logger.warn("Skipping legacy column cleanup for table '{}', column '{}': {}", tableName, legacyColumn, ex.getMessage());
        }
    }
}

