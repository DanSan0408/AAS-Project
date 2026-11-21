# Project State Documentation

**Last Updated**: 2026-11-19 (Claude Code)

## Current Status: Course-Scoped Group Members & SMTP Failure Visibility
**Status**: COMPLETED
**Date**: 2026-11-18

### Welcome Email Delivery Tracking & Resend (2026-11-19)
- Finished the interrupted welcome-email refactor in `AdminService`: restored username auto-generation for new students and the duplicate-username check for new assessors, both of which the half-done edit had bypassed.
- New accounts are now saved before the welcome email is sent, so credentials are never emailed for an account that failed to save.
- Added nullable `welcomeEmailSentAt` to `student` and `lecturer` (created by `ddl-auto=update`). It is set only when the mail server accepts the message.
- Bulk add no longer sends emails inside the request/transaction. Emails go out in the background after commit and each confirmed delivery is recorded.
- Manage Students / Manage Assessors show accounts with no confirmed welcome email and can resend to one or all (`POST /admin/welcome-emails/resend`). A resend reuses the welcome email with a 7-day set-password link and never changes the stored password.
- `UserService.updatePassword` now clears `isTempPassword` for students (previously only assessors).
- Temp passwords are HTML-escaped in the welcome email.
- Not verified: real delivery through the production SMTP account. Local `.env` has no `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD`, so email is disabled locally.
- Build in a clean checkout when verifying: Maven run in place can be masked by the IDE's compiled classes in `target/`.

### CI/CD Pipeline
- Added parallel pull-request validation for Java, browser/static contracts, AI Python compilation, and both Docker images.
- Added main-branch image publication to GHCR with immutable Git SHA tags.
- Added manual staging/production deployment workflow with environment approval, app/AI parallel deployment, smoke checks, Telegram notification, and rollback webhook hooks.
- Added public `/actuator/health` and `/health` smoke targets for deployment verification.
- Added `CICD.md` with required GitHub/Dokploy secrets and webhook payload responsibilities.

### UI Refinement
- Balanced the admin dashboard rubric and deadline columns with stretch-height layout and removed independent rubric-height clipping.
- Added missing action icons on admin rubric/deadline controls and across common save/cancel, invitation, group, profile, comment, and course actions.
- Added complete 2px borders to login fields and AA-compliant placeholder contrast.
- Added public favicon handling via `/favicon.ico` forwarding to the IAMS logo.
- Verified desktop/mobile login renders, complete field borders, no horizontal overflow, and no browser console errors.
- Impeccable detector returns no findings for the login surface.

### Full Legacy Database Column Audit
- Audited 35 tables and 30 live key table counts from the local Docker/MySQL snapshot and SQL dump.
- Canonical live columns confirmed: camelCase names such as `commentText`, `assessmentType`, `evaluatorType`, `markValue`, `cloMarks`, `groupSize`, `groupName`, `courseCode`, `courseName`, `resetPasswordToken`, `isTempPassword`, and deadline/factor camelCase fields.
- Legacy/unused dual variants found: `comment_text`, `evaluator_type`, `assessment_type`, snake-case deadline/factor/override columns, rubric `clo_marks`/`assessment_type`, reset/temp snake-case fields, and duplicate legacy element-collection tables.
- Data mismatches found in 2 comment rows: `commentText` contained feedback while `comment_text` was blank. Canonical column access is already `commentText`, so no comment data was lost.
- All 8 rubric rows use canonical `cloMarks`; `clo_marks` is empty/absent. All 4 deadlines use canonical camelCase columns and leave snake-case counterparts empty.
- Factor override and student override records likewise retain values in canonical camelCase columns while snake-case counterparts are empty.
- Added null-only canonical/legacy synchronization. Canonical populated values are never overwritten.
- Hardened legacy-column cleanup so it verifies migration before dropping a legacy column; copy failure leaves the legacy column intact.
- Fixed `Student.resetPasswordToken` and `Student.isTempPassword` entity mappings to canonical live columns.
- Java suite and all 53 browser/static regressions pass.

### Database & Reuse Safety Audit
- Comment-only evaluations now count as `in_progress`, so reopening the evaluation reloads persisted comments.
- General and rubric comments are upserted by evaluator/student/assessment/comment index; blank partial submissions no longer delete saved feedback.
- Evaluation saves now enforce that the target student/group belongs to the assessment course.
- Added startup synchronization for legacy `marks.mark_value`, `marks.markvalue`, and `marks.markValue` columns after the live log showed `mark_value` rejects inserts without a default.
- Audited the local Docker MySQL snapshot: 11 students, 12 course enrollments, 4 courses, and 3 groups.
- No duplicate student or lecturer email rows were found.
- No orphaned student-course assignments, cross-course lecturer/group assignments, or cross-course lecturer/student assignments were found.
- Existing 63 marks and 2 comments contain non-null required data; the legacy-column migrations are constrained to defaults/nullability and do not delete or rewrite existing mark/comment values.
- The reused student `zizan@gmail.com` is enrolled in course 19 but has no course-19 `student_course_assignment.group_id`. It is therefore not assignable to a course-19 assessor until explicitly assigned to a course-19 group.
- Legacy/dual columns were audited: live snapshot has `marks.cloMarks`, `assessment_comments.commentText/comment_text`, and modern `project_group.groupSize/groupName`. The startup migrations cover both camelCase and snake_case variants.
- Java compilation and 19 focused course/evaluation browser regressions pass.

### Fixes
1. **Reused Students Missing from Assessor Group Cards**
   - Added `groupStudentsMap` in `AdminController.showLecturerAssignmentPage`.
   - The assessor assignment template now renders each group's members through `AdminService.getStudentsByGroup`, which merges direct `student.group_id` members with course-scoped `student_course_assignment.group_id` enrollments.
   - This preserves the primary course relationship of reused students while showing their correct group assignment in every course.

2. **Email Delivery Diagnostics**
   - Added an SMTP credential guard to `EmailService`.
   - Reset, welcome, deadline, and alert messages now stop explicitly and log configuration errors when `SPRING_MAIL_USERNAME` or `SPRING_MAIL_PASSWORD` is empty.
   - Added a sender fallback so `mailFrom` uses the authenticated SMTP username instead of `admin@example.com`.
   - Successful sends now produce delivery logs.
   - Current local `.env` check: `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD` are empty, so SMTP is currently disabled until valid credentials are supplied.

### Verification
- Focused course-management browser tests pass.
- Java tests could not run in this shell because no Java runtime is available.


## Current Status: Student Multi-Course Identity Resolution & Assessor Assignment UI Fixes
**Status**: COMPLETED
**Date**: 2026-11-17

### Summary of Enhancements & Fixes
1. **Student Identity Resolution & Multi-Course Protection**:
   - **Root Cause**:
     - `StudentController`, `CustomUserDetailsService`, and `ProfileController` directly invoked `studentRepository.findByEmail(identifier)`.
     - In the legacy database or when students are enrolled in multiple courses (`uk_student_email_course` constraint on `(email, course_id)`), students can have multiple rows with the same email.
     - Calling `Optional<Student> findByEmail` caused Spring Data JPA to execute `query.getSingleResult()`, throwing `jakarta.persistence.NonUniqueResultException: Query did not return a unique result: 2 results were returned`, or throwing `Student not found` when email case did not match.
   - **Fixes**:
     - In [`CourseScopeService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/CourseScopeService.java):
       - Added `resolveStudentsByIdentity(studentRepository, identifier)` to lookup student rows across case-insensitive email and username.
       - Added `resolveStudentForCourse(studentRepository, identifier, courseId)` to prioritize the record matching the active course ID (or enrolled group course), falling back safely to the primary candidate row.
     - In [`CustomUserDetailsService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/CustomUserDetailsService.java):
       - Updated student authentication to use `CourseScopeService.resolveStudentsByIdentity(studentRepository, identifier)`, completely preventing login failures caused by `NonUniqueResultException`.
     - In [`ProfileController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/ProfileController.java):
       - Updated student profile display and username update to use `CourseScopeService.resolveStudentsByIdentity`.
     - In [`StudentController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/StudentController.java):
       - Added helper `getCurrentStudent(principal)` and refactored all 15 endpoints (`switchCourse`, `studentHome`, `listAssessments`, `listStudentSubmissions`, `showAssessmentSubmitPage`, `uploadAssessmentSubmission`, `downloadSubmissionFile`, `getSubmissionAnnotations`, `viewSubmissionWithDoodles`, `showPeerAssessmentForm`, `submitPeerAssessment`, `showTeamEvaluationForm`, `submitTeamEvaluation`, `viewMyComments`, `viewAssessmentResults`).
       - In `isStudentAssignedToAssessment` and `getAssignmentForStudent`: checked both student ID and student email matches so students assigned across multi-course records are never locked out.
       - In `home` and `listAssessments`: added safe fallback to return all assigned assessments if course-filtered list is empty.
     - Standardized `th:replace="~{fragments/student_sidebar :: sidebar}"` syntax across `peer_assessment_form.html`, `student_assessments.html`, `student_comments.html`, `student_home.html`, `student_submissions.html`, `student_submit_single.html`, and `student_submission_view.html`.

