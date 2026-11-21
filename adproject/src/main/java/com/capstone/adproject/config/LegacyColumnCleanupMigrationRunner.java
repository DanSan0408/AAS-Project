package com.capstone.adproject.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.persister.collection.AbstractCollectionPersister;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManagerFactory;

/**
 * Migration runner to detect and clean up legacy orphan columns in MySQL.
 *
 * <p>Two layers. First the known, hand-listed legacy renames below (copy data across, then drop). Then a generic
 * schema guard that compares the live database with what the JPA entities map and fixes the failure that keeps
 * recurring on this long-lived database: a column the code no longer maps, still declared NOT NULL without a
 * default, which makes every INSERT fail with "Field 'x' doesn't have a default value". Such columns are made
 * nullable (never dropped, so no data is lost). Columns the code needs but the database lacks are reported, not
 * guessed at. The outcome is kept in {@link #getFindings()} for the Super Admin monitoring page. The guard can
 * never stop the application from starting.
 * 
 * Specifically addresses the cases where:
 * 1. Earlier versions created 'submittedAt' (camelCase) with NOT NULL and no default value in 'marks' / 'assessment_comments'.
 * 2. Earlier versions or snake_case naming created 'course_code' and 'course_name' with NOT NULL and no default value in 'courses'.
 *    When Course entity maps to courseCode / courseName under PhysicalNamingStrategyStandardImpl,
 *    Hibernate inserts into (courseCode, courseName) without providing legacy course_code / course_name,
 *    causing "Field 'course_code' doesn't have a default value" errors on INSERT.
 */
