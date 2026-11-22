-- =====================================================================================================================
-- Move a person's IAMS account data from one email address to another (built for the super admin).
--
-- What it does: finds EVERY text column in the database that holds the old address exactly (account rows in
-- super_admin / admin / lecturer, and the "who did it" columns such as announcements.created_by, comment approvals,
-- evaluation deletions, factor overrides, ...) and changes those values to the new address. Ids are untouched, so everything
-- linked to the account (courses, assignments, marks) stays linked. The password hash is not changed.
-- It does NOT edit text that merely MENTIONS the address inside a longer value (for example a deletion snapshot): those are
-- listed for you to look at.
--
-- How to run it (MySQL 8, a session that keeps its variables between statements, such as the mysql client or the VS Code
-- MySQL extension run as ONE script):
--   0. Take a backup first:  mysqldump --single-transaction -h 127.0.0.1 -P <port> -u root -p <database> > backup.sql
--   1. Edit the two addresses just below, then run STEP 1. Read the results.
--   2. Run STEP 2: it returns one UPDATE statement per column that really holds the old address.
--   3. Run  START TRANSACTION;  paste and run those UPDATE statements;  run STEP 3 to check;  then COMMIT; (or ROLLBACK;).
--   4. Afterwards change BOOTSTRAP_SUPERADMIN_EMAIL (and ADPROJECT_ALERTS_RECIPIENT_EMAIL if it is the old address) in the
--      app's environment and redeploy. See the end of this file.
-- =====================================================================================================================

SET NAMES utf8mb4;
SET SESSION group_concat_max_len = 4000000;

-- ---- the only lines you edit ---------------------------------------------------------------------------------------
SET @old_email = 'ihsan.rubix@gmail.com';
SET @new_email = 'eduinnovax@gmail.com';
-- --------------------------------------------------------------------------------------------------------------------


-- =====================================================================================================================
-- STEP 1: look before changing anything (read-only)
-- =====================================================================================================================

