package com.capstone.adproject.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        migrateDuplicateLecturers();
        migrateTable("student", "uk_student_email_course");
        migrateTable("lecturer", "uk_lecturer_email_course");
        migrateUsernameTable("student", "uk_student_username_course");
        migrateUsernameTable("lecturer", "uk_lecturer_username_course");
        migrateStudentResultOverrides();
        migrateProjectGroupColumns();
        migrateMarksColumns();
        migrateAssessmentCommentColumns();
        migrateLegacyDualColumns();
    }

    public void migrateDuplicateLecturers() {
        try {
            if (!hasTable("lecturer")) {
                return;
            }

            // 1. Find all emails with duplicate lecturer rows
            List<String> duplicateEmails = jdbcTemplate.queryForList(
                    """
                    SELECT LOWER(TRIM(email))
                    FROM `lecturer`
                    WHERE email IS NOT NULL AND TRIM(email) <> ''
                    GROUP BY LOWER(TRIM(email))
                    HAVING COUNT(*) > 1
                    """,
                    String.class);

            if (duplicateEmails != null && !duplicateEmails.isEmpty()) {
                logger.info("Found {} duplicate assessor email group(s) to consolidate.", duplicateEmails.size());
                for (String email : duplicateEmails) {
                    consolidateDuplicateLecturersForEmail(email);
                }
            } else {
                logger.info("No duplicate assessor emails found in lecturer table.");
            }

            // 2. Disambiguate duplicate usernames before unique index is enforced
            migrateDuplicateLecturerUsernames();

        } catch (Exception ex) {
            logger.warn("Skipping duplicate lecturer consolidation migration: {}", ex.getMessage());
        }
    }

    private void migrateDuplicateLecturerUsernames() {
        try {
            List<String> duplicateUsernames = jdbcTemplate.queryForList(
                    """
                    SELECT LOWER(TRIM(username))
                    FROM `lecturer`
                    WHERE username IS NOT NULL AND TRIM(username) <> ''
                    GROUP BY LOWER(TRIM(username))
                    HAVING COUNT(*) > 1
                    """,
                    String.class);

            if (duplicateUsernames == null || duplicateUsernames.isEmpty()) {
                return;
            }

            for (String uname : duplicateUsernames) {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT id, username, email FROM `lecturer` WHERE LOWER(TRIM(username)) = ? ORDER BY id ASC",
                        uname);
                for (int i = 1; i < rows.size(); i++) {
                    Long id = ((Number) rows.get(i).get("id")).longValue();
                    String newUname = uname + "_" + id;
                    jdbcTemplate.update("UPDATE `lecturer` SET username = ? WHERE id = ?", newUname, id);
                    logger.info("Disambiguated duplicate lecturer username for ID {} to '{}'", id, newUname);
                }
            }
        } catch (Exception ex) {
            logger.warn("Skipping duplicate lecturer username migration: {}", ex.getMessage());
        }
    }

    private void consolidateDuplicateLecturersForEmail(String normalizedEmail) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM `lecturer` WHERE LOWER(TRIM(email)) = ? ORDER BY id ASC",
                    normalizedEmail);

            if (rows.size() <= 1) {
                return;
            }

            Map<String, Object> primaryRow = selectPrimaryLecturerRow(rows);
            Long primaryId = ((Number) primaryRow.get("id")).longValue();

            List<Long> secondaryIds = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Long id = ((Number) row.get("id")).longValue();
                if (!id.equals(primaryId)) {
                    secondaryIds.add(id);
                }
            }

            logger.info("Consolidating duplicate assessors for email '{}': keeping primary ID {}, merging secondary IDs {}",
                    normalizedEmail, primaryId, secondaryIds);

            mergeLecturerAttributes(primaryRow, rows);

            for (Long secId : secondaryIds) {
                repointLecturerReferences(secId, primaryId);
            }

            for (Long secId : secondaryIds) {
                try {
                    jdbcTemplate.update("DELETE FROM `lecturer` WHERE id = ?", secId);
                    logger.info("Deleted consolidated secondary lecturer ID {}", secId);
                } catch (Exception ex) {
                    logger.warn("Could not delete secondary lecturer ID {}: {}", secId, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            logger.warn("Error consolidating duplicate lecturers for email '{}': {}", normalizedEmail, ex.getMessage());
        }
    }

    private Map<String, Object> selectPrimaryLecturerRow(List<Map<String, Object>> rows) {
        Map<String, Object> bestRow = rows.get(0);
        int bestScore = -1;

        for (Map<String, Object> row : rows) {
            int score = scoreLecturerRow(row);
            if (score > bestScore) {
                bestScore = score;
                bestRow = row;
            }
        }
        return bestRow;
    }

    private int scoreLecturerRow(Map<String, Object> row) {
        int score = 0;
        Long id = ((Number) row.get("id")).longValue();

        Object pwdObj = row.get("password");
        if (pwdObj != null) {
            String pwd = pwdObj.toString();
            if (pwd.startsWith("$2a$") || pwd.startsWith("$2b$") || pwd.startsWith("$2y$") || pwd.length() >= 20) {
                score += 100;
            } else if (!pwd.isBlank()) {
                score += 20;
            }
        }

        Object rolesObj = row.get("roles");
        if (rolesObj != null) {
            String roles = rolesObj.toString();
            if (roles.contains("ROLE_ADMIN")) {
                score += 50;
            }
            if (roles.contains("ROLE_LECTURER")) {
                score += 20;
            }
        }

        if (row.get("course_id") != null) {
            score += 30;
        }

        if (hasTable("marks") && hasColumn("marks", "lecturer_id")) {
            try {
                Integer markCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM `marks` WHERE lecturer_id = ?",
                        Integer.class, id);
                if (markCount != null && markCount > 0) {
                    score += 200 + markCount;
                }
            } catch (Exception ignored) {}
        }

        if (hasTable("project_group")) {
            try {
                Integer groupCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM `project_group` WHERE academic_supervisor_id = ? OR industrial_supervisor_id = ?",
                        Integer.class, id, id);
                if (groupCount != null && groupCount > 0) {
                    score += 150 + groupCount;
                }
            } catch (Exception ignored) {}
        }

        return score;
    }

    private void mergeLecturerAttributes(Map<String, Object> primaryRow, List<Map<String, Object>> allRows) {
        Long primaryId = ((Number) primaryRow.get("id")).longValue();
        String primaryPwd = primaryRow.get("password") != null ? primaryRow.get("password").toString() : "";
        boolean primaryHasHashed = primaryPwd.startsWith("$2a$") || primaryPwd.startsWith("$2b$") || primaryPwd.startsWith("$2y$") || primaryPwd.length() >= 20;

        String primaryRoles = primaryRow.get("roles") != null ? primaryRow.get("roles").toString() : "";
        Set<String> rolesSet = new LinkedHashSet<>();
        if (!primaryRoles.isBlank()) {
            for (String r : primaryRoles.split(",")) {
                if (!r.trim().isEmpty()) rolesSet.add(r.trim());
            }
        }

        String bestPassword = primaryPwd;
        boolean updatePassword = false;

        for (Map<String, Object> row : allRows) {
            Object pwdObj = row.get("password");
            if (pwdObj != null) {
                String pwd = pwdObj.toString();
                if (!primaryHasHashed && (pwd.startsWith("$2a$") || pwd.startsWith("$2b$") || pwd.startsWith("$2y$") || pwd.length() >= 20)) {
                    bestPassword = pwd;
                    updatePassword = true;
                    primaryHasHashed = true;
                }
            }
            Object rolesObj = row.get("roles");
            if (rolesObj != null) {
                for (String r : rolesObj.toString().split(",")) {
                    if (!r.trim().isEmpty()) rolesSet.add(r.trim());
                }
            }
        }

        String mergedRoles = String.join(",", rolesSet);
        if (updatePassword || !mergedRoles.equals(primaryRoles)) {
            try {
                jdbcTemplate.update("UPDATE `lecturer` SET password = ?, roles = ? WHERE id = ?",
                        bestPassword, mergedRoles, primaryId);
                logger.info("Merged credentials and roles for primary lecturer ID {}", primaryId);
            } catch (Exception ex) {
                logger.warn("Could not merge attributes to primary lecturer ID {}: {}", primaryId, ex.getMessage());
            }
        }
    }

    private void repointLecturerReferences(Long secId, Long primaryId) {
        if (hasTable("marks") && hasColumn("marks", "lecturer_id")) {
            try {
                jdbcTemplate.update("UPDATE `marks` SET lecturer_id = ? WHERE lecturer_id = ?", primaryId, secId);
            } catch (Exception ex) {
                logger.warn("Repoint marks failed for lecturer {}: {}", secId, ex.getMessage());
            }
        }

        if (hasTable("assessment_comments") && hasColumn("assessment_comments", "evaluator_id")) {
            try {
                jdbcTemplate.update(
                        "UPDATE `assessment_comments` SET evaluator_id = ? WHERE evaluator_id = ? AND `evaluatorType` IN ('LECTURER', 'SUPERVISOR')",
                        primaryId, secId);
            } catch (Exception ex) {
                logger.warn("Repoint comments failed for lecturer {}: {}", secId, ex.getMessage());
            }
        }

        if (hasTable("project_group")) {
            if (hasColumn("project_group", "academic_supervisor_id")) {
                try {
                    jdbcTemplate.update("UPDATE `project_group` SET academic_supervisor_id = ? WHERE academic_supervisor_id = ?",
                            primaryId, secId);
                } catch (Exception ex) {
                    logger.warn("Repoint academic_supervisor_id failed for lecturer {}: {}", secId, ex.getMessage());
                }
            }
            if (hasColumn("project_group", "industrial_supervisor_id")) {
                try {
                    jdbcTemplate.update("UPDATE `project_group` SET industrial_supervisor_id = ? WHERE industrial_supervisor_id = ?",
                            primaryId, secId);
                } catch (Exception ex) {
                    logger.warn("Repoint industrial_supervisor_id failed for lecturer {}: {}", secId, ex.getMessage());
                }
            }
        }

        if (hasTable("courses")) {
            if (hasColumn("courses", "created_by_lecturer_id")) {
                try {
                    jdbcTemplate.update("UPDATE `courses` SET created_by_lecturer_id = ? WHERE created_by_lecturer_id = ?",
                            primaryId, secId);
                } catch (Exception ignored) {}
            }
            if (hasColumn("courses", "created_by_id")) {
                try {
                    jdbcTemplate.update("UPDATE `courses` SET created_by_id = ? WHERE created_by_id = ?",
                            primaryId, secId);
                } catch (Exception ignored) {}
            }
        }

        if (hasTable("lecturer_student_assignment") && hasColumn("lecturer_student_assignment", "lecturer_id")) {
            try {
                List<Long> duplicateIds = jdbcTemplate.queryForList(
                        """
                        SELECT sec.id
                        FROM `lecturer_student_assignment` sec
                        JOIN `lecturer_student_assignment` prim
                          ON prim.assessment_id = sec.assessment_id
                         AND prim.student_id = sec.student_id
                         AND prim.lecturer_id = ?
                        WHERE sec.lecturer_id = ?
                        """,
                        Long.class, primaryId, secId);

                for (Long dupId : duplicateIds) {
                    jdbcTemplate.update("DELETE FROM `lecturer_student_assignment` WHERE id = ?", dupId);
                }

                jdbcTemplate.update("UPDATE `lecturer_student_assignment` SET lecturer_id = ? WHERE lecturer_id = ?",
                        primaryId, secId);
            } catch (Exception ex) {
                logger.warn("Repoint lecturer_student_assignment failed for lecturer {}: {}", secId, ex.getMessage());
            }
        }

        if (hasTable("lecturer_rubric_assignments") && hasColumn("lecturer_rubric_assignments", "lecturer_id")) {
            try {
                List<Long> duplicateIds = jdbcTemplate.queryForList(
                        """
                        SELECT sec.id
                        FROM `lecturer_rubric_assignments` sec
                        JOIN `lecturer_rubric_assignments` prim
                          ON prim.assessment_id = sec.assessment_id
                         AND prim.rubric_id = sec.rubric_id
                         AND prim.lecturer_id = ?
                        WHERE sec.lecturer_id = ?
                        """,
                        Long.class, primaryId, secId);

                for (Long dupId : duplicateIds) {
                    jdbcTemplate.update("DELETE FROM `lecturer_rubric_assignments` WHERE id = ?", dupId);
                }

                jdbcTemplate.update("UPDATE `lecturer_rubric_assignments` SET lecturer_id = ? WHERE lecturer_id = ?",
                        primaryId, secId);
            } catch (Exception ex) {
                logger.warn("Repoint lecturer_rubric_assignments failed for lecturer {}: {}", secId, ex.getMessage());
            }
        }

        if (hasTable("lecturer_group_assignment") && hasColumn("lecturer_group_assignment", "lecturer_id")) {
            try {
                List<Long> duplicateIds = jdbcTemplate.queryForList(
                        """
                        SELECT sec.id
                        FROM `lecturer_group_assignment` sec
                        JOIN `lecturer_group_assignment` prim
                          ON prim.assessment_id = sec.assessment_id
                         AND prim.group_id = sec.group_id
                         AND prim.lecturer_id = ?
                        WHERE sec.lecturer_id = ?
                        """,
                        Long.class, primaryId, secId);

                for (Long dupId : duplicateIds) {
                    jdbcTemplate.update("DELETE FROM `lecturer_group_assignment` WHERE id = ?", dupId);
                }

                jdbcTemplate.update("UPDATE `lecturer_group_assignment` SET lecturer_id = ? WHERE lecturer_id = ?",
                        primaryId, secId);
            } catch (Exception ex) {
                logger.warn("Repoint lecturer_group_assignment failed for lecturer {}: {}", secId, ex.getMessage());
            }
        }

        if (hasTable("admin_course_assignment") && hasColumn("admin_course_assignment", "lecturer_id")) {
            try {
                List<Long> duplicateIds = jdbcTemplate.queryForList(
                        """
                        SELECT sec.id
                        FROM `admin_course_assignment` sec
                        JOIN `admin_course_assignment` prim
                          ON prim.course_id = sec.course_id
                         AND prim.lecturer_id = ?
                        WHERE sec.lecturer_id = ?
                        """,
                        Long.class, primaryId, secId);

                for (Long dupId : duplicateIds) {
                    jdbcTemplate.update("DELETE FROM `admin_course_assignment` WHERE id = ?", dupId);
                }

                jdbcTemplate.update("UPDATE `admin_course_assignment` SET lecturer_id = ? WHERE lecturer_id = ?",
                        primaryId, secId);
            } catch (Exception ex) {
                logger.warn("Repoint admin_course_assignment failed for lecturer {}: {}", secId, ex.getMessage());
            }
        }

        try {
            Long secCourseId = jdbcTemplate.queryForObject(
                    "SELECT course_id FROM `lecturer` WHERE id = ?",
                    Long.class, secId);
            if (secCourseId != null && hasTable("admin_course_assignment")) {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM `admin_course_assignment` WHERE lecturer_id = ? AND course_id = ?",
                        Integer.class, primaryId, secCourseId);
                if (count == null || count == 0) {
                    Long primaryCourseId = jdbcTemplate.queryForObject(
                            "SELECT course_id FROM `lecturer` WHERE id = ?",
                            Long.class, primaryId);
                    if (primaryCourseId == null || !primaryCourseId.equals(secCourseId)) {
                        jdbcTemplate.update(
                                "INSERT INTO `admin_course_assignment` (lecturer_id, course_id) VALUES (?, ?)",
                                primaryId, secCourseId);
                        logger.info("Preserved multi-course access for primary lecturer ID {} to course ID {}", primaryId, secCourseId);
                    }
                }
            }
        } catch (Exception ignored) {}

        if (hasTable("submission_annotation") && hasColumn("submission_annotation", "lecturer_id")) {
            try {
                jdbcTemplate.update("UPDATE `submission_annotation` SET lecturer_id = ? WHERE lecturer_id = ?",
                        primaryId, secId);
            } catch (Exception ignored) {}
        }
    }

    private boolean hasTable(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class,
                    tableName);
            return count != null && count > 0;
        } catch (Exception ex) {
            try {
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `" + tableName + "` WHERE 1=0", Integer.class);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private void migrateProjectGroupColumns() {
        try {
            List<String> sizeColumns = List.of("group_size", "groupsize", "groupSize");
            for (String col : sizeColumns) {
                if (hasColumn("project_group", col)) {
                    try {
                        jdbcTemplate.execute("ALTER TABLE `project_group` ALTER COLUMN `" + col + "` SET DEFAULT 0");
                    } catch (Exception ex) {
                        try {
                            jdbcTemplate.execute("ALTER TABLE `project_group` MODIFY COLUMN `" + col + "` INT DEFAULT 0");
                        } catch (Exception ignored) {}
                    }
                }
            }

            List<String> nameColumns = List.of("group_name", "groupname", "groupName");
            for (String col : nameColumns) {
                if (hasColumn("project_group", col)) {
                    try {
                        jdbcTemplate.execute("ALTER TABLE `project_group` ALTER COLUMN `" + col + "` SET DEFAULT NULL");
                    } catch (Exception ex) {
                        try {
                            jdbcTemplate.execute("ALTER TABLE `project_group` MODIFY COLUMN `" + col + "` VARCHAR(255) DEFAULT NULL");
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (hasColumn("project_group", "group_size") && hasColumn("project_group", "groupsize")) {
                try {
                    jdbcTemplate.execute("UPDATE `project_group` SET `group_size` = `groupsize` WHERE (`group_size` IS NULL OR `group_size` = 0) AND `groupsize` > 0");
                    jdbcTemplate.execute("UPDATE `project_group` SET `groupsize` = `group_size` WHERE (`groupsize` IS NULL OR `groupsize` = 0) AND `group_size` > 0");
                } catch (Exception ignored) {}
            }

            if (hasColumn("project_group", "group_name") && hasColumn("project_group", "groupname")) {
                try {
                    jdbcTemplate.execute("UPDATE `project_group` SET `group_name` = `groupname` WHERE `group_name` IS NULL AND `groupname` IS NOT NULL");
                    jdbcTemplate.execute("UPDATE `project_group` SET `groupname` = `group_name` WHERE `groupname` IS NULL AND `group_name` IS NOT NULL");
                } catch (Exception ignored) {}
            }
            logger.info("Migrated project_group columns: synchronized group_size/groupsize and group_name/groupname defaults");
        } catch (Exception ex) {
            logger.warn("Skipping project_group columns migration: {}", ex.getMessage());
        }
    }

    private void migrateLegacyDualColumns() {
        syncNullableDualColumn("assessment_comments", "commentText", "comment_text", true);
        syncNullableDualColumn("assessment_comments", "evaluatorType", "evaluator_type", true);
        syncNullableDualColumn("assessment_comments", "assessmentType", "assessment_type", true);
        syncNullableDualColumn("deadlines", "assessmentId", "assessment_id", false);
        syncNullableDualColumn("deadlines", "assessorType", "assessor_type", true);
        syncNullableDualColumn("deadlines", "courseId", "course_id", false);
        syncNullableDualColumn("deadlines", "openDate", "open_date", false);
        syncNullableDualColumn("factor_override_history", "changedAt", "changed_at", false);
        syncNullableDualColumn("factor_override_history", "changedBy", "changed_by", true);
        syncNullableDualColumn("factor_override_history", "newFactor", "new_factor", false);
        syncNullableDualColumn("factor_override_history", "oldFactor", "old_factor", false);
        syncNullableDualColumn("factor_override_history", "newGrandTotal", "new_grand_total", false);
        syncNullableDualColumn("factor_override_history", "oldGrandTotal", "old_grand_total", false);
        syncNullableDualColumn("factor_override_history", "course_id", "courseId", false);
        syncNullableDualColumn("student_result_overrides", "overriddenFactor", "overridden_factor", false);
        syncNullableDualColumn("student_result_overrides", "overriddenGrandTotal", "overridden_grand_total", false);
        syncNullableDualColumn("student_result_overrides", "course_id", "courseId", false);
        syncNullableDualColumn("rubric", "cloMarks", "clo_marks", false);
        syncNullableDualColumn("rubric", "assessmentTypes", "assessment_type", true);
        syncNullableDualColumn("student", "resetPasswordToken", "reset_password_token", true);
        syncNullableDualColumn("student", "isTempPassword", "is_temp_password", false);
        syncNullableDualColumn("lecturer", "resetPasswordToken", "reset_password_token", true);
        syncNullableDualColumn("lecturer", "isTempPassword", "is_temp_password", false);
        syncNullableDualColumn("admin", "resetPasswordToken", "reset_password_token", true);
        syncNullableDualColumn("super_admin", "resetPasswordToken", "reset_password_token", true);
    }

    private void syncNullableDualColumn(String tableName, String canonicalColumn,
                                        String legacyColumn, boolean textColumn) {
        if (!hasTable(tableName) || !hasColumn(tableName, canonicalColumn)
                || !hasColumn(tableName, legacyColumn)) {
            return;
        }
        try {
            if (textColumn) {
                jdbcTemplate.execute(
                    "UPDATE `" + tableName + "` SET `" + canonicalColumn + "` = `" + legacyColumn + "` "
                    + "WHERE (`" + canonicalColumn + "` IS NULL "
                    + "OR TRIM(`" + canonicalColumn + "`) = '') "
                    + "AND `" + legacyColumn + "` IS NOT NULL "
                    + "AND TRIM(`" + legacyColumn + "`) <> ''");
                jdbcTemplate.execute(
                    "UPDATE `" + tableName + "` SET `" + legacyColumn + "` = `" + canonicalColumn + "` "
                    + "WHERE (`" + legacyColumn + "` IS NULL "
                    + "OR TRIM(`" + legacyColumn + "`) = '') "
                    + "AND `" + canonicalColumn + "` IS NOT NULL "
                    + "AND TRIM(`" + canonicalColumn + "`) <> ''");
            } else {
                jdbcTemplate.execute(
                    "UPDATE `" + tableName + "` SET `" + canonicalColumn + "` = `" + legacyColumn + "` "
                    + "WHERE `" + canonicalColumn + "` IS NULL "
                    + "AND `" + legacyColumn + "` IS NOT NULL");
                jdbcTemplate.execute(
                    "UPDATE `" + tableName + "` SET `" + legacyColumn + "` = `" + canonicalColumn + "` "
                    + "WHERE `" + legacyColumn + "` IS NULL "
                    + "AND `" + canonicalColumn + "` IS NOT NULL");
            }
        } catch (Exception ex) {
            logger.warn("Could not synchronize legacy dual columns {}.{} / {}.{}: {}",
                tableName, canonicalColumn, tableName, legacyColumn, ex.getMessage());
        }
    }

    private void migrateAssessmentCommentColumns() {
        try {
            if (!hasTable("assessment_comments")) {
                return;
            }
            for (String column : List.of("commentText", "comment_text")) {
                if (!hasColumn("assessment_comments", column)) {
                    continue;
                }
                try {
                    jdbcTemplate.execute("ALTER TABLE `assessment_comments` MODIFY COLUMN `" + column + "` TEXT NULL");
                } catch (Exception ignored) {}
            }
            if (hasColumn("assessment_comments", "commentText")
                    && hasColumn("assessment_comments", "comment_text")) {
                jdbcTemplate.execute("UPDATE `assessment_comments` SET `comment_text` = `commentText` "
                        + "WHERE `comment_text` IS NULL AND `commentText` IS NOT NULL");
                jdbcTemplate.execute("UPDATE `assessment_comments` SET `commentText` = `comment_text` "
                        + "WHERE `commentText` IS NULL AND `comment_text` IS NOT NULL");
            }
            logger.info("Migrated assessment comment text columns");
        } catch (Exception ex) {
            logger.warn("Skipping assessment comment text column migration: {}", ex.getMessage());
        }
    }

    private void migrateMarksColumns() {
        try {
            if (!hasTable("marks")) {
                return;
            }

            List<String> markColumns = List.of("cloMarks", "clomarks", "clo_marks");
            for (String column : markColumns) {
                if (!hasColumn("marks", column)) {
                    continue;
                }
                try {
                    jdbcTemplate.execute("ALTER TABLE `marks` ALTER COLUMN `" + column + "` SET DEFAULT 0");
                } catch (Exception ex) {
                    try {
                        jdbcTemplate.execute("ALTER TABLE `marks` MODIFY COLUMN `" + column + "` DOUBLE NOT NULL DEFAULT 0");
                    } catch (Exception ignored) {}
                }
            }

            if (hasColumn("marks", "cloMarks") && hasColumn("marks", "clomarks")) {
                jdbcTemplate.execute("UPDATE `marks` SET `cloMarks` = `clomarks` WHERE `cloMarks` IS NULL");
                jdbcTemplate.execute("UPDATE `marks` SET `clomarks` = `cloMarks` WHERE `clomarks` IS NULL");
            }
            if (hasColumn("marks", "clomarks") && hasColumn("marks", "clo_marks")) {
                jdbcTemplate.execute("UPDATE `marks` SET `clomarks` = `clo_marks` WHERE `clomarks` IS NULL");
                jdbcTemplate.execute("UPDATE `marks` SET `clo_marks` = `clomarks` WHERE `clo_marks` IS NULL");
            }
            if (hasColumn("marks", "cloMarks") && hasColumn("marks", "clo_marks")) {
                jdbcTemplate.execute("UPDATE `marks` SET `cloMarks` = `clo_marks` WHERE `cloMarks` IS NULL");
                jdbcTemplate.execute("UPDATE `marks` SET `clo_marks` = `cloMarks` WHERE `clo_marks` IS NULL");
            }

            List<String> markValueColumns = List.of("markValue", "markvalue", "mark_value");
            for (String column : markValueColumns) {
                if (!hasColumn("marks", column)) {
                    continue;
                }
                try {
                    jdbcTemplate.execute("ALTER TABLE `marks` ALTER COLUMN `" + column + "` SET DEFAULT 0");
                } catch (Exception ex) {
                    try {
                        jdbcTemplate.execute("ALTER TABLE `marks` MODIFY COLUMN `" + column + "` DOUBLE NOT NULL DEFAULT 0");
                    } catch (Exception ignored) {}
                }
            }

            if (hasColumn("marks", "markValue") && hasColumn("marks", "mark_value")) {
                jdbcTemplate.execute("UPDATE `marks` SET `markValue` = `mark_value` WHERE `markValue` IS NULL");
                jdbcTemplate.execute("UPDATE `marks` SET `mark_value` = `markValue` WHERE `mark_value` IS NULL");
            }
            if (hasColumn("marks", "markvalue") && hasColumn("marks", "mark_value")) {
                jdbcTemplate.execute("UPDATE `marks` SET `markvalue` = `mark_value` WHERE `markvalue` IS NULL");
                jdbcTemplate.execute("UPDATE `marks` SET `mark_value` = `markvalue` WHERE `mark_value` IS NULL");
            }
            logger.info("Migrated marks CLO columns and defaults");
        } catch (Exception ex) {
            logger.warn("Skipping marks CLO column migration: {}", ex.getMessage());
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() "
                    + "AND table_name = ? "
                    + "AND column_name = ?",
                    Integer.class,
                    tableName,
                    columnName);
            if (count != null && count > 0) {
                return true;
            }
        } catch (Exception ignored) {}
        try {
            jdbcTemplate.queryForObject("SELECT `" + columnName + "` FROM `" + tableName + "` WHERE 1=0", Object.class);
            return true;
        } catch (Exception ignored) {
            return false;
        }
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