@Component
@Order(1)
public class LegacyColumnCleanupMigrationRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LegacyColumnCleanupMigrationRunner.class);

    /** kind: MISSING_COLUMN, LEGACY_RELAXED (fixed), or LEGACY_BLOCKING (needs a human). */
    public record SchemaFinding(String kind, String table, String column, String detail) {}

    /** One row of information_schema.columns. */
    record DbColumn(String table, String column, String type, boolean nullable, boolean hasDefault,
                    boolean primaryKey, boolean generated, String charset, String collation) {}

    private final JdbcTemplate jdbcTemplate;
    private final EntityManagerFactory entityManagerFactory;
    private volatile List<SchemaFinding> findings = List.of();
    private volatile Instant lastRunAt;

    @Value("${app.schema-guard.auto-relax:true}")
    private boolean autoRelax = true;

    public LegacyColumnCleanupMigrationRunner(JdbcTemplate jdbcTemplate, EntityManagerFactory entityManagerFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManagerFactory = entityManagerFactory;
    }

    public List<SchemaFinding> getFindings() {
        return findings;
    }

    public Instant getLastRunAt() {
        return lastRunAt;
    }

    @Override
    public void run(String... args) {
        cleanLegacyColumn("marks", "submittedAt", "submitted_at", "DATETIME");
        cleanLegacyColumn("assessment_comments", "submittedAt", "submitted_at", "DATETIME");
        cleanLegacyColumn("courses", "course_code", "courseCode", "VARCHAR(255)");
        cleanLegacyColumn("courses", "course_name", "courseName", "VARCHAR(255)");
        cleanLegacyColumn("course", "course_code", "courseCode", "VARCHAR(255)");
        cleanLegacyColumn("course", "course_name", "courseName", "VARCHAR(255)");
    }

    /** Runs after every other startup runner, so columns they add or rename are already in place when we compare. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onApplicationReady() {
        guardSchema();
    }

    /** Re-runs the generic schema check on demand (used by the monitoring page's "re-check" action). */
    public void guardSchema() {
        try {
            if (entityManagerFactory == null) {
                return;
            }
            Map<String, Set<String>> mapped = mappedColumns(entityManagerFactory);
            List<DbColumn> dbColumns = readDatabaseColumns();
            List<SchemaFinding> result = new ArrayList<>();
            for (SchemaFinding finding : analyse(mapped, dbColumns)) {
                result.add(finding.kind().equals("LEGACY_BLOCKING") && autoRelax ? relax(finding, dbColumns) : finding);
            }
            findings = List.copyOf(result);
            lastRunAt = Instant.now();
            for (SchemaFinding f : result) {
                logger.warn("SCHEMA GUARD [{}] {}.{}: {}", f.kind(), f.table(), f.column(), f.detail());
            }
            if (result.isEmpty()) {
                logger.info("Schema guard: database matches the entity mappings.");
            }
        } catch (Exception e) {
            // A failing guard must never take the application down with it.
            logger.warn("Schema guard could not complete and was skipped: {}", e.getMessage());
        }
    }

    /**
     * Pure comparison of what the code maps against what the database has. Only tables the code maps are looked at;
     * tables owned by something else are never touched.
     */
    static List<SchemaFinding> analyse(Map<String, Set<String>> mapped, List<DbColumn> dbColumns) {
        Map<String, Set<String>> dbByTable = new HashMap<>();
        for (DbColumn c : dbColumns) {
            dbByTable.computeIfAbsent(c.table().toLowerCase(Locale.ROOT), k -> new HashSet<>())
                    .add(c.column().toLowerCase(Locale.ROOT));
        }
        List<SchemaFinding> out = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : mapped.entrySet()) {
            String table = entry.getKey();
            Set<String> present = dbByTable.get(table);
            if (present == null) {
                out.add(new SchemaFinding("MISSING_COLUMN", table, "*", "Table is mapped by the code but does not exist"));
                continue;
            }
            for (String column : entry.getValue()) {
                if (!present.contains(column)) {
                    out.add(new SchemaFinding("MISSING_COLUMN", table, column,
                            "The code reads/writes this column but the database does not have it"));
                }
            }
        }
        for (DbColumn c : dbColumns) {
            Set<String> expected = mapped.get(c.table().toLowerCase(Locale.ROOT));
            if (expected == null || expected.contains(c.column().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!c.nullable() && !c.hasDefault() && !c.primaryKey() && !c.generated()) {
                out.add(new SchemaFinding("LEGACY_BLOCKING", c.table(), c.column(),
                        "Unused by the code but NOT NULL with no default, so every insert into the table fails"));
            }
        }
        return out;
    }

    private SchemaFinding relax(SchemaFinding finding, List<DbColumn> dbColumns) {
        DbColumn column = dbColumns.stream()
                .filter(c -> c.table().equalsIgnoreCase(finding.table()) && c.column().equalsIgnoreCase(finding.column()))
                .findFirst().orElse(null);
        if (column == null) {
            return finding;
        }
        try {
            StringBuilder sql = new StringBuilder("ALTER TABLE `").append(column.table()).append("` MODIFY COLUMN `")
                    .append(column.column()).append("` ").append(column.type());
            if (column.charset() != null && !column.charset().isBlank()) {
                sql.append(" CHARACTER SET ").append(column.charset());
                if (column.collation() != null && !column.collation().isBlank()) {
                    sql.append(" COLLATE ").append(column.collation());
                }
            }
            sql.append(" NULL DEFAULT NULL");
            jdbcTemplate.execute(sql.toString());
            return new SchemaFinding("LEGACY_RELAXED", finding.table(), finding.column(),
                    "Unused legacy column was NOT NULL with no default; made nullable so inserts work (no data removed)");
        } catch (Exception e) {
            return new SchemaFinding("LEGACY_BLOCKING", finding.table(), finding.column(),
                    finding.detail() + ". Automatic fix failed: " + e.getMessage());
        }
    }

    private List<DbColumn> readDatabaseColumns() {
        List<DbColumn> rows = jdbcTemplate.query(
                """
                SELECT table_name, column_name, column_type, is_nullable, column_default, column_key, extra,
                       character_set_name, collation_name
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                """,
                (rs, i) -> {
                    String extra = rs.getString("extra") == null ? "" : rs.getString("extra").toLowerCase(Locale.ROOT);
                    return new DbColumn(rs.getString("table_name"), rs.getString("column_name"),
                            rs.getString("column_type"), "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                            rs.getString("column_default") != null, "PRI".equalsIgnoreCase(rs.getString("column_key")),
                            extra.contains("auto_increment") || extra.contains("generated"),
                            rs.getString("character_set_name"), rs.getString("collation_name"));
                });
        return rows == null ? List.of() : rows;
    }

    /** Lower-cased table -> set of lower-cased column names that the JPA mappings read or write. */
    static Map<String, Set<String>> mappedColumns(EntityManagerFactory emf) {
        Map<String, Set<String>> mapped = new HashMap<>();
        var metamodel = emf.unwrap(SessionFactoryImplementor.class).getMappingMetamodel();
        metamodel.forEachEntityDescriptor(persister -> {
            if (!(persister instanceof AbstractEntityPersister entity)) {
                return;
            }
            String[] tables = entity.getTableNames();
            if (tables == null || tables.length == 0) {
                return;
            }
            for (String table : tables) {
                String[] id = entity.getIdentifierColumnNames();
                addAll(mapped, table, id);
            }
            if (entity.getDiscriminatorColumnName() != null) {
                addAll(mapped, tables[0], new String[] {entity.getDiscriminatorColumnName()});
            }
            for (String property : entity.getPropertyNames()) {
                String table = entity.getPropertyTableName(property);
                String[] columns = entity.getPropertyColumnNames(property);
                if (table != null && columns != null) {
                    addAll(mapped, table, columns);
                }
            }
        });
        metamodel.forEachCollectionDescriptor(persister -> {
            if (persister instanceof AbstractCollectionPersister collection && collection.getTableName() != null) {
                String table = collection.getTableName();
                addAll(mapped, table, collection.getKeyColumnNames());
                addAll(mapped, table, collection.getElementColumnNames());
                addAll(mapped, table, collection.getIndexColumnNames());
                if (collection.getIdentifierColumnName() != null) {
                    addAll(mapped, table, new String[] {collection.getIdentifierColumnName()});
                }
            }
        });
        return mapped;
    }

    private static void addAll(Map<String, Set<String>> mapped, String table, String[] columns) {
        if (table == null || columns == null) {
            return;
        }
        // Hibernate returns table names that may be quoted or a subselect; keep plain identifiers only.
        String cleanTable = table.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
        if (cleanTable.contains(" ") || cleanTable.contains("(")) {
            return;
        }
        Set<String> set = mapped.computeIfAbsent(cleanTable, k -> new HashSet<>());
        for (String column : columns) {
            if (column != null && !column.contains("(") && !column.contains(" ")) {
                set.add(column.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT));
            }
        }
    }

    private void cleanLegacyColumn(String tableName, String legacyColumn, String currentColumn) {
        cleanLegacyColumn(tableName, legacyColumn, currentColumn, "VARCHAR(255)");
    }

    private void cleanLegacyColumn(String tableName, String legacyColumn, String currentColumn, String defaultType) {
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

            // Fetch actual column type if possible, or fall back to defaultType
            String columnType = defaultType;
            try {
                String detectedType = jdbcTemplate.queryForObject(
                    """
                    SELECT column_type
                    FROM information_schema.columns
                    WHERE table_schema = DATABASE()
                      AND table_name = ?
                      AND column_name = ?
                    LIMIT 1
                    """,
                    String.class,
                    tableName,
                    legacyColumn
                );
                if (detectedType != null && !detectedType.isBlank()) {
                    columnType = detectedType;
                }
            } catch (Exception e) {
                logger.debug("Could not determine column_type for {}.{}: {}", tableName, legacyColumn, e.getMessage());
            }

            // Check if current column exists
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
                boolean copyVerified = false;
                try {
                    jdbcTemplate.execute(
                        "UPDATE `" + tableName + "` SET `" + currentColumn + "` = `" + legacyColumn + "` " +
                        "WHERE `" + currentColumn + "` IS NULL AND `" + legacyColumn + "` IS NOT NULL"
                    );
                    Integer remainingLegacyOnly = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM `" + tableName + "` "
                        + "WHERE `" + legacyColumn + "` IS NOT NULL "
                        + "AND `" + currentColumn + "` IS NULL",
                        Integer.class
                    );
                    copyVerified = remainingLegacyOnly == null || remainingLegacyOnly == 0;
                } catch (Exception e) {
                    logger.debug("Could not copy legacy data from {} to {}: {}", legacyColumn, currentColumn, e.getMessage());
                }

                if (copyVerified) {
                    try {
                        jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP COLUMN `" + legacyColumn + "`");
                        logger.info("Successfully dropped legacy column '{}' from table '{}'.", legacyColumn, tableName);
                    } catch (Exception dropEx) {
                        logger.warn("Could not drop column '{}' from '{}', making it nullable with default NULL: {}",
                            legacyColumn, tableName, dropEx.getMessage());
                        jdbcTemplate.execute("ALTER TABLE `" + tableName + "` MODIFY COLUMN `" + legacyColumn + "` " + columnType + " NULL DEFAULT NULL");
                    }
                } else {
                    jdbcTemplate.execute("ALTER TABLE `" + tableName + "` MODIFY COLUMN `" + legacyColumn + "` " + columnType + " NULL DEFAULT NULL");
                    logger.warn("Preserved legacy column '{}' in table '{}' because migration copy could not be fully verified.", legacyColumn, tableName);
                }
            } else {
                // Only legacy column exists: make it nullable with default NULL so it does not block inserts
                jdbcTemplate.execute("ALTER TABLE `" + tableName + "` MODIFY COLUMN `" + legacyColumn + "` " + columnType + " NULL DEFAULT NULL");
                logger.info("Modified legacy column '{}' in table '{}' to be NULLABLE with DEFAULT NULL.", legacyColumn, tableName);
            }
        } catch (Exception ex) {
            logger.warn("Skipping legacy column cleanup for table '{}', column '{}': {}", tableName, legacyColumn, ex.getMessage());
        }
    }
}