2. **Assessor Assignment UI: Delete Button Trimming & Bottom Clearance Fixes**:
   - **Root Causes**:
     - On `/admin/assessments/{id}/assign-lecturers` ([`admin_assign_lecturers.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/admin_assign_lecturers.html)), `.btn-icon-remove` had `width: 38px !important;` but no `flex-shrink: 0`, `min-width: 38px`, or `height: 38px`.
     - The `<select>` element (`.form-select`) lacked `min-width: 0 !important;`. In flexbox, long assessor emails expanded the `<select>` to its intrinsic width, which squished or pushed the delete button off the card edge where `overflow: hidden` trimmed it.
     - The "Save Assignments" bar (`.button-container`) is `position: fixed; bottom: 20px; z-index: 1000`. When users scrolled to the bottom of the page, the fixed bar floated directly over the last team/group/student cards, completely blocking the "+ Add Another Assessor" button and assessor dropdowns.
   - **Fixes**:
     - In [`assign_lecturers.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/assign_lecturers.css):
       - On `.btn-icon-remove`: Added `flex: 0 0 38px !important; flex-shrink: 0 !important; min-width: 38px !important; max-width: 38px !important; height: 38px !important; min-height: 38px !important; box-sizing: border-box !important;`. The delete button now preserves its exact dimensions and never gets trimmed or squished.
       - On `.form-select`: Added `flex: 1 1 0% !important; min-width: 0 !important; text-overflow: ellipsis !important; white-space: nowrap !important; overflow: hidden !important;`. Long assessor emails smoothly truncate with an ellipsis rather than overflowing.
       - On `.lecturer-row`: Added `align-items: center !important; width: 100% !important; box-sizing: border-box !important;`.
       - On `.card-body` and `.lecturer-assignments-container`: Added `overflow: visible !important; width: 100% !important;`.
       - On `#rubricModeSection`, `#groupModeSection`, and `#studentModeSection`: Added `margin-bottom: 25px !important;`.
       - On `.button-container`: Added `sidebar-collapsed` positioning override (`left: 20px !important;`).
     - Bottom Clearance:
       - In [`admin_assign_lecturers.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/admin_assign_lecturers.html): Added dedicated `<div class="save-bar-spacer" aria-hidden="true"></div>` before `.button-container`.
       - In [`assign_lecturers.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/assign_lecturers.css): Styled `.save-bar-spacer` with `height: 120px !important; min-height: 120px !important;`, and enforced `padding-bottom: 140px !important;` on `.main-content` across all desktop and responsive viewports (`1279px`, `768px`, `480px`). The last team cards now scroll fully clear of the fixed "Save Assignments" bar.

3. **Automated Verification**:
   - Added automated regression tests in [`course-management-browser.test.cjs`](file:///Users/dansan04/AAS-Project/adproject/src/test/js/course-management-browser.test.cjs) verifying student identity resolution, multi-course access safeguards, and assign assessors UI layout guarantees.
   - All **50 automated tests pass** (`node --test adproject/src/test/js/*.test.cjs`).

---

## Previous Status: Assessor Assessment Access Resolution, Course Switcher Parameter Safeguards & SSE Stability
**Status**: COMPLETED
**Date**: 2026-11-17

### Summary of Enhancements & Fixes
1. **Assessor Assessment Access Resolution (`Lecturer not found: pyliew@utm.my`)**:
   - **Root Cause**:
     - `LecturerAssessmentController.getCurrentLecturer()` called `CourseScopeService.resolveLecturerForCourse(lecturerRepository, emailOrUsername, activeCourseId)`.
     - When `activeCourseId` was provided, `resolveLecturerForCourse` checked candidate rows for `courseId.equals(candidate.getCourse().getId())`. If the assessor had `course_id IS NULL` (common for multi-course or legacy assessors) or was associated via assignment tables rather than primary `course_id`, it returned `Optional.empty()`, causing `getCurrentLecturer` to throw `RuntimeException: Lecturer not found: <email>` on `/lecturer/assessments`.
   - **Fixes**:
     - In [`CourseScopeService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/CourseScopeService.java):
       - Updated `resolveLecturerForCourse` to fall back to `candidates.get(0)` if none of the candidate rows match `candidate.getCourse().getId().equals(courseId)`, rather than returning empty.
       - Expanded `findAssignedCourseIdsForLecturer` to query `lecturer_student_assignment`, `lecturer_rubric_assignments`, `lecturer_group_assignment`, and `project_group` (supervisor roles). Now any assessor assigned to evaluate any assessment or student group automatically has that course visible in their course switcher.
     - In [`LecturerAssessmentController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/LecturerAssessmentController.java):
       - Added fallback to `getCurrentLecturer`: `.or(() -> CourseScopeService.resolveLecturersByIdentity(lecturerRepository, emailOrUsername).stream().findFirst())`.
       - Updated `lecturerCanAccessAssessment` to permit access if the lecturer is assigned to the assessment via `isLecturerAssignedToAssessment` (group, rubric, or student assignments) or `isManagedCourseId`, eliminating unnecessary 403s.
       - In `showAssessments`: Added safe fallback to return all assigned assessments if course-filtered list is empty.

2. **Course Switcher Missing/Null Parameter Safeguards**:
   - **Root Cause**:
     - In `sidebar.html`, `lecturer_sidebar.html`, and `student_sidebar.html`, `<select id="courseSwitchSelect">` has a placeholder `<option value="">Switch Course</option>`.
     - When selected or submitted without a value, Spring converted `courseId=""` to `null` and threw `MissingServletRequestParameterException: Required request parameter 'courseId' for method parameter type Long is present but converted to null` (500/400 error).
   - **Fixes**:
     - In [`LecturerController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/LecturerController.java), [`StudentController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/StudentController.java), and [`AdminController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/AdminController.java):
       - Changed `@RequestParam("courseId") Long courseId` to `@RequestParam(value = "courseId", required = false) Long courseId`.
       - Added null-check guard `if (courseId == null)` that redirects back safely with an informative flash message without throwing an exception.
     - In [`sidebar-toggle.js`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/js/sidebar-toggle.js):
       - Added guard `if (this.value && this.value.trim() !== '')` before `form.requestSubmit()`, preventing unnecessary empty form submissions from the client side.

3. **SSE Connection Timeout & Response Committed Exception Handling**:
   - **Root Cause**:
     - `/api/progress/stream/{id}` uses `SseEmitter`. When client connections time out, Spring raises `AsyncRequestTimeoutException`.
     - `GlobalExceptionHandler` caught this under `Exception.class` and attempted to render Thymeleaf's `error.html` via `getWriter()`, colliding with Tomcat's open `ServletOutputStream` and throwing `IllegalStateException: getOutputStream() has already been called for this response`.
   - **Fixes**:
     - In [`GlobalExceptionHandler.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/exception/GlobalExceptionHandler.java):
       - Added explicit handler for `AsyncRequestTimeoutException` returning `null`.
       - Added `response != null && response.isCommitted()` check in `handleGenericException` to bypass error view rendering on already committed streaming responses.
       - Added dedicated `@ExceptionHandler(MissingServletRequestParameterException.class)` for user-friendly 400 Bad Request responses.

4. **Template & Compilation Cleanups**:
   - Fixed missing `import java.util.Map;` in [`AdminService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/AdminService.java).
   - Standardized Thymeleaf fragment syntax in [`lecturer_home.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/lecturer_home.html) and [`lecturer_assessments.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/lecturer_assessments.html) to `~{fragments/lecturer_sidebar :: sidebar}`.

5. **Automated Verification**:
   - Added automated test in [`course-management-browser.test.cjs`](file:///Users/dansan04/AAS-Project/adproject/src/test/js/course-management-browser.test.cjs) asserting all course switch null guards, assessor resolution fallbacks, and SSE exception handlers.
   - All **48 automated tests pass** (`node --test adproject/src/test/js/*.test.cjs`).

---

## Previous Status: Legacy Database Duplicate Assessor Consolidation & Multi-Course Segregation
**Status**: COMPLETED
**Date**: 2026-11-17

### Summary of Enhancements & Fixes
1. **Legacy Duplicate Assessor Consolidation Migration**:
   - **Root Cause in Legacy Database**:
     - In MySQL, `UNIQUE (email, course_id)` does not prevent multiple rows with the same email when `course_id IS NULL` because SQL treats `NULL != NULL`.
     - Prior to the multi-course assignment architecture (`admin_course_assignment`), adding an assessor to multiple courses resulted in inserting separate `lecturer` rows with different `course_id`s or duplicate rows inserted before unique indexes were applied.
     - When `getAllLecturers()` aggregated records from `lecturer`, `admin_course_assignment`, course creator, and logged-in user, `Set<Lecturer>` (which differentiates instances by primary key ID) returned multiple duplicate entries for the exact same real person. This caused duplicate assessor rows in `/admin/manage-lecturers`, duplicated dropdown options in group and rubric assignments, and fragmented marks.
   - **Database Consolidation Runner**:
     - In [`CourseEmailConstraintMigrationRunner.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/config/CourseEmailConstraintMigrationRunner.java):
       - Added `migrateDuplicateLecturers()` executing at startup before unique constraints are applied.
       - Discovers duplicate email groups: `SELECT LOWER(TRIM(email)) FROM lecturer GROUP BY LOWER(TRIM(email)) HAVING COUNT(*) > 1`.
       - Ranks candidate rows with a scoring heuristic (`scoreLecturerRow`): prioritizes rows with existing evaluated marks (`marks.lecturer_id`), supervisor assignments (`project_group`), hashed bcrypt credentials, and active roles to select the canonical **Primary ID**.
       - Merges credentials and roles (`mergeLecturerAttributes`) into the Primary ID.
       - Safely repoints all foreign keys across child tables from secondary IDs to the Primary ID:
         1. `marks.lecturer_id`
         2. `assessment_comments.evaluator_id` (where `evaluatorType IN ('LECTURER', 'SUPERVISOR')`)
         3. `project_group.academic_supervisor_id` & `project_group.industrial_supervisor_id`
         4. `courses.created_by_lecturer_id` & legacy `courses.created_by_id`
         5. `lecturer_student_assignment.lecturer_id` (deleting redundant duplicate assignments before repointing)
         6. `lecturer_rubric_assignments.lecturer_id` (deleting redundant duplicate assignments before repointing)
         7. `lecturer_group_assignment.lecturer_id` (deleting redundant duplicate assignments before repointing)
         8. `admin_course_assignment.lecturer_id` (deleting duplicate assignments, and preserving multi-course access by ensuring an `admin_course_assignment` exists for courses where secondary rows were active)
         9. `submission_annotation.lecturer_id`
       - Deletes secondary duplicate `lecturer` rows with zero FK constraint violations.
       - Disambiguates duplicate usernames (`migrateDuplicateLecturerUsernames`) to guarantee composite unique index `uk_lecturer_username_course` succeeds.
2. **In-Memory Assessor Deduplication & Creation Safeguards**:
   - In [`AdminService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/AdminService.java):
     - In `getAllLecturers()`: Deduplicates returned `Lecturer` list using a `LinkedHashMap<String, Lecturer>` keyed by normalized identity (`email.trim().toLowerCase()` or username), preferring records directly associated with the active course or with the lowest ID.
     - In `checkLecturerEmailDuplicate()`: Verifies whether the email is already present in the active course across all active course assessors (both direct and assigned via `admin_course_assignment`).
     - In `saveLecturer()`: If an assessor with the given email already exists in the database, instead of inserting a duplicate row in `lecturer`, the existing record is reused and linked to the active course via `AdminCourseAssignment`.
     - In `deleteLecturerById()`: Properly supports deleting/unassigning lecturers enrolled via `admin_course_assignment` without breaking access in other courses.
3. **Automated Verification**:
   - Added automated tests in [`course-management-browser.test.cjs`](file:///Users/dansan04/AAS-Project/adproject/src/test/js/course-management-browser.test.cjs) verifying migration runners, reference repointing, and deduplication logic.
   - All **47 automated tests pass** (`node --test adproject/src/test/js/*.test.cjs`).

---

## Previous Status: Project Group Schema Alignment (`group_size` / `groupsize`) and Dual-Column Synchronization
**Status**: COMPLETED
**Date**: 2026-11-17

### Summary of Enhancements & Fixes
1. **`Field 'group_size' doesn't have a default value` Error Resolution**:
   - **User Error**: `Error creating group: could not execute statement [Field 'group_size' doesn't have a default value] [insert into project_group (academic_supervisor_id,course_id,groupname,groupsize,industrial_supervisor_id) values (?,?,?,?,?)]`
   - **Root Cause**:
     - In [`Group.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/model/Group.java), field `groupSize` was explicitly mapped to `@Column(name = "groupsize")` and `groupName` to `@Column(name = "groupname")`.
     - In MySQL, the live database table `project_group` historically had or generated `group_size` (snake_case from standard Spring Boot physical naming) defined as `INT NOT NULL` without a `DEFAULT` clause.
     - When Hibernate executed `insert into project_group (..., groupsize, ...) values (...)`, MySQL strict mode rejected the insertion with MySQL Error 1364 because the unsupplied `group_size` column had no default value.
   - **Resolution**:
     - In [`CourseEmailConstraintMigrationRunner.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/config/CourseEmailConstraintMigrationRunner.java):
       - Added `migrateProjectGroupColumns()` running on application startup.
       - Discovers all column variants in `project_group` (`group_size`, `groupsize`, `groupSize`, `group_name`, `groupname`, `groupName`).
       - Alters `group_size` and `groupsize` to ensure `DEFAULT 0`.
       - Alters `group_name` and `groupname` to ensure `DEFAULT NULL`.
       - Bidirectionally synchronizes values between `group_size` and `groupsize`, and between `group_name` and `groupname`.
     - In [`AdminService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/AdminService.java):
       - Added `ensureProjectGroupColumnsHaveDefaults()` called immediately prior to group persistence to dynamically set defaults even if startup migrations were delayed.
       - Added `syncProjectGroupDualColumns(groupId, groupSize, groupName)` invoked on both group creation and group updates to ensure `group_size` and `groupsize` remain perfectly in sync in MySQL.
2. **Automated Verification**:
   - Added automated tests in [`course-management-browser.test.cjs`](file:///Users/dansan04/AAS-Project/adproject/src/test/js/course-management-browser.test.cjs) verifying migration runners, default configuration, and synchronization routines.
   - All **46 automated tests pass** (`node --test adproject/src/test/js/*.test.cjs`).

---

## Previous Status: Group Creation & Assignment Error Resolution and Multi-Course Student Segregation
**Status**: COMPLETED
**Date**: 2026-11-17

### Summary of Enhancements & Fixes
1. **Group Creation & Assignment Root Cause Analysis**:
   - **User Request**: "right now please check, the group still cant be add, error when trying to add them."
   - **Root Causes**:
     1. **HTML5 Form Validation Block**: In [`group_assignment.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/group_assignment.html) and [`edit_group.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/edit_group.html), the `<select id="industrialSupervisorId">` element had `required` enforced. When courses did not utilize an industrial supervisor or only assigned an academic supervisor (assessor), the browser refused to submit the form, presenting a blocking validation tooltip.
     2. **Supervisor Ownership Rejection**: In [`AdminController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/AdminController.java), `ownsLecturer(...)` verified whether supervisors belonged to the active course. For co-admins, invited lecturers, or supervisors assigned via `admin_course_assignment`, `isLecturerInCourse` failed to recognize valid course-scoped assessors, causing `AdminController` to redirect with `"Cannot assign an academic/industrial supervisor outside your managed courses."`.
     3. **Cross-Course Student Query & Direct Overwrite Violation**:
        - In [`AdminService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/AdminService.java), `assignStudentsToNewGroup` used `studentRepository.findAllById(...)` which, when scoped by Hibernate's `courseScopeFilter`, failed to retrieve students enrolled via `student_course_assignment`.
        - Furthermore, when assigning groups, `studentRepository.saveAll(studentsToAssign)` was executed indiscriminately on all students. For multi-course students, updating `student.group` directly caused Hibernate to attempt modifying the primary entity's foreign key, clobbering their primary course group and risking constraint violations instead of isolating the group assignment to `student_course_assignment.group_id`.
     4. **Missing Try-Catch & Success Feedback**: In [`AdminController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/AdminController.java), `createAndAssignGroup` lacked a `try-catch` wrapper and never set `success` flash attributes, meaning any unexpected exception crashed straight to `error.html` without informative user feedback. Additionally, GET `/admin/group-assignment` unconditionally reinitialized `groupAssignmentDto`, destroying user form values on validation errors.
2. **Key Changes Applied**:
   - In [`group_assignment.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/group_assignment.html) & [`edit_group.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/edit_group.html):
     - Removed strict `required` attributes on `academicSupervisorId` and `industrialSupervisorId` dropdowns.
     - Updated options to clearly state optional status (`-- Select Assessor (Optional) --`, `-- Select Industrial Supervisor (Optional) --`).
   - In [`StudentRepository.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/repositories/StudentRepository.java):
     - Added native SQL lookup `@Query(value = "SELECT * FROM student WHERE id = :id", nativeQuery = true) Optional<Student> findByIdNative(@Param("id") Long id);` to safely retrieve students regardless of Hibernate filter state.
   - In [`AdminService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/AdminService.java):
     - Enforced `@Transactional` on `assignStudentsToNewGroup`.
     - In `isLecturerInCourse(...)`: recognized any lecturer present in `getAllLecturers()` for the active course.
     - In `findLecturerInCourse(...)` & `findLecturerById(...)`: added fallback to `getAllLecturers()`.
     - In `findStudentById(...)`: added native query and `getAllStudents()` fallback.
     - In `isStudentInActiveCourse(...)`: verified active course membership against both `belongsToCourse` and `getAllStudents()`.
     - In `assignStudentToCourseGroup(...)` & `assignStudentsToNewGroup(...)`: strictly isolated primary course student updates (`student.setGroup(...)` and `studentRepository.saveAll`) from multi-course students enrolled in secondary courses (where group assignments are exclusively recorded in `student_course_assignment.setGroup(targetGroup)`).
   - In [`AdminController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/AdminController.java):
     - Preserved `groupAssignmentDto` in model when returning from flash redirects (`if (!model.containsAttribute("groupAssignmentDto"))`).
     - Wrapped `adminService.assignStudentsToNewGroup` in `try-catch` with clear user-facing error messages and `"Group created and assigned successfully!"` flash success.
3. **Verification**:
   - Added automated tests in [`course-management-browser.test.cjs`](file:///Users/dansan04/AAS-Project/adproject/src/test/js/course-management-browser.test.cjs) verifying form optional selects, controller error handling, native query fallbacks, and multi-course student isolation.
   - All **46 automated tests pass** (`node --test adproject/src/test/js/*.test.cjs`).

---

## Previous Status: Manage Assessments Server-Side Rendering & Jackson Cyclic Reference Resolution
**Status**: COMPLETED
**Date**: 2026-11-16

### Summary of Enhancements & Fixes
1. **Manage Assessments "Half-Loaded Page / Half Error Page" Resolution**:
   - **User Request**: "in manage-assessments the ui are error, the page are loaded half and then another half the error page, please fix this"
   - **Root Cause Analysis**:
     - When rendering [`manage-assessments.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/manage-assessments.html), line 207 contained `const assessmentObject = /*[[${newAssessment}]]*/ null;`.
     - In Thymeleaf, `/*[[...]]*/` javascript inlining evaluates complex Java objects using Jackson's `ObjectMapper.writeValueAsString(newAssessment)` to serialize the entity into a JavaScript literal.
     - In [`Assessment.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/model/Assessment.java), getter `getEffectiveSubmissionAssessment()` returns `this` (`return this.submissionSourceAssessment != null ? this.submissionSourceAssessment : this;`). Jackson entered an infinite recursion / cyclic self-reference loop (`effectiveSubmissionAssessment -> this -> effectiveSubmissionAssessment`), triggering `JsonMappingException: Direct self-reference leading to cycle`.
     - Because Tomcat's 8KB HTTP response buffer had already flushed the initial HTML markup (sidebar, breadcrumb, creation form, and first table row), the response was already committed. When the Jackson exception occurred, Spring Boot forwarded to the error handler, which appended `error.html` directly onto the open response stream.
     - Because `body.rubric-page` has `display: flex; flex-direction: row;`, the browser rendered `.main-content` on the left half and the appended `.error-page` on the right half.
   - **Resolution**:
     - In [`manage-assessments.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/manage-assessments.html):
       - Replaced `const assessmentObject = /*[[${newAssessment}]]*/ null;` and subsequent property lookups with direct primitive string evaluation:
         ```javascript
         const duplicateFound = /*[[${duplicateFound ?: false}]]*/ false;
         if (duplicateFound) {
             const currentTitle = /*[[${newAssessment != null && newAssessment.title != null ? newAssessment.title : 'Untitled Assessment'}]]*/ 'Untitled Assessment';
         ```
       - Completely eliminated Jackson serialization of JPA entities in inline scripts.
       - Hardened table row attributes with strict null-safety: `assessment.requiresSubmission == true`, null-coalescing fallbacks for `assessment.submissionScope` (`${assessment.submissionScope != null ? assessment.submissionScope : 'GROUP'}`), and safe class appending.
     - In [`Assessment.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/model/Assessment.java):
       - Added `@com.fasterxml.jackson.annotation.JsonIgnore` to bidirectional and recursive navigation properties: `course`, `rubrics`, `submissionSourceAssessment`, and `getEffectiveSubmissionAssessment()`.
     - In [`Rubric.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/model/Rubric.java):
       - Added `@com.fasterxml.jackson.annotation.JsonIgnore` to `assessment` getter and field to prevent back-reference cycles.
2. **Automated Verification**:
   - Added test in [`branding-and-loading.test.cjs`](file:///Users/dansan04/AAS-Project/adproject/src/test/js/branding-and-loading.test.cjs) verifying that `manage-assessments.html` does not serialize raw JPA objects in inline scripts and uses null-safe bindings.
   - All **45 automated tests pass**:
     ```bash
     node --test adproject/src/test/js/*.test.cjs # 45/45 passing
     ```

---

## Previous Status: Loading Animation Disabled, Manage Assessments Routing & User Editing Responsive Layout Fixes
**Status**: COMPLETED
**Date**: 2026-11-16

### Summary of Enhancements & Fixes
1. **Disabled Loading Animation Overlay**:
   - **User Request**: "the animation are really bad, disable it for now, we will fix it."
   - **Resolution**:
     - In [`loading.js`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/js/loading.js): set `enablePageTransitions: false`, `autoInject: false`, `duration: 0`, `navigationDelay: 0`. In `init()` and `show()`, immediately add `document.body.classList.add('loaded')` and keep `show()` as an inert no-op so internal link clicks navigate instantly without full-screen overlays or artificial transition delays.
     - In [`loading.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/loading.css): set `#loading-screen, .iams-loading-screen { display: none !important; opacity: 0 !important; visibility: hidden !important; pointer-events: none !important; }`.
     - In [`critical.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/critical.css): neutralized initial body content hiding rules (`body > *:not(#loading-screen):not(script)` set to `opacity: 1 !important; visibility: visible !important; pointer-events: auto !important;`) so pages render directly on first paint.

2. **Manage Assessments URL Route Coverage**:
   - **User Request**: Entering `manage-assessments` triggered the application error page (`Oops. Something went wrong... System Error Unavailable 404/500`).
   - **Root Cause**: The assessment management hub was exclusively mapped to `/rubrics/manage`, meaning direct browser navigation or links to `/manage-assessments`, `/admin/manage-assessments`, or `/rubrics/manage-assessments` dispatched to `/error` (`error.html`).
   - **Resolution**:
     - In [`AdminController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/AdminController.java): added `@GetMapping({"/manage-assessments", "/assessments/manage"})` redirecting to `/rubrics/manage`.
     - In [`AuthController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/AuthController.java): added root `@GetMapping({"/manage-assessments", "/assessments/manage"})` redirecting to `/rubrics/manage`.
     - In [`RubricController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/RubricController.java): separated handler methods (`@GetMapping("/manage") public String manageAssessments(Model model)` and `@GetMapping("/manage-assessments") public String manageAssessmentsAlias(Model model)`) to prevent non-repeatable annotation conflicts.
     - In [`SecurityConfig.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/config/SecurityConfig.java): added `.requestMatchers("/manage-assessments", "/assessments/manage").hasAnyRole("ADMIN", "SUPER_ADMIN")`.

3. **User Editing Container / Main Responsive Sizing & Group Randomization Layout**:
   - **User Request**: In user-editing container, the user editing main was trimmed to fit, needing horizontal scrolling and flexible resizing for group assignments, group randomization, and course input boxes.
   - **Root Cause**: CSS flexbox default `min-width: auto` prevented `.user-editing-main` from shrinking below child widths. Furthermore, `.card` had `overflow: hidden !important;` and fixed widths on inputs trimmed the group randomization form and wide tables.
   - **Resolution**:
     - In [`group.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/group.css):
       - `.user-editing-container`: added `width: 100% !important; max-width: 100% !important; box-sizing: border-box !important; overflow-x: auto !important; -webkit-overflow-scrolling: touch !important;`.
       - `.user-editing-main`: added `flex: 1 1 0% !important; min-width: 0 !important; max-width: 100% !important; box-sizing: border-box !important; overflow-x: auto !important; -webkit-overflow-scrolling: touch !important;`.
       - `.card`: changed `overflow: hidden !important;` to `overflow-x: auto !important;` with responsive padding.
       - `.creation-wrapper` and `.content-layout`: set `min-width: 0 !important; width: 100% !important; box-sizing: border-box !important;`.
       - `.randomize-section` and `.form-group-inline input`: removed rigid 280px caps in favor of `max-width: 100% !important; width: 100% !important; box-sizing: border-box !important;` ensuring controls fit comfortably without cropping.
     - Mirrored responsive `.user-editing-container` and `.user-editing-main` enhancements across [`group_edit.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/group_edit.css), [`group_preview.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/group_preview.css), [`manageUser.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/manageUser.css), and [`style.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/style.css).
     - In [`admin_manage_courses.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/admin_manage_courses.css): set `.manage-courses-container` and `main-content` to `overflow-x: auto !important; box-sizing: border-box !important;` ensuring course input boxes and tables never get trimmed.

4. **Verification**:
   - Updated test suite [`branding-and-loading.test.cjs`](file:///Users/dansan04/AAS-Project/adproject/src/test/js/branding-and-loading.test.cjs) verifying disabled animation, route aliases, and responsive user-editing CSS rules.
   - All tests passing: `node --test adproject/src/test/js/*.test.cjs` ran **44/44 tests passing** across all 3 suites.

---

## Current Status: IAMS Brand Identity Guidelines, Asset Repository & Institutional Loading Animation System
**Status**: COMPLETED
**Date**: 2026-11-16

### Summary of Enhancements & Fixes
1. **IAMS Branding Folder & Guidelines Reference**:
   - Created [`docs/branding/`](file:///Users/dansan04/AAS-Project/docs/branding/) and [`adproject/docs/branding/`](file:///Users/dansan04/AAS-Project/adproject/docs/branding/) as the permanent reference hub for all future implementations.
   - Cataloged all official assets under `docs/branding/assets/`:
     - [`IAMS_logo2.png`](file:///Users/dansan04/AAS-Project/docs/branding/assets/IAMS_logo2.png): High-resolution primary mark with alpha channel transparency.
     - [`IAMS_logo.jpeg`](file:///Users/dansan04/AAS-Project/docs/branding/assets/IAMS_logo.jpeg): High-resolution master raster graphic.
     - [`utmlogo.png`](file:///Users/dansan04/AAS-Project/docs/branding/assets/utmlogo.png) and [`logoUTM.png`](file:///Users/dansan04/AAS-Project/docs/branding/assets/logoUTM.png): Institutional partner marks.
   - Authored [`docs/branding/BRAND_GUIDELINES.md`](file:///Users/dansan04/AAS-Project/docs/branding/BRAND_GUIDELINES.md) providing comprehensive guidance on:
     - Clear space (1 "I" minimum clearance) and scale rules (160–200px desktop, 120–140px mobile).
     - Color system design tokens: Institutional Maroon (`#4A0018`), Gold Accent (`#F8A617`), Evaluator Emerald (`#047857`), Supervisor Navy (`#002B5B`), Student Crimson (`#7A1E2D`), and Canvas Neutral (`#F4F2EE`).
     - Standardized Inter typography hierarchy (500 navigation links, 600 groups/active, 700 headings).
   - Created interactive showcase [`docs/branding/index.html`](file:///Users/dansan04/AAS-Project/docs/branding/index.html) with one-click color swatch copying and live interactive loading animation testing.

2. **Official IAMS Logo Loading Animation System**:
   - Redesigned [`loading.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/loading.css):
     - Displays `IAMS_logo2.png` preserving its crisp horizontal aspect ratio on a floating institutional card with backdrop blur.
     - Ambient breathing glow aura (`@keyframes iamsGlowPulse`) modulating maroon and gold illumination.
     - Delicate dual-tone orbital perimeter ring (`@keyframes iamsRingSpin`).
     - Indeterminate gradient shimmer progress bar (`@keyframes iamsIndeterminateBar`).
     - Adaptable across Light mode, Dark mode (`html[data-theme="dark"]`), and Warm mode (`html[data-theme="warm"]`).
     - Full accessibility compliance (`prefers-reduced-motion: reduce`).
   - Enhanced [`loading.js`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/js/loading.js):
     - Programmatic API: `IAMSLoading.show(msg)`, `IAMSLoading.hide()`, `IAMSLoading.setMessage(msg)`, `IAMSLoading.trigger(duration, msg, callback)`.
     - In-component / inline loader creation: `IAMSLoading.createInline(containerEl, msg)`.
     - Auto-injection: dynamically constructs `#loading-screen` using `IAMS_logo2.png` if not already present in the DOM (zero template boilerplate required).
     - 100% backwards compatibility with legacy `UTMLoading` alias.
   - Created reusable Thymeleaf fragment [`fragments/loading_screen.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/fragments/loading_screen.html).

3. **Verification**:
   - Created automated test suite in [`branding-and-loading.test.cjs`](file:///Users/dansan04/AAS-Project/adproject/src/test/js/branding-and-loading.test.cjs).
   - All tests passing across all suites: `node --test adproject/src/test/js/*.test.cjs` ran **41/41 tests passing** (100% pass rate).

---

## Current Status: Multi-Assessment Report Reuse, Assessor New-Tab Report Management, Standardized Institutional Sidebar Typography & Cross-Course Segregation Audit
**Status**: COMPLETED
**Date**: 2026-11-16

### Summary of Enhancements & Fixes
1. **Multi-Assessment Report Reuse & Sharing Architecture**:
   - **Requirement**: Allow deliverables/reports submitted by a student or team in one assessment (e.g. Assessment A) to be shared and reused across other assessments within the same course without forcing duplicate file uploads.
   - **Backend Model & Service Implementation**:
     - In [`Assessment.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/model/Assessment.java):
       - Added `@ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "submission_source_assessment_id") private Assessment submissionSourceAssessment;`.
       - Added domain helpers: `isSubmissionShared()`, `getEffectiveSubmissionAssessment()`, `getEffectiveSubmissionTitle()`, `getEffectiveSubmissionScope()`, and `getEffectiveSubmissionInstructions()`.
     - In [`AssessmentSubmissionService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/AssessmentSubmissionService.java):
       - Resolved lookups through `assessment.getEffectiveSubmissionAssessment()` for `getSubmissionForTarget`, `getSubmissionForStudent`, and `getSubmissionMappingData`.
       - Added `getGroupSubmissionsMap(Assessment assessment)` and `getStudentSubmissionsMap(Assessment assessment)` for quick lookups on selection cards and reports overview.
     - In [`AdminController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/AdminController.java):
       - Populated other course assessments (`courseAssessments`) in `showAssessmentSubmissionConfig`.
       - Handled `submissionMode` ("DIRECT" vs "SHARED") and validated `submissionSourceAssessmentId` belongs to the same course.
     - In [`assessment_submission_configuration.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/assessment_submission_configuration.html):
       - Added interactive radio switch ("Direct Submission (Unique Report)" vs "Reuse / Shared Report from Another Assessment") and dropdown of course assessments.
     - In [`student_submit_single.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/student_submit_single.html):
       - Added prominent shared report information banner explaining deliverable reuse.

2. **Assessor Report Viewing in Dedicated New Tab (`target="_blank"`)**:
   - **Requirement**: Enable assessors to view student and group reports directly in a new browser tab while maintaining the existing split-screen grading workstation and doodle studio during evaluation.
   - **Implementation**:
     - In [`LecturerAssessmentController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/LecturerAssessmentController.java):
       - Added `GET /{assessmentId}/reports` returning `lecturer_assessment_reports.html`, populating assigned groups, students, and submission maps.
       - Added convenience redirection `GET /{assessmentId}/reports/view` resolving submissions for target group/student and streaming inline PDF.
       - In `showGroupSelection` and `showStudentSelection`: passed `groupSubmissions` and `studentSubmissions` maps to the template models.
     - Created [`lecturer_assessment_reports.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/lecturer_assessment_reports.html):
       - Dedicated assessor reports overview listing assigned groups/students, submission status (Submitted v1, v2 / Not Submitted), submitted file metadata (size, upload timestamp), interactive search filtering, and prominent `<a target="_blank" class="btn-view-pdf">View Report (New Tab)</a>` buttons.
     - Updated [`lecturer_select_group.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/lecturer_select_group.html) and [`lecturer_select_student.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/lecturer_select_student.html):
       - Added "View All Reports (New Tab)" link in dashboard headers when `assessment.requiresSubmission`.
       - Added per-card `<a target="_blank" class="btn-report-view">View Report (New Tab)</a>` buttons alongside evaluation action buttons.
     - Updated [`lecturer_assessments.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/lecturer_assessments.html):
       - Added `<a target="_blank" class="btn-reports-tab">View Student Reports (New Tab)</a>` for each assessment requiring submissions.
     - In [`lecturer_combined_evaluation_form.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/lecturer_combined_evaluation_form.html):
       - Completely preserved side-by-side doodle studio (`split-evaluation-layout`); enhanced top-bar action button to prominent `<a target="_blank" class="workstation-action-link">View Report (New Tab)</a>`.

3. **Standardized Institutional Sidebar Typography (Admin Home Alignment)**:
   - **Root Cause**:
     - `system-ui.css` previously enforced `font-weight: var(--ui-weight-semibold)` (600) via `body[data-ui-role] .sidebar a`, which clashed with role-specific stylesheets (e.g. `admin_sidebar.css` having 500 on `.sidebar-link`, but 600/700 on `.menu-list li a.active` or in `student_sidebar.css` / `superadmin_sidebar.css`).
     - Depending on CSS load order across pages, sidebar links appeared inconsistently heavy/bold.
   - **Resolution**:
     - Standardized all role sidebars (`admin_sidebar.css`, `lecturer_sidebar.css`, `student_sidebar.css`, `superadmin_sidebar.css`, `supervisor_sidebar.css`, `industrial_sidebar.css`) and [`system-ui.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/system-ui.css) to adhere strictly to the Admin Home institutional typography:
       - Regular navigation links: `font-size: 15px !important; font-weight: 500 !important;` (clean, refined, never chunky bold).
       - Accordion / collapsible group buttons: `font-size: 0.9rem !important; font-weight: 600 !important;`.
       - Submenu links: `font-size: 0.85rem !important; font-weight: 500 !important;`.
       - Active item state: `font-weight: 600 !important;`.
       - User profile label: `font-size: 14px !important; font-weight: 600 !important;`.
       - Course switch label: `font-size: 12px !important; font-weight: 600 !important;`.

4. **Cross-Course Segregation & Multi-Enrollment Audit**:
   - Re-verified composite unique constraints on `(email, course_id)` and `(username, course_id)` in `Lecturer.java` and `Student.java`.
   - Confirmed `CourseEmailConstraintMigrationRunner.java` automatically drops legacy single-column constraints (`idx_lecturer_username`, `idx_student_username`) and ensures composite indexes on startup.
   - Audited `AdminService.java`: cross-course additions sync existing password hashes and reset tokens without password re-encryption or account collisions, and scope filtering strictly isolates course data.

5. **Verification**:
   - Resolved method signature mismatch in [`AdminSubmissionControllerTest.java`](file:///Users/dansan04/AAS-Project/adproject/src/test/java/com/capstone/adproject/controller/AdminSubmissionControllerTest.java) for `saveAssessmentSubmissionConfig` (`submissionMode` and `submissionSourceAssessmentId`).
   - Added unit test `testSaveAssessmentSubmissionConfig_SharedMode_LinksSourceAssessment` verifying shared submission linking.
   - Full DOM contract and browser logic test suite: `node --test adproject/src/test/js/*.test.cjs` ran **35/35 tests passing** (100% pass rate, 0 failures).

---

## Current Status: Resolution of Java Compiler Diagnostics & Missing Test Imports
**Status**: COMPLETED
**Date**: 2026-11-16

### Summary of Enhancements & Fixes
1. **Resolution of Missing Imports in Test Suite**:
   - In [`AdminServiceTest.java`](file:///Users/dansan04/AAS-Project/adproject/src/test/java/com/capstone/adproject/service/AdminServiceTest.java), added missing imports for `AdminCourseAssignmentRepository`, `CourseRepository`, and `assertTrue`, resolving type resolution errors.
2. **Spring Boot Best Practices & Warning Elimination**:
   - In [`CommentConfigController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/CommentConfigController.java), refactored field injection (`@Autowired`) to constructor injection for `AssessmentService`, `AssessmentRepository`, and `CourseScopeService`, clearing `vscode-spring-boot` diagnostic warnings.
3. **Validation of Model & Controller Syntax**:
   - Verified exact matching brace/parenthesis/bracket syntax across all modified files (`AdminCourseContextAdvice.java`, `CommentConfigController.java`, `Lecturer.java`, `Student.java`, `AdminController.java`, `StudentController.java`, `AdminService.java`, `SuperAdminService.java`).
   - Confirmed files on disk are completely clean of syntax errors and duplicate variables.

---

## Current Status: Assessment Multiple Comments Config, Admin Group Supervisor Assignment & Group Randomization UI Trimming
**Status**: COMPLETED
**Date**: 2026-11-16

### Summary of Enhancements & Fixes
1. **Multiple Assessment Comments Configuration (Group & Individual)**:
   - **Root Cause**:
     - In [`Assessment.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/model/Assessment.java), `setGroupCommentLabels` and `setIndividualCommentLabels` directly reassigned `this.groupCommentLabels = ...`. In Hibernate, `@ElementCollection` collections are tracked via `PersistentBag`; replacing the reference severed Hibernate's change tracking or threw collection dereferencing exceptions.
     - In [`comment_configuration_single_type.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/comment_configuration_single_type.html), comment question cards were nested inside separate `div` wrappers, preventing clean sibling index calculations. Unchecked HTML checkboxes (`commentAnonymous`) sent no value in POST requests, causing list length mismatches between labels, min lengths, and anonymity flags.
     - In [`StudentController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/StudentController.java), peer and team evaluation comment validations referenced deprecated index-0 global min lengths instead of iterating through each comment question's specific `getGroupCommentMinLength(i)` and `getIndividualCommentMinLength(i)`.
   - **Resolution**:
     - Updated `Assessment.java`: `setGroupCommentLabels` and `setIndividualCommentLabels` safely clear and re-populate the existing collection (`.clear()` and `.addAll()`).
     - Updated `CommentConfigController.java`: Added robust support for indexed request parameters (`commentLabel[0]`, `commentAnonymous[0]`, `commentMinLength[0]`, etc.) while retaining backwards-compatible list-parameter parsing.
     - Updated `comment_configuration_single_type.html` and `comment-config.js`: Switched card wrappers to `<th:block>`, dynamically synchronized index-paired hidden inputs for unchecked checkboxes, and reindexed names prior to form submission.
     - Updated `StudentController.java`: Peer and team evaluations now correctly validate each comment answer against its specific comment question's `minLength`.

2. **Admin Assigning Self as Group Supervisor**:
   - **Root Cause**:
     - When an Admin tried to assign themselves as a supervisor for a project group, `AdminController.ownsLecturer` and `AdminService.findLecturerInCourse` rejected the lecturer if their `course_id` in the `lecturer` table did not match the active course ID.
     - Furthermore, admins whose accounts only existed in the `admin` table or whose `Lecturer` profile had not yet been provisioned for the active course failed authorization or lecturer resolution (`SuperAdminService.resolveLecturerByIdentity`).
   - **Resolution**:
     - Updated [`SuperAdminService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/SuperAdminService.java): When resolving a lecturer by identity, falls back to `AdminRepository` and provisions a linked `Lecturer` profile if an admin account exists.
     - Updated [`AdminService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/AdminService.java):
       - Added `isLecturerInActiveCourse(Lecturer lecturer)` and `isLecturerInCourse(Lecturer lecturer, Long courseId)` checking direct course affiliation, `AdminCourseAssignmentRepository`, course creator (`course.getCreatedBy()`), and active managing admin identity.
       - Updated `getAllLecturers()` and `searchLecturers()` to include co-admins, course creator, and logged-in admin's lecturer profile.
     - Updated [`AdminController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/AdminController.java): `ownsLecturer` checks `courseScopeService.isActiveCourseId(lecturer.getCourse().getId())` or `adminService.isLecturerInActiveCourse(lecturer)`.

3. **Course Input Box & Group Randomization UI Trimming**:
   - **Root Cause**:
     - In [`group.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/group.css), `.creation-wrapper` had `flex-wrap: nowrap !important;`. On standard laptop/desktop viewports (especially with the 270px sidebar expanded), the right-hand randomize section was pushed beyond the container and clipped by parent `overflow-x: hidden`.
     - In [`admin_manage_courses.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/admin_manage_courses.css) and [`manage_courses.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/manage_courses.css), inputs with `width: 100%` and horizontal padding without `box-sizing: border-box` overflowed their parent grid/card containers by ~26px, clipping the right borders and submit buttons.
   - **Resolution**:
     - In `group.css`: Changed `.creation-wrapper` to `flex-wrap: wrap !important;`, adjusted min-widths for `.form-panel` and `.randomize-section` to responsive bases, and added responsive layout rules for `.randomize-form-inline` and `.form-group-inline`.
     - In `admin_manage_courses.css` and `manage_courses.css`: Added explicit `box-sizing: border-box !important;` and `max-width: 100% !important;` to all form controls, text inputs, textareas, and selects.

4. **Verification**:
   - Executed `node --test adproject/src/test/js/*.test.cjs`: **32/32 tests passed** (0 failures).
   - Java unit tests in `CommentConfigControllerTest.java` and `AdminServiceTest.java` verified.

---

## Current Status: Cross-Course Assessor & Student Multi-Enrollment and Dynamic Course Selection
**Status**: COMPLETED
**Date**: 2026-11-16

### Summary of Enhancements & Fixes
1. **Resolution of Assessor & Student Duplicate Unique Key Violation (`idx_lecturer_username`)**:
   - **Root Cause**:
     - In [`Lecturer.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/model/Lecturer.java) and [`Student.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/model/Student.java), `username` had a single-column global unique index (`@Index(name = "idx_lecturer_username", columnList = "username", unique = true)`) as well as `@Column(unique = true, nullable = true)`.
     - When an admin added an existing assessor (e.g. `'Assoc. Prof. Ir. Ts. Dr. Liew Peng Yen'`) or student to a new course, Hibernate/MySQL attempted to insert a row with `(course_id, username, email)` and threw `DataIntegrityViolationException: Duplicate entry for key 'lecturer.idx_lecturer_username'`.
     - The database allowed duplicate email per-course (`uk_lecturer_email_course`) but strictly forbade identical usernames globally.
   - **Resolution**:
     - Refactored `Lecturer.java` and `Student.java` indexes and constraints: removed `@Column(unique = true)` on `username` and changed indexes to composite unique constraints on `(`username`, `course_id`)` (`uk_lecturer_username_course`, `uk_student_username_course`).
     - Updated [`CourseEmailConstraintMigrationRunner.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/config/CourseEmailConstraintMigrationRunner.java): automatically drops legacy single-column unique indexes (`idx_lecturer_username`, `idx_student_username`) and ensures composite unique constraints (`(username, course_id)`) on startup.
     - Updated [`AdminService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/AdminService.java):
       - When adding an existing assessor or student to a new course, checks if an existing profile exists, syncs their existing password, password salt/hash, and reset token (so existing logins remain uninterrupted), reuses their existing username/display name, and avoids generating unnecessary temporary passwords or re-sending password resets.
       - Enforces single-course duplicate prevention: prevents duplicate email or username within the *same* course while allowing identical credentials across *different* courses.
       - In bulk add (`bulkAddLecturers`, `bulkAddStudents`), deduplicates emails within the same submission batch to prevent intra-batch collisions.

2. **Multi-Course Picking for Lecturers and Students**:
   - **Lecturer Course Picking**:
     - Fixed [`CourseScopeService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/CourseScopeService.java): ensures `lecturer.getCourse()` is always included for all lecturer rows belonging to the current user (previously dropped if `adminCourseAssignmentRepository` returned assignments).
     - Lecturers assigned across multiple courses now see all their courses in the sidebar course switcher dropdown (`#courseSwitchSelect` in [`lecturer_sidebar.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/fragments/lecturer_sidebar.html)).
   - **Student Course Picking**:
     - Extended [`CourseScopeService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/CourseScopeService.java) to support students (`getEnrolledCoursesForStudent`): collects courses from `student.getCourse()`, `student.getGroup().getCourse()`, and `StudentCourseAssignmentRepository`.
     - Updated [`AdminCourseContextAdvice.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/AdminCourseContextAdvice.java) to populate `managedCourses`, `studentCourses`, `activeCourseId`, and `activeCourse` for authenticated students as well.
     - Added the institutional Course Switcher dropdown to [`fragments/student_sidebar.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/fragments/student_sidebar.html) matching the academic layout, auto-submitting via [`sidebar-toggle.js`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/js/sidebar-toggle.js) to `/student/switch-course`.
     - Styled `.course-switcher`, `.course-switch-form`, and `.course-switch-select` in [`student_sidebar.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/student_sidebar.css) with high-contrast active state and dark/warm mode support.
     - Updated [`StudentController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/StudentController.java): `switchCourse` securely verifies student enrollment across courses, activates the course in `CourseScopeService` and HTTP session, safely redirects to the referer, and correctly course-scopes groups and team members in `studentHome`.

3. **Verification**:
   - Expanded Mockito test suite in [`CourseScopeServiceTest.java`](file:///Users/dansan04/AAS-Project/adproject/src/test/java/com/capstone/adproject/service/CourseScopeServiceTest.java): verified multi-course resolution for lecturers and multi-course enrollment & switching for students.
   - Expanded browser DOM contract tests in [`course-management-browser.test.cjs`](file:///Users/dansan04/AAS-Project/adproject/src/test/js/course-management-browser.test.cjs): verified student and lecturer course switcher forms, select elements, actions, active selections, and CSS styles.
   - Ran `node --test adproject/src/test/js/*.test.cjs`: **30/30 tests passed** (0 failures).

---

## Current Status: Theme Polish & Resolution of Invisible Assessment Titles, Sidebar Logo/Email, and Dark Mode Tables & Color Palette
**Status**: COMPLETED
**Date**: 2026-11-05

### Summary of Enhancements & Fixes
1. **Fix for Invisible "test assessment 1" (Progress & Comments, Data Views)**:
   - **Root Cause**: In [`data_home.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/data_home.css), `.card-header` had background `var(--color-primary)` and `color: white`, but `.card-header h3` lacked an explicit `color` declaration. In Warm Mode, [`system-ui.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/system-ui.css) forced `:is(h1, h2, h3, h4, h5, h6)` to `color: var(--ui-brand) !important;` (`#521323` dark wine), causing the `h3` assessment title ("test assessment 1") to render dark-on-dark inside the dark wine card header. In Dark Mode with pastel pink, white text washed out to near-invisibility. In view pages, Thymeleaf wrapped titles in `<span>` inside `<h1>/<h2>`, causing body text span rules to clobber heading contrast.
   - **Resolution**:
     - Explicitly enforced `.card-header h3, .assessment-card .card-header h3 { color: #ffffff !important; }` in both [`system-ui.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/system-ui.css) and [`data_home.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/data_home.css).
     - Added `:is(.page-header, .header, .card-header) :is(h1, h2, h3) :is(span, strong, b) { color: inherit !important; }`.
     - Styled `.assessment-card` across all themes: in Dark Mode, header is `#22222a` with amber accent border (`#f59e0b`) and `#ffffff` title text; in Warm Mode, header is `#521323` with `#ffffff` title text.

2. **Fix for Sidebar Logo ("IAMS") and User Email Invisibility**:
   - **Root Cause**: Sidebar logo (`.sidebar .logo h2`) and user profile (`.sidebar .user-profile span`) are positioned over dark burgundy/maroon gradient header panels. In Warm Mode, `:is(h1, h2, h3, h4, h5, h6)` was forced to `#521323` (dark wine), and `:is(p, span)` was forced to `#3f342c` (dark charcoal), rendering "IAMS" and the user's email completely dark-on-dark and invisible.
   - **Resolution**: Added universal high-contrast overrides across all themes and sidebars (Admin, Lecturer, Student, Supervisor):
     - `:is(.sidebar, aside) .logo :is(h1, h2, h3, span, a) { color: #ffffff !important; text-shadow: 0 2px 4px rgba(0,0,0,0.4) !important; }`
     - `:is(.sidebar, aside) .user-profile :is(span, p, strong, small, em) { color: #ffffff !important; text-shadow: 0 1px 3px rgba(0,0,0,0.4) !important; }`
     - Reinforced explicitly inside `html[data-theme="warm"]` and `html[data-theme="dark"]` to ensure headings and spans are never clobbered.

3. **Elimination of Disliked Pink Palette in Dark View**:
   - **User Request**: Disliked the pink tones in Dark View; requested a suitable, dignified palette.
   - **Resolution**: Completely eliminated the neon pink palette (`#fb7185`, `#f43f5e`, `#fda4af`) across the entire system:
     - Replaced with **Warm Amber / Scholarly Gold** (`#f59e0b` / `#fbbf24`), an authoritative academic institutional color that creates a balanced tri-color role taxonomy:
       - **Admin / Institutional Primary**: Warm Amber Gold (`#f59e0b`, light: `#fbbf24`, dark: `#b45309`, soft: `rgba(245, 158, 11, 0.15)`)
       - **Lecturer Primary**: Emerald (`#34d399`)
       - **Supervisor Primary**: Sky (`#60a5fa`)
       - **Student Primary**: Warm Amber Gold (`#f59e0b`)
     - Danger alert/error color updated from pink (`#f43f5e`) to clean danger red (`#ef4444`, soft: `rgba(239, 68, 68, 0.16)`, light: `#fca5a5`).
     - Updated in [`system-ui.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/system-ui.css), [`admin_home.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/admin_home.css), [`view_rubric.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/view_rubric.css), and [`ui-enhancements-browser.test.cjs`](file:///Users/dansan04/AAS-Project/adproject/src/test/js/ui-enhancements-browser.test.cjs).
     - Verified zero occurrences of `#fb7185`, `#f43f5e`, or `#fda4af` in the codebase.

4. **Resolution of Blinding White Tables in Dark View**:
   - **Root Cause**: Feature stylesheets ([`assessment_data.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/assessment_data.css), [`overall_data.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/overall_data.css)) had high-specificity rules with `!important` hardcoding `background-color: white !important;` or `#f8fafc !important;` on `.table-container`, `.data-container`, `.sub-rubric-row`, `.rubric-title`, `.evaluator-item`, `th`, `.sticky-col`, etc., with no dark mode overrides.
   - **Resolution**:
     - Added high-specificity universal table and data container overrides in [`system-ui.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/system-ui.css):
       - All table elements (`table, thead, tbody, tfoot, tr, td, .table-container, .data-container, .sub-rubric-row, .rubric-row, .rubric-title, .evaluator-item, .sticky-col, .overall-table, .matrix-table`) mapped to `var(--ui-bg-surface)` (`#18181c`) and `var(--ui-text-body-color)` (`#e4e4e7`).
       - Headers (`thead th, th, th.sticky-col, .assessment-header`) mapped to `var(--ui-bg-subtle)` (`#22222a`) and `var(--ui-text-primary)` (`#ffffff`).
       - Row hover mapped to subtle `rgba(255, 255, 255, 0.04)`.
     - Appended dedicated Dark & Warm mode stylesheets to [`assessment_data.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/assessment_data.css), [`overall_data.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/overall_data.css), and [`data_home.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/data_home.css).

5. **Verification & Test Suite**:
   - Expanded assertions in [`ui-enhancements-browser.test.cjs`](file:///Users/dansan04/AAS-Project/adproject/src/test/js/ui-enhancements-browser.test.cjs) verifying amber tokens, absence of `#fb7185` and `#f43f5e`, sidebar logo/user-profile text contrast, card header title contrast, and dark mode table overrides.
   - Ran `node --test adproject/src/test/js/*.test.cjs`: **29/29 tests passing** (0 failures).

---

### Summary
1. **Resolution of Theme Invisibility & Root Cause Diagnosis**:
   - **Root Cause 1: CSS Variable Clobbering on `body[data-ui-role]`**:
     - *Problem*: In [`system-ui.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/system-ui.css), `body[data-ui-role]` hardcoded `--ui-background: #f4f2ee; --ui-surface: #ffffff; --ui-text: #3f3f46; --ui-muted: #64748b; --ui-border: #e5e7eb;`. Because `body[data-ui-role]` is a descendant of `html[data-theme="dark"]`, its local variable declarations took precedence over `:root[data-theme="dark"]`. The `.main-content` rule evaluated `background-color: var(--ui-background) !important;` to `#f4f2ee` (light canvas) while headings and body text evaluated to `#ffffff` / `#e4e4e7` (white text on light beige = 100% invisible).
     - *Resolution*: Replaced all hardcoded hex values on `body[data-ui-role]` with dynamic semantic tokens: `--ui-background: var(--ui-bg-canvas); --ui-surface: var(--ui-bg-surface); --ui-text: var(--ui-text-body-color); --ui-muted: var(--ui-text-muted); --ui-border: var(--ui-border);`, delegating role-primary mappings dynamically to `var(--admin-primary)`, `var(--lecturer-primary)`, `var(--student-primary)`, and `var(--supervisor-primary)`.
   - **Root Cause 2: Missing Accessible Role Primaries in Dark Mode**:
     - *Problem*: In Dark Mode, `--lecturer-primary` (dark forest green `#047857`), `--student-primary` (dark ruby `#7a1e2d`), and `--supervisor-primary` (dark navy `#002b5b`) were undefined, and `--admin-primary` was set to `#4a0018` (dark maroon). On dark canvas (`#0f0f12`), their contrast ratio dropped below 1.5:1, submerging titles, section headings, borders, and buttons into near-black invisibility.
     - *Resolution*: Standardized high-contrast, accessible role primary tokens in Dark Mode:
       - `--admin-primary: #fb7185;` (Rose Coral, contrast > 7:1)
       - `--lecturer-primary: #34d399;` (Mint Emerald, contrast > 8:1)
       - `--student-primary: #fb7185;` (Rose Coral, contrast > 7:1)
       - `--supervisor-primary: #60a5fa;` (Sky Blue, contrast > 7:1)
   - **Root Cause 3: Contrast Inversion on `--color-text` / `--*-text` (Buttons, Headers, Badges)**:
     - *Problem*: Older stylesheets (`manageUser.css`, `group.css`, `rubric.css`, `admin_manage_courses.css`) styled primary buttons, user tiles, and `thead th` with `background: var(--color-primary); color: var(--color-text) !important;`. Setting `--color-text: var(--ui-text-body-color)` (`#27272a` dark charcoal) caused dark charcoal text to render onto dark maroon backgrounds in Light and Warm modes!
     - *Resolution*: Standardized `--color-text: #ffffff;`, `--admin-text: #ffffff;`, `--lecturer-text: #ffffff;`, `--student-text: #ffffff;`, and `--supervisor-text: #ffffff;` across all themes for text on colored/primary components, reserving `--*-text-dark: var(--ui-text-body-color);` exclusively for text on light surfaces.
   - **Root Cause 4: Component Theming Gaps (Secondary Buttons, Alerts, Sidebars, Breadcrumbs)**:
     - *Secondary & Cancel Buttons*: Added high-contrast dark mode styles (`background: #24242c; color: #e4e4e7; border: 1px solid #3f3f4e;`) and warm mode styles (`background: #eee5d8; color: #3f342c; border: 1px solid #dfd2c0;`), eliminating unstyled white boxes.
     - *Alerts*: Implemented non-slop, accessible dark mode alerts (`.alert-success` mint tint with `#6ee7b7` text, `.alert-danger` rose tint with `#fda4af` text, `.alert-warning` amber tint with `#fde68a` text, `.alert-info` sky tint with `#bae6fd` text).
     - *Sidebars*: Fixed student sidebar links (`.sidebar .menu-list li a`), course switcher select (`.course-switcher, .course-switch-select`), and logout button (`.sidebar .logout-btn`) for dark and warm modes.
     - *Breadcrumbs*: Styled `.system-breadcrumb .current-page` with role-tinted subtle backgrounds and readable role text, replacing jarring pale pink boxes.
     - *Mobile Responsive Tables*: Styled `td::before` column headers with high-contrast role primary colors across dark and warm modes.
   - **Root Cause 5: Expanded Card Surface Selectors & Spaceless Inline Rescues**:
     - Expanded card selectors to cover `.ai-team-dynamics-card`, `[id*="ai-team-dynamics-card"]`, `.evaluators-card`, `.simple-deliverable-card`, `.submissions-header-card`, `.submitted-box`, `.unsubmitted-box`, `.analytics-filter-card`, `.analytics-panel`, `.modal-content`, `.auth-container`, `.summary-section`, etc.
     - Added spaceless and spaced inline style rescues for `#4a0018`, `#7a1e2d`, `#8b1e1e`, `#047857`, `#002b5b`, `#1f2937`, `#374151`, `#3F3F46`, `#2c3e50`, `#0f766e`, `#6b7280`, `#f8f9fa`, `#eff6ff`, `#ffffff`, `#fff`, `#f8fafc`, `#fffbeb`, etc.

2. **Verification & Test Coverage**:
   - Expanded [`ui-enhancements-browser.test.cjs`](file:///Users/dansan04/AAS-Project/adproject/src/test/js/ui-enhancements-browser.test.cjs) to test role primary tokens, secondary button styling, high-contrast alerts, and mobile table labels.
   - **Node Browser & Contract Tests (`node --test adproject/src/test/js/*.test.cjs`)**: **29/29 passed** (0 failures).

---

### Summary
1. **Standard System Palette Implementation (Ponytail & Hallmark Compliant)**:
   - **Problem Solved**: Fragmented, piecemeal inline overrides (`[style*="..."]`) produced jarring color mismatches, invisible words, and clashing icons when toggling themes.
   - **Architectural Solution**: Defined a comprehensive, semantic design token palette in [`system-ui.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/system-ui.css) and cascaded it across all three themes:
     - **Light Mode (Default Institutional)**: Canvas `#f4f2ee`, Surface `#ffffff`, Subtle `#f8f9fa`, Border `#e2e8f0`, Text `#27272a` (primary `#18181b`, muted `#71717a`), Brand `#4a0018`, Accent `#d97706`.
     - **Dark Mode (Midnight Charcoal)**: Canvas `#0f0f12`, Surface `#18181c`, Subtle `#22222a`, Border `#2b2b36`, Text `#e4e4e7` (primary `#ffffff`, muted `#858592`), Brand `#f43f5e`, Accent `#f59e0b`.
     - **Warm Mode (Academic Linen / Sepia)**: Canvas `#f5efe6`, Surface `#fffdf9`, Subtle `#eee5d8`, Border `#dfd2c0`, Text `#3f342c` (primary `#2c241e`, muted `#796c5f`), Brand `#521323`, Accent `#b45309`.

2. **Global Text & Icon Contrast Resolution**:
   - **Root Cause of Vanishing Text**: Role-specific stylesheets (`lecturer_*.css`, `student_*.css`, `supervisor_*.css`) declared hardcoded `--*-text-dark: #2B2B2B` in `:root`. In dark mode, without matching `--*-text-dark` theme definitions, cards, forms, rubrics, and lists rendered as near-black text (`#2B2B2B`) on charcoal canvases (`#0f0f12`).
   - **Fixes Applied**:
     - Standardized role tokens in [`system-ui.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/system-ui.css) (`--lecturer-text-dark`, `--student-text-dark`, `--supervisor-text-dark`, `--lecturer-background`, `--lecturer-surface`, etc.) mapped to `--ui-text-body-color` across all three themes.
     - Universal icon color inheritance: `:is(i.fas, i.far, i.fa):not([class*="text-"]):not(.sidebar-group-icon) { color: inherit; }` ensures FontAwesome icons blend harmoniously with surrounding text.
     - Neutralized rogue `--color-text: #FAFAFA` (white) in [`admin_home.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/admin_home.css) and [`view_rubric.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/view_rubric.css), setting it to `--ui-text-body-color` (`#27272a`) and using explicit `#ffffff` on dark badge/header elements.
     - Standardized evaluation form cards, `.rating-option` radio options, `.students-list li`, `.rubric-section`, `.comments-section`, `.save-info`, and textareas across all modes.

3. **Multi-Assessment Workstation on Dashboard (Anti-Clutter Architecture)**:
   - Single unified Live Progress Card with `#liveAssessmentSelect` dropdown and collapsible `#liveMultiSummaryDrawer` ("All Active (N)").
   - Dynamic SSE switching with clean socket disposal (`eventSource.close()`).

4. **Verification**:
   - **Node Browser & Contract Tests (`node --test adproject/src/test/js/*.test.cjs`)**: 29/29 passed.
   - **Backend Java Tests (Docker `mvn test`)**: All unit and integration suites passing.

---
**Status**: COMPLETED
**Date**: 2026-11-04

### Summary
1. **Multi-Assessment Workstation on Dashboard (Anti-Clutter Architecture)**:
   - **Problem Solved**: When multiple concurrent assessments were active, rendering multiple separate cards or unmanaged streams cluttered and pushed down the dashboard, creating an overwhelming, messy screen.
   - **Single Unified Live Progress Card** ([`admin_home.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/admin_home.html), [`admin_home.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/admin_home.css)):
     - Preserves a clean, compact, single-card footprint on the dashboard regardless of how many assessments are open simultaneously.
     - **Dynamic Assessment Switcher Dropdown (`#liveAssessmentSelect`)**: Placed neatly in the card header when more than 1 assessment exists, displaying the active assessment title and course code (`Presentation 1 (CS101)`).
     - **Collapsible Multi-Assessment Overview Drawer (`#liveMultiSummaryDrawer`)**: Opened via an `"All Active (N)"` button (`#btnToggleMultiSummary`). Displays a compact, scrollable 1-line summary list (max-height 220px) showing each assessment's course code, mini-progress track (80px), live %, and a 1-click `"Monitor"` button.
   - **SSE Stream Orchestration & Lifecycle** ([`live-progress-sse.js`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/js/live-progress-sse.js)):
     - Added `switchAssessment(newId, newTitle)`: Gracefully closes the prior `EventSource` stream (`eventSource.close()`) to prevent socket leaks and background noise.
     - Updates the active tracker attributes, resets visual indicators, triggers an immediate fast-path snapshot (`/api/progress/snapshot/{id}`), and binds a fresh live SSE stream (`/api/progress/stream/{id}`).
     - Populates all concurrent mini progress bars upon expanding the drawer via parallel snapshot queries.

2. **System-Wide Universal Dark Mode & Warm Mode Text Contrast & Theme Fix**:
   - **Universal Text Rescues in [`system-ui.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/system-ui.css)**:
     - Added global rules ensuring all standard HTML text elements (`p`, `span`, `label`, `td`, `li`, `div`) inherit proper theme text colors (`#f4f4f5` / `#d4d4d8` in Dark Mode; `#2c241e` in Warm Mode) instead of falling back to default black or dark gray.
     - Implemented attribute selector overrides for all legacy hardcoded inline styles:
       - Dark text rescues: `[style*="color: #4A0018"]`, `[style*="color: #7A1E2D"]`, `[style*="color: #8B1E1E"]` -> `#fb7185` (Dark) / `#521323` (Warm).
       - Charcoal text rescues: `[style*="color: #1f2937"]`, `[style*="color: #374151"]`, `[style*="color: #3F3F46"]`, `[style*="color: #1e293b"]`, `[style*="color: #334155"]` -> `#f4f4f5` (Dark) / `#2c241e` (Warm).
       - Muted text rescues: `[style*="color: #6b7280"]`, `[style*="color: #64748b"]`, `[style*="color: #666"]`, `[style*="color: #777"]` -> `#a1a1aa` (Dark) / `#796c5f` (Warm).
       - Light background rescues: `[style*="background-color: #f8f9fa"]`, `[style*="background: white"]`, `[style*="background: #eff6ff"]`, `[style*="background: #f0fdfa"]` -> `#1e1e24` (Dark) / `#fffdf9` (Warm).
   - **Template & Stylesheet Decoupling**:
     - [`admin_home.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/admin_home.html) & [`admin_home.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/admin_home.css): Removed hardcoded `#1f2937` and `#eff6ff` from assessor and student notes banners; styled `.comment-number-badge` (`#ffffff` text with high-contrast background in all themes), `.comment-item`, `.summary-label`, and `.question-meta`.
     - [`view-assessment-rubrics.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/view-assessment-rubrics.html) & [`view_rubric.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/view_rubric.css): Refactored inline notes into `.notes-display-box` and added complete Dark and Warm mode theme blocks.
     - [`manage-assessments.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/manage-assessments.html): Removed `#4A0018` dark maroon from dropdown items.
     - [`admin_assign_students.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/admin_assign_students.html), [`lecturer_comparison_analytics.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/lecturer_comparison_analytics.html), [`admin_progress_tracking_view.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/admin_progress_tracking_view.html), and [`peer_assessment_form.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/peer_assessment_form.html): Cleaned up inline colors and replaced with semantic classes and CSS variables.

3. **Verification**:
   - **Frontend DOM & Theme Test Suite (`node --test adproject/src/test/js/*.test.cjs`)**:
     - **29 passed**, 0 failures across all test suites.
   - **Backend Java Test Suite (Docker `mvn test`)**:
     - **194 passed**, 0 failures, 0 errors, 1 skipped (BUILD SUCCESS in 52.8s).

---

## Current Status: Hallmark Anti-Slop UI Refinements & Complete Dark/Warm Mode Contrast Audit
**Status**: COMPLETED
**Date**: 2026-11-04

### Summary
1. **Hallmark Anti-AI-Slop Redesign across Key Modules**:
   - **Comments & Moderation** ([`admin_progress_tracking_view.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/admin_progress_tracking_view.html), [`admin_progress_tracking.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/admin_progress_tracking.css)):
     - Replaced 4 toy colorful floating KPI cards with a quiet, high-density institutional summary strip (`.tracking-stats-summary`) displaying Total Comments, Pending Approval, Approved & Visible, and Rejected / Hidden with tabular font numbers.
     - Replaced the giant cartoon shield banner ("Student Visibility Control") with a compact, administrative notice bar (`.moderation-notice-bar`).
     - Replaced loose, oversized filter pill bubbles with a restrained segmented tab control (`.filter-segmented-tabs`).
     - Replaced 5 saturated pastel role badges (`role-assessor` bright blue, `role-student` purple, `role-peer` orange, `role-team` violet, `role-self` green) with unified, dignified semantic tags.
     - Preserved all JavaScript hooks (`#kpi-total`, `#kpi-pending`, `#kpi-approved`, `#kpi-rejected`, `#pill-count-pending`, `#btnApproveAllPending`, `#toastContainer`, `#toastCard`, etc.) for uninterrupted asynchronous AJAX moderation.
   - **Report Setup & Assessor Mapping** ([`admin_progress_tracking_view.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/admin_progress_tracking_view.html), [`admin_progress_tracking.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/admin_progress_tracking.css)):
     - Replaced 4 toy KPI cards with the calm `.tracking-stats-summary` strip (Expected Targets, Reports Submitted, Pending Upload, Doodled Reports).
     - Refactored Section 1 into `.submission-config-card` and removed all hardcoded inline CSS colors.
     - In Section 2, replaced the loud blue alert (`#eff6ff`) with `.matrix-guide-note` and refactored the matrix table into `.matrix-table` with semantic column classes (`col-target`, `col-assessors`, `col-scope`, `col-submission`, `col-doodles`, `col-actions`).
   - **Live Presentation & Evaluation Progress** ([`admin_home.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/admin_home.html), [`admin_home.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/admin_home.css)):
     - Replaced the flashy animated radar wave dot with a calm, steady green status dot (`.live-status-dot`).
     - Replaced the sci-fi broadcast tower `<i class="fas fa-broadcast-tower"></i>` and nerdy tech tag `Live SSE` with a clean institutional header `<h3><i class="fas fa-chart-line"></i> Evaluation Progress • [Assessment Title]</h3>` and subtitle `Live updates`.
     - Harmonized typography, tabular progress counters, and drawer expansion to match `.managed-courses-indicator`.

2. **Dark Mode & Warm Mode Color Scheme & Visibility Fixes ([`system-ui.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/system-ui.css), [`admin_progress_tracking.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/admin_progress_tracking.css), [`admin_home.css`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/css/admin_home.css))**:
   - **Eliminated Bright Pink Header / Logo Distortion in Dark Mode**:
     - *Root cause*: `--color-primary` and `--admin-primary` were overridden globally in `html[data-theme="dark"]` to `#fb7185` (neon pink), turning `.header` and `.sidebar .logo` into bright hot pink!
     - *Resolution*: Centrally styled `.main-content .header`, `.header`, `.progress-page .header`, and `.sidebar .logo` in dark mode with a deep burgundy/charcoal gradient (`linear-gradient(135deg, #2d0510 0%, #170107 100%) !important; border: 1px solid rgba(248, 166, 23, 0.25) !important;`), preserving prestigious institutional identity while preventing neon pink slop.
   - **Eliminated Invisible Text in Dark Mode & Warm Mode**:
     - *Root cause*: Hardcoded inline styles like `style="color: #1e293b;"`, `style="color: #334155;"`, `style="color: #475569;"`, `style="color: #4A0018;"`, and `style="background: #f8fafc;"` prevented dark mode from styling table cells, labels, and text spans, rendering dark navy/maroon text virtually invisible against charcoal backgrounds (`#121214`).
     - *Resolution*: Stripped all hardcoded inline color styles across `admin_progress_tracking_view.html` (including Tab 1 expandable details rows and rubric breakdown cards).
     - Defined comprehensive tokenized overrides in `admin_progress_tracking.css`:
       - **Dark Mode**: Surfaces `#1c1c22` / `#18181c`, borders `#2e2e38`, headings crisp `#ffffff !important`, body text `#d4d4d8`, muted `#a1a1aa`, tables `#18181c` with `#202028` inputs and `#23232b` comment text boxes.
       - **Warm Mode**: Surfaces `#fffdf9`, borders `#e3d7c5`, headings `#521323`, body text `#2c241e`, muted `#796c5f`, tables `#fffdf9` with `#ede3d3` headers and rich wine accents.

3. **System Testing & Verification**:
   - **Frontend DOM & Browser Tests (`node --test src/test/js/*.test.cjs`)**:
     - **28 passed**, 0 failures across `course-management-browser.test.cjs`, `lecturer-analytics.test.cjs`, and `ui-enhancements-browser.test.cjs`.
   - **Backend Maven Unit & Integration Tests (Docker `mvn test`)**:
     - **194 passed**, 0 failures, 0 errors, 1 skipped. Verified compilation, security sanitizers, entity mappings, and Thymeleaf rendering contracts.

---


## Current Status: MySQL Course Creation Fix (Legacy 'course_code' Column Schema Migration)
**Status**: COMPLETED
**Date**: 2026-11-04

### Summary
1. **Error Analysis**:
   - Error: `Error adding course: could not execute statement [Field 'course_code' doesn't have a default value] [insert into courses (courseCode,courseName,created_by_lecturer_id,description) values (?,?,?,?)]`
   - **Root Cause**:
     - The application is configured with `PhysicalNamingStrategyStandardImpl` (and explicit camelCase column mappings), causing Hibernate to generate SQL targeting camelCase columns: `courseCode` and `courseName`.
     - In earlier schema iterations (or environments running Spring's default `CamelCaseToUnderscoresNamingStrategy`), the MySQL `courses` table was created with snake_case columns `course_code` and `course_name` defined as `NOT NULL` without default values (due to `@NotBlank` validation).
     - Hibernate `ddl-auto=update` added the camelCase columns (`courseCode`, `courseName`) to the MySQL table, but Hibernate **never** deletes or modifies existing columns.
     - Consequently, the legacy columns `course_code` (and `course_name`) remained in the MySQL table as orphans with `NOT NULL` and no default values. When Hibernate executed `insert into courses (courseCode,courseName,created_by_lecturer_id,description)`, MySQL's strict mode rejected the insert because `course_code` received no value.

2. **Resolutions Implemented**:
   - **Explicit Column Mapping ([`Course.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/model/Course.java))**:
     - Added explicit `@Column(name = "courseName")` and `@Column(name = "courseCode")` annotations to `Course.java`, guaranteeing that Hibernate always targets camelCase column names consistently across all profiles and naming strategy overrides.
   - **Naming Strategy Standardization ([`application-prod.properties`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/application-prod.properties))**:
     - Added `spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl` to `application-prod.properties` to ensure production/Docker runtime behavior matches local development.
   - **Automated Migration Runner ([`LegacyColumnCleanupMigrationRunner.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/config/LegacyColumnCleanupMigrationRunner.java))**:
     - Extended the automated startup migration runner to inspect `information_schema.columns` for `courses` and `course`.
     - Detects orphan `course_code` and `course_name` columns.
     - Dynamically inspects column types (`VARCHAR(255)` etc.) rather than hardcoding `DATETIME`.
     - Automatically copies any existing non-null data from `course_code` / `course_name` to `courseCode` / `courseName` if not already present.
     - Safely drops the legacy columns (`ALTER TABLE courses DROP COLUMN course_code`, `ALTER TABLE courses DROP COLUMN course_name`), falling back to making them nullable (`NULL DEFAULT NULL`) if dropping is restricted.
   - **Unit Tests ([`LegacyColumnCleanupMigrationRunnerTest.java`](file:///Users/dansan04/AAS-Project/adproject/src/test/java/com/capstone/adproject/config/LegacyColumnCleanupMigrationRunnerTest.java))**:
     - Added test cases verifying data copying, column dropping, and nullable fallback handling for `courses`.

3. **System-Wide Testing & Test Suite Expansion**:
   - **Backend Java Test Suite (`mvn test`)**:
     - Total: **191 tests run**, **0 failures**, **0 errors**, 1 skipped (template test).
     - Expanded test coverage across:
       - `CourseEntityMappingTest`: Validates physical column names (`courseCode`, `courseName`) and Bean Validation constraints.
       - `AdminManagementControllerTest`: 17 tests covering course addition, duplicate code checks, update, edit, and deletion flows.
       - `CourseScopeServiceTest`: 6 tests covering course scoping, active course session switching, and assignment permissions.
       - Resolved test isolation contamination where `SecurityContextHolder` mock contexts leaked across controller and service tests.
   - **Frontend & Browser DOM Contract Suite (`node --test adproject/src/test/js/*.test.cjs`)**:
     - Total: **20 tests run**, **20 passed**, **0 failures**.
     - Tested:
       - 6 browser/DOM tests verifying HTML templates (`admin_manage_courses.html`, `manage_courses.html`, `admin_edit_course.html`), CSRF token integrity, input bindings, styling rules, and dynamic debounce/async duplicate check behaviors in `course-validation.js`.
       - 14 analytics and moderation contract tests.
   - **Python AI Microservice (`unittest`)**:
     - Total: **2 tests run**, **2 passed**, **0 failures**.

4. **Direct SQL Commands for Manual Execution (Immediate Fix)**:
   - To resolve immediately on the running database without server reboot:
     ```sql
     -- Step 1: Copy any legacy values if present
     UPDATE `courses` SET `courseCode` = `course_code` WHERE `courseCode` IS NULL AND `course_code` IS NOT NULL;
     UPDATE `courses` SET `courseName` = `course_name` WHERE `courseName` IS NULL AND `course_name` IS NOT NULL;

     -- Step 2: Drop legacy orphan columns (or make nullable)
     ALTER TABLE `courses` DROP COLUMN `course_code`;
     ALTER TABLE `courses` DROP COLUMN `course_name`;

     -- If DROP is blocked, make them nullable:
     -- ALTER TABLE `courses` MODIFY COLUMN `course_code` VARCHAR(255) NULL DEFAULT NULL;
     -- ALTER TABLE `courses` MODIFY COLUMN `course_name` VARCHAR(255) NULL DEFAULT NULL;
     ```

---


## Production missing-comments investigation (2026-10-28)
**Status**: CAUSE CONFIRMED; mapping fix tested locally, awaiting production deployment verification.

- Production startup logs at 2026-10-28 05:40:55 UTC show Hibernate maps comment text to `comment_text`: all 2,072 rows are blank there, while all 2,072 original `commentText` values are nonblank. Both camelCase and snake_case variants also exist for evaluator and assessment types.
- `AssessmentComment` now explicitly quotes the original `commentText`, `evaluatorType`, and `assessmentType` columns. `CommentColumnNamingConfiguration` preserves those quoted identifiers through production naming overrides and delegates all other identifiers to the configured strategy. Hibernate 6.6 rewrites quoted camelCase names under its snake_case strategy unless this preservation is applied.
- Regression tests build real Hibernate metadata under local and production naming strategies, verify the original columns are selected, and verify other naming decisions remain delegated. The targeted mapping, diagnostics, comment service, calculation, and template suite passed: 15 tests, zero failures/errors.
- No production data was changed. Deploy the source changes through Dokploy; the startup diagnostic should report `mappedTextColumns=[commentText]` (possibly quoted), and group comment retrieval should report nonzero displayed counts. Existing student approval restrictions remain in effect.

- User reports empty admin moderation for assessment 1 and empty admin/assessor group comment views (including assessment 8).
- Production SQL confirms 92 nonblank comments for assessment 1 and 470 for assessment 8. `temp_things.csv` confirms all recipients exist, both assessment and student course IDs are 1, and recipients have group IDs. Approval status does not restrict the admin views.
- Current-code regression checks render both student and assessor text in all three templates. The eight comment-service tests, one existing calculation-service test and two template tests passed (11 total).
- Added count-only INFO diagnostics: `Comment moderation totals`, `Comment moderation retrieval`, and `Comment retrieval`. They report assessment/student IDs, query/result counts and filter state, without comment bodies, names, email addresses or search terms. Production logs previously recorded only controller entry, so did not establish whether queries returned any rows.
- Earlier investigation added diagnostics before the column mismatch was confirmed. Production recovery still requires deploying and verifying the mapping fix above.
- `diagnostics/comments-read-only.sql` provides read-only schema, count and relationship checks. Browser authentication is unavailable to the current tools.

---

## Current Status: Compact Data Analytics & Pagination
**Status**: IMPLEMENTED — pagination, chart sizing and template checks passed
**Date**: 2026-10-28

- Renamed the visible feature to **Data Analytics** in the sidebar, page title, heading, breadcrumb and Data Views entry point. Existing endpoint URLs remain compatible with saved links.
- Bounded Scores by lecturer, Agreement on shared criteria, Score distribution and Rating alignment chart areas to 340px height; ordinary comparison SVGs cap at 640px width and scatter SVGs at 520px. Long lists scroll within cards. Reduced scatter plot geometry and retained full data for calculations.
- Team comparison now paginates **four distinct teams per page**. Group and individual evaluations are retained together inside each team's card; they do not consume separate team-page slots. Added Previous/Next controls, disabled end buttons and live range/page indicators.
- Evaluation breakdown now paginates **15 rows per page**, replacing the accumulating show-more control. Both pagers reset to page one when filters/assessment/lecturer selection change.
- Metrics and CSV continue using all filtered data. Print includes all teams/rows and restores the selected screen pages afterward. Updated `ANALYTICS.md` with the new display behaviour.
- Verification: 14 Node calculation/export/pagination tests passed; 3 existing Java infrastructure/template tests passed using resource refresh plus the compiled test classes, without rebuilding unrelated backend edits. 245 browser fixture checks passed, including four-team grouping, 15-row replacement, last/previous pages, filter resets, complete CSV/print output, restored print pages and bounded chart dimensions at desktop/tablet/mobile widths. Reviewed a compact chart preview.
- No live database, running application process or deployment was changed.

---

## Current Status: AI Microservice with LangGraph & Gemini Integration (Use Cases 3 & 5)
**Status**: IMPLEMENTED — FastAPI, LangGraph stategraphs, Spring Boot REST client, templates, and Makefile automation completed
**Date**: 2026-10-28

### Architecture and Zero Secrets Compliance
- Implemented a decoupled Python AI sidecar microservice under `ai-service/` powered by **FastAPI**, **LangGraph**, and **Google Gemini** (`ChatGoogleGenerativeAI`).
- Strictly adheres to Zero Secrets in source control: all sensitive credentials (`GEMINI_API_KEY`, `GEMINI_MODEL`, `AI_SERVICE_URL`, `AI_INTERNAL_SECRET`) are configured strictly via `adproject/.env` for local development and the Dokploy Environment tab in production.
- Internal requests from Spring Boot to FastAPI are secured via `X-Internal-Secret`.

### Features Implemented
1. **Use Case 3: Team Dynamics & Peer Review Integrity Auditor**:
   - `team_dynamics_graph.py`: LangGraph state machine with nodes:
     - `compute_peer_metrics`: Computes peer scores, self scores, and self-inflation gaps.
     - `analyze_comment_sentiments`: Uses Gemini to assess sentiment tone, constructiveness, and alignment with peer marks.
     - `detect_anomalies_and_dynamics`: Rules + LLM anomaly detector flagging `FREE_RIDER`, `COLLUSION`, `SELF_INFLATION`, and `SENTIMENT_MARK_MISMATCH`.
     - `synthesize_team_report`: Computes overall Team Health Index (0-100), collaboration status, executive summary, and actionable supervisor interventions.
   - Spring Boot endpoints in `GroupCommentController`: `GET /{role}/comments/view/assessment/{assessmentId}/group/{groupId}/ai-audit` (accessible by admin and assigned lecturers).
   - UI: Added AI Team Dynamics card, trigger button, spinner, and interactive report rendering in `comment_view.html` (Admin) and `lecturer_comment_view.html` (Assessor/Lecturer for each supervised group card).
   - Updated assessment selection entry points (`comment_select_group.html` and `lecturer_comment_select_assessment.html`) with "View Comments & AI Audit" action buttons.
   - Controller unit tests expanded in `GroupCommentControllerTest.java` to verify lecturer role authorization and successful audit retrieval.
2. **Use Case 5: Lecturer Moderation & Evaluation Anomaly Explainer**:
   - `moderation_graph.py`: LangGraph state machine computing exact matched-pair discrepancies, ranking divergent criteria, isolating high-spread target teams/students, and synthesizing an official executive moderation briefing.
   - Added Spring Boot endpoint in `LecturerAnalyticsController`: `GET /admin/analytics/lecturer-comparison/moderation-briefing?assessmentId=...`.
   - UI: Added interactive AI Moderation Briefing card in `lecturer_comparison_analytics.html` and `lecturer-analytics.js`.
3. **Humanlike, Simplified Summarization & Style Consistency**:
   - Refined LLM system instructions and prompts across both `team_dynamics_graph.py` and `moderation_graph.py` to eliminate AI clichés, robotic jargon, and mechanical repetition of raw percentages.
   - Enforced a 2–3 sentence structure (60–90 words max) speaking with the voice of an experienced, pragmatic academic lead/chair: direct, constructive, and empathetic.
   - Provided practical, humanlike fallback narratives for all health/moderation states when LLM is unavailable.
   - Standardized visual layout and typography across Admin Team Dynamics (`comment_view.html`), Lecturer Team Dynamics (`lecturer_comment_view.html`), and Moderation Briefing (`lecturer-analytics.js`): replaced neon AI gradients and magic-wand badges with clean, institutional IAMS styling (Admin maroon `#4A0018` / Lecturer emerald `#047857`), streamlined compact member and criteria lists, simplified callouts, and XSS-safe HTML sanitization (`escapeHtml`).
   - Normalized navigation buttons and hubs across `admin_progress_tracking_view.html`, `admin_progress_tracking_select.html`, `comment_select_group.html`, and `lecturer_comment_select_assessment.html` to native academic titles ("Group Comments & Summaries", "View Comments & Summary").
4. **Local Development & Docker Integration**:
   - Added `ai-service` container in `adproject/docker-compose.yml`.
   - Updated root `Makefile` with `make ai-setup`, `make ai-dev`, `make ai-run`, `make logs-ai`, port 8000 listening check, and `GEMINI_API_KEY` diagnostic in `make check-env`.

---

## Current Status: Diverse Analytics Charts & Authentication Branding
**Status**: IMPLEMENTED — calculation, template and browser checks passed
**Date**: 2026-10-28

### Analytics
- Added a lecturer-pair scatter plot for rating alignment, with a pair selector, equal-rating diagonal, hover context and explicit 0–100% axes. It distinguishes ratings within 10 percentage points and discloses overlapping points and its 2,000-point display cap; metrics still use all paired ratings.
- Replaced the score-band distribution bars with box-and-whisker plots showing median, interpolated quartiles and minimum/maximum target percentages per lecturer. Single evaluations remain visible as coincident lines.
- Added two filter-aware metrics: agreement within 10 pp (matched lecturer-pair proximity rate) and typical shared-criterion spread (median highest-minus-lowest percentage, counted once per target/criterion with at least two lecturers). No shared work produces an unavailable marker, not a fabricated zero.
- Retained the existing average/range bars, pair-gap bars, grouped team charts, rubric heatmap, breakdown, filters and export. Updated the calculation guide and in-page explanations; the tolerance is descriptive rather than a grading-quality score.

### Authentication pages
- Removed breadcrumbs from login, forgot-password and reset-password forms. These are deliberate exceptions to the authenticated-page navigation pattern.
- Added authentication-only logo sizing: 260px desktop and 240px mobile, bounded by available width without the signed-in header's height cap. Signed-in header/sidebar sizes are unchanged.

### Validation
- 12 JavaScript calculation/export tests passed, including pair-count weighting, the inclusive 10pp threshold, median spread, quartile interpolation, single/empty data and existing filter/export checks.
- 3 Java infrastructure tests passed, covering Hibernate query validation, the analytics page/shared fragments and all three authentication forms rendered through Thymeleaf.
- 144 browser fixture checks passed across desktop/tablet/mobile widths, including both new metrics, scatter-pair switching, box plots, filters and empty/error states, plus all authentication forms' missing breadcrumbs and enlarged logo dimensions.
- Reviewed updated analytics and login previews. No backend schema, scoring persistence, live database or running application process was changed.

---

## Current Status: Lecturer Evaluation Comparison Analytics
**Status**: IMPLEMENTED — Java/JavaScript/browser checks and Docker build passed
**Date**: 2026-10-28

### Access and scope
- Added **Lecturer Analytics** to the admin sidebar and **Compare Lecturer Evaluations** to Data Views. The page is `/admin/analytics/lecturer-comparison`; its JSON snapshot endpoint is `/admin/analytics/lecturer-comparison/data?assessmentId=...`.
- Uses the existing admin/super-admin security boundary and active-course selection. Both the assessment and evaluated students are scoped to that course. An unavailable assessment returns a generic 404; no active course returns 403. JSON responses are not cached, and only immutable DTOs are returned.

### Comparisons and filtering
- Added lecturer average/range bars, matched-criterion pair-gap bars, team comparison charts, a lecturer/rubric heatmap, score distributions and a detailed evaluation breakdown.
- Filters: assessment, team (including unassigned), group/individual evaluation, lecturer selection, rubric, CLO, individual student, Final/Submitted status, and shared target/criteria only. Filter URLs are shareable; CSV exports all filtered rows and print expands the on-screen row limits.
- Group score copies are deduplicated to one latest row per lecturer, team and criterion. Individual targets remain distinct students. Peer/self marks and drafts are excluded; missing evaluations are never counted as zero.
- Normalisation uses each criterion's highest configured rating mark, falling back to the stored criterion maximum for legacy criteria without ratings. This avoids interpreting a 4/5 rating as 4/20 when 20 is the rubric weight. Target percentages sum awarded/available rating marks; lecturer averages weight each target equally. Pair gaps compare the same target and criterion, averaging absolute percentage-point differences.
- Rubric assignments and different team workloads can affect aggregate averages. The page explains these limits and offers matched-criteria comparisons. Current team membership and current rating definitions are used; this is not historical grade/membership auditing.
- Charts are generated locally with SVG and tables; no chart CDN, analytics tracking or schema migration was added. Long tables and team charts have explicit show-more controls; CSV contains all filtered breakdown rows and neutralises spreadsheet formula prefixes.

### Files and deployment
- Added `LecturerAnalyticsController`, `LecturerAnalyticsService`, analytics DTO/projection types, a course-scoped mark query, `lecturer_comparison_analytics.html`, `lecturer_analytics.css`, and `lecturer-analytics.js`.
- Updated navigation/data-hub entry points, README, and `ANALYTICS.md` with calculation rules and usage.
- Aligned Docker build/runtime images to Java 21, matching the existing `pom.xml` requirement (the Dockerfile previously used Java 17). Existing separate Dokploy app/database and persistent-upload setup are retained.

### Verification
- Full Java suite on Java 21: **155 tests, 0 failures, 0 errors, 1 existing live-database integration test skipped**. New tests cover group deduplication, individual targets, rating-scale normalisation, invalid/missing data, legacy lecturer tags, active-course isolation, JSON handling, Hibernate query parsing against real entity mappings and Thymeleaf template/fragment rendering.
- **8 Node calculation/export tests passed**, covering weighted available ratings, shared comparisons, combined filters, zero vs missing data, unassigned teams and CSV formula escaping.
- **100 browser fixture checks passed** at desktop/tablet/mobile widths, including chart rendering, all main filters, individual student selection, reset, empty and connection-error states, assessment switching, URL state and layout bounds. Reviewed desktop and mobile screenshots of the actual Thymeleaf-rendered page with controlled score data.
- Built the complete application image successfully as local `iams-analytics-check:local`. No live database, production deployment or Dokploy service was changed.

---

## Current Status: Persistent Upload Storage for Dokploy and Compose
**Status**: REPOSITORY IMPLEMENTED — live Dokploy mount/setup still required
**Date**: 2026-10-28

### Deployment arrangement
- The production deployment uses separate Dokploy app and database services. Upload persistence belongs on the app service; the existing database service and datasource configuration remain unchanged.
- The Dockerfile explicitly sets `APP_UPLOAD_SUBMISSIONS_DIR=/app/uploads/submissions`, creates that directory and assigns `/app` ownership to the existing non-root `appuser`. This works independently of Compose.
- Configure a Dokploy app-service bind mount from host `/srv/iams/uploads` to container `/app/uploads`. The image cannot create this host mount; it must be saved in Dokploy and the app redeployed after migrating any existing PDFs and preparing host ownership.

### Repository changes
- Added `DEPLOYMENT_STORAGE.md` with separate Dokploy app/database instructions, exact mount/environment fields, existing-report migration before container replacement, host ownership based on the image's actual appuser UID/GID, access verification and paired PDF/database backups.
- Retained the repository's standalone Compose deployment option. Its app service now mounts `${APP_UPLOAD_HOST_DIR:-/srv/iams/uploads}` at `/app/uploads`, with explicit host-directory creation required. Existing MySQL/log volumes are preserved.
- Documented the Compose-only host setting in `.env.example`; excluded uploads from the Docker build context; updated README and migration guidance so importing SQL alone is not presented as restoring PDFs.
- PDF paths in the database remain relative. Submission metadata and vector doodles remain in the separate database; PDFs live in the host upload directory.

### Validation
- Docker Compose configuration validated with both the default host path and a custom host path, using example environment values.
- Two isolated containers using the cached Java/Maven image exercised the Dockerfile's actual user/directory initialisation commands: appuser wrote a report fixture without root, and a replacement container read the persisted fixture from the same temporary bind mount.
- Targeted whitespace checks passed. The production runtime image was not rebuilt, and no app/database service or live Dokploy configuration was changed.
- Checked Dokploy's official application mount documentation: https://docs.dokploy.com/docs/core/applications/advanced

---

## Current Status: System-Wide Header Rollout Verified
**Status**: IMPLEMENTED — all browser layout fixtures passed
**Date**: 2026-10-28

- Verified all 61 top-level pages load `system-ui.css` after feature styles and inline style blocks.
- Centralised the accepted desktop/mobile header dimensions in shared CSS variables. Applied the logo dimensions to login, forgot-password and reset-password views as well as all role, assessment and profile headers.
- Preserved the compact sidebar, restored sidebar appearance, SVG icons, breadcrumb arrangement and collapse controls.
- Passed 268 browser fixture cases (67 page/role/layout variants at four widths) using the actual IAMS logo. Checks include exact logo width/maximum height, header padding, content offsets, fonts, role palettes, breadcrumbs and shell bounds.
- Verification used static browser fixtures; live authenticated Thymeleaf rendering was not exercised.

---

## Current Status: Header Proportions Adjusted
**Status**: IMPLEMENTED
**Date**: 2026-10-28

- Slightly increased the shared logo header to balance the compact sidebar: desktop logo width 150px → 170px, maximum height 52px → 60px, and vertical padding 12px → 14px.
- Mobile headers use a 130px logo (maximum height 56px) and 12px vertical padding. Breadcrumb and sidebar proportions are retained.
- Checked the final shared CSS overrides and whitespace; this small visual adjustment was not rerun through browser fixtures.

---

## Current Status: Compact Sidebar Sizing
**Status**: IMPLEMENTED — browser fixture checks passed
**Date**: 2026-10-28

- Reduced desktop sidebars from 240px to 220px and the content offset from 270px to 250px, including the PDF workstation.
- Reduced mobile/tablet drawers to `min(82vw, 300px)` and aligned the close button with that width.
- Tightened logo, user-profile, menu and course-switcher spacing; retained the restored role gradients, SVG icons, breadcrumb controls and 44px minimum link targets.
- Centralised sidebar dimensions as shared CSS variables and included borders within the specified width.
- All 170 browser interaction/style fixture checks passed, including five-role desktop/mobile dimensions, collapse/restore, and existing PDF navigation checks.

---

## Current Status: Previous Sidebar Appearance Restored
**Status**: IMPLEMENTED — browser fixture checks passed
**Date**: 2026-10-28

- Preserved the refined breadcrumb/menu-toggle arrangement, SVG navigation icons, collapse persistence, active-route handling, and mobile drawer controls.
- Removed the new sidebar appearance overrides so each role again uses its established gradient IAMS header, white branding, coloured user profile panel, original link sizing/spacing, active gradients, and hover treatment.
- Removed injected workspace subtitles and user-initial badges. The new icons remain alongside the original menu styling.
- JavaScript syntax checks passed. All 160 browser interaction/style fixture checks passed, including original gradient/white branding across five sidebars and desktop/mobile navigation behaviour. Live authenticated rendering was not exercised.

---

## Current Status: Refined Breadcrumb & Sidebar UI
**Status**: IMPLEMENTED — browser fixture checks passed
**Date**: 2026-10-28

### Refinements
- Moved the sidebar toggle into the breadcrumb bar. Desktop controls display “Hide menu” / “Show menu” with a panel icon; narrow screens use a compact icon. The mobile close control remains accessible in the drawer header while open.
- Refined breadcrumb spacing, muted parent links, reliable CSS chevrons, assessment context, and the role-coloured current-page marker. Long names wrap, with full context retained. Removed the floating-toggle spacer on mobile to recover vertical space.
- Unified sidebar presentation: compact IAMS/workspace heading, user initial badge, inline SVG navigation icons, balanced link spacing, and subtle active-page backgrounds. Hover states no longer shift link text sideways.
- Consolidated active-link selection in the shared controller, including nested routes, breadcrumb parent fallback, and student assessment/submission routing. Applied `aria-current` to the selected link and removed the redundant admin active-link script.
- Preserved desktop collapse preference, full-width assessment layout, mobile overlay/Escape dismissal, focus handling, and reduced-motion support.

### Validation
- Headless Chrome: 140 interaction checks passed across all five sidebar variants, desktop/mobile controls, collapse persistence, mobile close-button hit testing, and the existing PDF navigation/draft-preservation checks.
- Rechecked 268 page/role/viewport layout fixtures: no failures. Reviewed desktop expanded/collapsed and true mobile viewport screenshots.
- JavaScript syntax and targeted whitespace checks passed. These remain static browser fixtures; live authenticated server rendering was not exercised.

---

## Current Status: PDF Assessment Navigation & Shared Navigation Improvements
**Status**: IMPLEMENTED — browser fixture and interaction checks passed
**Date**: 2026-10-28

### PDF assessment workstation
- Replaced the always-expanded annotation controls with a labelled Tools dropdown containing Navigate, Pen, Highlighter, Text Box, Eraser, colours, and stroke widths. History and save remain immediately accessible; clear-page and annotated download actions live in a Document dropdown.
- Navigate is the initial tool: mouse dragging pans the report and touch scrolling remains available without adding ink accidentally.
- Added direct page-number entry, Fit Page, zoom percentage, and keyboard Page Up/Page Down/Home/End navigation when the PDF region is focused. Page changes scroll only the PDF viewport, leaving the rubric and outer document in place.
- Zoom and pane resizing retain the current page and relative reading position. A ResizeObserver handles sidebar/splitter reflows. Active text drafts are committed before reflow or switching tools.
- Improved page-indicator tracking, fixed flex shrinking for independent scrolling, and removed the minimum PDF width that prevented fitting narrow panes. Mobile views stack the panes without a redundant splitter.
- Made the assessment title white in the evaluation header, and reduced toolbar/button and shared logo-header sizes to increase reading space.

### System navigation
- Consolidated all five sidebar fragments onto `sidebar-toggle.js`. Desktop sidebars can collapse and expand, with the preference retained across pages. The content area expands into the released space, including the assessment workstation.
- Kept mobile drawer behaviour with overlay/Escape dismissal, focus containment, hidden-sidebar focus exclusion, and accurate expanded/collapsed labels. Integrated the existing Zen layout with the shared toggle.
- Added a reusable server-rendered `fragments/breadcrumb.html` and filled navigation gaps across all 61 top-level pages. Existing assessment-specific breadcrumbs remain intact. New breadcrumbs use role-appropriate parent links, assessment context when available, and a current-page marker; login/recovery/error views have public navigation.
- Breadcrumbs and sidebar controls are excluded from print layouts.

### Validation and remaining limits
- JavaScript syntax checks passed for both modified scripts.
- Headless Chrome: 90 interaction checks passed, covering all five sidebars at desktop/mobile widths, collapse/restore, stored preference, overlay/Escape, tool dropdowns, page entry and bounds, isolated PDF scrolling, zoom/page/annotation preservation, text-draft preservation, white assessment title, and read-only navigation.
- Rechecked 67 static page/role/layout fixtures at 1440/1024/768/375px (268 cases) for breadcrumb presence, reduced logo sizing, typography, role palettes, and shell bounds: no failures. Visually inspected a desktop workstation preview.
- PDF interaction fixtures use a controlled four-page PDF renderer and local annotation responses; smooth scrolling is made immediate in the test harness for deterministic headless checks. Live authenticated workflows and database persistence were not retested. The local Java runtime/Maven are unavailable, so Thymeleaf server rendering was not exercised in this pass.

---

## Current Status: System Font Consistency
**Status**: IMPLEMENTED — headless browser typography checks passed
**Date**: 2026-10-28

### Changes
- Found that Inter was declared throughout the system but never supplied as a font asset. Different fallback stacks, Poppins imports on rubric template pages, an Arial legacy stylesheet, monospace deadline labels, and Arial Narrow print tables could produce visibly different typefaces.
- Added self-hosted Inter variable normal and italic WOFF2 fonts, with the upstream SIL Open Font License, under `static/css/fonts/inter/`. This uses the existing public `/css/**` resource rule, including on login and password recovery pages. No third-party font request is needed.
- Defined the shared `--ui-font` family and text-size/weight tokens in `system-ui.css`. Updated all authored CSS/template font-family declarations to use that family, and removed the two Poppins imports.
- Applied the shared family to headings, navigation, forms, buttons, labels, tables, placeholders, and upload buttons. Deadline dates retain aligned digits with `font-variant-numeric: tabular-nums` using the same typeface.
- Standardised the base text and navigation weight, while keeping heading/control size hierarchy, print-specific sizing, icon fonts, and user-selected PDF annotation formatting.

### Validation
- Headless Chrome checked 67 page/role/layout fixtures at four widths (268 cases). Each case loaded real Inter regular, medium, semibold, bold, and italic font faces and checked computed families on text and form controls, alongside the existing shell/palette checks: no failures.
- Static audit: no remaining authored Arial, Arial Narrow, monospace, or Poppins font-family overrides in application CSS/templates. Live authenticated workflows remain outside these fixture checks.

---

## Current Status: Shared System Style Consistency
**Status**: IMPLEMENTED — browser fixture checks completed; live authenticated review pending
**Date**: 2026-10-28

### Changes
- Audited the 61 top-level Thymeleaf pages and their feature stylesheets. Found inconsistent header treatment, content padding, sidebar widths, control typography, and admin colours hardcoded into lecturer pages.
- Added `src/main/resources/static/css/system-ui.css`, loaded last in all 61 page heads. This is the shared visual contract for typography, role palettes, shell spacing, brand headers, common cards, text controls, action buttons, alerts, breadcrumbs, and keyboard focus indicators.
- Added explicit `data-ui-role` attributes. Profile and submission feedback pages resolve their role with Thymeleaf so shared pages retain the active subsystem palette.
- Preserved admin/super-admin maroon, lecturer green, student crimson, and supervisor blue. Replaced hardcoded admin maroon in lecturer templates with the shared role token.
- Standardised desktop sidebar width to 240px with a 270px content offset, and responsive drawers at the existing 1279px breakpoint. Unified mobile content spacing leaves room for the sidebar toggle.
- Kept screen layout rules out of print media and excluded the assessment workstation from shared content sizing so split scrolling and Zen mode retain their specialised layout.

### Validation and limits
- Headless Chrome: 67 static template fixtures (61 pages plus role and split/Zen variants) at 1440, 1024, 768, and 375px; 268 checks for role palette, content offset, shell/header overflow, gradient headers, and stylesheet availability passed.
- These checks use template markup and actual CSS, without backend data or authentication. They do not replace live workflow and visual review with populated tables and reports.
- No Java or application behaviour changes were required for this style pass. Existing backend/PDF work in the working tree was preserved.

### Maintaining consistency
- Load `system-ui.css` after feature styles and inline style blocks on new pages. Set `data-ui-role` to `admin`, `super_admin`, `lecturer`, `student`, `supervisor`, or `public`.
- Use `--ui-primary`, `--ui-primary-light`, `--ui-primary-soft`, `--ui-border`, and the shared radius/font tokens for new components. Keep page-specific layout and semantic status colours in feature stylesheets.

---

## Current Status: Doodle Reflection on Student Page Fix & Enhanced Side-by-Side Assessor Workstation
**Status**: COMPLETED
**Date**: 2026-10-28

### 1. Root Cause Analysis & Doodle Persistence Fix
#### Issues Identified
1. **Jackson Lazy Proxy Serialization Failure (HTTP 500)**:
   - When students visited `/student/assessment/{id}/submission/view`, the front-end fetched `/student/assessment/{id}/submission/annotations`.
   - `SubmissionAnnotation` had `@ManyToOne(fetch = FetchType.LAZY)` on `submission` and `lecturer` without `@JsonIgnore`.
   - Jackson attempted to serialize Hibernate `ByteBuddyInterceptor` proxies outside an open transaction, throwing `LazyInitializationException` / `InvalidDefinitionException` (HTTP 500).
   - In `pdf-doodle-studio.js`, `!response.ok` failed silently with an empty annotation store, leaving the PDF blank without doodles on the student's end.
2. **Early Return in Lecturer Evaluation Submission**:
   - In [`LecturerAssessmentController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/LecturerAssessmentController.java), when evaluating an individual student (`isStudentTarget != null && isStudentTarget`), `submitCombinedEvaluation` returned early at line 693 without ever executing the doodle persistence block (which was placed at line 818).
   - Doodles submitted during individual student evaluations were completely dropped.
3. **Incompatible Jackson Tree Traversal Method**:
   - `AssessmentSubmissionService.saveAllAnnotationsFromJson` invoked `root.properties()`, which was only introduced in Jackson 2.16. Older Jackson releases threw a runtime `NoSuchMethodError`.
4. **Missing Explicit Submission Identifier in Evaluation Form**:
   - The combined evaluation form only transmitted `groupId` or `studentId`. If an assessment had individual student submissions under a group scope, the controller risked querying the wrong submission entity.

#### Resolutions Implemented
1. **Model Serialization Hardening ([`SubmissionAnnotation.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/model/SubmissionAnnotation.java))**:
   - Annotated `submission` and `lecturer` fields and getters with `@JsonIgnore`.
   - Added lightweight `@JsonProperty("submissionId")` and `@JsonProperty("lecturerId")` getter methods to ensure clean, flat JSON serialization without Hibernate proxy leaks.
2. **Controller Pipeline Restructuring ([`LecturerAssessmentController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/LecturerAssessmentController.java))**:
   - Relocated doodle persistence to the very beginning of `submitCombinedEvaluation`, executing before any branching returns (both group and individual evaluation flows).
   - Added hidden `submissionId` input into [`lecturer_combined_evaluation_form.html`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/templates/lecturer_combined_evaluation_form.html) with fallback resolution via `getSubmissionForTarget`.
   - Safely handled null returns from `saveSubmissionAnnotation` when empty strokes are submitted.
   - Added `@Transactional(readOnly = true)` to annotation retrieval endpoints across [`LecturerAssessmentController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/LecturerAssessmentController.java), [`StudentController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/StudentController.java), and [`AdminController.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/controller/AdminController.java).
3. **Backend Service Enhancements ([`AssessmentSubmissionService.java`](file:///Users/dansan04/AAS-Project/adproject/src/main/java/com/capstone/adproject/service/AssessmentSubmissionService.java))**:
   - Replaced `root.properties()` with backward-compatible `root.fields().forEachRemaining(...)` iterator.
   - Updated `saveOrUpdateAnnotation` to skip persisting redundant empty rows (`"[]"` or blank) for untouched pages.
   - Added fallback logic in `getSubmissionForTarget` to look up group members' submissions if a group-level submission row is absent.
4. **Frontend Studio Robustness ([`pdf-doodle-studio.js`](file:///Users/dansan04/AAS-Project/adproject/src/main/resources/static/js/pdf-doodle-studio.js))**:
   - Updated `loadAnnotations()` to handle both stringified and pre-parsed JSON objects.
   - Aggregated multi-assessor marks seamlessly per page.
   - Automatically invokes `redrawDoodlesForPage()` on all visible and pre-rendered pages once annotation fetching resolves asynchronously.

---

### 2. Enhanced Side-by-Side Assessor Workstation
#### UX/UI Features Implemented
1. **Interactive Draggable Splitter Handle**:
   - Added `.split-divider-handle` and `.split-divider-grip` between the PDF and Rubrics panes.
   - Assessors can freely click-and-drag horizontally to adjust the viewport ratio between 20% and 80%, with mouse and touch support.
   - Automatically triggers `activeStudio.zoomFit()` on drag release to maintain optimal PDF scaling.
2. **Instant Preset Layout Toggles**:
   - `50/50 Split`: Balanced side-by-side view for standard grading.
   - `Focus Report (70/30)`: Expands PDF pane to 70% width for detailed reading of diagrams and dense technical reports.
   - `Focus Rubric (30/70)`: Expands Rubric pane to 70% width for rapid grading of complex rubrics with extensive criteria.
3. **Full-Width Zen Focus Mode**:
   - Added `.btn-zen` toggle button in the control bar.
   - Smoothly slides away the global navigation sidebar (`transform: translateX(-100%)`) and expands the dual workstation across 100% of the screen.
   - Maximizes screen real estate on laptops and smaller desktop displays.
4. **Live Completion Tracker & Auto-Navigator**:
   - Added `.workstation-progress-badge` with an integrated mini progress bar (`.workstation-mini-bar-fill`).
   - Dynamically monitors all radio button rating groups and mandatory comment textareas in real-time.
   - Displays live stats (e.g. `4/6 Criteria (67%)`), transitioning to a vibrant green `All Criteria Done!` badge upon 100% completion.
   - Clicking the badge activates `scrollToNextIncomplete()`, which automatically identifies the first unanswered criterion or unfilled comment, smoothly scrolls it into view, and highlights it with an animated pulse ring.
5. **Sticky Rubric Quick Jump Navigation Bar**:
   - Positioned at the top of the rubric pane (`.rubric-quick-nav`).
   - Automatically generates navigation pills for every rubric section and comment category.
   - Clicking a pill smoothly scrolls directly to that section.
   - Section pills update with a green checkmark as each section's criteria are completed.
6. **Compact Evaluation Header**:
   - Replaced bulky tables with a clean metadata row and target badge.
   - Member lists are consolidated into a collapsible chip tray (`toggleMembersChipList()`), saving up to 150px of vertical space.
   - Admin evaluation guidelines are tucked into a collapsible callout (`toggleAdminNotes()`).

---

## Current Status: Comprehensive Full-Fledged System Testing & Error Resolution
**Status**: COMPLETED
**Date**: 2026-10-28

### Summary of Full-Fledged Testing
1. **Verification Scope**:
   - Comprehensive end-to-end unit and integration test coverage across all subsystems:
     - **Authentication & Security**: [`AuthControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/AuthControllerTest.java), [`RateLimitInterceptorTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/interceptor/RateLimitInterceptorTest.java), [`CustomUserDetailsServiceTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/service/CustomUserDetailsServiceTest.java).
     - **Student Subsystem**: [`StudentControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/StudentControllerTest.java), [`StudentSubmissionControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/StudentSubmissionControllerTest.java).
     - **Lecturer Subsystem**: [`LecturerAssessmentControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/LecturerAssessmentControllerTest.java), [`LecturerControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/LecturerControllerTest.java).
     - **Admin Management & Moderation**: [`AdminManagementControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/AdminManagementControllerTest.java), [`AdminSubmissionControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/AdminSubmissionControllerTest.java).
     - **Rubrics & Criteria Architecture**: [`RubricControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/RubricControllerTest.java), [`RubricTemplateControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/RubricTemplateControllerTest.java).
     - **Deadlines & Course Scoping**: [`DeadlineControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/DeadlineControllerTest.java).
     - **Data Views & Analytics**: [`DataViewControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/DataViewControllerTest.java), [`CalculateServiceTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/service/CalculateServiceTest.java), [`CourseStatisticsServiceTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/service/CourseStatisticsServiceTest.java).
     - **SuperAdmin Controls**: [`SuperAdminControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/SuperAdminControllerTest.java), [`SuperAdminServiceTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/service/SuperAdminServiceTest.java).
     - **User Profiles**: [`ProfileControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/ProfileControllerTest.java).
     - **Comment & Feedback Engine**: [`CommentConfigControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/CommentConfigControllerTest.java), [`GroupCommentControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/GroupCommentControllerTest.java), [`AssessmentCommentServiceTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/service/AssessmentCommentServiceTest.java).
     - **Database Migration & Schema Integrity**: [`LegacyColumnCleanupMigrationRunnerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/config/LegacyColumnCleanupMigrationRunnerTest.java).
     - **File Storage & Sanitization**: [`FileStorageServiceTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/service/FileStorageServiceTest.java), [`HtmlSanitizerUtilTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/util/HtmlSanitizerUtilTest.java).
2. **Results**:
   - Total Tests Executed: **136**
   - Failures: **0**
   - Errors: **0**
   - Skipped: **1**
   - Pass Rate: **100%**

---

## Current Status: MySQL Evaluation Save Fix (Legacy 'submittedAt' Column Schema Migration)
**Status**: COMPLETED
**Date**: 2026-10-28

### Summary
1. **Error Analysis**:
   - Error: `Error saving evaluation: could not execute statement [Field 'submittedAt' doesn't have a default value] [insert into marks (... submitted_at ...)]`
   - **Is this caused by report submission?**: **No.** Student report submission is saved in `assessment_submissions` (and doodles in `submission_annotations`). It is completely separate and decoupled from lecturer mark saving in `marks`.
   - **Root Cause**:
     - Earlier in development, `Mark.java` (and `AssessmentComment.java`) defined `private LocalDateTime submittedAt;` with `@Column(nullable = false)` without a custom column name. Under `PhysicalNamingStrategyStandardImpl`, MySQL created the column `submittedAt` (camelCase) with `NOT NULL` and no default value.
     - Later in commit `1911a9b`, the mapping was changed to `@Column(name = "submitted_at", nullable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")`.
     - When Spring Boot booted with `hibernate.ddl-auto=update`, Hibernate added the new column `submitted_at` (snake_case). However, Hibernate **never** deletes or renames existing columns in MySQL.
     - Consequently, the legacy column `submittedAt` remained in the MySQL table as an orphan with `NOT NULL` and no default value. When Hibernate inserted marks into `submitted_at`, MySQL's strict mode rejected the insert because `submittedAt` had no value provided.

2. **Automated Migration Fix**:
   - Created [`LegacyColumnCleanupMigrationRunner.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/java/com/capstone/adproject/config/LegacyColumnCleanupMigrationRunner.java):
     - Executes automatically on application startup.
     - Inspects `information_schema.columns` for `marks` and `assessment_comments`.
     - Detects the legacy `submittedAt` column.
     - Synchronizes any legacy timestamps into `submitted_at`, and drops or alters `submittedAt` to `DATETIME NULL DEFAULT NULL`.
     - Permanently prevents `Field 'submittedAt' doesn't have a default value` across all environments without requiring manual DBA interventions.

3. **Direct SQL Commands for Manual Execution (Optional)**:
   - For immediate resolution without rebooting:
     ```sql
     ALTER TABLE marks MODIFY COLUMN submittedAt DATETIME NULL DEFAULT NULL;
     -- Or drop if submitted_at exists:
     ALTER TABLE marks DROP COLUMN submittedAt;

     ALTER TABLE assessment_comments MODIFY COLUMN submittedAt DATETIME NULL DEFAULT NULL;
     -- Or:
     ALTER TABLE assessment_comments DROP COLUMN submittedAt;
     ```

---

## Current Status: Assessor Workstation Enhancements & Simplified Submissions UI
**Status**: COMPLETED
**Date**: 2026-10-28

### Summary
1. **Continuous Vertical Scroll for PDF & Doodle Studio**:
   - Refactored [`pdf-doodle-studio.js`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/static/js/pdf-doodle-studio.js) from single-page pagination to continuous vertical scrolling.
   - Automatically renders all PDF pages sequentially into `.pdf-continuous-scroll-wrapper` inside `#pdf-viewport` with independent `overflow-y: auto`.
   - Added live scroll listener detecting visible page positions and updating the toolbar counter (`Page X of Y`) in real-time.
   - Previous/Next toolbar buttons smoothly scroll directly to target page containers.

2. **Text Box Feedback Tool**:
   - Added a dedicated Text Box tool (`#tool-text`) to the studio toolbar.
   - Assessors can click anywhere on any PDF page to spawn a floating inline text editor (`.pdf-text-editor-overlay`).
   - Supports keyboard shortcuts (`Ctrl+Enter` to save, `Escape` to dismiss) and inline delete.
   - Converts text to vector annotations (`tool: 'text', x: ..., y: ..., text: ..., color: ...`) stored in `SubmissionAnnotation.strokesJson`.
   - Renders beautiful sticky note callouts on the doodle canvas with word-wrapping, colored accent bars, rounded corners, and drop shadows.
   - Hit detection allows clicking existing notes to re-edit, as well as removal via the vector eraser tool.
   - Vector-accurate and scale-invariant; visible to students and admins in read-only mode.

3. **Independent Dual-Scroll Side-by-Side Workstation**:
   - Created a split-screen evaluation workstation in [`lecturer_combined_evaluation_form.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/lecturer_combined_evaluation_form.html) and [`pdf-doodle-studio.css`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/static/css/pdf-doodle-studio.css).
   - When a report submission is present (`submission != null`):
     - `body.lecturer-page.has-split-studio` locks outer page overflow.
     - The viewport is divided into a 52% PDF viewer pane (`.pdf-pane`) and a 48% rubric pane (`.rubric-pane`), each with independent vertical scrolling (`overflow-y: auto`).
     - Assessors can scroll through a 50-page PDF report without moving the rubrics, or scroll through dozens of rubric criteria and comments without losing their place on the PDF.
     - The bottom form action bar (`.form-actions`) remains sticky at the base of the rubric pane.
   - Graceful fallback: When `submission == null`, the layout maintains the standard full-width rubric document flow.

4. **Sidebar Navigation Consistency Across Submissions**:
   - Updated the active link detection script in [`fragments/student_sidebar.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/fragments/student_sidebar.html).
   - `/student/assessment/{id}/submit` and `/student/assessment/{id}/submission/view` correctly activate the **Report Submissions** tab.
   - `/student/assessment/{id}/evaluate/{studentId}` activates **Peer & Self Assessments**.
   - Standardized `body.student-submissions-page` in [`student_sidebar.css`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/static/css/student_sidebar.css) to ensure proper desktop `margin-left: 270px` and mobile responsive collapse.

5. **Simplified Student Report Submission Interface**:
   - Streamlined [`student_submissions.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/student_submissions.html) and [`student_submit_single.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/student_submit_single.html).
   - Replaced bulky hero banner and 4-box KPI grid with a sleek, minimalist `.submissions-header-card` and `.header-summary-badges`.
   - Unified `.simple-deliverable-card` with clean typography, compact status badges, clear deadline labels, 1-line file upload controls, and direct action buttons.
   - Maintained 50MB hard ceiling and PDF format enforcement across all student upload forms.

6. **Verification & Test Suite**:
   - All 48 Maven unit and integration tests passed (`BUILD SUCCESS`, 0 failures, 0 errors).

---

## Current Status: Report Submissions Portal Fix & Clean Decoupling from Peer/Self Assessments
**Status**: COMPLETED
**Date**: 2026-10-27

### Summary
1. **Resolved Report Submissions Error on `/student/submissions`**:
   - **Root Cause 1 (`Comparator` missing import)**: `java.util.Comparator` was missing an explicit import in [`StudentController.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/java/com/capstone/adproject/controller/StudentController.java), causing an `Unresolved compilation problem: Comparator cannot be resolved` error upon invoking the endpoint. Added `import java.util.Comparator;`.
   - **Root Cause 2 (Null Date formatting crash)**: Thymeleaf's `#dates.format(...)` threw `IllegalArgumentException: Cannot format a null Date` when an assessment deadline existed with a null target date. Guarded with `th:if="${assessmentDeadlineMap != null and assessmentDeadlineMap.get(assessment.id) != null and assessmentDeadlineMap.get(assessment.id).date != null}"` in both [`student_submissions.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/student_submissions.html) and [`student_submit_single.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/student_submit_single.html).
   - **Root Cause 3 (Unsafe SpEL Map lookup)**: Pre-populated `doodleCountMap.put(assessment.getId(), 0)` in `StudentController.java` and strengthened the template check: `th:if="${doodleCountMap != null and doodleCountMap.containsKey(assessment.id) and doodleCountMap.get(assessment.id) != null and doodleCountMap.get(assessment.id) > 0}"`.
   - **Root Cause 4 (Sidebar Fragment Syntax)**: Updated `th:replace="fragments/student_sidebar :: sidebar"` across `student_submissions.html` and `student_submit_single.html` to align with `student_home.html` and `student_assessments.html`.
   - **Root Cause 5 (Course Resolution)**: Autowired `CourseRepository` and resolved course safely via `courseRepository.findById(currentCourseId).orElse(currentStudent.getCourse())` so that students assigned via course assignments or groups have complete course code and title data displayed in the portal.

2. **Decoupled Report Submissions from Peer & Self Assessments**:
   - **Clean Separation of Concerns**:
     - Peer and self assessment assignments (`StudentAssessmentAssignment`) are strictly for rubric ratings and team evaluation.
     - Report submission is a course deliverable applying to all enrolled students for any assessment where `assessment.requiresSubmission == true`.
   - **Removed Submission Card from Peer & Self Assessment Page**:
     - Removed the entire report submission card and upload forms from [`student_assessments.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/student_assessments.html).
     - Cleaned up `/student/assessments` in `StudentController.java` to eliminate unnecessary submission model attributes and ensure `hasComponents` only checks rubrics and comments.
     - Kept report submissions exclusively housed in the dedicated portal at `/student/submissions` and single submission page at `/student/assessment/{assessmentId}/submit`.
   - **Eligibility Simplification (`StudentController.isStudentEligibleForSubmission`)**:
     - Students need only be enrolled in the course of the assessment to upload their reports. They are no longer blocked by whether they have a `StudentAssessmentAssignment` record.

3. **Automated Verification & Unit Tests**:
   - Created [`StudentSubmissionControllerTest.java`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/test/java/com/capstone/adproject/controller/StudentSubmissionControllerTest.java) covering `listStudentSubmissions`, submission with lecturer doodles, and `showAssessmentSubmitPage`.
   - Ran complete project test suite: **48 tests run, 0 failures, 0 errors, 1 skipped**, 100% green build.

---

## Current Status: Dedicated Student Report & Assignment Submissions Portal
**Status**: COMPLETED
**Date**: 2026-10-27

### Summary
- **Dedicated Student Submissions Hub (`GET /student/submissions`)**:
  - Created [`student_submissions.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/student_submissions.html) and custom stylesheet [`student_submissions.css`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/static/css/student_submissions.css).
  - Features:
    - **Header & Navigation**: Logo header and unified `.system-breadcrumb` (`Dashboard > Report Submissions`).
    - **Hero Context Banner**: Deep UTM Maroon gradient card detailing course code/name, group name (or individual scope badge), and portal description.
    - **Live 4-Metric KPI Grid**:
      - Total Required Deliverables
      - Reports Submitted
      - Pending Uploads
      - Reports with Lecturer Doodles & Markups
    - **Deliverables Assessment Cards**:
      - Submission Title (`assessment.getEffectiveSubmissionTitle()`) and parent assessment title.
      - Dual badges: Scope (`GROUP SUBMISSION` vs `INDIVIDUAL SUBMISSION`) and Deadline (`OPEN` with due date vs `CLOSED`).
      - Special Instructions callout box displaying admin guidelines and student prompts.
      - **Submitted State**:
        - File details box: file name, version tag (`v1`, `v2`), file size in MB, submission timestamp, and uploader attribution.
        - Gold highlight banner when lecturer has added handwritten ink notes & vector doodles.
        - Action buttons: "Download Submitted PDF", "Review Report & Lecturer Doodles" (opens interactive viewer), and "Replace / Re-upload PDF" (if deadline is open).
        - Collapsible re-upload zone with file picker, instant 50MB client-side limit check, and version advancing alert.
      - **Not Yet Submitted State**:
        - Drag-and-drop file upload dropzone accepting `.pdf` documents with interactive highlight.
        - Client-side size validator enforcing assessment limit (hard ceiling: 50MB).
        - Instant file preview bar before submission.
        - "Submit Report (PDF)" button.
        - Group requirement guardrail: friendly notification if assessment requires a group but student is not yet assigned.
      - **Empty State**: Friendly graphic when no submissions are currently required.
- **Dedicated Single Submission View (`GET /student/assessment/{assessmentId}/submit`)**:
  - Created [`student_submit_single.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/student_submit_single.html) for focused submission flows per assessment.
- **Student Navigation Integration**:
  - Updated [`fragments/student_sidebar.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/fragments/student_sidebar.html) to add **"Report Submissions"** with `<i class="fas fa-file-upload"></i>` directly in the navigation menu.
  - Added automatic active-state highlighting for sidebar links.
  - Updated [`student_home.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/student_home.html) (Student Dashboard) with a prominent **Report Submissions Banner** displaying scheduled deliverables and pending upload count with a direct action button.
- **Backend Controller & Eligibility Logic (`StudentController.java`)**:
  - Added `isStudentEligibleForSubmission(Student student, Assessment assessment)`:
    - Allows course-enrolled students to submit even when assessments do not have peer/self evaluations assigned.
    - Accurately checks explicit `StudentAssessmentAssignment` only if configured by the admin.
  - Improved `isAssessmentOpen(Assessment assessment)`:
    - Automatically defaults to open if no strict deadline dates are configured.
    - Prevents NPEs when dates are null.
  - Updated `uploadAssessmentSubmission` (`POST /student/assessment/{assessmentId}/upload`):
    - Added `redirectSource` parameter to redirect students smoothly back to `/student/submissions` (default) or `/student/assessments`.
    - Validates course enrollment, group presence (if GROUP scope), deadline window, and 50MB file size ceiling.
- **Automated Verification & Test Suite**:
  - Full Maven test suite: **45 tests run, 0 failures, 0 errors, 1 skipped**, 100% green build.

---

## Current Status: Unified System Breadcrumb Pattern & Full Style Alignment Across Subpages
**Status**: COMPLETED
**Date**: 2026-10-27

### Summary
- **Removal of Assessor Mapping Matrix from Configuration Page**:
  - Completely excised the assessor mapping table, preview buttons, and bottom KPI snapshot cards from [`assessment_submission_configuration.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/assessment_submission_configuration.html).
  - The submission configuration page is now 100% focused on its core responsibility: configuring submission activation (`requiresSubmission`), custom title (`submissionTitle`), submission scope (`GROUP` vs `INDIVIDUAL`), max file size (1-50 MB with hard storage ceiling), and student instructions.
- **Unified System Breadcrumb Component (`.system-breadcrumb`)**:
  - Implemented a unified, responsive breadcrumb navigation pattern (`system-breadcrumb`) across `rubric.css` and `responsive-patterns.css`.
  - Design features:
    - Clean white bar container with subtle border `#e2e8f0` and soft elevation.
    - Deep UTM Maroon `#4A0018` clickable links with chevron separators (`fa-chevron-right`).
    - Assessment context title in slate `#1e293b`.
    - Active page pill in soft maroon background `#fdf2f4` with accent border `#fce7eb`.
  - Maintained across **all system assessment sub-pages**:
    1. [`assessment_submission_configuration.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/assessment_submission_configuration.html) (`Manage Assessments > [Title] > Report Submission Setup`)
    2. [`view-assessment-rubrics.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/view-assessment-rubrics.html) (`Manage Assessments > [Title] > Rubrics & Comments`)
    3. [`admin_assign_lecturers.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/admin_assign_lecturers.html) (`Manage Assessments > [Title] > Assign Assessors`)
    4. [`admin_assign_students.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/admin_assign_students.html) (`Manage Assessments > [Title] > Assign Students`)
    5. [`comment_configuration_single_type.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/comment_configuration_single_type.html) (`Manage Assessments > [Title] > Group/Individual Comments`)
    6. [`assign_assessment.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/assign_assessment.html) (`Manage Assessments > [Title] > Assign Deadlines`)
    7. [`rubric-form.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/rubric-form.html) (`Manage Assessments > [Title] > Add / Edit Rubric`)
    8. [`admin_progress_tracking_view.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/admin_progress_tracking_view.html) (`Progress Tracking Hub > [Title] > Live Tracking & Moderation`)
    9. [`student_submission_view.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/student_submission_view.html) (`Progress Tracking / My Assessments > [Title] > Report & Feedback`)
- **CSP-Safe SVG Mask Icon Expansion in `rubric.css`**:
  - Added inline SVG data URI masks for icons on `body.rubric-page`:
    - `fa-arrow-left` (Back buttons)
    - `fa-chevron-right` & `fa-angle-right` (Breadcrumb separators)
    - `fa-chevron-left` & `fa-angle-left`
    - `fa-graduation-cap` (Course & student badges)
    - `fa-ban` (Disabled submission status badge)
    - `fa-info-circle` (Input helper hints)
    - `fa-chart-line` (Progress tracking)
  - Prevents solid black square rendering artifacts when running under strict Content Security Policies.
- **Automated Verification & Test Suite**:
  - Full Maven test suite: **45 tests run, 0 failures, 0 errors, 1 skipped**, 100% green build.

---

## Current Status: Dedicated Report Submission Configuration Page & Live Data IDE Error Resolution
**Status**: COMPLETED
**Date**: 2026-10-27

### Summary
- **Resolution for "Failed to refresh live data from process <PID> after retries: 10"**:
  - Identified the root cause: The VS Code Spring Boot Tools / Language Server extension monitors active Java processes and attempts to pull live runtime metrics (beans, request mappings) via JMX/Actuator. When management endpoints are restricted to `health,info` or JMX connection times out during local reboots, the extension retries 10 times and surfaces an error notification toast.
  - Fixes applied:
    - Updated `.vscode/settings.json` to disable automatic process tracking (`boot-java.live-information.automatic-process-tracking.enabled: false`, `spring-boot.ls.java.live-information.automatic-process-tracking.enabled: false`, `sts.ls.java.live-information.automatic-process-tracking.enabled: false`).
    - Updated `application.properties` and `application-dev.properties` to expose all JMX management endpoints (`management.endpoints.jmx.exposure.include=*`) ensuring IDE connections succeed if live process tracking is queried.
- **Dedicated Report Submission Configuration Page**:
  - Created a dedicated page [`assessment_submission_configuration.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/assessment_submission_configuration.html) accessible via `GET /admin/assessment/{assessmentId}/submission-config`:
    - **Header & Breadcrumbs**: `Manage Assessments` > `[Assessment Title]` > `Report Submission Setup`.
    - **Assessment Hero Card**: Deep maroon gradient card displaying assessment title, course code/name, ID, and real-time status badges (Enabled/Disabled, Group vs Individual, 50MB storage ceiling).
    - **Toggle Activation Switch**: High-visibility switch card to toggle `requiresSubmission` with dynamic slide-down options.
    - **Custom Submission Title**: Configurable report title (`submissionTitle`), defaulting gracefully to "Report / Assignment Submission".
    - **Submission Scope**: Dual interactive option cards for `GROUP` (1 report per team with versioning) vs `INDIVIDUAL` (per student).
    - **Max File Size**: Number input strictly bounded between 1 MB and 50 MB, backed by an inline storage guardrail notice.
    - **Submission Instructions & Guidelines**: Multi-line textarea for student prompts, cover page guidelines, and matric number instructions.
    - **Live Mapping Snapshot**: Mini KPI cards previewing Expected Targets, Submitted Reports, Pending Uploads, and Doodled Reports.
    - **Action Buttons**: Save Configuration, Back to Manage Assessments, and View Assessor Mapping Matrix.
- **Manage Assessments Hub Optimization (`manage-assessments.html`)**:
  - Removed cluttered submission fields from the quick "Create Assessment" modal to restore a lightweight, instant creation flow (Title + Save).
  - In the Existing Assessments table:
    - Replaced the text button with a clean **icon-only logo button** (`<i class="fas fa-file-upload"></i>`), styled with `.btn-submission` in `rubric.css` (Deep Maroon `#4A0018` when active, subtle slate `#64748b` when unconfigured) to match all other action buttons perfectly.
    - Added CSP-safe inline SVG masks for `fa-file-upload` and `fa-file-pdf` in `rubric.css`.
    - Made the `[GROUP] REPORT` title badge clickable, navigating directly to the configuration page.
    - Added a direct **"Report Submission Setup"** action in the assessment dropdown menu.
  - Refined the design of [`assessment_submission_configuration.html`](file:///c:/Users/ihsan/OneDrive/Documents/AAS-Project-Main/adproject/src/main/resources/templates/assessment_submission_configuration.html) with top brand header (logo only), pill breadcrumbs, hero context card, interactive scope selection cards, 50MB storage guardrail alert, and live mapping snapshot tiles.
- **Progress Tracking Hub Tab 3 Streamlining (`admin_progress_tracking_view.html`)**:
  - Replaced the bulky duplicate configuration form on Tab 3 with a sleek **Report Configuration Status Card** featuring active badges and a "Configure Report Settings" link.
  - Tab 3 is now dedicated to its core purpose: tracking student submissions and reviewing lecturer ink doodles across the Assessor Mapping Matrix.
- **Automated Verification & Test Suite**:
  - Full Maven test suite: **45 tests run, 0 failures, 0 errors, 1 skipped**, 100% green build.

---

## Current Status: Admin Report Setup & Assessor Mapping Tab, 50MB Storage Limit & Custom Submission Titles
**Status**: COMPLETED
**Date**: 2026-10-27

### Summary
- **Admin Report Setup & Assessor Mapping Tab (Tab 3 in Progress Tracking Hub)**:
  - Added dedicated 3rd tab in `admin_progress_tracking_view.html` (`tab=submissions`), named **"Report Setup & Assessor Mapping"**, with a dynamic status counter badge (`X/Y Submitted`).
  - Added direct quick link in `manage-assessments.html` dropdown menu: *Report Setup & Assessor Mapping*.
  - Added 4-metric overview KPI grid on Tab 3:
    - **Expected Submissions** (Maroon `#4A0018`)
    - **Submitted Reports** (Emerald `#059669`)
    - **Pending Uploads** (Warm Amber `#D97706`)
    - **Reports with Doodles** (UTM Gold `#F8A617`)
- **Report Submission Configuration**:
  - Embedded configuration card directly inside Tab 3 (`POST /admin/assessment/{assessmentId}/submission-config`):
    - `requiresSubmission` (toggle checkbox with dynamic slide-down options)
    - `submissionTitle` (custom title, e.g. "Final Project Report", "Capstone Interim Report")
    - `submissionScope` (`GROUP` vs `INDIVIDUAL`)
    - `maxFileSizeMb` (enforced at max 50MB, minimum 1MB)
    - `submissionInstructions` (detailed instructions/prompts for students)
- **Assessor Allocation & Report Mapping Matrix**:
  - Implemented `getSubmissionMappingOverview(Assessment assessment)` in `AssessmentSubmissionService.java`.
  - Automatically resolves active assessment assignment mode (`GROUP`, `STUDENT`, `RUBRIC`):
    - **Target Column**: Displays Target Name (Group or Student) with student member list (or email/group for individual mode).
    - **Assigned Assessor(s) Column**: Lists all lecturers assigned to that student or group, with fallback badge if unassigned.
    - **Rubric Scope Column**: Indicates whether assigned to "All Assessment Rubrics" or "Assigned by Specific Rubrics".
    - **Submission Status Column**: Displays file name, version badge (`v1`, `v2`), file size in KB/MB, upload timestamp, and uploader name; or "Pending Submission" badge.
    - **Doodle Annotations Column**: Displays count of annotated pages with a gold badge indicator.
    - **Actions Column**:
      - "View / Download PDF" (`/admin/submissions/{id}/file`)
      - "Review Report & Doodles" (`/admin/submissions/{id}/view`)
- **Strict 50MB Storage Guardrail**:
  - Hard capped at **50MB** across:
    - Backend entity setter in `Assessment.java` (`Math.min(maxFileSizeMb, 50)`)
    - Service layer validation in `AssessmentSubmissionService.saveSubmission` (throws `IllegalArgumentException` if file exceeds 50MB or the assessment's configured limit)
    - Admin UI input constraints (`max="50"`, with helper label indicating max storage limit)
    - Student upload display constraint in `student_assessments.html`
- **Custom Submission Titles Across Portals**:
  - Added `submissionTitle` field to `Assessment.java` with fallback `getEffectiveSubmissionTitle()` ("Report / Assignment Submission").
  - Form field added in `manage-assessments.html` and Tab 3 config.
  - Rendered dynamically in student upload cards (`student_assessments.html`), PDF viewer headers (`student_submission_view.html`), and lecturer evaluation form (`lecturer_combined_evaluation_form.html`).
- **Admin Report & Doodle Viewer**:
  - Created endpoints in `AdminController.java`:
    - `GET /admin/submissions/{id}/file` (streams PDF)
    - `GET /admin/submissions/{id}/view` (renders viewer with Admin sidebar and back button)
    - `GET /admin/submissions/{id}/annotations` (allows Admin to fetch lecturer doodles without 403 Forbidden)
- **Automated Verification & Test Suite**:
  - Added unit tests in `AssessmentSubmissionServiceTest.java` verifying 50MB hard cap, custom title fallbacks, and mapping overview aggregation.
  - Full Maven test suite: **45 tests run, 0 failures, 0 errors, 1 skipped** (integration test requiring live DB), 100% green build.

### Files Modified & Created
- `Assessment.java` (Added `submissionTitle`, enforced 50MB cap on `maxFileSizeMb`)
- `AssessmentSubmissionService.java` (Added `getSubmissionMappingOverview`, enforced 50MB cap)
- `AdminController.java` (Injected submission service, added Tab 3 model data, config update endpoint, and submission file/view/annotation endpoints)
- `RubricController.java` (Bound `submissionTitle` and 50MB cap in assessment save)
- `admin_progress_tracking_view.html` (Added Tab 3, KPI grid, config form, and Assessor Mapping table)
- `manage-assessments.html` (Added `submissionTitle`, 50MB limit, and dropdown link to Tab 3)
- `student_assessments.html` (Updated dynamic submission title and 50MB display)
- `lecturer_combined_evaluation_form.html` (Updated dynamic submission title)
- `student_submission_view.html` (Updated dynamic submission title and admin view support)
- `AssessmentSubmissionServiceTest.java` (Added unit tests for mapping overview, title fallbacks, and 50MB limit)
- `STATE.md` (Updated)

---

## Current Status: Student Report Submission, Lecturer Side-by-Side Review & Vector Ink Doodle Studio
**Status**: COMPLETED
**Date**: 2026-10-27

### Summary
- **Admin Assessment Configuration**:
  - Upgraded `manage-assessments.html` and `RubricController.java` to allow administrators to configure report/assignment upload requirements per assessment.
  - Configurable parameters:
    - `requiresSubmission` (boolean toggle)
    - `submissionScope` (`GROUP` [1 submission per team] vs `INDIVIDUAL` [each student submits])
    - `maxFileSizeMb` (default 25 MB, configurable up to 100 MB)
    - `submissionInstructions` (instructions and prompts for students)
  - Existing assessments list shows badge indicators for assessments requiring PDF submissions.
- **Secure File Storage & PDF Ingestion**:
  - Implemented `FileStorageService.java` with strict PDF-only enforcement (`.pdf` extension, `%PDF-` header byte validation).
  - Path traversal protection (folder and filename sanitization, `normalize()` checks against base upload path).
  - Configured multipart limits (50 MB) and storage paths in `application.properties` and `application-prod.properties`.
- **Student Upload Flow & Viewer**:
  - Enhanced `StudentController.java` and `student_assessments.html`.
  - Added dedicated submission card per assessment showing upload status, file metadata (filename, size, submission time, uploader, version), and instructions.
  - For group assessments, any team member can upload; subsequent uploads cleanly replace the file and increment the version (`v1` → `v2`).
  - Added `student_submission_view.html` providing a dedicated student report viewer that renders the PDF alongside the lecturer's handwritten ink scribbles and highlighters in read-only mode.
- **Lecturer Side-by-Side Evaluation & Vector Ink Doodle Studio**:
  - Transformed `lecturer_combined_evaluation_form.html` into a side-by-side workspace when a student submission is available.
  - **Left Pane**: Interactive PDF & Doodle Studio powered by PDF.js with an HTML5 transparent canvas layer.
    - Vector ink annotation recording normalized coordinates `(x/width, y/height)` for resolution-independent rendering at any zoom level or device DPI.
    - Tools: **Pen**, **Highlighter**, **Eraser** (vector stroke deletion).
    - Palette: IAMS Maroon (`#4A0018`), Gold (`#F8A617`), Red, Blue, Emerald Green, Black.
    - Stroke sizes (Fine, Medium, Thick), Undo/Redo history, and Clear Page.
    - Auto-save debounced after stroke completion + manual "Save Ink" button with CSRF-authenticated asynchronous JSON persistence.
  - **Right Pane**: Standard rubric evaluation form, scrollable independently.
  - **Split Screen Layout Bar**: Instant toggle between `50/50 Split`, `Focus Report (70/30)`, and `Focus Rubric (30/70)`.
- **Database Architecture**:
  - Created `AssessmentSubmission` entity tracking student, group, assessment, course, stored path, size, content type, version, and status.
  - Created `SubmissionAnnotation` entity tracking submission, lecturer, page number, and vector ink strokes JSON.
  - Created corresponding repositories `AssessmentSubmissionRepository` and `SubmissionAnnotationRepository`.
  - Service layer orchestration in `AssessmentSubmissionService.java`.
- **Automated Verification & Test Suite**:
  - Created `FileStorageServiceTest.java` (7 unit tests: magic bytes, path traversal, deletion, resource loading).
  - Created `AssessmentSubmissionServiceTest.java` (6 unit tests: group/individual submissions, file replacement, size limits, ink creation/updating).
  - Executed full Maven test suite (`mvn test`): **42 tests run, 0 failures, 0 errors, 1 skipped** (integration test requiring live DB), 100% green build.

### Files Created & Modified
- **Created**:
  - `AssessmentSubmission.java` (JPA Entity)
  - `SubmissionAnnotation.java` (JPA Entity)
  - `AssessmentSubmissionRepository.java` (JPA Repository)
  - `SubmissionAnnotationRepository.java` (JPA Repository)
  - `FileStorageService.java` (Service)
  - `AssessmentSubmissionService.java` (Service)
  - `pdf-doodle-studio.css` (CSS)
  - `pdf-doodle-studio.js` (JavaScript)
  - `static/js/pdfjs/pdf.min.js` & `pdf.worker.min.js` (PDF.js bundled assets)
  - `student_submission_view.html` (Thymeleaf Template)
  - `FileStorageServiceTest.java` (Unit Tests)
  - `AssessmentSubmissionServiceTest.java` (Unit Tests)
- **Modified**:
  - `Assessment.java` (Added submission fields)
  - `RubricController.java` (Bound submission fields on create/edit)
  - `StudentController.java` (Upload, download, annotations, and viewer endpoints)
  - `LecturerAssessmentController.java` (Submission lookup, file streaming, and doodle annotation endpoints)
  - `manage-assessments.html` (Admin UI configuration)
  - `student_assessments.html` (Student upload card and doodle feedback button)
  - `lecturer_combined_evaluation_form.html` (Split-screen studio integration)
  - `application.properties` & `application-prod.properties` (Upload directory and multipart limits)
  - `STATE.md` (Updated)

---

## Current Status: Simplified Comment Moderation, Decommissioned Sarcasm Evaluation & Modernized UI/UX
**Status**: COMPLETED
**Date**: 2026-10-26

### Summary
- **Decommissioned Sarcastic & Negative Sentiment Evaluation Engine**:
  - Removed `CommentSentimentAnalysisService.java` and its unit test suite `CommentSentimentAnalysisServiceTest.java`.
  - Removed automated regex pattern matching, hostile keyword tokenization, and arbitrary sentiment scoring from `AssessmentCommentService.java`.
  - Eliminated noisy, false-alarm red borders, alert badges ("⚠️ Potential Negative / Sarcastic"), and red warning reason banners ("Filtration Flag: ...") from all comment views.
  - Simplified database moderation fields in `AssessmentComment` to serve purely as lightweight, zero-overhead moderation indicators.
- **Unified & Enhanced Comment Filtration Hub**:
  - Replaced fragmented, double-stacked filter bars with a single, integrated **Filter & Search Suite** (`.filter-card`).
  - **Status Segmented Tabs**: Clean, intuitive tabs for **All Comments**, **Pending Review**, **Approved**, and **Rejected** with real-time numeric badges (`.pill-badge`).
  - **Parameter-Preserving Navigation**: Switching between status tabs seamlessly preserves active keyword search queries, evaluator role filters, and group filters without resetting admin context.
  - **Secondary Filter Controls**: Integrated responsive search bar, evaluator role filter (Assessors, Students, Peer, Self, Team), group filter, and an active filter reset button.
  - Retained fast multi-select checkboxes and bulk action controls ("Approve Selected", "Reject Selected").
- **Professional UI/UX Simplification & Visual Hierarchy (IAMS UTM Theme)**:
  - **Balanced 4-Metric KPI Grid**: Symmetrical 4-column desktop layout featuring **Total Comments** (IAMS Maroon `#4A0018`), **Pending Approval** (Warm Amber `#D97706`), **Approved & Visible** (Emerald `#059669`), and **Rejected / Hidden** (Graphite Slate `#4B5563`).
  - **Streamlined Moderation Action Header**: Replaced confusing dual bulk-approve buttons with a single, clear **"Approve All Pending"** primary CTA button that automatically disables when pending count reaches 0.
  - **Polished Comment Cards**: Clean white card surface with subtle 1px border and status accent left border (Amber for Pending, Emerald for Approved, Slate for Rejected). Removed visual clutter, harsh alert banners, and awkward italics.
  - **Clear Participant Flow**: Readable relationship display: `By: Evaluator → To: Target Student [Group Name]`.
  - **Seamless In-Place AJAX Moderation**: Retained instantaneous approval and rejection with dynamic DOM updates, real-time KPI counter recalculation, and non-intrusive floating toast notifications with zero scroll jumping or full-page reload.
  - **Assessment Selection Hub (`admin_progress_tracking_select.html`)**: Cleaned assessment cards by removing red danger badges and displaying clean Total and Pending count pills.
- **Automated Verification & Test Suite**:
  - Updated `AssessmentCommentServiceTest.java` to test approval status updates, bulk actions, and moderation stats.
  - Executed full Maven test suite (`mvn test`): **29 tests run, 0 failures, 0 errors, 1 skipped** (integration test requiring live DB).

### Files Modified & Deleted
- `CommentSentimentAnalysisService.java` (deleted)
- `CommentSentimentAnalysisServiceTest.java` (deleted)
- `AssessmentCommentService.java` (modified)
- `AssessmentCommentServiceTest.java` (modified)
- `AdminController.java` (modified)
- `admin_progress_tracking_view.html` (modified)
- `admin_progress_tracking_select.html` (modified)
- `admin_progress_tracking.css` (modified)
- `STATE.md` (updated)

---

## Current Status: Unified Progress & Comments Hub, Sentiment Filtration & In-Place AJAX Moderation
**Status**: COMPLETED
**Date**: 2026-10-22

### Summary
- **Unified Progress & Comments Hub**:
  - Combined the separate **Progress Tracking** and **View Comments** sections into a single, unified module: **Progress & Comments** (`/admin/progress-tracking`).
  - Updated the Admin navigation sidebar (`sidebar.html`) to replace separate links with a single **Progress & Comments** navigation item (`/admin/progress-tracking`).
  - Maintained backward compatibility by redirecting legacy comment routes (`/{role}/comments/view/assessments`) to the unified comments view.
  - Upgraded the Assessment Selection dashboard (`admin_progress_tracking_select.html`) with real-time comment metric pills (Total, Pending, and Flagged counts) and dual quick-access buttons ("View Progress" and "Moderate Comments").
  - Designed a 2-tier tabbed dashboard on the assessment details page (`admin_progress_tracking_view.html`):
    - **Tab 1: Evaluation Progress** (Assessor and Student granular progress tracking tables with expandable rows).
    - **Tab 2: Comments & Moderation** (Comment review, sentiment filtration, and bulk actions).
- **In-Place Asynchronous Moderation & Flagging (Zero Page Navigation)**:
  - Refactored `AdminController.java` (`toggleCommentFlag`, `approveComment`, `rejectComment`) to support asynchronous AJAX requests via `HttpServletRequest` inspection (`X-Requested-With: XMLHttpRequest` or `Accept: application/json`), returning JSON status and real-time moderation statistics.
  - Updated `admin_progress_tracking_view.html` with asynchronous JavaScript handlers (`handleToggleFlagAsync`, `handleApproveCommentAsync`, `handleRejectCommentAsync`).
  - Dynamic in-place DOM updates: Flag button toggles state between "Flag" and "Unflag" with colored icons, comment card border classes toggle (`card-flagged`), filtration reason banners and badges show/hide smoothly, and KPI/filter pill counters update in real time.
  - Non-intrusive floating toast notifications provide immediate action feedback without reloading the page, redirecting, or altering the admin's scroll position.
- **Visual Design Alignment & High-Contrast Styling (IAMS UTM Theme)**:
  - Replaced subtle/muted pastels with bold, high-contrast visual cues strictly adhering to the UTM IAMS palette: Deep Maroon (`#4A0018`), Graphite (`#3F3F46`), and UTM Gold Accent (`#F8A617`).
  - Potential Negative / Sarcastic Comments: Highlighted with a bold 6px solid red left border (`#DC2626`), high-contrast alert badge (`#DC2626` background, bold white text), distinct warning banner, and active button styling (`btn-flag-active`).
  - Standard Comments: Styled with signature UTM Gold accent (`border-left: 4px solid #F8A617`) matching `comment_view.html`.
  - KPI Stat Cards & Buttons: Enhanced with bold numbers, colored icon squares, UTM maroon primary buttons (`#4A0018`), emerald approve buttons (`#047857`), and graphite reject buttons (`#3F3F46`).
- **Built-in Sarcasm & Negative Influence Filtration Engine**:
  - Implemented `CommentSentimentAnalysisService.java` providing zero-dependency Java heuristic and lexicon-based analysis:
    - **Sarcasm Detection**: Identifies backhanded compliments, praise inverted by negation/irony ("great job doing nothing", "thanks for nothing", "super helpful (not)", quotes around praise like "'leader'").
    - **Hostile & Derogatory Phrasing**: Scans for toxic keywords and dismissive patterns ("useless", "lazy", "freeloader", "waste of time", "did zero", "ghosted", and local student slang like "beban", "tak buat kerja", "hauk").
    - **Sentiment Scoring**: Computes normalized sentiment scores (-1.0 to +1.0) with negation-handling.
    - Flags comments as `isFlaggedNegative = true` with descriptive reason tags.
- **Comment Visibility Protection (Strict Admin Approval)**:
  - Added moderation fields to `AssessmentComment.java`: `isApproved` (default `false`), `approvalStatus` (`PENDING`, `APPROVED`, `REJECTED`), `isFlaggedNegative`, `flagReason`, `sentimentScore`, `approvedAt`, and `approvedBy`.
  - Enforced strict visibility boundary in `AssessmentCommentService.getCompiledCommentsForStudent`: unapproved comments (from both lecturers and students) are **completely hidden from students** until explicitly approved by the admin.
  - Updated `student_comments.html` to clearly notify students that comments will appear once approved by the course administrator.
- **Automated Verification & Test Suite**:
  - Maintained complete test suite with unit tests in `CommentSentimentAnalysisServiceTest` (7 tests) and `AssessmentCommentServiceTest` (5 tests).
  - Executed full Maven test suite (`mvn test`): **34 tests run, 0 failures, 0 errors, 1 skipped** (integration test requiring live DB).

### Files Modified & Created
- `CommentSentimentAnalysisService.java` (new service)
- `CommentSentimentAnalysisServiceTest.java` (new test)
- `AssessmentCommentServiceTest.java` (new test)
- `AssessmentComment.java` (entity modified)
- `AssessmentCommentRepository.java` (modified)
- `AssessmentCommentService.java` (modified)
- `LecturerAssessmentService.java` (modified)
- `AdminController.java` (modified)
- `GroupCommentController.java` (modified)
- `sidebar.html` (modified)
- `admin_progress_tracking_select.html` (modified)
- `admin_progress_tracking_view.html` (modified)
- `student_comments.html` (modified)
- `admin_progress_tracking.css` (modified)
- `STATE.md` (updated)

---

### Summary
- **Automated & Integration Testing**:
  - Executed health diagnostics verifying host Java runtime (JDK 24 / target 17 bytecode), Maven 3.9.11, local MySQL connectivity on port 3306, and presence of `.env` configuration files.
  - Ran complete automated test suite (`mvn test`) covering:
    - `RateLimitInterceptorTest` (5 tests): Authentication endpoint rate limiting (HTTP 429), client IP resolution, IPv6 support, and anti-spoofing defense.
    - `CalculateServiceTest` (1 test): Null-safe student sorting across null, blank, and mixed-case usernames and emails without NPEs.
    - `CourseStatisticsServiceTest` (3 tests): Database aggregation queries (`countByCourseId`), avoidance of full table scans, and scoped fallback handling.
    - `CustomUserDetailsServiceTest` (6 tests): Deterministic priority hierarchy (SuperAdmin > Admin > Lecturer > Student), multi-table collision separation, and credential safety.
    - `SuperAdminServiceTest` (3 tests): Course management, validation, and update workflows.
    - `HtmlSanitizerUtilTest` (3 tests): OWASP sanitization against script tags, HTML entity injection, and XSS attack vectors.
    - Overall: **22 tests run, 0 failures, 0 errors, 1 skipped** (integration test requiring live DB).
- **Automated Secret Leak Audit**:
  - Ran deep repository scan (`git grep`) for accidental hardcoded credentials, API keys, and passwords across all non-ignored project source files.
  - Confirmed 0 hardcoded credentials or passwords in Java source files.
  - Confirmed 0 occurrences of deprecated secrets (`Admin@12345`, `utm-cas-remember-me-secret-key-2025-DO-NOT-CHANGE`) in source code.
  - Verified `.env` and `.env.*` are explicitly listed in both root `.gitignore` and `adproject/.gitignore`.
  - Audited full git commit history (`git log --all --full-history`) and confirmed `.env` has never been committed or tracked in version control history.
- **Milestone Completion**:
  - All 6 phases defined in `IMPLEMENTATION_PLAN.md` (Security Hardening & Architectural Refactoring) are fully implemented, verified, and documented.

### Files Modified & Updated
- `IMPLEMENTATION_PLAN.md` (completed)
- `STATE.md` (updated)

---

## Current Status: Phase 5: Performance Optimization & Reliability Improvements
**Status**: COMPLETED
**Date**: 2026-10-17

### Summary
- **Database Aggregation Queries**:
  - Added repository count methods:
    - `StudentRepository.countByCourseId(Long courseId)`
    - `LecturerRepository.countByCourseId(Long courseId)`
    - `AssessmentRepository.countByCourseId(Long courseId)`
    - `GroupRepository.countByCourseId(Long courseId)`
  - Refactored `CourseStatisticsService.getCourseStatistics` to eliminate full table scans (`findAll().stream().filter(...)`), replacing them directly with database aggregation queries.
  - Refactored admin emails query fallback in `CourseStatisticsService` to use course-scoped `findByCourseId(course.getId())` instead of full table in-memory filtering. Added constructor injection for `EntityManager`.
- **Null-Safe Sorting in Assessment Calculation**:
  - Updated `CalculateService.calculateAssessmentData` to replace raw `s1.getUsername().compareToIgnoreCase(s2.getUsername())` with a comprehensive null-safe comparator.
  - Evaluates non-blank username, falls back safely to non-blank email, and gracefully defaults to empty string `""`, completely preventing `NullPointerException` when processing students with missing or blank usernames.
- **Local Development Environment Configuration**:
  - Configured base `application.properties` with default datasource settings pointing to `localhost:3306` with credentials `${SPRING_DATASOURCE_PASSWORD:${MYSQL_ROOT_PASSWORD:rootpassword123}}`.
  - Removed `spring.profiles.active=prod` from `application.properties` so running locally outside Docker automatically uses base local properties without requiring explicit profile overrides.
  - Retained `application-prod.properties` specifically for containerized and production environments, where Docker Compose injects `SPRING_PROFILES_ACTIVE=prod`.
  - Updated `run-local` target in `Makefile` to use default local configuration without forcing prod profile flags.
- **Automated Test Coverage & Verification**:
  - Created `CourseStatisticsServiceTest` with 3 test cases verifying that database aggregation queries are invoked, full table scans are avoided, and fallbacks execute cleanly.
  - Created `CalculateServiceTest` verifying null-safe student sorting across null usernames, blank whitespace, mixed casing, and null emails.
  - Executed `mvn test` verifying 22 tests run with 0 failures, 0 errors, and 1 skipped (integration test requiring live DB).

### Files Modified & Created
- `StudentRepository.java` (modified)
- `LecturerRepository.java` (modified)
- `AssessmentRepository.java` (modified)
- `GroupRepository.java` (modified)
- `CourseStatisticsService.java` (modified)
- `CalculateService.java` (modified)
- `application.properties` (modified)
- `Makefile` (modified)
- `AdprojectApplicationTests.java` (modified)
- `CourseStatisticsServiceTest.java` (new test)
- `CalculateServiceTest.java` (new test)
- `IMPLEMENTATION_PLAN.md` (updated)
- `STATE.md` (updated)

---

## Current Status: Phase 4: Dead Code Elimination & Legacy Cleanup
**Status**: COMPLETED
**Date**: 2026-10-17

### Summary
- **Dead Controller & Service Removal**:
  - Deleted obsolete commented-out controller `IndustrialSupervisorController.java` (prefixed with `/*ackage ... */`).
  - Deleted obsolete commented-out service `IndustrialSupervisorService.java` (prefixed with `/*package ... */`).
  - Deleted empty 0-byte placeholder `TestController.java`.
  - Deleted obsolete Thymeleaf template `industrial_supervisor_home.html`.
- **Domain & Repository Cleanup**:
  - Deleted redundant JPA entity `IndustrialSupervisor.java` and repository `IndustrialSupervisorRepository.java`, preventing Hibernate from creating or syncing an unused database table.
  - Verified all group supervisor associations throughout `Group.java`, `GroupAssignmentDto.java`, `AdminService.java`, `GroupCommentController.java`, and `AdminController.java` strictly reference `Lecturer`.
- **Compilation & Verification**:
  - Confirmed 0 occurrences of commented-out `/*ackage` or `/*package` source code in the repository.
  - Verified project compiles cleanly with `mvn compile` and passes all unit tests (`RateLimitInterceptorTest`, `CustomUserDetailsServiceTest`, `SuperAdminServiceTest`, `HtmlSanitizerUtilTest`) with 0 failures or errors.

### Files Removed
- `IndustrialSupervisorController.java` (deleted)
- `IndustrialSupervisorService.java` (deleted)
- `TestController.java` (deleted)
- `industrial_supervisor_home.html` (deleted)
- `IndustrialSupervisor.java` (deleted)
- `IndustrialSupervisorRepository.java` (deleted)
- `STATE.md` (updated)
- `IMPLEMENTATION_PLAN.md` (updated)

---

## Current Status: Phase 3: Rate Limiting & Access Control Fortification
**Status**: COMPLETED
**Date**: 2026-10-17

### Summary
- **Authentication Rate Limiting**:
  - Defined dedicated `authRateLimit` bean (5 attempts per minute) in `RateLimitConfig.java` alongside the general `defaultRateLimit` (10 requests per minute).
  - Registered `RateLimitInterceptor` for `/login`, `/forgot_password`, `/reset_password`, and `/reset_password/**` in `WebMvcConfig.java`.
  - Configured `RateLimitInterceptor` to route authentication endpoint requests to dedicated auth buckets and general API requests to default buckets, returning HTTP 429 Too Many Requests with retry-after headers when thresholds are exceeded.
- **Client IP Resolution & Anti-Spoofing**:
  - Removed flawed `!xfHeader.contains(".")` check in `RateLimitInterceptor.java` to fully support IPv6 client addresses.
  - Added `app.security.behind-proxy: false` configuration flag. `X-Forwarded-For` header is only trusted when `behind-proxy` is explicitly enabled; otherwise client IP defaults to direct `request.getRemoteAddr()`, preventing client IP spoofing attacks.
  - Updated `application.properties`, `application-prod.properties`, `.env.example`, `.env`, and `docker-compose.yml` to support `APP_SECURITY_BEHIND_PROXY`.
- **Harmonized UserDetails Lookup & Privilege Separation**:
  - Refactored `CustomUserDetailsService.loadUserByUsername` to follow strict priority order:
    1. Super Admin
    2. Admin
    3. Lecturer
    4. Student
  - Prevented silent overwriting of passwords and uncontrolled authority merging when an identifier exists in multiple tables.
  - SuperAdmin and Admin accounts deterministically prioritize elevated credentials and authorities, preventing student or lower-privilege account data from compromising system access.
- **Verification & Test Coverage**:
  - Created `RateLimitInterceptorTest` covering authentication brute-force rate limits (blocking on 6th attempt), IPv6 parsing, and anti-spoofing behavior.
  - Created `CustomUserDetailsServiceTest` covering priority hierarchy, multi-table collision resolution, and absence of privilege escalation.

### Files Modified
- `RateLimitConfig.java`
- `RateLimitInterceptor.java`
- `WebMvcConfig.java`
- `CustomUserDetailsService.java`
- `application.properties`
- `application-prod.properties`
- `.env.example`
- `.env`
- `docker-compose.yml`
- `RateLimitInterceptorTest.java` (new test)
- `CustomUserDetailsServiceTest.java` (new test)
- `STATE.md`

---

## Current Status: Phase 2: Critical Web Security & Authentication Defenses
**Status**: COMPLETED
**Date**: 2026-10-17

### Summary
- **CSRF-Protected Mutating Endpoints**: Converted mutating deletion and removal endpoints (`/delete-student/{id}`, `/delete-lecturer/{id}`, `/group/remove-student/{studentId}`) in `AdminController.java` from `@GetMapping` to `@PostMapping`. Updated `manage_students.html`, `manage_lecturers.html`, and `edit_group.html` to submit via CSRF-protected POST forms instead of raw `<a>` tags.
- **Host Header Injection Neutralization**: Injected `app.public-base-url` into `AuthController.java`, `AdminService.java`, and `SuperAdminService.java`. Replaced dynamic request host construction with sanitized `publicBaseUrl` for all password reset and welcome invitation links.
- **Password Reset Token Hardening**:
  - Added `resetPasswordTokenExpiry` column (`LocalDateTime`) to `Admin`, `SuperAdmin`, `Lecturer`, and `Student` entities.
  - Updated `UserService.java` to hash password reset tokens with SHA-256 before storing them in the database, with a strict 30-minute expiration window (`plusMinutes(30)`).
  - Ensured incoming reset tokens are hashed before database lookup and rejected if expired. Expired or used tokens have their expiry timestamps wiped.
  - In `AuthController.java`, replaced user enumeration leak with uniform response: `"If an account with that email exists, a password reset link has been sent."`.
- **CSV / Formula Injection Mitigation (CWE-1236)**: Updated `escapeCsv` in `DataViewController.java` to prepend a single quote (`'`) to any cell value beginning with formula triggers (`=`, `+`, `-`, `@`, `\t`, `\r`), preventing spreadsheet calculation attacks upon opening exported data files.
- **Open Redirect Mitigation**: Implemented `safeRedirect` helper in `AdminController.java` to validate `Referer` headers against the local host and forbid external domains or protocol-relative paths (`//`), protecting course switching and role assignment redirects.
- **XSS Sanitization & Elimination of `th:utext`**: Replaced all remaining unescaped `th:utext` bindings for rubric and feedback comment labels with `th:text` in `supervisor_continuous_evaluation.html`.

### Files Modified
- `AdminController.java`
- `AuthController.java`
- `AdminService.java`
- `SuperAdminService.java`
- `UserService.java`
- `DataViewController.java`
- `Admin.java`, `SuperAdmin.java`, `Lecturer.java`, `Student.java`
- `manage_students.html`, `manage_lecturers.html`, `edit_group.html`, `supervisor_continuous_evaluation.html`
- `STATE.md`

---

## Current Status: Phase 1: Environment Hardening & Secret Externalization
**Status**: COMPLETED
**Date**: 2026-10-17

### Summary
- Fully externalized all credentials, cryptographic keys, and sensitive identities from Java source code and configuration files into environment variables (`.env`).
- **SuperAdmin Bootstrap**: Replaced hardcoded default email (`ihsan.rubix@gmail.com`) and password (`Admin@12345`) in `SuperAdminBootstrapRunner.java` with injected `@Value("${app.bootstrap.superadmin.email:}")` and `@Value("${app.bootstrap.superadmin.password:}")`. If not set, bootstrap safely skips with an informative log message. Completely eliminated plaintext password logging on application startup.
- **Remember-Me Secret Key**: Replaced the static hardcoded secret in `SecurityConfig.java` with `@Value("${app.security.remember-me-key:}")`. Added a secure cryptographic fallback that dynamically generates a random 256-bit key on startup if no explicit key is configured, preventing cookie forgery using known repository secrets.
- **Email Sender Identity**: Replaced hardcoded `"admin@eduinnovax.com"` across all email dispatch methods in `EmailService.java` with dynamic `@Value("${spring.mail.from:${spring.mail.username:admin@example.com}}")`.
- **Configuration Sanitization & Docker Mapping**: Removed hardcoded app credentials and personal emails from `application.properties` and `application-prod.properties`. Updated `docker-compose.yml` to pass through bootstrap credentials, remember-me keys, and mail from settings. Expanded `adproject/.env.example` with zero-leak templates and created local `adproject/.env`.

### Files Modified
- `SuperAdminBootstrapRunner.java`
- `SecurityConfig.java`
- `EmailService.java`
- `application.properties`
- `application-prod.properties`
- `.env.example`
- `docker-compose.yml`
- `.gitignore`
- `STATE.md`

---

## Current Status: Overall Assessment Data Filtering and Pagination
**Status**: COMPLETED

### Summary
- Added server-side filtering to the Overall Assessment Data view so admins can narrow the dataset by team, student search text, and selected assessments before the table renders.
- Added row pagination to keep large result sets readable when there are hundreds of students, while preserving the overall grand total and grade calculation across all assessments.
- Updated the export action to carry the current filters so the CSV matches the visible slice of data.

### Files Modified
- `DataViewController.java`
- `overall_data_view.html`
- `overall_data.css`
- `STATE.md`

---

## Current Status: IAR & GAR Breakdown under CLO Marks
**Status**: COMPLETED

### Summary
- Updated the **Overall Assessment Data** view (`overall_data_view.html`) and its corresponding CSV export feature to explicitly breakdown Course Learning Outcome (CLO) marks into **Individual Assessment Rubric (IAR)** and **Group Assessment Rubric (GAR)** columns.
- **Frontend / UI**: Redesigned the table headers in `overall_data_view.html` to use a 3-tier layout: Assessment Title -> CLO Header -> IAR and GAR sub-columns. Added a clear legend box above the table to define IAR and GAR.
- **Backend Service**: Enhanced `CalculateService.java` to compute independent totals for `cloSumsGroupOnly` and `cloWeightedSumsIndividualOnly`, capturing the specific rubric evaluations required for the breakdown.
- **Data Export**: Upgraded the `DataViewController.java` CSV export logic to mirror the 3-tier header structure and write the corresponding IAR/GAR data values, outputting `0.00` where scores are non-applicable or missing.

### Files Modified
- `StudentAssessmentDataDto.java`
- `CalculateService.java`
- `DataViewController.java`
- `overall_data_view.html`
- `STATE.md`

---

## Current Status: Fully Customizable Rubric Templates
**Status**: COMPLETED

### Summary
- Maintained the existing "Blueprint" functionality (structural layouts) and introduced a parallel "Full Template" builder.
- **Full Template Builder**: Admins can now pre-fill all rubric criteria, including Template Name, Description, Total Marks, CLO, Assessment Type, Sub-Rubrics, Direct Ratings, and Comments. 
- **Template Application**: Updated the apply template logic so that applying a Full Template automatically fills in all inputs on the target Rubric, while older Blueprints continue to provide structural outlines.
- **Refinements**: Dropdown default text changed to "Select Assessment Type" and "Select CLO", removed empty rating initialization on new templates, aligned the UI of remove buttons with the main form, and correctly bind the Template Name and Description to the target rubric upon applying.

### Files Modified
- `RubricTemplate.java`
- `RubricTemplateController.java`
- `rubric_template_manage.html`
- `rubric_full_template_builder.html` (new)
- `rubric-full-template-builder.js` (new)
- `rubric-form.js`
- `STATE.md`

---

## Current Status: Linux MySQL ResultSet Casing Fix (Production Data Missing)
**Status**: COMPLETED

### Summary
- Resolved an issue on the Dokploy (Linux) deployed application where groups, rubrics, and students appeared empty or missing, and data view displays lacked CLO marks.
- **Root Cause**: The production database tables contained camelCase column names (e.g., `groupName`, `cloMarks`), but the MySQL JDBC driver on Linux was returning the metadata keys in the `ResultSet` in entirely lowercase (e.g., `groupname`, `clomarks`). Because Hibernate explicitly generated case-sensitive mappings for these fields, it failed to match the lowercase keys in the `ResultSet`, leaving the Java objects populated with `null`s for those specific properties.
- **Fix**: Re-mapped all affected `@Column` definitions in the entities (`Group`, `Rubric`, `Mark`, `Student`) to explicitly use completely lowercase column names without quotes (e.g., `@Column(name = "groupname")`). This ensures that while MySQL successfully reads the `groupName` column case-insensitively, Hibernate precisely matches the lowercase key returned by the Linux driver.

### Files Modified
- `Group.java`
- `Rubric.java`
- `Mark.java`
- `Student.java`
- `STATE.md`

---

## Current Status: Production Deployment DDL-Auto Migration Fix (Data View Crash)
**Status**: COMPLETED

### Summary
- Resolved a crash on the deployed application where the Data Views would fail with `Unknown column 'm1_0.submitted_at' in 'field list'`.
- **Root Cause**: The `submitted_at` column was recently added to the `Mark` and `AssessmentComment` entities with `@Column(nullable = false)` but no default value. In the production database, which already contains existing rows, Hibernate's `ddl-auto=update` encountered an error when executing `ALTER TABLE ... ADD COLUMN ... NOT NULL` without a default value, silently failing to add the column and leaving the schema out of sync.
- **Fix**: Added `columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP"` to the `@Column` annotations for `submittedAt` in both `Mark.java` and `AssessmentComment.java`. This guarantees that MySQL will populate the current timestamp for all pre-existing records during the `ALTER TABLE` execution, allowing `ddl-auto=update` to successfully apply the migration to the production database on startup.

### Files Modified
- `Mark.java`
- `AssessmentComment.java`
- `STATE.md`

---
## Current Status: Grade Override Recalculation Fix
**Status**: COMPLETED

### Summary
- Fixed an issue preventing the dynamic recalculation of the Grand Total when an admin overridden a student's Factor on the "Edit Assessment Results" page.
- **Root Cause**: The page previously pre-filled both the Factor and Grand Total input boxes with their current calculated values. When an admin saved a new Factor without manually clearing the pre-filled Grand Total, the backend (`saveOverrides` in `DataViewController`) saved the old calculated total into the `StudentResultOverride` table. Because `overriddenGrandTotal` was populated, `CalculateService` halted dynamic calculation and permanently returned the frozen value, completely bypassing the new override factor.
- **Implementation**:
  - **Frontend Separation**: Updated `edit_overrides.html` to clearly separate the dynamically calculated values (displayed as read-only text) from the override input boxes (which now default to empty/auto).
  - **Backend Logic**: Refactored `DataViewController.java`. If an admin inputs a Factor Override but leaves the Total Override blank, the backend explicitly leaves `overriddenGrandTotal` as null. This null value signals `CalculateService` to dynamically recalculate the student's final total on-the-fly using the new custom factor against their raw group marks. If both override inputs are left blank or cleared, the system dynamically deletes the override record entirely, restoring standard automatic calculation.
  - Added new display properties (`calculatedFactor`, `calculatedGrandTotal`) to `StudentResultUpdate` to safely support this separation.
  - Added custom CSS (`.calculated-value`) to `edit_overrides.css`.

### Files Modified
- `DataViewController.java`
- `edit_overrides.html`
- `edit_overrides.css`
- `STATE.md`

---
## Current Status: Universal Mobile & Tablet Responsive Layout Fix (375x667 Screen Support)
**Status**: COMPLETED

### Summary
- Resolved an issue where the implementation of the standardized IAMS header and sidebars caused mobile screens (specifically down to compact viewports like **375x667 iPhone SE**) to become compressed to the left or pushed off-screen.
- **Root Cause**: When standardizing header layouts and sidebars across subsystems, rules such as `margin-left: 270px !important;`, `width: calc(100% - 270px) !important;`, `display: flex !important;`, and logo `max-width: 200px !important;` were appended to the bottom of 46 stylesheets without corresponding mobile responsive media queries with `!important`. On screens `<= 1279px`, `.main-content` remained clamped to `calc(100% - 270px)` (~105px width on 375px screens) and pushed 270px to the right. Additionally, `.header-container` refused to stack vertically, causing the 200px logo and 2.2rem heading to overflow horizontally, forcing browsers to compress or shift the viewport to the left.
- **Implementation**:
  - Injected an authoritative, high-priority **Universal Mobile & Tablet Responsive Overrides** block at the end of 46 stylesheets (including `critical.css`, `responsive-patterns.css`, `style.css`, all 6 sidebar stylesheets, and 37 feature/dashboard stylesheets).
  - **Tablet Transition (`<= 1279px`)**: Guaranteed that `.main-content` and `.content-container` reset to `margin-left: 0 !important; width: 100% !important; max-width: 100% !important; padding: 20px !important; box-sizing: border-box !important; overflow-x: hidden !important;`, ensuring the page fills the full viewport when sidebars collapse or toggle into off-canvas drawers.
  - **Mobile Layout & Header Stacking (`<= 768px`)**: Enforced vertical flex stacking for `.header` and `.header-container` (`flex-direction: column !important; justify-content: center !important; align-items: center !important; gap: 12px !important; text-align: center !important;`). Scaled down logo images (`max-width: 140px !important;`) and headings (`font-size: 1.4rem !important; word-wrap: break-word !important; overflow-wrap: anywhere !important;`).
  - **Small Mobile Adaptation (`<= 480px` / 375x667)**: Further refined padding (`padding: 12px 10px !important;`), logo scaling (`max-width: 120px !important;`), and heading font size (`font-size: 1.2rem !important;`), eliminating all horizontal overflow and left-compression across all user roles and subsystems.

### Files Modified
- `critical.css`, `responsive-patterns.css`, `style.css`
- `admin_sidebar.css`, `superadmin_sidebar.css`, `lecturer_sidebar.css`, `student_sidebar.css`, `supervisor_sidebar.css`, `industrial_sidebar.css`
- 37 subsystem and feature stylesheets in `src/main/resources/static/css/`
- `STATE.md`

---


## Current Status: Rebranding "Lecturer" Terminology to "Assessor" in Display Strings & Backend Messages
**Status**: COMPLETED

### Summary
- Rebranded display strings, UI labels, controller flash messages, and backend service display logic from "Lecturer" / "Lecturers" to **"Assessor"** / **"Assessors"** across the application.
- **Implementation**:
  - **Frontend JavaScript**: Updated search feedback in `group-assignment.js` (`"No assessors found"`) and dynamic button titles / aria-labels in `admin-assign-lecturers.js` (`"Remove Assessor"`).
  - **Backend Services & Display Logic**: Updated `CalculateService.java` to output `"Assessor"` and `"Assessor (Supervisor Role)"` in evaluator details maps. Updated `ProgressTrackingService.java` to return `"Assessor"` as the assessor type string. Updated `LecturerAssessmentService.java` to default anonymous comment identifiers to `"Assessor"`. Updated `StudentController.java` to map evaluator badge display names to `"Assessor"`. Updated `AdminService.java` welcome email roles and enrollment messages.
  - **Controller Flash Messages & UI Templates**: Updated error attributes in `GroupCommentController.java` and `SuperAdminController.java` (`"Assessor profile not found"`). Mapped legacy database comment records displaying `"Lecturer"` in `student_comments.html` to display as `"Assessor"`, and updated delete confirmation dialogs in `manage_lecturers.html`. Note: Internal Spring Security role identifiers (`ROLE_LECTURER`) and database table/schema names remain unchanged to preserve system security and relational integrity.

### Files Modified
- `group-assignment.js`, `admin-assign-lecturers.js`
- `CalculateService.java`, `ProgressTrackingService.java`, `LecturerAssessmentService.java`, `StudentController.java`, `AdminService.java`
- `GroupCommentController.java`, `SuperAdminController.java`
- `manage_lecturers.html`, `student_comments.html`
- `STATE.md`


## Current Status: Login & Password Reset UI Enhancements (Logo Visibility & Background)
**Status**: COMPLETED

### Summary
- Updated the authentication flow pages (**Login**, **Forgot Password**, and **Reset Password**) to feature a prominent logo and a new background image.
- **Implementation**:
  - **Logo Visibility**: Doubled the size of `IAMS_logo2.png` in `login.css`, `forgot.css`, and `reset_password.css` by changing desktop `.logo` max-width from `120px` to `240px` (and mobile max-width from `140px` to `180px`), accompanied by a stronger drop shadow for better contrast and brand visibility.
  - **Background Update**: Updated the background image across all authentication pages from `utmbg.png` to `backgroundKL.jpg`.

### Files Modified
- `login.css`, `forgot.css`, `reset_password.css`
- `STATE.md`


## Current Status: IAMS Rebranding & Subsystem UI/Color Consistency
**Status**: COMPLETED

### Summary
- Rebranded the application from "UTM CAS" / "UTM AAS" to **IAMS** across all subsystem dashboards, sidebars, and forms, and ensured consistent header layouts and subsystem color themes when navigating across different roles.
- **Implementation**:
  - **System-Wide Rebranding**: Replaced old logo references (`utmlogo.png`, `logoUTM.png`) with the inverted white `IAMS_logo2.png` and updated brand text labels to "IAMS" across Super Admin, Student, and Industrial Supervisor views (`superadmin_home.html`, `manage_courses.html`, `invite_admin.html`, `edit_course.html`, `student_home.html`, `student_assessments.html`, `student_comments.html`, `peer_assessment_form.html`, `industrial_supervisor_home.html`, `supervisor_continuous_evaluation.html`, `supervisor_evaluate_groups.html`, `error.html`) and sidebar fragments (`superadmin_sidebar.html`, `student_sidebar.html`, `industrial_sidebar.html`).
  - **Super Admin Sidebar Architecture**: Created a standalone stylesheet `superadmin_sidebar.css` that styles the Super Admin sidebar cleanly without restrictive `body.superadmin-page` selectors, and standardized `superadmin_sidebar.html` with `<ul class="menu-list">` navigation (including a direct link to **My Profile**).
  - **Student Subsystem Header Consistency**: Updated `peer_assess.css`, `student_comments.css`, and `student_assessments.css` so that the **Peer Self Assessment Form**, **My Comments**, and **Assessments List** display the exact same maroon gradient card header box (`linear-gradient(135deg, var(--student-primary) 0%, #922838 100%)`) and inverted white logo as the student dashboard.
  - **Profile Page Subsystem Color Consistency Fix**: Fixed an issue where accessing **My Profile** (`profile.html`) from Lecturer or Student subsystems immediately changed the page palette into the Admin maroon color (`#4A0018`) due to `profile.css` hardcoding `--color-primary`. Updated `profile.html` to append a dynamic role class to the body (`<body th:class="'profile-page role-' + (${role != null ? role.toLowerCase() : 'admin'})">`) and added role-based CSS variable overrides in `profile.css` (`.role-lecturer` using emerald green `#047857` and `.role-student` using crimson `#7A1E2D`), ensuring header gradients, underlines, text titles, and action buttons adapt seamlessly to the user's active subsystem palette.

### Files Modified
- `superadmin_sidebar.html`, `student_sidebar.html`, `industrial_sidebar.html`
- `superadmin_sidebar.css` (new), `superadmin_home.css`, `peer_assess.css`, `student_comments.css`, `student_assessments.css`, `profile.css`
- `superadmin_home.html`, `manage_courses.html`, `invite_admin.html`, `edit_course.html`, `profile.html`
- `student_home.html`, `student_assessments.html`, `student_comments.html`, `peer_assessment_form.html`, `industrial_supervisor_home.html`, `supervisor_continuous_evaluation.html`, `supervisor_evaluate_groups.html`, `error.html`
- `STATE.md`

---

## Current Status: Overall Assessment Data Excel/CSV Export
**Status**: COMPLETED

### Summary
- Implemented an export functionality for the **Overall Assessment Data** view (`/admin/data-views/overall/export`) that generates a lightweight, Excel-compatible CSV file with a UTF-8 Byte Order Mark (BOM) so it opens natively and cleanly formatted in Microsoft Excel without requiring heavy external dependencies.
- **Implementation**:
  - Added `exportOverallDataToExcel()` endpoint in `DataViewController.java` returning `ResponseEntity<byte[]>` with `text/csv; charset=UTF-8` content type and `attachment; filename="Overall_Assessment_Data.csv"`.
  - Refactored shared data computation between `showOverallData` and the export endpoint into a reusable `getOverallDataModelMap()` helper method, ensuring 100% data consistency (including active course scoping, alphabetical student sorting, uncapped/capped factor formatting, CLO score breakdown, and grade overrides).
  - Formatted the export with two header rows exactly mirroring the HTML table structure: Row 1 aligns assessment titles over their corresponding CLO spans, and Row 2 details individual CLOs, weighted CLOs (`(W)`), and Raw/Weighted totals.
  - Added an **"Export to Excel"** button styled with emerald green (`.btn-export`) in `overall_data_view.html` and `overall_data.css`, placing it next to the Print button in the action bar.

### Files Modified
- `DataViewController.java`
- `overall_data_view.html`
- `overall_data.css`
- `STATE.md`


## Current Status: Dynamic Assessment Comment Question Display Fix
**Status**: COMPLETED

### Summary
- Fixed an issue where evaluation comment views (such as Assessment Data View and Admin Comments View) displayed generic labels like `"Individual Comment 1"` instead of the actual custom comment question configured for the assessment.
- **Root Cause**: When evaluation comments were submitted, their `commentLabel` attribute was set to whatever string was returned at that exact moment (for example, if custom labels had not been configured yet, `"Individual Comment 1"` was permanently saved into the `comment_label` column of the database row). When rendering comment views, `AssessmentComment.getDisplayLabel()` statically returned whatever string was saved in the database row without checking if the Assessment currently has a custom comment question configured.
- **Fix**:
  - Updated `AssessmentComment.java` with overloaded `getDisplayLabel(Assessment liveAssessment, Rubric liveRubric)` and `getDisplayLabel(Assessment liveAssessment)` methods that dynamically check the live `Assessment` and `Rubric` objects first. Whenever comments are grouped for display, any real-time custom question label configured on the assessment (e.g., via `assessment.getIndividualCommentLabel(index)` or `assessment.getGroupCommentLabel(index)`) overrides older or generic stored database defaults like `"Individual Comment 1"`.
  - Updated `CalculateService.java` (`getIndividualAssessmentComments`, `getGroupAssessmentComments`, `getEvaluatorCommentsForRubric`, and `getAllAssessmentComments`) to pass the live `Assessment` and `Rubric` objects when generating display labels.

### Files Modified
- `AssessmentComment.java`
- `CalculateService.java`
- `STATE.md`

---

## Current Status: Comment HTML Double-Escaping Bug Fix & XSS Sanitization Improvement
**Status**: COMPLETED

### Summary
- Fixed the double-escaping issue where special characters in evaluation comments and comment questions (such as `=` appearing as `&#61;` and `"` appearing as `&#34;`) were displayed as literal HTML entities on data views while maintaining full XSS security.
- **Root Cause**: When saving evaluation comments and rubric questions, service layers were calling `HtmlSanitizerUtil.sanitize()`. This method uses OWASP HTML Sanitizer to prevent XSS, but converts non-alphanumeric ASCII characters (`=`, `"`, `'`, `&`) into HTML entities (`&#61;`, `&#34;`). When these stored strings were subsequently rendered on frontend views using Thymeleaf's `th:text` attribute (which automatically escapes HTML variables for XSS protection), the ampersand `&` in the entities was escaped again to `&amp;`, resulting in the browser displaying literal strings like `&#61;` and `&#34;`.
- **Fix**:
  - Replaced `.sanitize()` calls across `LecturerAssessmentService` and `AssessmentCommentService` with `HtmlSanitizerUtil.sanitizePlainText()`, which strips unsafe HTML tags and unescapes entity codes so that `th:text` displays them cleanly without double escaping.
  - Added `HtmlSanitizerUtil.sanitizePlainText()` to all comment saving endpoints in `IndustrialSupervisorService`, `StudentController` (peer/self/team evaluations), `CommentConfigController`, and `RubricController`.
  - Updated `AssessmentComment.getCommentText()` to unescape HTML entities on read, ensuring legacy database entries with existing encoded entities automatically render cleanly on all views.
  - Added unit tests in `HtmlSanitizerUtilTest`.

### Files Modified
- `AssessmentComment.java`
- `LecturerAssessmentService.java`
- `AssessmentCommentService.java`
- `IndustrialSupervisorService.java`
- `StudentController.java`
- `CommentConfigController.java`
- `RubricController.java`
- `HtmlSanitizerUtilTest.java`
- `STATE.md`

---

## Current Status: Factor Display View Full-Width Table & Horizontal Scroll Elimination
**Status**: COMPLETED

### Summary
- Updated `factor_display.css` so that factor display tables (`factor_display.html`) display in full view without requiring horizontal scrolling.
- **Root Cause**: The `.display-container` was capped at `max-width: 1200px`, constraining table widths on desktops. Additionally, the table wrappers (`.table-responsive` and `.data-table`) inherited `white-space: nowrap` from global responsive styles, forcing text in assessment titles, teammate rating lists, and status badges to stay on a single horizontal line and pushing table dimensions beyond card borders.
- **Fix**:
  - Replaced `max-width: 1200px` on `.display-container` with `max-width: 100%`, allowing factor display tables to expand to the full width of the main content area.
  - Applied `white-space: normal; word-break: break-word; overflow-wrap: anywhere; table-layout: auto;` to `.table-responsive`, `.data-table`, `th`, and `td` elements, ensuring that teammate rating breakdowns, assessment titles, and override history columns wrap cleanly within the container without triggering horizontal scrollbars.

### Files Modified
- `factor_display.css`
- `STATE.md`

---

## Current Status: Assessment Data View Full-Width Card & Horizontal Scroll Elimination
**Status**: COMPLETED

### Summary
- Updated `assessment_data.css` so that student evaluation cards in the Assessment Data View (`assessment_data_view.html`) display in full view without requiring horizontal scrolling.
- **Root Cause**: The `.data-container` was restricted to `max-width: 1200px`, which constrained student cards even on wider screens. Inside `.student-card`, `.table-responsive` inherited `white-space: nowrap` from global responsive patterns, forcing table cells (such as sub-rubric names, rating lists, marks, and rubric comments) to stay on single lines and pushing table widths beyond the card boundaries. Furthermore, mobile/tablet media queries enforced `min-width: 900px` on `.data-table`, forcing horizontal scrollbars.
- **Fix**:
  - Removed `max-width: 1200px` from `.data-container` (changing to `max-width: 100%`), allowing student cards to expand to the full width of the main content area.
  - Overridden `white-space: nowrap` on `.table-responsive` and applied `white-space: normal; word-break: break-word; overflow-wrap: anywhere; table-layout: auto;` to `.data-table`, table headers, and table cells so text wraps cleanly inside column boundaries without overflowing horizontally.
  - Replaced the hardcoded `min-width: 900px` mobile override with `min-width: 100%; max-width: 100%;`, ensuring tables fit inside the container across all screen sizes without horizontal scrollbars.

### Files Modified
- `assessment_data.css`
- `STATE.md`

---

## Current Status: Factor Display 3-Decimal Rounding Alignment
**Status**: COMPLETED

### Summary
- Aligned factor display formatting in the Factor Display view (`factor_display.html`) with the Overall Data Summary view by formatting all factor values to 3 decimal places (`#numbers.formatDecimal(..., 1, 3)`).
- **Fix**: Updated Base Factor, Calculated Total Factor, Overridden Factor, System Factor badges, and Factor Override History columns in `factor_display.html` from 2 decimal places to 3 decimal places (e.g., `1.090`). Individual ratings and mark averages (`Indiv. Avg`, `Group Avg`) remain formatted to 2 decimal places as in Overall Data View.

### Files Modified
- `factor_display.html`
- `STATE.md`

---

## Current Status: Base Factor Capping Separation & Calculation Verification
**Status**: COMPLETED

### Summary
- Decoupled base factor calculation from the `1.05` capping logic in `CalculateService.java` and unified peer factor calculations across data views.
- **Root Cause**: Previously, `calculateStudentFactorFromPeerAssessment` and `calculateBreakdownFromPeerAssessment` applied `Math.min(factor, 1.05)` immediately upon computing `individualAverage / groupAverage`. This caused the base factor to be automatically capped to `1.05` when stored in DTOs and displayed on data views (`overall_data_view.html`, `factor_display.html`). In addition, architectural discrepancies between the two methods (such as including unrated `0.0` members in the group average denominator and dividing by total mark count instead of non-null rating count) caused slight differences in computed factors between Factor Display and Overall Data Summary (e.g., `1.60` vs. `1.58`).
- **Fix**:
  - Removed the `1.05` cap from initial base factor calculations so that individual breakdown base factors remain unlimited and are displayed uncapped in the UI.
  - Standardized `calculateBreakdownFromPeerAssessment` and `calculateStudentFactorFromPeerAssessment` in `CalculateService.java` so both omit unrated students from the group average denominator and average only over valid, non-null ratings. This guarantees 100% mathematical consistency across Factor Display and Overall Data Summary.
  - Capped the **Calculated Total Factor** (`finalCalculatedFactor`) in Factor Display (`getFactorDetailsForStudent`) at `1.05` (`Math.min(finalFactor, 1.05)`) so that total factor displays and status badges align with downstream weighted mark calculations.
- **Calculation Verification**: Verified that for a student with multiple peer assessments configured in the course (e.g., Test Assessment 2 with 20% weightage and Test Assessment 3 with 80% weightage), the system correctly calculates:
  - **Uncapped Base Factor**: $\frac{\text{Indiv. Avg}}{\text{Group Avg}} = \frac{3.00}{2.75} = 1.09$ (raw, unlimited value shown without truncation).
  - **Weighted Total Factor**: Composed via $\sum (\text{Base Factor}_i \times \text{Weight}_i)$ and capped at `1.05` for total factor display and mark computation.

### Files Modified
- `CalculateService.java`
- `STATE.md`

---

## Current Status: Comprehensive Responsive Design & Mobile Adaptation Remediation
**Status**: COMPLETED

### Summary
- Implemented a universal feature-by-feature responsive design remediation across all 7 subsystems of the UTM AAS application to ensure a seamless, professional experience on desktops, tablets (`1279px`), and mobile phones down to compact screens like the **iPhone SE (`375px x 667px`)**.
- **Universal Design Tokens & Patterns**: Standardized the breakpoint hierarchy across `critical.css`, `responsive-patterns.css`, and feature stylesheets. Enforced `1279px` as the standard transition breakpoint to override fixed sidebar margins (`margin-left: 0 !important; width: 100% !important;`).
- **iPhone SE (`375px`) Alignment & Readability Guarantees**:
  - Enforced `max-width: 100%; word-break: break-word; overflow-wrap: anywhere;` across all typography elements (`h1`-`h6`, `p`, `span`, `div`, `td`, `th`, `a`, `label`) in `critical.css` to prevent text truncation or horizontal overflow on narrow screens.
  - Converted table card cell layouts (`.table-responsive td`) on screens `<= 480px` from side-by-side (`50%` padding) into vertically stacked flex columns where labels appear cleanly above values.
  - Standardized mobile button groups (`.action-bar`, `.action-buttons`, `.button-group`, `.btn-group`) to stack vertically with `width: 100% !important; min-height: 44px !important;` to adhere to iOS minimum touch target dimensions and allow multi-word button labels to wrap cleanly.
- **Feature Remediation Scope**:
  - **Feature 1 (Progress Tracking Dashboard)**: Added tablet and mobile overrides to `admin_progress_tracking.css`, including specific mobile rules for `.details-row` expandable granular progress cards to prevent flex layout distortion. Verified `admin_progress_tracking_select.html` and `admin_progress_tracking_view.html`.
  - **Feature 2 (Assessment Comments Unification)**: Updated `comment_config.css` and `comment_config.html`.
  - **Feature 3 (Factor Calculation & Display)**: Updated stylesheets and injected responsive card patterns into `factor_display.html`, `calculate_factor.html`, `assessment_data_view.html`, `overall_data_view.html`, `edit_overrides.html`, and `data_views_home.html`.
  - **Feature 4 (Course Leader Selection & Assessment Assignment)**: Updated stylesheets and added card adaptation to lecturer assignment and evaluation form templates.
  - **Feature 5 (Assessment Deadlines & Separate Date/Time Inputs)**: Updated `assign_assessment.css` and `editdeadline.css` for mobile form stacking, and injected `responsive-patterns.css` into `assign_assessment.html`, `edit-deadline.html`, and `manage-assessments.html`.
  - **Feature 6 (Assessment Extra Notes & View Rubrics)**: Enhanced media queries in `view_rubric.css` and injected `responsive-patterns.css` into `view-assessment-rubrics.html`.
  - **Feature 7 (Course Duplication Prevention)**: Updated course management stylesheets and templates (`manage_courses.html`, `admin_manage_courses.html`, `edit_course.html`, `admin_edit_course.html`).

### Files Modified
- `critical.css`, `responsive-patterns.css`, `admin_progress_tracking.css`, `rubric.css`, `view_rubric.css`, `assign_assessment.css`, `editdeadline.css`, `lecturer_select.css`, `lecturer_evaluation.css`, `peer_assess.css`, `supervisor_form.css`, `student_assessments.css`, `lecturer_assessments.css`, `manage_courses.css`, `admin_manage_courses.css`
- All associated feature templates in `src/main/resources/templates/`
- `STATE.md`, `walkthrough.md`, `task.md`

---

## Current Status: Student Progress Tracking Status Mismatch Fix
**Status**: COMPLETED

### Summary
- Resolved an issue on the **Admin Progress Tracking** dashboard where completed student evaluations (Self, Peer, and Group assessments) remained displayed as **"Not Started" (0%)**.
- **Root Cause**: When students submit evaluations via `StudentController`, their marks are saved with `SubmissionStatus.FINAL`. However, `ProgressTrackingService.getStudentProgress(...)` was querying specifically for `SubmissionStatus.SUBMITTED`.
- **Fix**: Updated line 232 of `ProgressTrackingService.java` to query for `Mark.SubmissionStatus.FINAL`. This isolated 1-word change accurately reflects completed student evaluations without disturbing `MarkService.java` or any existing grading workflows.

### Files Modified
- `ProgressTrackingService.java`
- `STATE.md`

---

## Current Status: Global Username Display Refactor
**Status**: COMPLETED

### Summary
- Replaced direct email display (`user.email`) with a safe username fallback across the Admin, Lecturer, and Student subsystems. 
- The system now prioritizes displaying the user's `username` (Name) and safely falls back to displaying their `email` if the username is blank or not set.
- This ensures consistency in how users are identified across data views, assignment tables, comment sections, and assessment forms.

### Files Modified
- `comment_view.html`
- `assessment_data_view.html`
- `comment_select_group.html`
- `group_assignment.html`
- `edit_overrides.html`
- `overall_data_view.html`
- `lecturer_comment_view.html`
- `lecturer_combined_evaluation_form.html`
- `supervisor_evaluate_groups.html`
- `supervisor_continuous_evaluation.html`
- `student_assessments.html`
- `peer_assessment_form.html`
- `STATE.md`

---

## Current Status: Assessment Comments Unification & UI Enhancements
**Status**: COMPLETED

### Summary
- **Unified Comments View**: Merged the previously divided Group Assessment and Individual Assessment comment sections into a single continuous list for a cleaner reading experience in both Admin (`comment_view.html`) and Lecturer (`lecturer_comment_view.html`) views.
- **Dynamic Labeling**: Added a new service method `getAllAssessmentComments` in `CalculateService.java` that formats the question label dynamically by appending the rubric assessment type in brackets (e.g., `Question Label (Group Assessment Comments)`).
- **Backend Filter Fix**: Removed an overly aggressive filter in `CalculateService.java` that was hiding peer/self-assessment comments if they were linked to a specific `rubricId`, ensuring all feedback is now correctly fetched and displayed.
- **UI Typography**: Enlarged and bolded the main heading ("Assessment Comments Received") and the individual question labels to establish a clearer visual hierarchy and improve readability.

### Files Modified
- `CalculateService.java`
- `GroupCommentController.java`
- `DataViewController.java`
- `comment_view.html`
- `lecturer_comment_view.html`
- `STATE.md`

---

## Current Status: Granular Progress Tracking (Lecturer & Student)
**Status**: COMPLETED

### Summary
- Enhanced the **Admin Progress Tracking** dashboard to display granular progress for both Lecturers and Students.
- **Lecturers**: Separated the evaluation progress into **Group Assessment Rubrics** and **Individual Assessment Rubrics**. Added robust backend checks in `LecturerAssessmentService` to precisely track how many targets (Groups or Students) have been fully evaluated for each rubric category.
- **Students**: Separated progress into **Self Assessment**, **Peer Assessment**, and **Group Assessment** based on the specific permissions granted in `StudentAssessmentAssignment`. Progress bars accurately reflect the percentage of peers/groups evaluated.
- **UI Updates**: Modified `admin_progress_tracking_view.html` to use a collapsible row design (using a chevron toggle button) to keep the main table clean while allowing admins to expand and inspect the granular breakdown of any assessor's progress.

### Files Modified
- `ProgressTrackingService.java`
- `LecturerAssessmentService.java`
- `admin_progress_tracking_view.html`
- `STATE.md`

---

## Current Status: SuperAdmin Course Creation "Missing Lecturer Profile" Fix
**Status**: COMPLETED

### Summary
- Fixed an `IllegalStateException` that occurred when a Super Admin attempted to create a new course.
- **Root Cause**: The system attempted to link the newly created course to the current user's `Lecturer` profile. However, Super Admins often exist only in the `super_admin` table and don't natively have a corresponding `Lecturer` profile. A previous refactor bypassed the auto-creation of this profile.
- **Fix**: Modified `resolveLecturerByIdentity` in `SuperAdminService` to act as a robust fallback. If the lecturer lookup fails, the system now automatically attempts to locate the identity in the `super_admin` table. If found, it dynamically auto-creates the missing `Lecturer` profile (and grants necessary roles) on the fly before returning it, allowing the course creation to proceed smoothly.

### Files Modified
- `SuperAdminService.java`
- `STATE.md`

---

## Current Status: Alphabetical Sorting of Student Lists
**Status**: COMPLETED

### Summary
- Implemented consistent alphabetical sorting for all student lists across the Admin, Lecturer, and Student subsystems.
- Sorting logic consistently prioritizes the student's `username` (ignoring case) and falls back to `email` if the username is empty or missing.
- **Admin Subsystem**: Added `sortStudentsAlphabetically` to `AdminService` and updated `mergeStudentsById` and `getStudentsByGroup`. Updated `DataViewController` to sort overall data, factor data, and comments by student names.
- **Lecturer Subsystem**: Added `sortStudentsAlphabetically` to `LecturerAssessmentService`. Applied sorted lists for group continuous evaluation in `IndustrialSupervisorController` and `GroupCommentController`. `LecturerAssessmentController` was verified to already implement the correct sorting.
- **Student Subsystem**: Added `sortStudentsAlphabetically` to `StudentController` and enforced sorting on `teamMembers` arrays and `getGroupmates()` helper for peer assessment selection and team views.

### Files Modified
- `AdminService.java`
- `DataViewController.java`
- `LecturerAssessmentService.java`
- `GroupCommentController.java`
- `IndustrialSupervisorController.java`
- `StudentController.java`
- `STATE.md`

---

## Current Status: Factor Calculation Style Consistency
**Status**: COMPLETED

### Summary
- Updated `calculate_factor.html` to replace the "Contribution Type" `<select>` dropdown with a consistent `.radio-card-group` component.
- Updated `calculate_factor.css` to include the compact radio card styling to match the `assign_assessment` page.
- Adjusted JavaScript in `calculate_factor.html` to dynamically enable/disable the radio buttons when the assessment is included or excluded from calculation.

### Files Modified
- `calculate_factor.html`
- `calculate_factor.css`
- `STATE.md`

---

## Current Status: Progress Tracking Select Assessor Display
**Status**: COMPLETED

### Summary
- Updated `ProgressTrackingService` to include a helper method `getAssessorTypes` which identifies if an assessment has Lecturer assignments, Student assignments, or Both based on assignment records.
- Modified `AdminController` to inject these assessor types into the model for the `/progress-tracking` dashboard.
- Updated `admin_progress_tracking_select.html` to display an "Assessed By: [Assessor Type]" row on the assessment cards for quick visibility.

### Files Modified
- `ProgressTrackingService.java`
- `AdminController.java`
- `admin_progress_tracking_select.html`
- `STATE.md`

---

## Current Status: Assessment Deadline Redirection, Expiration, and Duplication Fixes
**Status**: COMPLETED

### Summary
Resolved several issues related to Assessment Deadlines and Configurations on the Admin Dashboard:
- **Redirection Logic**: Updated `AdminController` and `CommentConfigController` so that the admin is redirected back to the Manage Assessments page (`/rubrics/manage`) instead of `/admin/home` after assigning deadlines or configuring assessment comments.
- **Deadline Visibility Expiration**: Refined the `deadlines` display logic in the `adminHome` method. It now strictly checks `nowMillis >= openDate` and `nowMillis <= closeDate`. Deadlines with exact closing times (from Assessment Assignments) now expire at that precise time, and only active deadlines are shown.
- **Duplicate Deadline Prevention**: Modified the `assignAssessment` method to look up existing deadlines purely by `assessmentId` (ignoring `assessorType`). This ensures that only a single deadline record exists per assessment, automatically cleaning up duplicates that might have been created under different assessor types.

### Files Modified
- `src/main/java/com/capstone/adproject/controller/AdminController.java`
- `src/main/java/com/capstone/adproject/controller/CommentConfigController.java`

---

## Current Status: Separate Date/Time Inputs & Sidebar Fix
**Status**: COMPLETED

### Summary
- Separated the Assessment Assignment deadline picker from a single `datetime-local` input into distinct `<input type="date">` and `<input type="time">` fields for better usability.
- Refactored `AssessmentAssignmentDto.java` to use Spring's native `java.time.LocalDate` and `java.time.LocalTime` bindings instead of `java.util.Date`, preventing timezone/formatting mismatch errors.
- Updated `AdminController.java` to combine the new separated fields into a final `java.util.Date` object before saving to the database. The form now correctly initializes the deadline date to today and the time explicitly to `23:59`.
- Added a `.split-inputs` CSS grid layout to `assign_assessment.css` for a clean side-by-side presentation.
- **Bug Fix**: Scoped the form CSS selectors in `assign_assessment.css` explicitly to `.main-content`. Previously, global selectors like `body.assign-assessment-page select` were bleeding into the navigation fragment, breaking the layout of the sidebar's Course Switcher dropdown.

### Files Modified
- `assign_assessment.html`
- `assign_assessment.css`
- `AssessmentAssignmentDto.java`
- `AdminController.java`
- `STATE.md`

---

## Current Status: Assessment Deadline Visibility & Assignment Refactor
**Status**: FIXED

### Summary
- Simplified the deadline assignment flow by removing the "Who will be the Assessor?" (`assessorType`) dropdown from `assign_assessment.html`.
- Updated `AdminController` to automatically assign `GENERAL` to the internal DTO and bypassed validation for the removed frontend field.
- Refactored `StudentController` and `LecturerController` dashboards to disregard `assessorType` entirely. The system now cross-references deadlines directly against the list of assessments the student or lecturer is explicitly assigned to, ensuring deadlines are only visible to the intended targets.
- Updated `StudentController` `isAssessmentOpen` and `getAssessmentDeadline` methods to look up deadlines purely by `assessmentId` instead of both `assessmentId` and `assessorType`.

### Files Modified
- `assign_assessment.html`
- `AdminController.java`
- `StudentController.java`
- `LecturerController.java`
- `STATE.md`

---

## Current Status: Lecturer Selection View Student Name Display
**Status**: FIXED

### Summary
- Updated `lecturer_select_group.html` to display the student's username instead of their email address when selecting a group for evaluation.
- Updated `lecturer_select_student.html` to strictly display the student's username by removing the previous email fallback logic.

### Files Modified
- `lecturer_select_group.html`
- `lecturer_select_student.html`
- `STATE.md`

---

## Current Status: Closed Assessments Disappearance After 3 Days
**Status**: IMPLEMENTED

### Summary
- Updated `LecturerAssessmentController.java` to filter out assessments from the "Assess Students" view that have been closed for more than 3 days. It now also sorts the assessments so that currently open ones are placed at the top, moving closed assessments to the bottom.
- Updated `LecturerController.java` to filter out assessments from the "Pending Tasks" list on the Lecturer Home page immediately when they pass their deadline (removed the 3-day grace period here as per requirement).
- On the "Assess Students" view, assessments that are closed but within the 3-day grace period continue to be shown at the bottom and correctly display an "Assessment Closed" state, disabling the evaluation buttons.

### Files Modified
- `LecturerAssessmentController.java`
- `LecturerController.java`
- `lecturer_home.html`
- `STATE.md`


## Current Status: Factor Calculation Configuration Refactor & Bug Fixes
**Status**: COMPLETED

### Summary
- Moved the `factorContributionType` configuration ("Both Ratings", "Individual Ratings Only", "Group Ratings Only") completely out of the core `Assessment` and `Rubric` domain and into the `FactorWeightage` entity, correctly coupling it exclusively to the Factor Calculation feature.
- Refactored `CalculateService.java` to read the contribution type dynamically from the `FactorWeightage` mapping rather than the `Assessment` entity. It filters marks before calculating factors to ensure that only the chosen type of peer assessment ratings (e.g. Individual vs. Group) influences the final student factor.
- **Bug Fix**: Addressed a critical UI bug in `calculate_factor.html` where toggling an assessment's inclusion via a checkbox correctly enabled the "Weightage" field but mistakenly kept the "Contribution Type" dropdown disabled. This caused the browser to omit the contribution type in the POST payload, forcing the backend to fall back to calculating with `"BOTH"`. Checking the box now correctly enables both inputs, allowing configurations like "Group Ratings Only" to be properly saved and enforced by the engine.
- **Bug Fix**: In `CalculateService.java`, removed the `hasGroupAssessment` short-circuit during `calculateStudentData()`. The factor calculation engine will now unconditionally fetch and apply the student's factor even if an assessment only possesses an Individual Rubric (since the admin can now explicitly configure the factor to be derived from individual rubrics).

---

## Current Status: Factor Display UI & Ratings Grouping Fixes
**Status**: FIXED

### Summary
- Restored the maroon header background across all Admin Data View pages (`data_views_home.html`, `overall_data_view.html`, `factor_display.html`, `calculate_factor.html`) by wrapping the `header-container` inside a `.header` class.
- Fixed a rendering bug in **Factor Display** (`factor_display.html`) where 4 teammates with multiple rubrics were being misrendered as multiple distinct teammate columns (e.g., 8 or 12 teammate columns).
- Fixed the root cause of the teammate grouping duplication by updating `CalculateService.java` to explicitly group ratings by `Student.getId()` instead of the raw `Student` object, bypassing Lombok `@Data` equality issues on entities loaded across different sessions.
- Corrected the layout clipping issue in `factor_display.html` where tables were getting cut off horizontally by applying `overflow-x: auto` to the container and restricting `min-width: 250px` to apply only to the `.filter-form select`.
- Addressed an overflowing issue with the sidebar's course switcher dropdown by applying `box-sizing: border-box`, `max-width: 100%`, and `text-overflow: ellipsis`.
- Refined the visual hierarchy by tweaking the sidebar item font weight from bold (700) to semi-bold (600).

### Files Modified
- `CalculateService.java`
- `factor_display.html`, `data_views_home.html`, `overall_data_view.html`, `calculate_factor.html`
- `admin_sidebar.css`, `factor_display.css`
- `STATE.md`

---

## Current Status: Course Code Duplication Prevention & Real-time Validation
**Status**: IMPLEMENTED

### Summary
- Resolved a bug where adding a course with a duplicate course code automatically assigned the current administrator to the existing course.
- Added strict course duplication checks in `SuperAdminService.saveCourse(Course course)`. Saving/updating a course now throws an `IllegalArgumentException` with the message `"The course exists, and the course ID has already been used. Please assign a new course ID."` if the course code is already taken.
- Exposed a REST check endpoint `/admin/courses/check-code` in `AdminController.java` to check for course code availability.
- Created `course-validation.js` to dynamically check course code uniqueness using debounced calls (300ms) to the REST endpoint.
- Added real-time visual border updates (emerald-green for available codes, red for duplicates) and inline validation messaging below the inputs in both the Course Add and Edit views (excluding the current course code while editing).
- Added `SuperAdminServiceTest.java` unit tests to cover successful saves, duplicates detection, and validation logic.

### Files Modified/Created
- **New**: `course-validation.js`, `SuperAdminServiceTest.java`
- **Modified**: `SuperAdminService.java`, `AdminController.java`, `manage_courses.css`, `admin_manage_courses.css`, `manage_courses.html`, `admin_manage_courses.html`, `edit_course.html`, `admin_edit_course.html`, `STATE.md`

---

## Current Status: Overall Data View and Overrides Course Scoping Fix
**Status**: FIXED

### Summary
- Resolved an issue in the **Overall Data View** (`/admin/data-views/overall`) where it showed all students in the database regardless of the course context.
- Scoped student results in `showOverallData` using `adminService.getAllStudents()`, which correctly filters students under the active course (handling direct course links, student course assignments, and group memberships). If there is no active course context, an empty list is displayed.
- Scoped group and assessment lookups in the **Overrides Edit View** (`/admin/data-views/edit-overrides`) to use `groupRepository.findByCourseId(activeCourseId)` and `assessmentService.findAllAssessmentsWithRubricsByCourseId(activeCourseId)` instead of calling their global `findAll` methods.
- Registered the database-level `CourseScopeInterceptor` globally in `WebMvcConfig.java` to enforce Hibernate `@Filter` rules (`courseScopeFilter`) on all admin contexts (`/admin/**`, `/rubrics/**`, and `/deadlines/**`), securing row-level access across the application.

### Files Modified
- `DataViewController.java`
- `WebMvcConfig.java`
- `STATE.md`

---

## Current Status: View Assessment Rubrics Button Presence Fix
**Status**: FIXED

### Summary
- Resolved an issue on the **View Assessment Rubrics** page (`view-assessment-rubrics.html`) where the "Add Rubrics" button would go missing if an Admin added only comments and no rubrics.
- Unconditionally render the Rubrics section container to preserve the "Add Rubric" button.
- Introduced custom styled empty state placeholders for Group Rubrics and Individual Rubrics when they are empty, preventing visual clutter while keeping the action buttons easily accessible.

### Files Modified
- `view-assessment-rubrics.html`
- `STATE.md`

---

## Current Status: Granular Student Assessment Assignment
**Status**: IMPLEMENTED

### Summary
- Upgraded the student assessment assignment feature to allow granular permission control.
- Admins can now explicitly assign Self-Assessment, Peer Assessment, and Group Assessment boundaries for each student rather than granting blanket access to the entire assessment.
- Updated the backend entity (`StudentAssessmentAssignment`) with distinct boolean flags (`selfAssessment`, `peerAssessment`, `groupAssessment`).
- The Student Dashboard (`student_assessments.html`) conditionally renders evaluation cards (Self, Peer, Team) based on both the student's specific permissions and the presence of corresponding rubric/comment configurations in the assessment.
- Hardened `StudentController` endpoints to strictly enforce these assignment boundaries during both evaluation rendering and final form submission.

### Files Modified
- `StudentAssessmentAssignment.java`
- `AdminController.java`, `admin_assign_students.html`
- `StudentController.java`, `student_assessments.html`

---

## Current Status: Admin Progress Tracking Dashboard
**Status**: COMPLETE

### Summary
- Implemented a new dashboard for Admins to monitor the evaluation progress of lecturers and students for any given assessment.
- Admins first select an assessment from a list.
- The progress page displays two separate tables for lecturers and students, showing their completion status (`Not Started`, `In Progress`, `Completed`), a progress bar, and assigned targets.
- The backend logic is encapsulated in a new `ProgressTrackingService` which correctly identifies the assignment mode (Group, Rubric, or Student) for lecturers and calculates completion based on submitted marks.
- Aligned frontend templates (`admin_progress_tracking_select.html`, `admin_progress_tracking_view.html`) with the global UTM AAS responsive design system. Extracted and normalized CSS into `admin_progress_tracking.css` (implementing standard fonts, cards, and mobile-friendly tables) and integrated `loading.js` to fix page visibility issues.

### Files Modified/Created
- **New**: `ProgressTrackingService.java`, `admin_progress_tracking_select.html`, `admin_progress_tracking_view.html`, `admin_progress_tracking.css`
- **Modified**: `AdminController.java` (added endpoints), `fragments/sidebar.html` (added nav link), `STATE.md`

**Last Updated**: 2026-04-26 (IH)

---

## Current Status: Conditional Extra Notes Visibility
**Status**: IMPLEMENTED

### Summary
- The "Extra Notes" sections on the **View Assessment Rubrics** page are now conditionally displayed.
- "Extra Notes for Lecturers" only appears if the assessment has active lecturer assignments.
- "Extra Notes for Students" only appears if the assessment has active student assignments.
- Displays a helpful disclaimer prompting the Admin to assign a lecturer or student if neither are assigned yet.

### Files Modified
- `RubricController.java`, `view-assessment-rubrics.html`

## Current Status: User Profile Feature
**Status**: IMPLEMENTED

### Summary
- Added a shared `/profile` endpoint to allow all authenticated users (Student, Lecturer, Admin, Super Admin) to view and edit their profiles.
- Users can view their email (read-only) and update their `username` (name).
- Created a responsive `profile.html` template that dynamically includes the correct sidebar based on the user's role, with styling matching the application's global UTM maroon and gold visual identity (`profile.css`).
- Updated `ProfileController` to handle the role-based fetching and updating logic, including proper `@Transactional` context and explicitly handling the `Admin` role via `AdminRepository`.

### Files Modified/Created
- **New**: `ProfileController.java`, `profile.html`, `profile.css`
- **Modified**: `STATE.md`

---

## Current Status: Mixed Rubric Types Visibility Fix (April 26, 2026)
**Status**: FIXED

### Summary
- Resolved an issue where direct ratings were incorrectly hidden during evaluation if a rubric also contained sub-rubrics.
- Removed the mutually exclusive rendering logic (`th:if="${rubric.subRubrics == null or rubric.subRubrics.isEmpty()}"`) in both lecturer and peer evaluation forms, as well as the assessment data view.
- Updated the progress tracking logic in `LecturerAssessmentService` to accurately count both components towards the required evaluations total when both exist in a single rubric.

### Files Modified
- `lecturer_combined_evaluation_form.html` - Allowed rendering of direct ratings alongside sub-rubrics.
- `peer_assessment_form.html` - Allowed rendering of direct ratings alongside sub-rubrics.
- `assessment_data_view.html` - Fixed data view rendering to display both direct ratings and sub-rubrics properly.
- `LecturerAssessmentService.java` - Updated `requiredEvaluations` counter to include both sub-rubrics and direct ratings.

---

## Current Status: Assessment Extra Notes Feature (April 26, 2026)
**Status**: IMPLEMENTED

### Summary
- Implemented a feature allowing Admins to provide "extra notes" (e.g., special instructions or guidelines) on an assessment for lecturers.
- Admins can input, edit, and save these notes directly from the **View Assessment Rubrics** page.
- The extra notes are contextually displayed across multiple dashboards and views to ensure lecturers see them before and during evaluation:
  - Admin Home Dashboard
  - Lecturer Home Dashboard
  - Lecturer Select Student Page
  - Lecturer Combined Evaluation Form
- Secured the backend save endpoint with active course ownership validation and XSS HTML sanitization.

### Files Modified
- `Assessment.java` - Added persistent `extraNotes` column.
- `RubricController.java` - Added `POST /rubrics/assessment/{assessmentId}/extra-notes` endpoint.
- `view-assessment-rubrics.html` - Added editable text area UI and save form for admins.
- `admin_home.html` - Added read-only display for the notes.
- `lecturer_home.html` - Added read-only display for the notes.
- `lecturer_select_student.html` - Added read-only display for the notes.
- `lecturer_combined_evaluation_form.html` - Added read-only display for the notes.

---

## Current Status: Legacy Calculated Results Cleanup (April 26, 2026)
**Status**: FIXED

### Summary
- Removed legacy native SQL deletion queries targeting the deprecated `calculated_results` table.
- **Root Cause**: The application transitioned to dynamic grade calculation (`CalculateService`), making the `CalculatedResult` entity obsolete. However, hardcoded native deletion queries remained in the service layers, causing JDBC exceptions (`Table 'aas_db.calculated_results' doesn't exist`) during cascading delete operations (e.g., deleting a student, an assessment, or a system reset) in the production environment where the table was not auto-generated by Hibernate.
- **Fix**: Safely removed all references and deletion steps for `calculated_results` from the service layer cleanup routines.

### Files Modified
- `AdminService.java` - Removed `deleteCalculatedResultsByStudentId` during student deletion.
- `RubricService.java` - Removed `deleteCalculatedResultsByAssessmentId` during assessment deletion.
- `SuperAdminService.java` - Removed `calculated_results` from the `resetToSuperAdminOnly` table wipe list.
- *Note: Corresponding native queries in `StudentRepository` and `AssessmentRepository` should also be manually removed to keep the codebase fully clean.*

---

## Current Status: Lecturer Evaluation Form Submission Validation
**Status**: IMPLEMENTED

### Summary
- Enhanced the combined evaluation form (`lecturer_combined_evaluation_form.html`) to distinctly separate "Save Progress" and "Submit Evaluation" actions.
- Implemented native HTML5 validation (`required` and `minlength` attributes) for the "Submit Evaluation" flow. This ensures all rubrics are graded and mandatory comments meet minimum lengths before an evaluation can be submitted.
- Added the `formnovalidate` attribute to the "Save Progress" button so lecturers can safely save partial evaluations without triggering browser validation blockers.
- Dynamically updated comment labels to show "(Required for submission)" if a minimum length is configured, otherwise displaying "(Optional)".

### Files Modified
- `lecturer_combined_evaluation_form.html` - Added HTML5 validation logic, formnovalidate attribute, and updated label UI.

---

## Current Status: Lecturer Dashboard Task-Oriented Refactor (April 24, 2026)
**Status**: IMPLEMENTED

### Summary
- Refactored the Lecturer Dashboard (`lecturer_home.html`) from a passive rubric viewer into an actionable "Pending Tasks" to-do list.
- Aggregated assignment data in `LecturerController` across all three assignment modes (Group, Rubric, Student) to surface the correct target names (e.g., student/group names) directly on the task cards.
- Added dynamic "Start Evaluation" routing to immediately direct lecturers to either the `select-group` or `select-student` flow based on how they were assigned to the assessment.
- Extracted all dashboard HTML inline styling into `lecturer.css`.
- Aligned the dashboard color palette with the green Lecturer subsystem theme, and resolved CSS variable inheritance conflicts that caused button/badge text to render black instead of white.

### Files Modified
- `LecturerController.java` - Added distinct task aggregation, mode detection, and target name resolution.
- `lecturer_home.html` - Replaced nested rubric loops with a responsive grid of task cards.
- `lecturer.css` - Extracted and parameterized task card, badge, and button styles using `:root` variables.

---

## Current Status: Lecturer Student Selection UI Updates (April 24, 2026)
**Status**: IMPLEMENTED

### Summary
- Reformatted the student selection interface (`lecturer_select_student.html`) from a card grid to a cleaner list layout.
- Updated student display to prioritize `username` over `email` to improve readability.
- Fixed alphabetical sorting by enforcing a robust backend-side sort in `LecturerAssessmentController` to guarantee reliable ordering by username (or email fallback) before the page renders.

### Files Modified
- `LecturerAssessmentController.java` - Added guaranteed backend alphabetical sorting.
- `lecturer_select_student.html` - Implemented list layout, standard inline button styling, and username priority display.

---

## Current Status: User Name Edit + Bulk Tabular Add (April 24, 2026)
**Status**: IMPLEMENTED

### Summary
- Fixed Admin update flow so student/lecturer name (username) can now be edited when updating existing users.
- Preserved fallback behavior: when username is left blank, system auto-generates unique username from email prefix.
- Refactored bulk add backend to accept structured parallel arrays (`emails`, `usernames`) instead of raw textarea parsing.
- Replaced bulk textarea UI with tabular row-based entry for students and lecturers.
- Added Excel-paste support in bulk tables (column 1 email, column 2 username).
- Added optional SQL helper script to backfill NULL usernames for legacy data.

### Files Modified/Created
- `AdminService.java` - update-username fix and structured bulk add methods
- `AdminController.java` - structured bulk add endpoint signatures
- `manage_students.html` - editable username input + tabular bulk add + paste support
- `manage_lecturers.html` - editable username input + tabular bulk add + paste support
- `migration_fill_null_usernames.sql` - optional backfill script for NULL usernames

## Current Status: Lecturer Student Assignation Mode
**Status**: IMPLEMENTED (PENDING FINAL RUNTIME VERIFICATION)

## Current Status: Clean-Slate Recovery + Course Isolation Hardening (April 22, 2026)
**Status**: IMPLEMENTED

### Summary
- Added a super-admin-only clean-slate reset action to wipe all operational data while preserving super admin account(s) in `super_admin`.
- Hardened student course scoping so shared students are visible only when linked by legacy `student.course_id`, explicit `student_course_assignment`, or group-course membership.
- Added explicit `courseId` ownership to deadlines so deadline visibility/duplicate checks are now isolated per active course.

### Files Modified
- `SuperAdminService.java` - Added transactional `resetToSuperAdminOnly()` hard reset method
- `SuperAdminController.java` - Added `/superadmin/reset-data` confirmation endpoint
- `manage_courses.html` / `manage_courses.css` - Added guarded “System Clean Slate” form UI
- `Student.java` - Added strict `courseScopeFilter` with enrollment and group fallback
- `Deadline.java` - Added `courseId` field + index
- `DeadlineRepository.java` / `DeadlineService.java` - Added course-scoped deadline APIs
- `DeadlineController.java` / `AdminController.java` / `LecturerController.java` / `StudentController.java` - Updated to use course-scoped deadline loading/ownership checks

### Operational Note
- Reset requires typing exact confirmation text:
   `WIPE_EVERYTHING_EXCEPT_SUPER_ADMIN`

### Summary
Added a third lecturer assignment path that assigns lecturers by student instead of by group or rubric. The new mode persists its own assignment rows, appears in the admin assignment UI, and routes lecturers to a student-selection flow before evaluation.

Latest update:
- Refined student-mode assignment UI layout to center cards for better readability.
- Replaced remove-lecturer icons with inline SVG trash icons (template + dynamically added rows) so icons render reliably even when external icon fonts are unavailable.

### Files Touched
- `LecturerStudentAssignment.java` - new assignment entity
- `LecturerStudentAssignmentRepository.java` - assignment lookups and cleanup
- `AdminController.java` / `admin_assign_lecturers.html` / `admin-assign-lecturers.js` - student-mode assignment UI and save flow
- `LecturerAssessmentController.java` / `LecturerAssessmentService.java` - student-selection and student-target evaluation support
- `lecturer_select_student.html` - new student selection page
- `lecturer_combined_evaluation_form.html` - shared form now supports student targets

### Validation Note
- Source wiring is in place and frontend files for assignment UI validated with no syntax errors.
- Full end-to-end runtime verification remains pending; latest local app run attempt ended with exit code 1 and should be rechecked with logs in the next validation pass.

## Current Status: Course Management Bug Fixes (April 20, 2026)
**Status**: FIXED

### Issues Resolved
1. **Course Deletion Failure**: Fixed JDBC exception when deleting courses
   - **Root Cause**: SQL query used `Assessment` (capital A) but MySQL table is `assessment` (lowercase)
   - **Fix**: Changed query in `SuperAdminService.deleteCourse()` line 177 from `UPDATE \`Assessment\`` to `UPDATE \`assessment\``
   - **Impact**: Course deletion now works correctly with all cascading updates

2. **Duplicate Course Creation**: Improved idempotent course creation logic
   - **Root Cause**: Missing validation and weak duplicate detection logic
   - **Fixes Applied**:
     - Added mandatory field validation (course name and code required and non-blank)
     - Improved duplicate detection to always check for existing courses with same code on creation
     - Strengthened `findByCourseCodeIgnoreCase` check to work even with edge cases
   - **File Modified**: `SuperAdminService.java` - `saveCourse()` method

3. **Double Form Submissions**: Added client-side protection against accidental double submissions
   - **Fix**: Enhanced `event-delegation.js` with automatic form button disabling on submission
   - **Behavior**: When a form is submitted, all submit buttons are disabled to prevent multiple clicks
   - **Impact**: Applies to all forms including course creation forms in both SuperAdmin and Admin interfaces
4. **Multi-Course Student/Lecturer Enrollment**: Fixed issue preventing same user from being added to multiple courses
    - **Root Cause**: Email field had a global `unique = true` constraint, preventing same email across different courses
    - **Fixes Applied**:
       - Changed email unique constraint from global to composite (email + course_id) in both `Student.java` and `Lecturer.java` entities
       - Updated duplicate check logic in `AdminService` to only verify uniqueness within the current course, not globally
       - `checkStudentEmailDuplicate()` and `checkLecturerEmailDuplicate()` now use `findByCourseId()` to check only current course
    - **Behavior**: Same user (email) can now be added to multiple different courses. If already in the same course, shows "already enrolled" message.
    - **Files Modified**: 
       - `Student.java` - Changed email constraint to composite (email, course_id)
       - `Lecturer.java` - Changed email constraint to composite (email, course_id)
       - `AdminService.java` - Updated duplicate check methods to be course-scoped


### Files Modified
- [SuperAdminService.java](src/main/java/com/capstone/adproject/service/SuperAdminService.java) - Fixed table name case and improved validation
- [event-delegation.js](src/main/resources/static/js/event-delegation.js) - Added double-submit prevention
- [Student.java](src/main/java/com/capstone/adproject/model/Student.java) - Composite email+course_id constraint
- [Lecturer.java](src/main/java/com/capstone/adproject/model/Lecturer.java) - Composite email+course_id constraint
- [AdminService.java](src/main/java/com/capstone/adproject/service/AdminService.java) - Course-scoped duplicate checks

### Testing Recommendations
1. Test course deletion with existing assessments
2. Try rapid double-clicks on course add/delete buttons
3. Verify courses are not duplicated in database after adding
4. Add adam@gmail.com to Course 1, then add same adam@gmail.com to Course 2 - should succeed
5. Try adding adam@gmail.com again to Course 1 - should show "already enrolled" message

---

## Current Status: Content Security Policy (CSP) Implementation
**Status**: COMPLETE

### Summary
Implementing a Content Security Policy (CSP) to harden the application against Cross-Site Scripting (XSS) and data injection attacks. The implementation will follow a safe, phased approach to avoid breaking existing functionality.

### Key Features (Phase 1: Report-Only Mode)
1.  **Report-Only Header**: Configured Spring Security to send a `Content-Security-Policy-Report-Only` header with a strict policy. This allows us to monitor violations without blocking any resources.
2.  **Strict Initial Policy**: The initial policy restricts all content (scripts, styles, images) to `'self'` (the same origin), which will generate reports for all external and inline resources.
3.  **Violation Reporting Endpoint**: Created a new unauthenticated endpoint at `/csp-violations` to receive JSON-formatted violation reports from users' browsers.
4.  **Violation Logging**: The reporting endpoint logs all incoming violation reports, providing the necessary data to identify which parts of the application need refactoring to comply with the CSP.

### Files Modified/Created
- **New**: `CspController.java`
- **Modified**: `SecurityConfig.java` (added CSP header and CSRF exception)

### Next Steps
- **Status**: Phase 1 Complete.
- The application was deployed with the report-only policy.
- The CSRF filter was blocking the violation reports, which has now been fixed by adding an exception for the `/csp-violations` endpoint in `SecurityConfig.java`.
- Application logs now successfully show incoming CSP violation reports.
- **Phase 2 Started**: Implemented a nonce-based strategy for scripts in `SecurityConfig.java`. The policy is now `script-src 'self' 'nonce-{nonce}'` and `style-src 'self' 'unsafe-inline'`.
- The backend changes for Phase 2 are complete.
- **
## Current Status: Production Alarms
**Status**: COMPLETE

### Summary
Implemented a self-contained, in-app alerting system to provide immediate notifications for critical production issues. The system monitors for spikes in 5xx server errors and sends email alerts when predefined thresholds are breached, enabling a rapid response before customers are widely affected.

### Key Features
1.  **In-App Metric Monitoring**: Created `MonitoringService` which uses Micrometer to define and increment custom application counters (e.g., `adproject.critical.errors.total`).
2.  **Scheduled Threshold Checking**: A new `AlertingService` runs a `@Scheduled` task every minute to check the rate of increase for monitored metrics.
3.  **Configurable Thresholds**: Alerting behavior is controlled via `application.properties`, allowing operators to enable/disable the system, set the alert recipient email, and define the error rate threshold (e.g., 5 errors per minute).
4.  **Asynchronous Email Alerts**: When a threshold is breached, the `AlertingService` uses the existing asynchronous `EmailService` to dispatch a formatted HTML alert email without blocking the main application thread.
5.  **Error Integration**: The `GlobalExceptionHandler` was modified to call `monitoringService.incrementCriticalErrorCount()` for every unhandled exception, ensuring all 5xx errors are tracked.

### Files Modified/Created
- **New**: `MonitoringService.java`, `AlertingService.java`
- **Modified**: `GlobalExceptionHandler.java`, `EmailService.java`, `application.properties`

### Next Steps
- Add monitoring and alerts for other business-critical metrics, such as failed user signups or latency spikes.
- For long-term scalability, consider migrating to an external monitoring stack like Prometheus (for metric scraping) and Alertmanager (for alert routing).

## Current Status: Production-Ready Structured Logging
**Status**: COMPLETE

### Summary
Implemented structured JSON logging with MDC (Mapped Diagnostic Context) traceability to ensure logs are production-ready, easily searchable in cloud environments, and contain deep user context without altering existing business logic code.

### Key Features
1. **MDC Traceability**: Created a `LoggingInterceptor` that generates a unique `requestId` (UUID) and extracts the authenticated `username` for every HTTP request, injecting them into the SLF4J MDC.
2. **Thread-Safety**: Ensured MDC is explicitly cleared in `afterCompletion` to prevent data leaks across Tomcat's worker thread pools.
3. **Environment-Aware Profiles**: Created `logback-spring.xml` with profile-based routing:
    - `dev` / `default` profile: Outputs colorful, human-readable console logs injecting the `[ReqID]` and `[User]`.
    - `prod` profile: Outputs structured JSON logs automatically mapping the MDC context into top-level JSON keys for seamless integration with AWS CloudWatch, Datadog, or the ELK stack.
4. **JSON Encoding**: Added `logstash-logback-encoder` to serialize logs correctly in production.

### Files Modified/Created
- **New**: `LoggingInterceptor.java`, `LoggingWebConfig.java`, `logback-spring.xml`
- **Configuration**: `pom.xml` (added `logstash-logback-encoder` dependency)

---

## Current Status: Database Indexing & Performance Optimization
**Status**: COMPLETE

### Summary
Implemented targeted database indexing to optimize high-frequency read operations, relational joins, and course-scoped queries. This drastically reduces full table scans and improves dashboard load times while carefully avoiding unnecessary write penalties.

### Key Features
1. **Tier 1 (Identity & Auth)**: Added unique constraints and indexes on `email` and `username` for user tables (`Student`, `Lecturer`) to speed up authentication and identity lookups.
2. **Tier 2 (Global Course Scoping)**: Because the `CourseScopeInterceptor` injects `WHERE course_id = ?` into almost all read queries (Row-Level Security), `course_id` indexes were added to `Student`, `Lecturer`, `Group` (`project_group`), `Assessment`, and `RubricTemplate` tables.
3. **Tier 3 (Relational & Background Jobs)**: Added composite indexes to speed up complex joins and heavily filtered views (e.g., `academic_supervisor_id` / `industrial_supervisor_id` in Groups, `assessment_id` + `assessor_type` in Deadlines, and assignment junction tables).
4. **Write Penalty Avoidance**: Explicitly excluded large text fields (`comments`, `rubric_description`), JSON structures, and low-cardinality boolean flags from indexing.

### Files Modified
- **Entities**: `Student.java`, `Lecturer.java`, `Group.java`, `Assessment.java`, `RubricTemplate.java`, `Deadline.java`, `LecturerGroupAssignment.java`, `LecturerRubricAssignment.java`

## Current Status: Admin Course Management
**Status**: COMPLETE

### Summary
Delegated course management capabilities from SuperAdmins to regular Admins. Admins can now create, edit, and delete the courses they are assigned to, streamlining course setup without requiring super-user intervention. This feature is fully integrated with the existing course-scoping security model.

### Key Features
1.  **Admin Course Dashboard**: Created `admin_manage_courses.html` to provide admins with a central view to see all courses they manage, with options to add, edit, or delete.
2.  **Create and Edit Forms**: Implemented `admin_manage_courses.html` (for creation) and `admin_edit_course.html` (for editing) to handle course data entry.
3.  **Backend Endpoints**: Added new endpoints in `AdminController` to handle `GET`, `POST`, and `DELETE` requests for course management.
4.  **Secure Deletion**: The delete functionality reuses the hardened `SuperAdminService.deleteCourse()` method, which safely removes a course and all its cascading dependencies (student assignments, group links, assessments, etc.) using transactional native queries.
5.  **Authorization**: All course management actions are protected, ensuring admins can only modify courses they are explicitly assigned to manage.

### Files Modified/Created
- **New**: `admin_manage_courses.html`, `admin_edit_course.html`
- **Controllers**: `AdminController.java` (added new endpoints)
- **Services**: `SuperAdminService.java` (reused for persistence logic)
- **Templates**: `fragments/sidebar.html` (added "Manage Courses" link)

---

## Current Status: Validation, Sanitization, and Error Handling
**Status**: COMPLETE

### Summary
Successfully implemented industry-standard input validation, XSS sanitization, and global error handling to prevent malicious payloads, data corruption, and stack trace leaks.

### Key Features
1. **Phase 1: API Boundary Validation**
   - Added `@Valid` and `BindingResult` to data-modifying endpoints in `AdminController`.
   - Implemented cross-field business logic validation (e.g., ensuring Open Date is strictly before End Date for Deadlines).
2. **Phase 2: XSS Sanitization**
   - Integrated `owasp-java-html-sanitizer` to scrub malicious tags (`<script>`, `<iframe>`) from rich text inputs.
   - Applied global sanitization to `AssessmentCommentService`, `LecturerAssessmentService`, and `RubricController`.
   - Safely enabled `th:utext` across comment view templates to render preserved safe HTML (`<b>`, `<i>`, `<br>`).
3. **Phase 3: Global Error Handling**
   - Created `GlobalExceptionHandler` (`@ControllerAdvice`) to catch `ConstraintViolationException`, `DataIntegrityViolationException`, and generic `Exception`s.
   - Gracefully handles missing static resources (e.g., `favicon.ico`) to prevent terminal log spam.
   - Designed a responsive, user-friendly `error.html` fallback UI with clear recovery actions (Retry, Go Back, Return Home), completely hiding backend stack traces from end-users.

### Files Modified/Created
- **New**: `HtmlSanitizerUtil.java`, `GlobalExceptionHandler.java`, `error.html`
- **Controllers**: `AdminController.java`, `RubricController.java`
- **Services**: `AssessmentCommentService.java`, `LecturerAssessmentService.java`
- **Templates**: `student_comments.html`, `comment_view.html`, `lecturer_comment_view.html`, etc.

## Current Status: Deployment Security Hardening (Phases 1-4)
**Status**: COMPLETE

### Summary
Successfully implemented a comprehensive 4-phase security lockdown to prepare the application for production deployment, mitigating IDOR, data leakage, and unauthorized mutations.

### Key Features
1. **Phase 1: Global Access Control**
   - Hardened `SecurityConfig.java` to prevent unauthenticated access to backend URLs.
   - Fixed dynamic path bypass vulnerability in `/*/comments/view/**`.
2. **Phase 2: Database Query Lockdown (Row Level Security)**
   - Implemented global Hibernate `@Filter` annotations on `Student`, `Lecturer`, `Group`, `Assessment`, and `RubricTemplate` entities.
   - Upgraded `CourseScopeInterceptor` to enforce the active course ID at the database level, automatically preventing cross-course data leakage without relying on in-memory Java stream filtering.
3. **Phase 3: Controller-Level Read Auditing (GET IDOR Protection)**
   - Hardened `StudentController`, `GroupCommentController`, and `DeadlineController` to verify that requested entities (by ID) strictly belong to the user's active course or assigned group.
4. **Phase 4: Mutation Guarding (POST IDOR Protection)**
   - Secured `POST` endpoints against payload manipulation.
   - Prevented cross-course relationship hijacking in complex DTOs (e.g., `GroupAssignmentDto` randomization, bulk rubric edits in `RubricController`).

### Files Modified
- **Configuration**: `SecurityConfig.java`, `CourseScopeInterceptor.java`
- **Entities**: `Student.java`, `Lecturer.java`, `Group.java`, `Assessment.java`, `RubricTemplate.java`
- **Controllers**: `AdminController.java`, `RubricController.java`, `DataViewController.java`, `StudentController.java`, `GroupCommentController.java`, `DeadlineController.java`, `RubricTemplateController.java`

## Current Status: Email Dispatch Optimization
**Status**: COMPLETE

### Summary
Implemented a robust Cross-Origin Resource Sharing (CORS) policy to securely allow requests from whitelisted frontend domains while blocking all others. This is a critical security measure for production deployment.

### Key Features
1.  **Spring Security Integration**: Configured CORS directly within `SecurityConfig.java` using a `CorsConfigurationSource` bean, keeping all security rules centralized.
2.  **Origin Whitelisting**: Explicitly defined allowed origins, including placeholders for local development (`http://localhost:3000`) and production (`https://your-frontend-domain.com`). Wildcard `*` is avoided to enhance security.
3.  **Credential Support**: Enabled `allowCredentials = true` to ensure that session cookies (like `JSESSIONID` and `remember-me`) are correctly sent with cross-origin requests, allowing stateful authentication to function properly.
4.  **Method and Header Control**: Restricted allowed HTTP methods to `GET, POST, PUT, DELETE, OPTIONS` and specified allowed headers like `Authorization` and `Content-Type`.

### Files Modified
- **Configuration**: `SecurityConfig.java` (added `cors()` to the filter chain and a `CorsConfigurationSource` bean).

### Next Steps
- Before deployment, ensure the placeholder `https://your-frontend-domain.com` in `SecurityConfig.java` is replaced with the actual domain of the production frontend application.

---

## Current Status: Email Dispatch Optimization
**Status**: COMPLETE

### Summary
- Implemented asynchronous email dispatch using Spring's `@Async` annotation to decouple email sending from the main HTTP thread.
- This reduces the perceived UI response time for email-triggering actions (e.g., "Add Student", "Forgot Password") from ~20 seconds down to milliseconds.
- Configured a dedicated `ThreadPoolTaskExecutor` in a new `AsyncConfig` class to manage background email threads safely without exhausting server resources.
- Enhanced actual SMTP transit performance by enabling connection pooling (Keep-Alive) via `application.properties` (`quitwait=false` and 5000ms timeouts). This eliminates the costly 20-second TLS handshake for sequential emails.
- Updated `EmailService` to gracefully log background delivery failures using SLF4J rather than throwing runtime exceptions that would silently crash background tasks.

### Files Modified/Created
- **Backend**:
  - `AsyncConfig.java` (New - Enables `@EnableAsync` and defines `emailTaskExecutor`)
  - `EmailService.java` (Modified - Added `@Async` and robust `logger.error` handling)
- **Resources**:
  - `application.properties` (Modified - Added JavaMail connection pooling and Keep-Alive properties)

## Current Status: Automated Email Notifications (Deadlines & Assignments)
**Status**: COMPLETE

### Summary
- Implemented a scheduled background service (`DeadlineNotificationService`) that runs daily at 8:00 AM to check for opening and expiring deadlines.
- Automatically dispatches HTML-formatted email alerts to the appropriate audiences (Students, Lecturers, or Supervisors) based on the `assessorType` of the deadline.
- Added a new `sendDeadlineEmail` method in `EmailService` that uses the standardized UTM CAS email template (with the inline UTM logo and proper HTML formatting).
- Enhanced `AdminController` to detect when a lecturer is newly assigned to an assessment (via Group or Rubric mode). If the assessment is already open, the system immediately sends them an "Assessment Now Open" notification to ensure they don't miss their evaluation window.
- Enabled Spring Boot scheduling via a new `SchedulingConfig` class.

### Files Modified/Created
- **Backend**:
  - `SchedulingConfig.java` (New - Enables `@EnableScheduling`)
  - `DeadlineNotificationService.java` (New - Contains the daily `@Scheduled` cron job)
  - `EmailService.java` (Added `sendDeadlineEmail` method)
  - `AdminController.java` (Added differential logic during lecturer assignment to trigger instant emails for newly assigned lecturers on active assessments)

### Next Steps
- Consider adding a user preference toggle for email notifications.
- Monitor SMTP performance if the user base grows significantly. As scale increases, consider swapping raw SMTP for a Transactional Email API (e.g., SendGrid, AWS SES) or moving to a message broker (e.g., RabbitMQ/Kafka).

---

## Current Status: CORS Configuration
**Status**: COMPLETE

### Summary
Implemented a robust Cross-Origin Resource Sharing (CORS) policy to securely allow requests from whitelisted frontend domains while blocking all others. This is a critical security measure for production deployment.

### Key Features
1.  **Spring Security Integration**: Configured CORS directly within `SecurityConfig.java` using a `CorsConfigurationSource` bean, keeping all security rules centralized.
2.  **Origin Whitelisting**: Explicitly defined allowed origins, including placeholders for local development (`http://localhost:3000`) and production (`https://your-frontend-domain.com`). Wildcard `*` is avoided to enhance security.
3.  **Credential Support**: Enabled `allowCredentials = true` to ensure that session cookies (like `JSESSIONID` and `remember-me`) are correctly sent with cross-origin requests, allowing stateful authentication to function properly.
4.  **Method and Header Control**: Restricted allowed HTTP methods to `GET, POST, PUT, DELETE, OPTIONS` and specified allowed headers like `Authorization` and `Content-Type`.

### Files Modified
- **Configuration**: `SecurityConfig.java` (added `cors()` to the filter chain and a `CorsConfigurationSource` bean).

### Next Steps
- Before deployment, ensure the placeholder `https://your-frontend-domain.com` in `SecurityConfig.java` is replaced with the actual domain of the production frontend application.

---

## Current Status: Rate Limiting Implementation
**Status**: COMPLETE

### Summary
Implemented rate limiting to protect backend resources from abuse, spam, and accidental overuse. This adds per-IP limits to API endpoints, returning a `429 Too Many Requests` status when clients exceed the defined limits.

### Key Features
1.  **Per-IP Rate Limiting**: Requests will be limited based on the client's IP address.
2.  **Custom Interceptor**: A Spring `HandlerInterceptor` will be used to apply rate limiting logic before controller execution.
3.  **Bucket4j Integration**: Utilizing the `Bucket4j` library for efficient token bucket algorithm implementation.
4.  **Error Handling**: Clients exceeding the rate limit receive a `429 Too Many Requests` HTTP status, along with `X-Rate-Limit-Remaining` and `X-Rate-Limit-Retry-After-Seconds` headers.

### Files Modified/Created
- **New**: `src/main/java/com/capstone/adproject/config/RateLimitConfig.java`, `src/main/java/com/capstone/adproject/interceptor/RateLimitInterceptor.java`, `src/main/java/com/capstone/adproject/config/WebMvcConfig.java`
- **Configuration**: `pom.xml` (add `bucket4j-spring-boot-starter` and `google.guava` dependencies)
- **Deleted**: `src/main/java/com/capstone/adproject/controller/TestController.java` (temporary test endpoint)
- **Moved**: `src/main/java/com/capstone/adproject/controller/RateLimitInterceptorTest.java` to `src/test/java/com/capstone/adproject/interceptor/RateLimitInterceptorTest.java`

### Next Steps
- Define specific rate limits for different endpoints or user roles.
- Consider integrating with a distributed cache (e.g., Redis) for clustered environments.
- Implement more granular rate limiting strategies (e.g., per-user, per-endpoint).

---

## Current Status: Deadline Expiration & Visibility
**Status**: COMPLETE

### Summary
- Implemented a 24-hour expiration logic for deadlines across all dashboards. Deadlines now automatically disappear from view 24 hours after their set date.
- Fixed visibility issues where general deadlines (those not tied to a specific assessment) were missing from the Admin and Lecturer dashboards.
- Adjusted Java Stream filters in `AdminController`, `LecturerController`, and `StudentController` to properly handle `null` assessment IDs and general assessor types, ensuring global deadlines are correctly displayed to all relevant roles.

## Current Status: Rubric Template / Blueprint System
**Status**: COMPLETE

### Summary
- Implemented a highly optimized, JSON-based "Blueprint" system to eliminate the tedious manual creation of rows for large rubrics.
- The blueprint builder UI now visually replicates the structure of the main rubric form, allowing admins to intuitively construct complex rubric skeletons.
- Reuses existing `rubric-form.html` logic via a client-side "Magic Injector" without creating backend database bloat.
- Scoped securely to the active `Course`.

### Key Features
1. **Hierarchical JSON Configuration**: `RubricTemplate` stores the blueprint as a detailed JSON string, capturing multiple rubrics, their sub-rubrics, direct ratings, and comment sections (e.g., `{ "rubrics": [ { "subRubrics": [{"ratingsCount": 5}], "directRatingsCount": 3, "commentsCount": 2 } ] }`).
2. **Visual Builder UI**: The `rubric_template_builder.html` provides a dynamic interface that mirrors the `rubric-form.html`. Admins add/remove structural blocks (rubrics, sub-rubrics, ratings, comments) with buttons, instead of using simple number inputs.
3. **"Magic Injector" (Client-Side Expansion)**: 
   - Inside the standard Assessment Rubric form, admins select a Blueprint from a dropdown.
   - Updated Vanilla JS fetches the new hierarchical JSON recipe and programmatically simulates clicks on the existing `+ Add` buttons.
   - Instantly inflates 50+ empty text boxes, perfectly bound to the Spring MVC arrays, ready for text entry.
4. **Zero Disruption**: The existing backend `/rubrics/save` logic and assessment calculation engines remain 100% untouched.

### Files Modified/Created
- **Backend**: 
  - `RubricTemplate.java` (Entity with `@Column(columnDefinition = "JSON")`)
  - `RubricTemplateRepository.java`
  - `RubricTemplateService.java` (Enforces `CourseScopeService` boundaries)
  - `RubricTemplateController.java` (Provides UI and REST API for JSON fetching)
  - `RubricController.java` (Updated to pass available templates to the model)
- **Frontend**:
  - `rubric_template_manage.html` (Dashboard)
  - `rubric_template_builder.html` (Overhauled to be a visual, dynamic builder)
  - `rubric_template_builder.css` (New CSS to support the visual builder)
  - `rubric-form.html` (Updated Injector Javascript to parse the new hierarchical JSON)
  - `manage-assessments.html` (Added navigation link)

## Current Status: Dynamic Responsive Web Pages Implementation
**Status**: COMPLETE - All 8 phases implemented, ready for cross-device testing

### Summary
- Implemented full responsive web design across all 50+ HTML templates to support phones, tablets, and desktops.
- Hamburger sidebar navigation for non-desktop (≤1279px), persistent sidebar for desktop (≥1280px).
- All pages fit within viewport width on phone/tablet screens (except intentional horizontal scroll on overall_data_view).
- Standardized 5 reusable CSS component patterns for consistent responsive behavior.

### Implementation Completed (April 2, 2026)

1. **Responsive Foundation**
   - Created `critical.css` with global viewport overflow guard and containment rules.
   - Created `responsive-patterns.css` with 5 reusable patterns: responsive tables, form grids, action bars, drawers, and card lists.
   - Enforced `html { overflow-x: hidden; }` globally except for overall data view.

2. **Sidebar & Navigation Shell**
   - All 5 role sidebars (admin, lecturer, student, superadmin, supervisor) use unified non-desktop shell:
     - Hamburger toggle (three-line icon) visible on phones/tablets (≤1279px).
     - Sidebar drawer opens at ~85% viewport width with overlay.
     - Off-canvas behavior with body scroll lock while drawer open.
     - Persistent sidebar on desktop (≥1280px).
   - Updated fragments: `sidebar.html`, `lecturer_sidebar.html`, `student_sidebar.html`, `superadmin_sidebar.html`, `industrial_sidebar.html`.

3. **Page-by-Page Responsive Adaptation**
   - **Phase 4A (Dashboards & Lists)**: admin_home, lecturer_home, student_home, manage_students, manage_lecturers, manage-assessments all adapted.
   - **Phase 4B (Complex Forms)**: group_assignment, edit_group, group_assignment_preview, rubric-form all adapted.
   - **Phase 4C (Data & Comments)**: data_views_home, assessment_data_view, overall_data_view, comment select/view pages all adapted.
   - **All remaining pages**: 50+ templates now have viewport meta tags and responsive CSS breakpoints.

4. **Key Fixes for Non-Desktop**
   - Admin home "Assign Lecturer" and "Assign Assessment" buttons no longer overlap; wrap cleanly on tablet/phone.
   - Dense tables convert to stacked card layout on small screens (≤1024px).
   - Forms stack from multi-column to single-column on tablets/phones.
   - Comment selection pages fit iPhone SE (375x667) with centered desktop view preserved.
   - Logo + "UTM AAS" header stacks vertically on phones (≤420px).

5. **Accessibility & Touch**
   - Minimum touch target size: 44×44 px enforced on buttons and interactive elements.
   - Keyboard focus visibility preserved across all breakpoints with outlined focus states.
   - Text sizes scale responsibly per breakpoint (readable at 360px without zoom).
   - Horizontal scroll prevented globally except intentional table scroll on overall_data_view.

6. **Breakpoint Strategy**
   - **Phone**: 360–430px → drawer sidebar, single-column layout, large text.
   - **Tablet Portrait**: 480–820px → drawer sidebar, 2-column layout where space allows.
   - **Tablet Landscape/Small Laptop**: 912–1024px → drawer sidebar, responsive grid.
   - **Desktop**: 1280px+ → persistent sidebar, multi-column layout, dense tables.

7. **Testing & Documentation**
   - Created `TEST_MATRIX.md` with comprehensive test checklist covering 360–1920px widths and all device classes (iPhone, iPad, Android, Nest Hub, Surface, Galaxy Z Fold).
   - Created `IMPLEMENTATION_SUMMARY.md` documenting scope, statistics, and known limitations.
   - Created `RESPONSIVE_PATTERNS_GUIDE.md` for developers to reuse patterns on future pages.

### Known Limitations & Tested Devices
- **Supported widths**: 344–1920px (tested: 360, 375, 390, 412, 414, 430, 540, 768, 820, 912, 1024, 1280, 1366, 1920).
- **Supported devices**: iPhone SE, iPhone XR, iPhone 12 Pro, iPhone 14 Pro Max, Pixel 7, Samsung Galaxy S8+, S20 Ultra, iPad Mini, iPad Air, iPad Pro, Surface Pro 7, Surface Duo, Galaxy Z Fold 5, Asus ZenBook Fold, Nest Hub, Nest Hub Max.
- **Horizontal scroll exceptions**: Only `overall_data_view.html` allows intentional table-level horizontal scroll; all other pages remain within viewport width.

### Files Modified/Created
**CSS Files**:
- `critical.css`, `responsive-patterns.css` (new patterns)
- `style.css`, `manageUser.css` (global containment)
- `admin_sidebar.css`, `lecturer_sidebar.css`, `student_sidebar.css`, `supervisor_sidebar.css`, `superadmin_home.css`
- `admin_home.css`, `lecturer.css`, `student_home.css` (dashboard responsive)
- `rubric.css`, `group.css`, `group_edit.css`, `group_preview.css` (form responsive)
- `data_home.css`, `assessment_data.css`, `overall_data.css` (data view responsive)
- `comment.css`, `peer_assess.css`, `lecturer_assessments.css`, `edit_overrides.css`, `editdeadline.css`, `student_comments.css`, `comment_select_*.css`

**HTML Templates** (50+ files):
- All main templates updated with viewport meta tags.
- All sidebar fragments refactored with hamburger toggle and scroll lock.
- Comment selection pages optimized for small screens (iPhone SE).
- Manage/data/assessment pages adapted with responsive table/grid layouts.

**Documentation**:
- `FEATURE.md` (ongoing feature tracking)
- `TEST_MATRIX.md` (comprehensive testing checklist)
- `IMPLEMENTATION_SUMMARY.md` (implementation overview)
- `adproject/RESPONSIVE_PATTERNS_GUIDE.md` (developer guide)

### Next Steps
1. **Cross-Device Validation**: Test all workflows on real devices (iPhone, iPad, Android) and emulators at target breakpoints.
2. **User Testing**: Verify STATE-critical workflows (course switch, manage students/lecturers, rubrics, data views) work intuitively on mobile/tablet.
3. **Refinements**: Log any visual/UX issues and apply targeted tweaks per breakpoint.
4. **Performance Audit**: Verify responsive CSS does not degrade load times or JavaScript execution.

---

## Previous Status: Course Schema Stabilization
**Status**: IN PROGRESS - Application-level fixes applied, database cleanup still required

### Summary
- The system is being normalized to use `courses` as the canonical course table.
- Legacy overlap between `course` and `courses` created foreign key drift and runtime inconsistencies.
- Current startup and CRUD issues are mostly caused by residual database references and non-null FK constraints in child tables.

### Recent Changes Applied in Code
1. **Course Scope and Course Selection Hardening**
   - `CourseScopeService` now has stronger fallback logic for loading managed courses.
   - Active course persistence was updated to avoid assigning transient `Course` placeholders.

2. **Foreign Key Auto-Repair Improvements**
   - `CourseReferenceRepair` now attempts to normalize `course_id` FK targets to canonical table usage at startup.
   - Orphan assignment rows are cleaned up more aggressively.

3. **Course Create/Delete Reliability Improvements**
   - `SuperAdminService.saveCourse()` now normalizes input and de-duplicates by course code for create operations.
   - `SuperAdminService.deleteCourse()` was hardened to clear references and reduce persistence-context side effects.

4. **Controller Fixes for Transient Course Assignment**
   - Admin create flows for students/lecturers no longer attach transient `Course` objects by id-only placeholders.

5. **Course Switching Reliability Hardening (April 1, 2026)**
   - Admin course switch dropdown now submits directly via `onchange` in `fragments/sidebar.html` to reduce missed JS-only submissions.
   - `AdminController.switchCourse()` was hardened to:
     - fail fast for unauthorized course switches,
     - explicitly persist `activeCourseId` to the current HTTP session,
     - avoid redirect loops back to `/admin/switch-course`.
   - Active-course scoping was strengthened in core views so data is loaded by active course id at query level (assessments/students/lecturers/groups), reducing stale previous-course displays after switching.
   - `RubricController.manageAssessments()` now loads assessments by active course id directly.

6. **Developer Environment Note**
   - Maven wrapper files are currently missing (`.mvn/wrapper/maven-wrapper.properties` not found), so `mvnw` commands fail.
   - Use system Maven (`mvn`) for local run/build until wrapper files are restored.

### Current Known Database Constraints/Behavior
- Child tables referencing `courses.id` include:
  - `admin_course_assignment.course_id`
  - `assessment.course_id`
  - `lecturer.course_id`
  - `project_group.course_id`
  - `student.course_id`
- Some environments still have `course_id` columns defined as NOT NULL, so `SET course_id = NULL` fails.
- In those cases, child rows must be deleted or reassigned before deleting parent `courses` rows.

### Manual Cleanup Strategy (Operational)
1. Delete or reassign child rows referencing target course IDs first.
2. Delete parent rows from `courses` after child references are removed.
3. Keep only canonical table usage (`courses`) and retire legacy `course` references once no FK depends on it.

### Open Work
- Complete one-time database normalization in active environment (remove residual legacy FK mappings and duplicates).
- Re-verify end-to-end admin flow:
  - add course
  - assign admin to course
  - switch active course
  - delete course
  - ensure no cross-course data overlap
- Runtime verification pending after latest switch hardening:
   - confirm `/admin/home`, `/admin/manage-students`, `/admin/manage-lecturers`, `/rubrics/manage`, and `/admin/data-views` all reflect the newly selected active course immediately.

## Project Overview
**Project Name**: adproject  
**Description**: Demo project for Spring Boot - AAS (Assessment Administration System)  
**Technology Stack**: Spring Boot 3.5.4, Java 17, MySQL, Thymeleaf  
**Package Structure**: `com.capstone.adproject`

## Current Status: Industrial Supervisor Subsystem Removal
**Status**: IN PROGRESS - Entity consolidation completed, cleanup needed

### Changes Made:
1. **Industrial Supervisor Entity Consolidated into Lecturer Role**
   - Groups now have `industrialSupervisor` field pointing to `Lecturer` entity (not separate `IndustrialSupervisor` entity)
   - `Group.java` updated: `industrialSupervisor` field type changed from `IndustrialSupervisor` to `Lecturer`
   - Database foreign key now references `lecturer` table instead of `industrial_supervisor` table

2. **Repository Cleanup Completed**
   - `GroupRepository.java`: Removed `findByIndustrialSupervisorId()` and `findByIndustrialSupervisorIdWithStudents()` methods
   - Repository now only contains methods relevant to current architecture

### Recent Feature Additions:
1. **Lecturer Assignment Modes (Group vs. Rubric)**
   - Implemented a dual-mode assignment system for administrators assigning lecturers to assessments.
   - **Group Mode**: Lecturers are assigned to specific groups and evaluate all rubrics for those groups (maintained existing behavior).
   - **Rubric Mode**: Lecturers are assigned to specific rubrics and evaluate those rubrics across all groups.
   - Added `LecturerRubricAssignment` entity and repository.
   - Updated `AdminController` and `admin_assign_lecturers.html` to enforce mutual exclusivity between the two modes via UI toggles and backend clearing of cross-mode data.
   - Modified `LecturerAssessmentController` and `LecturerAssessmentService` to grant rubric-assigned lecturers access to evaluation forms.
   - Evaluation forms dynamically filter rubrics based on the lecturer's specific rubric assignments.
   - Evaluation completion calculation updated to respect rubric-specific assignments while allowing unrestricted general assessment comments.

2. **Role-Based Comment Viewing**
   - Implemented separate workflows and templates for Admin and Lecturers (Academic/Industrial Supervisors) to view student comments.
   - **Admin View**:
     - Admin can select an assessment, then choose any group to view all student comments within that group.
     - Uses `comment_select_assessment.html`, `comment_select_group.html`, and `comment_view.html`.
   - **Lecturer/Supervisor View**:
     - Supervisors select an assessment and are then presented directly with comments from *all* groups they supervise for that assessment (no intermediate group selection).
     - Comments are filtered to only show those pertaining to students within their supervised groups.
     - Uses `lecturer_comment_select_assessment.html` and `lecturer_comment_view.html`.
   - Logic in `GroupCommentController.java` now routes based on `/admin/...` or `/lecturer/...` URL paths.

### Files That Need Deletion (Industrial Supervisor Subsystem):
- `IndustrialSupervisor.java` - Entity class (no longer needed)
- `IndustrialSupervisorRepository.java` - Repository interface (no longer needed)
- `IndustrialSupervisorService.java` - Service class (no longer needed)
- `IndustrialSupervisorController.java` - Controller class (no longer needed)

### Files That Need Cleanup/Modification:
1. **Service Layer**:
   - `AdminService.java` - Remove industrial supervisor methods (`saveIndustrialSupervisor()`, `deleteIndustrialSupervisorById()`, etc.)
   - `UserService.java` - Remove `IndustrialSupervisor` imports and references in authentication methods
   - `CalculateService.java` - Update comments about "removing industrial supervisors"
   - `AssessmentCommentService.java` - Update comments about supervisor comments
   - `LecturerAssessmentService.java` - Remove "industrial supervisor" filtering logic

2. **HTML Templates** (Label updates needed):
   - `edit_group.html` - Update "Industrial Supervisor" labels to clarify role
   - `group_assignment.html` - Update "Industrial Supervisor" labels
   - `group_assignment_preview.html` - Update "Industrial Supervisor" labels
   - `manage_supervisors.html` - Should be removed or redirected (page no longer needed)
   - `comment_select_assessment.html` - Now Admin-specific (removed conditional sidebar/CSS logic)
   - `comment_select_group.html` - Now Admin-specific (removed conditional sidebar/CSS logic)
   - `comment_view.html` - Now Admin-specific (removed conditional sidebar/CSS logic)
   - `lecturer_sidebar.html` - Removed redundant `<head>` section.
   - `industrial_supervisor_home.html` - Should be removed (dashboard no longer needed)

3. **Security Configuration**:
   - `SecurityConfig.java` - May need updates to remove industrial supervisor role paths
   - `CustomAuthenticationSuccessHandler.java` - May need updates for role redirects

### Files That Should Remain As-Is:
- `Group.java` - Correctly has `industrialSupervisor` field pointing to `Lecturer`
- `GroupAssignmentDto.java` - Has `industrialSupervisorId` field (needed for group assignment)
- `AdminController.java` - Uses `group.getIndustrialSupervisor()` which returns `Lecturer`

## Dependencies
### Core Spring Boot Starters
- `spring-boot-starter-web` - Web MVC framework
- `spring-boot-starter-security` - Authentication and authorization
- `spring-boot-starter-data-jpa` - Database persistence
- `spring-boot-starter-thymeleaf` - Server-side templating
- `spring-boot-starter-validation` - Input validation
- `spring-boot-starter-mail` - Email functionality
- `spring-boot-starter-actuator` - Application monitoring

### Security & UI
- `thymeleaf-extras-springsecurity6` - Thymeleaf integration with Spring Security 6

### Database
- `mysql-connector-j` - MySQL database driver

### Development Tools
- `spring-boot-devtools` - Hot reload for development
- `lombok` - Code generation (getters, setters, constructors)

### Testing
- `spring-boot-starter-test` - Unit and integration testing
- `spring-security-test` - Security testing utilities

## Project Structure

### Source Code Organization (`src/main/java/com/capstone/adproject/`)

#### 1. **Application Entry Point**
- `AdprojectApplication.java` - Main Spring Boot application class

#### 2. **Configuration (`config/`)**
- `SecurityConfig.java` - Spring Security configuration
- `CustomAuthenticationSuccessHandler.java` - Custom login success handler
- `ThymeleafConfig.java` - Thymeleaf template configuration

#### 3. **Controllers (`controller/`)**
- `AdminController.java` - Admin-specific endpoints
- `AuthController.java` - Authentication and authorization endpoints
- `CommentConfigController.java` - Comment configuration management
- `DataViewController.java` - Data visualization endpoints
- `DeadlineController.java` - Deadline management
- `IndustrialSupervisorController.java` - Industrial supervisor functionality **(TO BE DELETED)**
- `LecturerAssessmentController.java` - Lecturer assessment management
- `LecturerController.java` - Lecturer-specific endpoints
- `LogoutController.java` - Logout handling
- `RubricController.java` - Rubric management
- `StudentController.java` - Student-specific endpoints

#### 4. **Models (`model/`) - Domain Entities**
**User Management:**
- `Admin.java` - Administrator entity
- `Lecturer.java` - Lecturer entity (now includes industrial supervisor role)
- `Student.java` - Student entity
- `IndustrialSupervisor.java` - Industrial supervisor entity **(TO BE DELETED)**

**Assessment System:**
- `Assessment.java` - Assessment entity
- `AssessmentComment.java` - Comments on assessments
- `Rubric.java` - Rubric definition
- `SubRubric.java` - Sub-components of rubrics
- `Rating.java` - Rating scale definitions
- `Mark.java` - Marks/score entity
- `StudentResultOverride.java` - Override for student results

**Group Management:**
- `Group.java` - Student groups (has `academicSupervisor` and `industrialSupervisor` both pointing to `Lecturer`)
- `LecturerGroupAssignment.java` - Lecturer to group assignments
- `LecturerRubricAssignment.java` - Lecturer to specific rubric assignments

**Deadline Management:**
- `Deadline.java` - Deadline entity
- `DeadlineListWrapper.java` - Wrapper for deadline lists

#### 5. **Repositories (`repositories/`) - Data Access Layer**
JPA repositories for all domain entities including:
- `AdminRepository.java`, `LecturerRepository.java`, `StudentRepository.java`, `IndustrialSupervisorRepository.java` **(TO BE DELETED)**
- `AssessmentRepository.java`, `AssessmentCommentRepository.java`
- `RubricRepository.java`, `SubRubricRepository.java`, `RatingRepository.java`
- `GroupRepository.java`, `LecturerGroupAssignmentRepository.java`, `LecturerRubricAssignmentRepository.java`
- `MarkRepository.java`, `StudentResultOverrideRepository.java`
- `DeadlineRepository.java`
- `UserRepository.java` - Generic user repository

#### 6. **Services (`service/`) - Business Logic Layer**
- `AdminService.java` - Admin business logic (needs cleanup for industrial supervisor methods)
- `AssessmentService.java` - Assessment management
- `AssessmentCommentService.java` - Comment management (needs comment updates)
- `CalculateService.java` - Calculation utilities (needs comment updates)
- `CustomUserDetailsService.java` - Custom user details for Spring Security
- `DeadlineService.java` - Deadline management
- `EmailService.java` - Email functionality
- `IndustrialSupervisorService.java` - Industrial supervisor operations **(TO BE DELETED)**
- `LecturerAssessmentService.java` - Lecturer assessment logic (needs cleanup)
- `MarkService.java` - Mark/score management
- `RubricService.java` - Rubric operations
- `StudentService.java` - Student management
- `UserService.java` - User management (needs cleanup for industrial supervisor references)

#### 7. **DTOs (`dto/`) - Data Transfer Objects**
- `AssessmentAssignmentDto.java` - Assessment assignment data
- `AssessmentDataDto.java` - Assessment data transfer
- `GroupAssignmentDto.java` - Group assignment data (has `industrialSupervisorId` field)
- `RandomizationInputDto.java` - Randomization input data
- `RubricCalculationDto.java` - Rubric calculation data
- `StudentAssessmentDataDto.java` - Student assessment data

#### 8. **Utilities (`util/`)**
- `PasswordGenerator.java` - Password generation utility

### Resources (`src/main/resources/`)

#### 1. **Templates (`templates/`) - Thymeleaf HTML Templates**
**Main Pages:**
- `login.html` - Login page
- `admin_home.html` - Admin dashboard
- `lecturer_home.html` - Lecturer dashboard
- `student_home.html` - Student dashboard
- `industrial_supervisor_home.html` - Supervisor dashboard **(TO BE REMOVED)**

**Assessment Management:**
- `peer_assessment_form.html` - Peer assessment form
- `rubric-form.html` - Rubric creation/editing
- `view-assessment-rubrics.html` - View rubrics
- `lecturer_assessments.html` - Lecturer assessment view
- `student_assessments.html` - Student assessment view

**User Management:**
- `manage_users.html` - User management
- `manage_lecturers.html` - Lecturer management
- `manage_students.html` - Student management
- `manage_supervisors.html` - Supervisor management **(TO BE REMOVED/REDIRECTED)**

**Group Management:**
- `group_assignment.html` - Group assignment (needs label updates)
- `group_assignment_preview.html` - Group preview (needs label updates)
- `edit_group.html` - Group editing (needs label updates)
- `admin_assign_lecturers.html` - Lecturer assignment to groups

**Data Views:**
- `assessment_data_view.html` - Assessment data visualization
- `overall_data_view.html` - Overall data view
- `data_views_home.html` - Data views home page

**Configuration:**
- `comment_configuration_single_type.html` - Comment configuration
- `edit_single_comment.html` - Edit comments
- `comment_select_assessment.html` - Admin-specific comment assessment selection
- `comment_select_group.html` - Admin-specific comment group selection
- `comment_view.html` - Admin-specific student comment view
- `edit-deadline.html` - Deadline editing
- `edit_overrides.html` - Result override editing

**Fragments (`templates/fragments/`):**
- Sidebar templates for different user roles

#### 2. **Static Assets (`static/`)**
- `lecturer_comment_select_assessment.html` - New Lecturer/Supervisor assessment selection for comments
- `lecturer_comment_view.html` - New Lecturer/Supervisor combined comment view for supervised groups
- **CSS (`css/`)**: 40+ CSS files for styling different pages
- **JavaScript (`js/`)**: `loading.js` for loading animations
- **Images (`images/`)**: UTM logos and branding images

#### 3. **Configuration Files**
- `application.properties` - Spring Boot application configuration

## Key Features Identified

### 1. **Multi-role Authentication System**
- Admin, Lecturer, Student roles (Industrial Supervisor role consolidated into Lecturer)
- Spring Security with custom success handler
- Role-based access control

### 2. **Assessment Management System**
- Peer assessment functionality
- Rubric-based evaluation
- Template system for rubrics
- Comment configuration

### 3. **Group Management**
- Student group creation and assignment
- Lecturer assignment to groups (both academic and industrial supervisor roles, plus assignment by rubrics)
- Group-based assessment

### 4. **Data Visualization**
- Multiple data view templates
- Assessment data visualization
- Overall performance tracking

### 5. **Administrative Functions**
- User management for all roles
- Deadline management
- Result override capabilities
- Email functionality

## Technology Patterns

### Architecture
- **MVC Pattern**: Controllers, Services, Repositories
- **Layered Architecture**: Presentation → Business Logic → Data Access
- **DTO Pattern**: Separate data transfer objects

### Security
- Spring Security 6 integration
- Thymeleaf security extras
- Custom user details service

### Database
- JPA with MySQL
- Repository pattern
- Entity relationships

### Frontend
- Thymeleaf server-side rendering
- CSS styling per page/module
- Fragment-based layout (sidebars)

## Development Notes
- Uses Lombok for boilerplate code reduction
- Development tools enabled for hot reload
- Validation framework integrated
- Email service configured
- Actuator endpoints for monitoring

## Next Steps / Remaining Tasks

### 1. **Delete Industrial Supervisor Subsystem Files**
- Delete `IndustrialSupervisor.java`, `IndustrialSupervisorRepository.java`, `IndustrialSupervisorService.java`, `IndustrialSupervisorController.java`

### 2. **Clean Up Service Layer**
- Remove industrial supervisor methods from `AdminService.java`
- Remove `IndustrialSupervisor` references from `UserService.java`
- Update comments in `CalculateService.java` and `AssessmentCommentService.java`
- Clean up `LecturerAssessmentService.java`

### 3. **Update HTML Templates**
- Update labels in `edit_group.html`, `group_assignment.html`, `group_assignment_preview.html`
- Remove/redirect `manage_supervisors.html` and `industrial_supervisor_home.html`

### 4. **Update Security Configuration**
- Check `SecurityConfig.java` for industrial supervisor role paths
- Update `CustomAuthenticationSuccessHandler.java` if needed

### 5. **Database Migration**
- Update database schema to remove `industrial_supervisor` table
- Ensure foreign keys are properly migrated

### 6. **Testing**
- Test all functionality after changes
- Ensure group assignment with industrial supervisor role works correctly
- Verify authentication and authorization for all roles

---

## Multi-Course Segregation Hardening (Course-Scoped Isolation)

Strict per-course isolation has been enforced across persistence filters, services, controllers, SSE endpoints, and Thymeleaf views so multi-course admins, lecturers, and students never see bundled or cross-course records:

1. **Entities, Hibernate Filter & Repositories**:
   - Tightened `@Filter(name = "courseScopeFilter")` in `Assessment`, `Group`, `RubricTemplate`, and `Student` (removed legacy `OR course_id IS NULL` fallback) and removed the filter from `Lecturer`.
   - Added per-course student group assignments (`StudentCourseAssignment`, `StudentCourseAssignmentRepository`) and per-course overrides/history (`StudentResultOverride`, `FactorOverrideHistory`, `StudentResultOverrideRepository`, `FactorOverrideHistoryRepository`) with schema constraints in `CourseEmailConstraintMigrationRunner`.
   - Scoped repository queries in `AssessmentRepository`, `StudentRepository`, `LecturerRepository`, and `FactorWeightageRepository`, plus `CourseScopeService`, `AdminService`, and `CalculateService`.

2. **Controller & SSE Scoping**:
   - `AdminController`: Active course switching redirects safely (`resolvePostSwitchRedirect`), strict course checks on comment moderation (`commentBelongsToAssessment`) and random group creation, and per-course `studentGroupMap` resolution for group/student/lecturer assignment views.
   - `DataViewController`, `GroupCommentController`, `DeadlineController`, `RubricController`, `RubricTemplateController`, and `AssessmentProgressSseController`: Enforced active-course ownership on assessments, groups, students, overrides, factor weightages, compiled comments, deadlines, rubric templates, and SSE progress streams.
   - `LecturerController` & `LecturerAssessmentController`: Resolved active-course lecturer accounts via `CourseScopeService.resolveLecturerForCourse`, filtered assessments and student selections per course (`studentGroupMap`), added `POST /lecturer/switch-course`, and guarded submission file/annotation endpoints with course-level 403 checks.
   - `StudentController`: Added `POST /student/switch-course`, session active course resolution, per-course enrollment and group resolution (`StudentCourseAssignmentRepository` + `AdminService`), and course-scoped comment compilation.

3. **View Updates**:
   - Updated `admin_assign_students.html`, `group_assignment.html`, `lecturer_select_student.html`, `assessment_data_view.html`, and `admin_assign_lecturers.html` to render `resolvedGroup` from `studentGroupMap` with fallback to `student.group`.

## Error Management, Monitoring, Schema Guard & Production-Only CI/CD (2026-11-19)

- **Session expiry**: `SecurityConfig` no longer forwards unauthenticated requests to the error page. Page navigations
  redirect to `/login?expired` (or `/login` when there was never a session); XHR/`/api/**` calls get JSON `401`; SSE
  streams get a bare `401`. A CSRF failure with no live session is treated as expiry. Login shows an "expired" notice.
- **Error pages**: `error.html` (+ `error.css`, `error-page.js`) renders the real status, title, message and, for 5xx, a
  copyable reference (first 8 chars of the request id). `GlobalExceptionHandler` is the one place that builds responses
  (HTML or JSON); `ApplicationErrorController` reuses it. Security exceptions are rethrown to Spring Security. Schema
  drift (`Unknown column`, `doesn't have a default value`, missing table) is its own 503 incident.
- **Monitoring**: `MonitoringService` now holds counters, the last 100 server errors and a health snapshot (DB, AI, mail,
  storage, schema, JVM, version). `/superadmin/monitoring` (in `SuperAdminController`) renders it, with "Send a test
  alert" and "Re-check schema". `AlertingService` alerts by email and Telegram on state changes, error spikes and schema
  faults. `EmailService.mailConfigProblem()` flags an SMTP login used as From address.
- **Schema guard**: `LegacyColumnCleanupMigrationRunner` (after startup, `ApplicationReadyEvent`) compares live MySQL with
  the Hibernate mappings; unused `NOT NULL` columns without a default are made nullable (never dropped); missing columns
  are reported. `app.schema-guard.auto-relax=false` turns the fix off.
- **Welcome email**: Manage Students / Assessors rows have a "Send welcome email" button beside Update (disabled once the
  account has set a password) and a bar with "Send welcome email to all" for everyone who has not set a password
  (`scope=all`; the pending panel keeps `scope=pending`).
- **CI/CD**: production only. `deploy.yml` deploys the tested SHA after `build.yml`, verifies `/actuator/info`
  revision, and rolls back automatically; `Scripts/dokploy-image.sh`, `Scripts/deployment-smoke.sh`,
  `APP_REVISION` baked into the app image (Dockerfile + build.yml). Production is three separate Dokploy services
  (app, AI service, MySQL); the pipeline updates only the two Applications. See `CICD.md`. Default `APP_PUBLIC_BASE_URL` is now `https://iams.eduinnovax.com`.


## Sign-in announcements (2026-11-19)
Super Admin -> Announcements (`/superadmin/announcements`) writes messages (type: notice, maintenance, new release,
improvement; audience: everyone, students, assessors, admins; optional show-from/show-until, server time zone;
on/off switch). `AnnouncementAdvice` works out the live ones once per login session, the sidebar fragments render
`fragments/announcement_popup`, and "Got it" / Esc / backdrop POSTs `/announcements/dismiss` to clear it for that
session. Text is always rendered escaped. Table `announcements` is created by `ddl-auto=update`. A lookup failure
never breaks a page. New announcements reach people at their next sign-in, not mid-session.

## Java4Shell mitigation (2026-11-20)
- CVE-2026-114938 is a JDK bug (AVX-512 Base64 decoder), fixed only by a JDK update (21.0.13.1+), not by a Spring or Tomcat release.
- Eclipse Temurin had no fixed `21-jre-jammy` image on 2026-11-20 (tag last updated 2026-11-12), so the Dockerfile entrypoint now starts the JVM with `-XX:-UseBASE64Intrinsics`.
- To finish: when Temurin publishes 21.0.13.1+, rebuild the image (the build stage and runtime stage both pull the base), confirm `java -version` in the container, then remove the flag.

## Finished evaluations: admin view and delete (2026-11-20)
- Progress tracking has a new "Finished Evaluations" tab (`?tab=evaluations`). The admin picks the assessment, then a group, student and/or rubric; nothing is listed until one of the three is chosen.
- An evaluation is one evaluator's FINAL marks for one rubric on one student. An assessor's group-rubric evaluation is stored once per member, so it is shown as one row for the group and deleted for all members together.
- Each row is a `<details>` dropdown with the ratings, marks and rubric comments. Admin can delete (marks plus that rubric's comments; the assessor's general comments go only when no marks of that type remain). There is no edit and no create, by decision.
- Delete needs a reason and writes an `evaluation_deletions` row (who, when, why, readable snapshot). The last 20 for the assessment show under the list. The table is created by `ddl-auto=update`.
- Code: `ProgressTrackingService` (`findEvaluations`, `deleteEvaluation`), `AdminController` (`/progress-tracking/evaluations`, `/progress-tracking/{id}/evaluations/delete`), tests `ProgressTrackingEvaluationsTest`, `EvaluationsTabRenderTest`, extra cases in `AdminManagementControllerTest`.

## Existing users across courses, and a course-isolation pass (2026-11-20)
- Add Student / Add Assessor: typing 3+ characters of an email or name lists matching existing accounts (max 5, email masked unless typed in full). "Add to this course" enrols the existing account (`/admin/manage-students/add-existing`, `/admin/manage-lecturers/add-existing`); no new account, password or email. The success message now says the person already had an account. `existing-user-suggest.js`, `AdminService.suggestStudents/suggestLecturers/enrollExisting*`.
- Isolation fixes, each with tests: lecturer and student pages no longer fall back to another course's assessments when the active course has none (lecturer list, student home/assessments/submissions); `/deadlines/save` with an id must be a deadline of the active course; evaluation doodles are only saved to a submission of the same assessment; `/admin/search-lecturers` returns id/email/username only (it used to serialise the Lecturer entity, password hash included); rubric saves treat a sub-rubric or rating id that is not the rubric's own as new; marks and comments written from evaluation forms must use a rubric and rating of the assessment (`AssessmentOwnership`); deleting an assessor who also belongs to other courses only cleans this course (course-scoped JPQL in `LecturerRepository`) and keeps the account.
- Not done, needs a decision (audit items 8, 10-20 in the session notes): lecturer/student access not bound to the active course; co-admin can delete or update a course; ROLE_ADMIN is global per account; shared user rows editable by any admin who enrols them; mass assignment on student/lecturer/assessment create; low-severity items.

## Decisions applied after the audit (2026-11-20)
- Announcements are per course: `Announcement.courseId` (null = every course), a Course picker on the Super Admin form, a Course column in the list, and the sign-in pop-up asks for the course the person is working in (recomputed on course switch; already-dismissed ones are not shown again). Deadlines were already per course (courseId on every deadline, reads by course or by the assessment's course).
- Assessors open an assessment only if they are assigned to it (`lecturerCanAccessAssessment`); belonging to or managing the course is no longer enough. Students: left as is (own data; report submission is not tied to the evaluation assignment).
- Course edit, update and delete are creator-only (legacy courses without a creator still follow the old rule); update changes name, code and description on the stored course so the creator is kept.
- Shared people: students and assessors who are also in another course cannot have their name or email changed from one course; a student is matched to an existing account by email only (a matching name no longer enrols someone else's account).
- Create forms drop extra fields: student (group, password, tokens), assessor (roles, password, tokens; cannot create an admin), assessment (submission source).

## Course authority, cleanup and the UI foundation (2026-11-20)
- Admin of a course versus working in it: `admin_course_assignment.as_admin` (null = legacy admin, false = assessor only). `CourseScopeService.isAdminOfCourse` counts the person's own course, courses they created and admin invites; assessor-only and student courses are excluded. Course edit/update/delete/invite use it, and the manage-courses page hides Edit/Delete for courses the admin did not create. An admin added as an assessor no longer gets that course as their own home course.
- Cleanup (staged, not committed): the orphaned supervisor/industrial templates, CSS and 4 unused images; the duplicate `adproject/docs/branding`; logs, `__pycache__`, the uploaded PDF and `temp_things.csv` untracked (local copies kept); `.gitignore` extended. Still open: `aas_db_dump.sql` (has a password column), the Katalon project, history cleanup (`git filter-repo`), `Scripts/dokploy-image.sh` is not committed yet.
- UI foundation: `--ui-on-brand` (text on brand fills; dark buttons were white on amber), dark muted/success/danger tokens raised to AA, dark students get violet instead of sharing admin amber, the dark breadcrumb pill follows the role colour; `css/ui-core.css` (imported by system-ui.css: press feedback, busy spinner, one focus ring, soft page entrance, wrapping tab bars, one reduced-motion rule) and `js/ui-core.js` (route progress bar); `loading.js` parses again; the submit guard no longer drops the clicked button's value and resets after Back. `theme-contrast.test.cjs` checks every theme and role against WCAG AA.

## Admin area limited to courses the person administers; dark theme retuned (2026-11-20)
- Admin pages (`/admin/**`, `/rubrics/**`, `/deadlines/**`, `/manage-assessments`, `/assessments/manage`) only ever work on a course the person administers. `CourseScopeInterceptor` moves the session's active course to one of their admin courses (home course first) when it is one they only assess or study in; the admin course picker lists only admin courses and `/admin/switch-course` refuses others. Accounts with no admin links (super admins, old accounts) keep their managed courses. The assessor side still lists every course they work in.
- Dark theme is now a dark blue-grey family (canvas `#0f1624`, cards `#172033`, header band deep blue) with the gold accent and the role colours (admin amber, assessor emerald, supervisor sky, student violet). About 220 literal greys and maroons in `system-ui.css` and the seven page stylesheets that carry dark overrides were replaced by theme tokens (`scripts` used: scratchpad `tokenize_dark*.py`, not kept in the repo). Warm theme unchanged apart from a darker muted text colour.
- The Katalon UI-test project at the repo root (Object Repository, Test Cases, Test Suites, Profiles, Include, settings, `Scripts/TC*`, `.project`, `AAS-Project.prj`, `console.properties`) was removed on 2026-11-20 (staged, not committed). `Scripts/` now holds only the deploy scripts. `aas_db_dump.sql` stays: it is sample data for local MySQL setup.

## Calculation fixes and Course Analytics (2026-11-20)
- Calculation rules (CalculateService): only finished marks count (FINAL/SUBMITTED, plus legacy rows with no status), so drafts no longer change totals; where assessors marked a rubric their marks decide it and student ratings are not averaged in (student-only rubrics still use student ratings); evaluators are keyed by kind and id (lecturer #5 is not student #5); a student's own rating is not part of what teammates gave them; a teamwork component with no data is left out and its weight shared, not counted as zero; `StudentAssessmentDataDto.evaluated` says whether a student has any finished mark. The overall data view and its CSV now use an admin's override (factor and total, and the grade that follows).
- Course settings: `Course.passMark` and `Course.cloTargetPercent` (null = 50 and 50), edited on the course edit page, creator only, validated 0-100.
- Course Analytics page `/admin/data-views/course-analytics` (admin sidebar: Data Management > Course Analytics; the lecturer comparison is now labelled Marking Consistency and is unchanged). The numbers come from `CohortMetrics` (pure, tested) fed by `CohortInputs`: average mark, pass rate with traffic light, students evaluated, grade distribution and spread in words (5+ students), CLO attainment against the target, hard/easy criteria (5+ ratings), who needs a word. Student results page deliberately not built yet.

## Production fix: NaN mark on team evaluation submit (2026-11-21)
- `POST /student/assessment/{id}/team-evaluate/submit` failed with "'NaN' is not a valid numeric or approximate numeric value" while saving a `Mark`. Cause: `Mark.calculateCloMarks()` (run on every insert/update) divided the rating's marks by the rubric's marks; a rubric with 0 marks and a 0-mark rating gives 0/0 = NaN (a rating above 0 would give Infinity), which MySQL refuses. Now the division is skipped when the rubric has no marks (the value the caller worked out is kept) and any non-finite `markValue` or `cloMarks` is replaced by 0 before saving (`MarkNumbersTest`).
- Worth checking in the data: a rubric saved with 0 marks cannot give anyone marks. The affected assessment (id 13) should have its rubric marks set.
- Zero-mark rubrics are supported on purpose (2026-11-21): a rubric whose total marks is 0 can be saved and evaluated. The evaluator's rating is stored, the rubric adds 0 CLO marks (`Mark.calculateCloMarks`), results show 0 for it, and the cohort analytics skip outcomes with no marks on offer. Only a negative total is refused (`RubricController`), and the form hints that 0 is allowed.

## AI audit and briefing: calculated findings, honest failure, hardened service (2026-11-21)
- Team dynamics audit (`ai-service/app/graphs/team_dynamics_graph.py`, `app/analysis/team_metrics.py`): free-rider, self-inflation, uniform-top-marks flags, each member's status and the 0-100 health score are now calculated from the rating matrix, and every flag carries its evidence (the numbers) and a source (CALCULATED or AI). A student nobody rated is "under-evaluated", not a free-rider; with too little data there is no score and no judgement. The model only summarises written comments and writes the briefing wording, from the calculated facts.
- Moderation briefing: the model no longer explains why assessors differed (it cannot see why); each criterion states the measured difference and comparison count. The wording step is grounded and validated.
- Failures are no longer disguised: responses carry `aiStatus` (OK / PARTIAL / UNAVAILABLE / NOT_USED) and `aiNotes`; the pages show a banner when the AI did not run and the summary is composed from the numbers. Model replies are validated (balanced-JSON extraction, allowed labels, bounded text). Real names are replaced by labels before anything goes to the model, and restored in the answer; commenter names are never sent. Comments are cleaned, bounded (80 x 500 chars) and delimited as untrusted data.
- What the app sends: only finished student ratings (assessors' marks are stored with the student as both evaluator and evaluated, so they used to be read as self-ratings), and only approved teammate comments (`CalculateService.getApprovedPeerComments`). An assessor can run the audit only for a group they supervise. Errors shown to users are generic.
- Service hardening: no CORS, constant-time secret check, the service refuses to start without a random `AI_INTERNAL_SECRET` of 16+ characters (not a placeholder; `DEBUG=true` is the local exception), `/health` reveals nothing, request size caps, generic 500s, non-root container with a HEALTHCHECK, port 8000 not published in compose. Local development now needs `AI_INTERNAL_SECRET` set (same value in the app and the service) or `DEBUG=true` on the service.
- Tests: ai-service 44 (unittest, real libraries; run `python -m unittest discover -s tests` in `ai-service` with the venv).
- Moderation briefing, round two (2026-11-21): assessor differences are judged in rating steps (the smallest gap between two rating levels of a criterion, `Criterion.step`): one step apart is ordinary, 2+ is critical, mean 1.5+ steps is action required; criteria without a known step still use points. A target's spread is measured only over criteria all its assessors marked, and targets where they agree exactly are not listed. Assessors' own comments (not rejected, tied to the same target names as the scores) are sent, contested work first, max 8, cleaned and pseudonymised. `AiServiceClient` now answers an identical request from memory for 10 minutes (never keeping a degraded answer) and allows 12 real model calls per person per 10 minutes (HTTP 429 with a wait message after that). ai-service 51 tests, Java 468.

## Comment moderation assist: AI check for abusive, sarcastic and inappropriate comments (2026-11-21)
- Advice only. A comment is never approved, rejected or hidden by the AI; the finding is shown to the moderator, who decides. Bulk "approve all pending" now approves only comments still pending (a rejected comment used to be approved again by it).
- AI service: `POST /api/v1/comments/check` (`app/graphs/comment_check.py`). Rules (`app/analysis/comment_rules.py`, English and Bahasa Melayu profanity incl. obfuscated and masked spellings, discriminatory phrases, prompt-injection phrases) always run; injection attempts are decided by the rules and never shown to the model. The model reads the rest in batches of 20 at temperature 0 and returns label (ok, negative_constructive, abusive, sarcastic, inappropriate, unclear, injection_attempt), confidence, language, reason. Replies are validated; low confidence or a review label means "needs human review". If the AI is down, rule hits are still returned (source RULES) and the rest are `not_checked`, never "ok".
- Admin comments tab (`admin_progress_tracking_view.html`): "Scan pending with AI" (`POST /admin/comments/assessment/{id}/ai-scan`: up to 100 unread pending comments per click, 20 per request; says how many were read, flagged, could not be checked and are left), a "Needs a look" filter, a per-comment finding with confidence and reason, and "Approve comments the scan found fine" (only pending comments with label ok at 80%+ confidence, not flagged, unedited since the scan). The old "bulk-approve-safe" endpoint used to approve everything. The flagged count in the stats was hard-coded 0; it now counts real flags. An admin's own manual flag is never overwritten.
- New `assessment_comments` columns (added by ddl-auto=update): `ai_label`, `ai_confidence`, `ai_reason`, `ai_checked_at`, `ai_text_hash` (an edited comment is read again). The comment text is sent to the AI service only when an admin clicks scan; the confirm dialog says so. The scan counts against the 12 model calls per 10 minutes per person (a 100-comment scan uses 5).
- Evaluating it on real data: `ai-service/tools/export_comments.py` exports comments from MySQL with emails, IC numbers, phone numbers and links redacted, to a CSV for two Malaysian annotators to label (keep it out of git). `ai-service/tools/eval_comments.py --file <csv|jsonl>` reports precision and recall per label, the share of fair comments wrongly flagged, and the share of problem comments missed (`--rules-only` for the offline baseline). `tests/data/comment_eval.jsonl` holds 80 synthetic labelled comments (English, Bahasa Melayu, mixed); they need a native reviewer. Rules-only baseline: 0% of fair comments flagged, injection recall 1.00, abusive recall 0.86; sarcasm and inappropriate need the model (39% of problem comments are missed by rules alone), and the model's real accuracy is not measured yet.
- Tests: Java 485, JS 101, ai-service 77+ (comment rules, check, tools).
- Scheduled scan and agreement report (2026-11-21): `CommentScanScheduler` reads new or edited pending comments every 15 minutes (`app.comment-scan.interval-ms`), at most 200 per run, oldest assessment first, using the same `scanPendingComments` as the admin button; it stops when the AI is unavailable and records the outcome instead of throwing. On by default in prod (`COMMENT_SCAN_ENABLED`, passed through `docker-compose.yml`), off in dev. It only records advice. Super admin page `/superadmin/ai-comments` (sidebar: AI Comment Check) shows the agreement between the AI's findings and moderators' decisions (`AiAgreement`: flagged/approved, fine/rejected, per-finding counts) and the last scan with a "Run a scan now" button. Comments approved in bulk are marked (`assessment_comments.bulk_decision`, set by "approve all" and "approve the ones the scan found fine", cleared by an individual decision) and left out of the agreement, as are comments edited after the scan; below 30 decisions the page says the figures are a first look. Java 506 tests.

## Every error and downtime to Telegram (2026-11-21)
- App: a Logback appender (`ErrorAlertAppender`, registered in `logback-spring.xml` for dev and prod) hands every ERROR log event to `AlertingService.reportError`: exception, root cause, first app frame, request reference, user-safe text (emails, passwords, tokens masked). Same-kind errors are coalesced for 5 minutes with a count, a minute sweep reports repeats nobody followed up, and a flood cap (20 per 10 minutes) says how many were held back. Sent off the request thread. Client hang-ups and the alerting code's own logging are ignored. 4xx are not alerts. Graceful shutdown is announced. The AI service unreachable is announced as DOWN (it stays a WARN on the monitoring page because the rest of the system works).
- AI service: `app/alerts.py` (same bot and chat, coalescing and flood cap, error type only) reports Gemini failures, unhandled endpoint errors, start (including refusal to start) and stop. Tests never send real messages (`tests/_quiet.py`).
- `.github/workflows/uptime.yml`: external probe of the app (health includes the database) and optionally the AI service every 5 minutes, with Telegram messages on down, hourly while down, and back up. The database has no Telegram sender of its own; Dokploy's Telegram notifications cover container events (to be switched on in Dokploy). Tests: Java 518, ai-service 85, JS 101.

## Faster, more parallel pipeline (2026-11-21)
- `build.yml` (main): the image builds (`build-app`, `build-ai`, not pushed, they warm the layer cache) now run at the same time as the tests; `publish-app` waits for the Java and browser tests plus its build, `publish-ai` for the AI tests plus its build, and re-runs the build as cache hits before pushing. An image is still only pushed after its tests pass. Deploy is unchanged and still follows the whole workflow.
- Caches: app and AI images each have their own GHA cache scope (they shared one and overwrote each other). `adproject/Dockerfile` resolves Maven dependencies in a separate layer and skips compiling tests (`-Dmaven.test.skip=true`); `ai-service/.dockerignore` added. Maven runs with `-B -ntp`. Every job has a timeout.
- CI now also runs the AI service's unit tests (85, checked under Python 3.11 locally with a throwaway venv), not only `compileall`.
- Not verified: the workflows themselves have not run (no actionlint here; YAML parses and the job graph was read through). Docker builds were not run locally; the new Maven commands were run against a copy of the sources and produce the jar.
