-- Read-only production diagnosis. Does not select comment text or user identities.
-- Run against the SAME datasource/database used by the deployed Spring app.
SELECT DATABASE() AS database_name, VERSION() AS mysql_version;

-- Confirm the mapped columns exist, and detect parallel legacy column names.
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'assessment_comments'
ORDER BY ordinal_position;

-- Locate records by assessment and author role; blank content is omitted by group views.
SELECT assessment_id, evaluatorType AS evaluator_type,
       COUNT(*) AS total_comments,
       SUM(commentText IS NULL OR CHAR_LENGTH(TRIM(commentText)) = 0) AS blank_comments,
       SUM(approval_status = 'APPROVED' OR is_approved = 1) AS approved_comments,
       MIN(submitted_at) AS first_submitted_at, MAX(submitted_at) AS last_submitted_at
FROM assessment_comments
GROUP BY assessment_id, evaluatorType
ORDER BY assessment_id, evaluatorType;

-- Compare assessment and recipient course/group links, without exposing identities.
SELECT ac.assessment_id, a.course_id AS assessment_course_id,
       s.course_id AS student_course_id, s.group_id,
       COUNT(*) AS total_comments,
       SUM(a.id IS NULL) AS missing_assessment,
       SUM(s.id IS NULL) AS missing_student
FROM assessment_comments ac
LEFT JOIN assessment a ON a.id = ac.assessment_id
LEFT JOIN student s ON s.id = ac.evaluated_student_id
GROUP BY ac.assessment_id, a.course_id, s.course_id, s.group_id
ORDER BY ac.assessment_id, s.group_id;
