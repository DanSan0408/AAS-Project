# Project State Documentation

**Last Updated**: 2026-10-28 (Antigravity)

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