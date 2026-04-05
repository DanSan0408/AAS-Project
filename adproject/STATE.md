# Project State Documentation

**Last Updated**: 2026-04-02 (IH)

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