-- 1a. Is the NEW address already used anywhere? It must come back empty: if it is on another account, merging two
--     accounts is a manual decision, not something to do with an UPDATE.
SET @sql = (
    SELECT GROUP_CONCAT(
        CONCAT('SELECT ''', c.TABLE_NAME, '.', c.COLUMN_NAME, ''' AS new_address_already_in, COUNT(*) AS rows_found FROM `',
               c.TABLE_NAME, '` WHERE `', c.COLUMN_NAME, '` = CONVERT(@new_email USING ', c.CHARACTER_SET_NAME,
               ') COLLATE ', c.COLLATION_NAME, ' HAVING COUNT(*) > 0')
        SEPARATOR ' UNION ALL ')
    FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_TYPE = 'BASE TABLE'
    WHERE c.TABLE_SCHEMA = DATABASE() AND c.DATA_TYPE IN ('char', 'varchar', 'tinytext', 'text', 'mediumtext', 'longtext')
);
SET @sql = IFNULL(@sql, 'SELECT ''(no text columns found)'' AS new_address_already_in, 0 AS rows_found');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1b. Where is the OLD address stored exactly? This is what the script will change.
SET @sql = (
    SELECT GROUP_CONCAT(
        CONCAT('SELECT ''', c.TABLE_NAME, '.', c.COLUMN_NAME, ''' AS old_address_in, COUNT(*) AS rows_to_change FROM `',
               c.TABLE_NAME, '` WHERE `', c.COLUMN_NAME, '` = CONVERT(@old_email USING ', c.CHARACTER_SET_NAME,
               ') COLLATE ', c.COLLATION_NAME, ' HAVING COUNT(*) > 0')
        SEPARATOR ' UNION ALL ')
    FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_TYPE = 'BASE TABLE'
    WHERE c.TABLE_SCHEMA = DATABASE() AND c.DATA_TYPE IN ('char', 'varchar', 'tinytext', 'text', 'mediumtext', 'longtext')
);
SET @sql = IFNULL(@sql, 'SELECT ''(no text columns found)'' AS old_address_in, 0 AS rows_to_change');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1c. Where is the old address only MENTIONED inside a longer value? These are NOT changed by this script.
SET @sql = (
    SELECT GROUP_CONCAT(
        CONCAT('SELECT ''', c.TABLE_NAME, '.', c.COLUMN_NAME, ''' AS old_address_mentioned_in, COUNT(*) AS rows_not_changed FROM `',
               c.TABLE_NAME, '` WHERE `', c.COLUMN_NAME, '` LIKE CONCAT(''%'', CONVERT(@old_email USING ', c.CHARACTER_SET_NAME,
               '), ''%'') COLLATE ', c.COLLATION_NAME, ' AND `', c.COLUMN_NAME, '` <> CONVERT(@old_email USING ', c.CHARACTER_SET_NAME,
               ') COLLATE ', c.COLLATION_NAME, ' HAVING COUNT(*) > 0')
        SEPARATOR ' UNION ALL ')
    FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_TYPE = 'BASE TABLE'
    WHERE c.TABLE_SCHEMA = DATABASE() AND c.DATA_TYPE IN ('char', 'varchar', 'tinytext', 'text', 'mediumtext', 'longtext')
);
SET @sql = IFNULL(@sql, 'SELECT ''(no text columns found)'' AS old_address_mentioned_in, 0 AS rows_not_changed');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- =====================================================================================================================
-- STEP 2: the UPDATE statements to run (one per column that really holds the old address; read-only until you run them)
-- =====================================================================================================================
SET @sql = (
    SELECT GROUP_CONCAT(
        CONCAT('(SELECT CONCAT(''UPDATE `', c.TABLE_NAME, '` SET `', c.COLUMN_NAME, '` = @new_email WHERE `', c.COLUMN_NAME,
               '` = CONVERT(@old_email USING ', c.CHARACTER_SET_NAME, ') COLLATE ', c.COLLATION_NAME, ';'') AS run_these_updates FROM `',
               c.TABLE_NAME, '` WHERE `', c.COLUMN_NAME, '` = CONVERT(@old_email USING ', c.CHARACTER_SET_NAME,
               ') COLLATE ', c.COLLATION_NAME, ' LIMIT 1)')
        SEPARATOR ' UNION ALL ')
    FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_TYPE = 'BASE TABLE'
    WHERE c.TABLE_SCHEMA = DATABASE() AND c.DATA_TYPE IN ('char', 'varchar', 'tinytext', 'text', 'mediumtext', 'longtext')
);
SET @sql = IFNULL(@sql, 'SELECT ''(nothing to change)'' AS run_these_updates');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Now:  START TRANSACTION;  then run every statement STEP 2 returned (they use @old_email and @new_email), then STEP 3.


-- =====================================================================================================================
-- STEP 3: check, before COMMIT (read-only). The first result must be EMPTY; the second shows where the new address now is.
-- =====================================================================================================================
SET @sql = (
    SELECT GROUP_CONCAT(
        CONCAT('SELECT ''', c.TABLE_NAME, '.', c.COLUMN_NAME, ''' AS old_address_still_in, COUNT(*) AS rows_found FROM `',
               c.TABLE_NAME, '` WHERE `', c.COLUMN_NAME, '` = CONVERT(@old_email USING ', c.CHARACTER_SET_NAME,
               ') COLLATE ', c.COLLATION_NAME, ' HAVING COUNT(*) > 0')
        SEPARATOR ' UNION ALL ')
    FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_TYPE = 'BASE TABLE'
    WHERE c.TABLE_SCHEMA = DATABASE() AND c.DATA_TYPE IN ('char', 'varchar', 'tinytext', 'text', 'mediumtext', 'longtext')
);
SET @sql = IFNULL(@sql, 'SELECT ''(no text columns found)'' AS old_address_still_in, 0 AS rows_found');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (
    SELECT GROUP_CONCAT(
        CONCAT('SELECT ''', c.TABLE_NAME, '.', c.COLUMN_NAME, ''' AS new_address_now_in, COUNT(*) AS rows_found FROM `',
               c.TABLE_NAME, '` WHERE `', c.COLUMN_NAME, '` = CONVERT(@new_email USING ', c.CHARACTER_SET_NAME,
               ') COLLATE ', c.COLLATION_NAME, ' HAVING COUNT(*) > 0')
        SEPARATOR ' UNION ALL ')
    FROM information_schema.COLUMNS c
    JOIN information_schema.TABLES t ON t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME AND t.TABLE_TYPE = 'BASE TABLE'
    WHERE c.TABLE_SCHEMA = DATABASE() AND c.DATA_TYPE IN ('char', 'varchar', 'tinytext', 'text', 'mediumtext', 'longtext')
);
SET @sql = IFNULL(@sql, 'SELECT ''(no text columns found)'' AS new_address_now_in, 0 AS rows_found');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- COMMIT;     -- when the first result above is empty and the second looks right
-- ROLLBACK;   -- to undo everything you ran since START TRANSACTION


-- =====================================================================================================================
-- AFTER the commit (outside the database)
-- =====================================================================================================================
--  * BOOTSTRAP_SUPERADMIN_EMAIL: set it to the new address (or remove it) in the app's environment (Dokploy) and redeploy.
--  * ADPROJECT_ALERTS_RECIPIENT_EMAIL: if it was the old address, change it too.
--  * Sign in once with the new address (password or Sign in with Google) and check the super admin pages.
--  * Anyone signed in as the old address is signed out at their next request, and "remember me" cookies stop working.
