-- Clean-slate reset script
-- Purpose: wipe all operational data while preserving rows in super_admin.
-- Run against the target database (e.g., aas_db).

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM `calculated_results`;
DELETE FROM `student_result_overrides`;
DELETE FROM `marks`;
DELETE FROM `assessment_comments`;
DELETE FROM `lecturer_rubric_assignments`;
DELETE FROM `lecturer_group_assignment`;
DELETE FROM `lecturer_student_assignment`;
DELETE FROM `student_assessment_assignment`;
DELETE FROM `admin_course_assignment`;
DELETE FROM `student_course_assignment`;
DELETE FROM `deadlines`;
DELETE FROM `rating`;
DELETE FROM `sub_rubric`;
DELETE FROM `rubric`;
DELETE FROM `assessment`;
DELETE FROM `student`;
DELETE FROM `project_group`;
DELETE FROM `rubric_templates`;
DELETE FROM `industrial_supervisor`;
DELETE FROM `admin`;
DELETE FROM `lecturer`;
DELETE FROM `courses`;

SET FOREIGN_KEY_CHECKS = 1;

-- Optional sanity check
SELECT COUNT(*) AS super_admin_count FROM `super_admin`;
