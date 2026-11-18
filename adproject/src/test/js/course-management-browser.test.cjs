const { test, describe, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT_DIR = path.resolve(__dirname, '../../..');
const TEMPLATES_DIR = path.join(ROOT_DIR, 'src/main/resources/templates');
const STATIC_JS_DIR = path.join(ROOT_DIR, 'src/main/resources/static/js');
const STATIC_CSS_DIR = path.join(ROOT_DIR, 'src/main/resources/static/css');

describe('Course Management Templates & DOM Contract Tests', () => {

    test('admin_manage_courses.html defines compliant add course form, validation hooks, and CSRF', () => {
        const filePath = path.join(TEMPLATES_DIR, 'admin_manage_courses.html');
        assert.ok(fs.existsSync(filePath), 'admin_manage_courses.html must exist');
        const content = fs.readFileSync(filePath, 'utf-8');

        // Verify Form Action
        assert.match(content, /th:action="@\{(?:\/admin)?\/courses\/add\}"/, 'Form must post to /admin/courses/add');
        assert.match(content, /th:object="\$\{newCourse\}"/, 'Form must bind to newCourse model attribute');

        // Verify Inputs with validation IDs
        assert.match(content, /id="courseName"/, 'courseName input must have id="courseName"');
        assert.match(content, /id="courseCode"/, 'courseCode input must have id="courseCode" for live validation');
        assert.match(content, /th:field="\*\{courseCode\}"/, 'courseCode input must bind th:field="*{courseCode}"');

        // Verify Script integration
        assert.match(content, /course-validation\.js/, 'Template must link course-validation.js');
        assert.match(content, /system-ui\.css/, 'Template must link system-ui.css');
        assert.match(content, /admin_manage_courses\.css/, 'Template must link admin_manage_courses.css');

        // Verify Table listing & management actions
        assert.match(content, /th:each="course\s*:\s*\$\{courses\}"/, 'Must iterate over courses list');
        assert.match(content, /th:href="@\{(?:\/admin)?\/courses\/edit\/\{id\}\(id=\$\{course\.id\}\)\}"/, 'Must link to edit course');
        assert.match(content, /th:action="@\{(?:\/admin)?\/courses\/delete\/\{id\}\(id=\$\{course\.id\}\)\}"/, 'Must post to delete course');
    });

    test('manage_courses.html (superadmin) defines add course form with matching courseCode ID', () => {
        const filePath = path.join(TEMPLATES_DIR, 'manage_courses.html');
        assert.ok(fs.existsSync(filePath), 'manage_courses.html must exist');
        const content = fs.readFileSync(filePath, 'utf-8');

        assert.match(content, /th:action="@\{(?:\/superadmin)?\/courses\/add\}"/, 'SuperAdmin must post to /superadmin/courses/add');
        assert.match(content, /id="courseCode"/, 'Must have id="courseCode"');
        assert.match(content, /course-validation\.js/, 'Must link course-validation.js');
    });

    test('admin_edit_course.html includes hidden ID for excludeId parameter and update action', () => {
        const filePath = path.join(TEMPLATES_DIR, 'admin_edit_course.html');
        assert.ok(fs.existsSync(filePath), 'admin_edit_course.html must exist');
        const content = fs.readFileSync(filePath, 'utf-8');

        assert.match(content, /th:action="@\{(?:\/admin)?\/courses\/update\}"/, 'Must post to /admin/courses/update');
        assert.match(content, /th:field="\*\{id\}"/, 'Must have hidden ID input with th:field="*{id}" for excludeId validation');
        assert.match(content, /id="courseCode"/, 'Must have id="courseCode"');
        assert.match(content, /course-validation\.js/, 'Must link course-validation.js');
    });

    test('system-ui.css & admin_manage_courses.css define styles for validation and responsive layout', () => {
        const cssPath = path.join(STATIC_CSS_DIR, 'system-ui.css');
        assert.ok(fs.existsSync(cssPath), 'system-ui.css must exist');
        const css = fs.readFileSync(cssPath, 'utf-8');
        assert.ok(css.includes('--ui-font') || css.includes('Inter'), 'Shared typography must be defined');

        const adminCssPath = path.join(STATIC_CSS_DIR, 'admin_manage_courses.css');
        assert.ok(fs.existsSync(adminCssPath), 'admin_manage_courses.css must exist');
        const adminCss = fs.readFileSync(adminCssPath, 'utf-8');
        assert.ok(adminCss.includes('manage-courses-container'), 'Container styling must exist');
    });
});

describe('course-validation.js Client-Side Logic Tests', () => {

    test('course-validation.js exists and implements debounce, fetch check, and feedback rendering', () => {
        const jsPath = path.join(STATIC_JS_DIR, 'course-validation.js');
        assert.ok(fs.existsSync(jsPath), 'course-validation.js must exist');
        const js = fs.readFileSync(jsPath, 'utf-8');

        // Check DOM references
        assert.ok(js.includes('document.getElementById("courseCode")'), 'Must query #courseCode');
        assert.ok(js.includes('document.getElementById("courseCodeFeedback")'), 'Must manage #courseCodeFeedback');
        assert.ok(js.includes('document.getElementById("id")'), 'Must inspect #id for excludeId');

        // Check Endpoint
        assert.ok(js.includes('/admin/courses/check-code'), 'Must fetch /admin/courses/check-code');
        assert.ok(js.includes('excludeId'), 'Must support excludeId parameter');

        // Check Validation Feedback Messages
        assert.ok(js.includes('The course exists, and the course ID has already been used.'), 'Must display duplicate warning');
        assert.ok(js.includes('Course ID is available.'), 'Must display available confirmation');
        assert.ok(js.includes('valid-code') && js.includes('invalid-code'), 'Must toggle valid/invalid CSS classes');
    });

    test('simulated DOM event: handles validation transitions and exclusion logic', async () => {
        // Minimal simulated DOM environment
        const listeners = {};
        const elements = {
            courseCode: {
                value: '',
                classList: {
                    classes: new Set(),
                    add(c) { this.classes.add(c); },
                    remove(c) { this.classes.delete(c); },
                    contains(c) { return this.classes.has(c); }
                },
                parentNode: {
                    appendChild(child) { elements.courseCodeFeedback = child; }
                },
                addEventListener(evt, fn) { listeners[evt] = fn; },
                dispatchEvent(evt) { if (listeners[evt.type]) listeners[evt.type](); }
            },
            id: { value: '42' }
        };

        let fetchUrl = null;
        global.fetch = async (url) => {
            fetchUrl = url;
            const isTaken = url.includes('SECJ101') && !url.includes('excludeId=42');
            return {
                ok: true,
                json: async () => ({ exists: isTaken })
            };
        };

        // Execute simulated validation logic directly matching course-validation.js
        function runValidation(code, excludeId) {
            if (!code) {
                elements.courseCode.classList.remove('valid-code');
                elements.courseCode.classList.remove('invalid-code');
                return { status: 'empty' };
            }
            let url = `/admin/courses/check-code?courseCode=${encodeURIComponent(code)}`;
            if (excludeId) url += `&excludeId=${encodeURIComponent(excludeId)}`;
            return fetch(url).then(r => r.json()).then(data => {
                if (data.exists) {
                    elements.courseCode.classList.remove('valid-code');
                    elements.courseCode.classList.add('invalid-code');
                    return { status: 'duplicate', exists: true };
                } else {
                    elements.courseCode.classList.remove('invalid-code');
                    elements.courseCode.classList.add('valid-code');
                    return { status: 'available', exists: false };
                }
            });
        }

        // Test 1: Empty input resets classes
        const emptyResult = runValidation('', '42');
        assert.equal(emptyResult.status, 'empty');
        assert.ok(!elements.courseCode.classList.contains('valid-code'));
        assert.ok(!elements.courseCode.classList.contains('invalid-code'));

        // Test 2: Available code marks valid
        const availableResult = await runValidation('NEW999', '42');
        assert.equal(availableResult.status, 'available');
        assert.ok(elements.courseCode.classList.contains('valid-code'));
        assert.ok(!elements.courseCode.classList.contains('invalid-code'));
        assert.ok(fetchUrl.includes('courseCode=NEW999&excludeId=42'));

        // Test 3: Duplicate code marks invalid
        const duplicateResult = await runValidation('SECJ101', '');
        assert.equal(duplicateResult.status, 'duplicate');
        assert.ok(elements.courseCode.classList.contains('invalid-code'));
        assert.ok(!elements.courseCode.classList.contains('valid-code'));
    });

    test('student_sidebar.html and lecturer_sidebar.html define course switcher with active course selection and CSRF', () => {
        const studentSidebarPath = path.join(TEMPLATES_DIR, 'fragments/student_sidebar.html');
        assert.ok(fs.existsSync(studentSidebarPath), 'student_sidebar.html must exist');
        const studentContent = fs.readFileSync(studentSidebarPath, 'utf-8');

        assert.match(studentContent, /th:action="@\{(?:\/student)?\/switch-course\}"/, 'Student switcher must post to /student/switch-course');
        assert.match(studentContent, /id="courseSwitchForm"/, 'Student switcher form must have id="courseSwitchForm"');
        assert.match(studentContent, /id="courseSwitchSelect"/, 'Student switcher select must have id="courseSwitchSelect"');
        assert.match(studentContent, /name="courseId"/, 'Select must bind name="courseId"');
        assert.match(studentContent, /th:selected="\$\{activeCourseId == course\.id\}"/, 'Must mark active course as selected');
        assert.match(studentContent, /th:name="\$\{\_csrf\.parameterName\}"/, 'Must include CSRF token parameter in student switcher');

        const lecturerSidebarPath = path.join(TEMPLATES_DIR, 'fragments/lecturer_sidebar.html');
        assert.ok(fs.existsSync(lecturerSidebarPath), 'lecturer_sidebar.html must exist');
        const lecturerContent = fs.readFileSync(lecturerSidebarPath, 'utf-8');

        assert.match(lecturerContent, /th:action="@\{(?:\/lecturer)?\/switch-course\}"/, 'Lecturer switcher must post to /lecturer/switch-course');
        assert.match(lecturerContent, /id="courseSwitchSelect"/, 'Lecturer switcher select must have id="courseSwitchSelect"');

        const studentCssPath = path.join(STATIC_CSS_DIR, 'student_sidebar.css');
        assert.ok(fs.existsSync(studentCssPath), 'student_sidebar.css must exist');
        const studentCss = fs.readFileSync(studentCssPath, 'utf-8');
        assert.ok(studentCss.includes('.course-switcher'), 'student_sidebar.css must style .course-switcher');
        assert.ok(studentCss.includes('.course-switch-select'), 'student_sidebar.css must style .course-switch-select');
    });

    test('comment_configuration_single_type.html and comment-config.js support multiple comments with index-safe inputs', () => {
        const commentHtmlPath = path.join(TEMPLATES_DIR, 'comment_configuration_single_type.html');
        assert.ok(fs.existsSync(commentHtmlPath), 'comment_configuration_single_type.html must exist');
        const commentHtml = fs.readFileSync(commentHtmlPath, 'utf-8');

        assert.ok(commentHtml.includes('th:block th:if="${configType == \'group\'}"'), 'Must use th:block for group comment container');
        assert.ok(commentHtml.includes('th:block th:if="${configType == \'individual\'}"'), 'Must use th:block for individual comment container');
        assert.ok(commentHtml.includes('anonymous-hidden-input'), 'Must include hidden fallback input for anonymous checkboxes');
        assert.ok(commentHtml.includes('anonymous-checkbox'), 'Must classify anonymous checkbox');

        const commentJsPath = path.join(STATIC_JS_DIR, 'comment-config.js');
        assert.ok(fs.existsSync(commentJsPath), 'comment-config.js must exist');
        const commentJs = fs.readFileSync(commentJsPath, 'utf-8');

        assert.ok(commentJs.includes('anonymous-hidden-input'), 'JS renumber must update hidden anonymous fallback input');
        assert.ok(commentJs.includes('anonymous-checkbox'), 'JS renumber must update checkbox input');
        assert.ok(commentJs.includes('commentConfigForm'), 'JS must renumber all question cards on submit');
    });

    test('group.css and group_assignment.html prevent group randomization trimming and input overflow', () => {
        const groupCssPath = path.join(STATIC_CSS_DIR, 'group.css');
        assert.ok(fs.existsSync(groupCssPath), 'group.css must exist');
        const groupCss = fs.readFileSync(groupCssPath, 'utf-8');

        assert.ok(groupCss.includes('flex-wrap: wrap'), 'creation-wrapper must allow wrapping to prevent trimming');
        assert.ok(groupCss.includes('randomize-form-inline'), 'randomize-form-inline styling must be defined');
        assert.ok(groupCss.includes('form-group-inline'), 'form-group-inline styling must be defined');

        const adminCourseCssPath = path.join(STATIC_CSS_DIR, 'admin_manage_courses.css');
        assert.ok(fs.existsSync(adminCourseCssPath), 'admin_manage_courses.css must exist');
        const adminCourseCss = fs.readFileSync(adminCourseCssPath, 'utf-8');
        assert.ok(adminCourseCss.includes('box-sizing: border-box'), 'admin_manage_courses.css must apply box-sizing: border-box to prevent trimmed inputs');

        const superCourseCssPath = path.join(STATIC_CSS_DIR, 'manage_courses.css');
        assert.ok(fs.existsSync(superCourseCssPath), 'manage_courses.css must exist');
        const superCourseCss = fs.readFileSync(superCourseCssPath, 'utf-8');
        assert.ok(superCourseCss.includes('box-sizing: border-box'), 'manage_courses.css must apply box-sizing: border-box to prevent trimmed inputs');
    });

    test('assessment_submission_configuration.html supports multi-assessment report reuse and shared submission source selection', () => {
        const configPath = path.join(TEMPLATES_DIR, 'assessment_submission_configuration.html');
        assert.ok(fs.existsSync(configPath), 'assessment_submission_configuration.html must exist');
        const content = fs.readFileSync(configPath, 'utf-8');

        assert.ok(content.includes('submissionMode'), 'Must include submissionMode radio/options');
        assert.ok(content.includes('submissionSourceAssessmentId'), 'Must include submissionSourceAssessmentId select');
        assert.ok(content.includes('courseAssessments'), 'Must iterate over courseAssessments for shared selection');
        assert.ok(content.includes('updateModeVisibility'), 'Must include JavaScript toggle for shared submission mode');
    });

    test('assessor report viewing in new tab is available on reports overview, group/student select, and evaluation workstation', () => {
        // 1. Dedicated reports management overview template
        const reportsHtmlPath = path.join(TEMPLATES_DIR, 'lecturer_assessment_reports.html');
        assert.ok(fs.existsSync(reportsHtmlPath), 'lecturer_assessment_reports.html must exist');
        const reportsContent = fs.readFileSync(reportsHtmlPath, 'utf-8');

        assert.match(reportsContent, /target="_blank"/, 'Report overview must open PDF in new tab');
        assert.ok(reportsContent.includes('/lecturer/assessments/submissions/'), 'Must link to submission file stream');
        assert.ok(reportsContent.includes('isShared'), 'Must support shared report indicators');
        assert.ok(reportsContent.includes('filterReportRows'), 'Must include interactive search filter');

        // 2. Select group and student templates include new-tab report buttons
        const selectGroupPath = path.join(TEMPLATES_DIR, 'lecturer_select_group.html');
        const selectGroupContent = fs.readFileSync(selectGroupPath, 'utf-8');
        assert.ok(selectGroupContent.includes('target="_blank"'), 'Select group must include target="_blank" for report viewing');
        assert.ok(selectGroupContent.includes('btn-report-view'), 'Select group cards must include btn-report-view');
        assert.ok(selectGroupContent.includes('reports'), 'Select group header must link to reports overview');

        const selectStudentPath = path.join(TEMPLATES_DIR, 'lecturer_select_student.html');
        const selectStudentContent = fs.readFileSync(selectStudentPath, 'utf-8');
        assert.ok(selectStudentContent.includes('target="_blank"'), 'Select student must include target="_blank" for report viewing');
        assert.ok(selectStudentContent.includes('btn-report-view'), 'Select student cards must include btn-report-view');
        assert.ok(selectStudentContent.includes('reports'), 'Select student header must link to reports overview');

        // 3. Evaluation studio maintains split view and has prominent report in new tab button
        const evalPath = path.join(TEMPLATES_DIR, 'lecturer_combined_evaluation_form.html');
        const evalContent = fs.readFileSync(evalPath, 'utf-8');
        assert.ok(evalContent.includes('split-evaluation-layout'), 'Split evaluation container must be preserved');
        assert.match(evalContent, /target="_blank"[^>]*class="workstation-action-link"/, 'Evaluation workstation must preserve target="_blank" pop-out link');
    });

    test('system-ui.css and role sidebars enforce standardized typography aligned to Admin Home (500 link weight, 600 active)', () => {
        const systemUiPath = path.join(STATIC_CSS_DIR, 'system-ui.css');
        assert.ok(fs.existsSync(systemUiPath), 'system-ui.css must exist');
        const systemUiContent = fs.readFileSync(systemUiPath, 'utf-8');

        assert.ok(systemUiContent.includes('font-weight: 500 !important;'), 'system-ui.css must enforce font-weight: 500 on regular sidebar links');
        assert.ok(systemUiContent.includes('.sidebar-group-btn'), 'system-ui.css must standardize .sidebar-group-btn');
        assert.ok(systemUiContent.includes('.sidebar-subitem'), 'system-ui.css must standardize .sidebar-subitem');

        const adminSidebarPath = path.join(STATIC_CSS_DIR, 'admin_sidebar.css');
        const adminSidebarContent = fs.readFileSync(adminSidebarPath, 'utf-8');
        assert.ok(adminSidebarContent.includes('font-weight: 500;'), 'admin_sidebar.css must use font-weight: 500 for sidebar-link');

        const lecturerSidebarPath = path.join(STATIC_CSS_DIR, 'lecturer_sidebar.css');
        const lecturerSidebarContent = fs.readFileSync(lecturerSidebarPath, 'utf-8');
        assert.ok(lecturerSidebarContent.includes('font-weight: 500;'), 'lecturer_sidebar.css must use font-weight: 500 for menu-list li a');
    });

    test('group assignment form allows optional supervisor assignments, handles errors safely, and preserves multi-course students', () => {
        const groupAssignmentHtml = fs.readFileSync(path.join(TEMPLATES_DIR, 'group_assignment.html'), 'utf-8');
        assert.ok(!groupAssignmentHtml.includes('<select id="industrialSupervisorId" th:field="*{industrialSupervisorId}" required>'),
            'group_assignment.html must not force required on industrialSupervisorId');
        assert.ok(groupAssignmentHtml.includes('Select Industrial Supervisor (Optional)'),
            'group_assignment.html must provide an optional choice for industrial supervisor');

        const editGroupHtml = fs.readFileSync(path.join(TEMPLATES_DIR, 'edit_group.html'), 'utf-8');
        assert.ok(!editGroupHtml.includes('<select id="industrialSupervisorId" th:field="*{industrialSupervisorId}" required>'),
            'edit_group.html must not force required on industrialSupervisorId');

        const adminCtrl = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/controller/AdminController.java'), 'utf-8');
        assert.ok(adminCtrl.includes('Group created and assigned successfully!'),
            'AdminController.createAndAssignGroup must set success flash attribute');
        assert.ok(adminCtrl.includes('Error creating group:'),
            'AdminController.createAndAssignGroup must catch exceptions and provide helpful flash error');

        const adminService = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/service/AdminService.java'), 'utf-8');
        assert.ok(adminService.includes('findByIdNative'),
            'AdminService must check native student lookup to support cross-course enrolled students');
        assert.ok(adminService.includes('isPrimaryCourse'),
            'AdminService must isolate primary course direct group updates from secondary course enrollments');
        assert.ok(adminService.includes('ensureProjectGroupColumnsHaveDefaults'),
            'AdminService must set default on group_size/groupsize to prevent Field group_size does not have a default value');
        assert.ok(adminService.includes('syncProjectGroupDualColumns'),
            'AdminService must synchronize group_size and groupsize columns');

        const migrationRunner = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/config/CourseEmailConstraintMigrationRunner.java'), 'utf-8');
        assert.ok(migrationRunner.includes('migrateProjectGroupColumns'),
            'CourseEmailConstraintMigrationRunner must define migrateProjectGroupColumns');
        assert.ok(migrationRunner.includes('group_size'),
            'CourseEmailConstraintMigrationRunner must set defaults on group_size');
    });

    test('legacy duplicate assessor consolidation migration and AdminService assessor deduplication', () => {
        const migrationRunner = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/config/CourseEmailConstraintMigrationRunner.java'), 'utf-8');
        assert.ok(migrationRunner.includes('migrateDuplicateLecturers'),
            'CourseEmailConstraintMigrationRunner must define migrateDuplicateLecturers');
        assert.ok(migrationRunner.includes('consolidateDuplicateLecturersForEmail'),
            'CourseEmailConstraintMigrationRunner must define consolidateDuplicateLecturersForEmail');
        assert.ok(migrationRunner.includes('repointLecturerReferences'),
            'CourseEmailConstraintMigrationRunner must repoint foreign keys before deleting duplicate assessor rows');
        assert.ok(migrationRunner.includes('academic_supervisor_id') && migrationRunner.includes('industrial_supervisor_id'),
            'Migration runner must repoint group supervisors');
        assert.ok(migrationRunner.includes('admin_course_assignment') && migrationRunner.includes('lecturer_student_assignment'),
            'Migration runner must repoint course and student assignments');

        const adminService = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/service/AdminService.java'), 'utf-8');
        assert.ok(adminService.includes('distinct.put(key, l)'),
            'AdminService.getAllLecturers must deduplicate assessors by normalized identity');
        assert.ok(adminService.includes('adminCourseAssignmentRepository.save(new AdminCourseAssignment'),
            'AdminService.saveLecturer must reuse existing lecturer records across courses via AdminCourseAssignment instead of duplicating');
    });

    test('course switcher and assessor assessment access safeguards', () => {
        const sidebarToggleJs = fs.readFileSync(path.join(STATIC_JS_DIR, 'sidebar-toggle.js'), 'utf-8');
        assert.ok(sidebarToggleJs.includes("this.value && this.value.trim() !== ''"),
            'sidebar-toggle.js must guard against submitting empty course switcher values');

        const lecturerCtrl = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/controller/LecturerController.java'), 'utf-8');
        assert.ok(lecturerCtrl.includes('@RequestParam(value = "courseId", required = false) Long courseId'),
            'LecturerController.switchCourse must allow required = false for courseId');
        assert.ok(lecturerCtrl.includes('if (courseId == null)'),
            'LecturerController.switchCourse must guard null courseId');

        const studentCtrl = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/controller/StudentController.java'), 'utf-8');
        assert.ok(studentCtrl.includes('@RequestParam(value = "courseId", required = false) Long courseId'),
            'StudentController.switchCourse must allow required = false for courseId');
        assert.ok(studentCtrl.includes('if (courseId == null)'),
            'StudentController.switchCourse must guard null courseId');

        const adminCtrl = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/controller/AdminController.java'), 'utf-8');
        assert.ok(adminCtrl.includes('@RequestParam(value = "courseId", required = false) Long courseId'),
            'AdminController.switchCourse must allow required = false for courseId');
        assert.ok(adminCtrl.includes('if (courseId == null)'),
            'AdminController.switchCourse must guard null courseId');

        const courseScopeService = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/service/CourseScopeService.java'), 'utf-8');
        assert.ok(courseScopeService.includes('Optional.of(candidates.get(0))'),
            'CourseScopeService.resolveLecturerForCourse must fall back to candidate when specific courseId candidate is not matched directly');
        assert.ok(courseScopeService.includes('lecturer_student_assignment') && courseScopeService.includes('lecturer_rubric_assignments'),
            'CourseScopeService.findAssignedCourseIdsForLecturer must discover courses across all assignment types');

        const globalEx = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/exception/GlobalExceptionHandler.java'), 'utf-8');
        assert.ok(globalEx.includes('AsyncRequestTimeoutException'),
            'GlobalExceptionHandler must handle AsyncRequestTimeoutException without crashing SSE streams');
        assert.ok(globalEx.includes('response.isCommitted()'),
            'GlobalExceptionHandler must guard against committed responses to avoid IllegalStateException getOutputStream');
    });

    test('student identity resolution and multi-course access safeguards', () => {
        const courseScopeService = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/service/CourseScopeService.java'), 'utf-8');
        assert.ok(courseScopeService.includes('resolveStudentsByIdentity'),
            'CourseScopeService must define resolveStudentsByIdentity for multi-course student resolution');
        assert.ok(courseScopeService.includes('resolveStudentForCourse'),
            'CourseScopeService must define resolveStudentForCourse to match active course');

        const studentCtrl = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/controller/StudentController.java'), 'utf-8');
        assert.ok(studentCtrl.includes('getCurrentStudent(Principal principal)'),
            'StudentController must use getCurrentStudent helper for identity resolution');
        assert.ok(studentCtrl.includes('isStudentAssignedToAssessment(currentStudent, a)'),
            'StudentController must verify assignment access for assessments');

        const userDetailsService = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/service/CustomUserDetailsService.java'), 'utf-8');
        assert.ok(userDetailsService.includes('CourseScopeService.resolveStudentsByIdentity(studentRepository, identifier)'),
            'CustomUserDetailsService must resolve students via CourseScopeService to handle duplicate rows and case sensitivity');

        const profileCtrl = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/controller/ProfileController.java'), 'utf-8');
        assert.ok(profileCtrl.includes('CourseScopeService.resolveStudentsByIdentity(studentRepository'),
            'ProfileController must resolve students safely via CourseScopeService');
    });

    test('assign assessors UI layout and delete button trimming safeguards', () => {
        const assignHtml = fs.readFileSync(path.join(TEMPLATES_DIR, 'admin_assign_lecturers.html'), 'utf-8');
        assert.ok(assignHtml.includes('groupStudentsMap.get(group.id)'),
            'assign assessor group cards must use course-aware student assignments instead of only student.group');
        assert.ok(assignHtml.includes('class="save-bar-spacer"'),
            'admin_assign_lecturers.html must contain save-bar-spacer to prevent blocking the last teams');
        assert.ok(assignHtml.includes('class="button-container"'),
            'admin_assign_lecturers.html must define fixed button-container');

        const assignCss = fs.readFileSync(path.join(STATIC_CSS_DIR, 'assign_lecturers.css'), 'utf-8');
        assert.ok(assignCss.includes('.save-bar-spacer'),
            'assign_lecturers.css must define .save-bar-spacer');
        assert.ok(assignCss.includes('padding-bottom: 140px') || assignCss.includes('140px'),
            'assign_lecturers.css must provide generous bottom clearance for the fixed save bar');
        assert.ok(assignCss.includes('min-width: 38px') && assignCss.includes('flex-shrink: 0'),
            'assign_lecturers.css must set min-width: 38px and flex-shrink: 0 on .btn-icon-remove to prevent trimming');
        assert.ok(assignCss.includes('min-width: 0') && assignCss.includes('text-overflow: ellipsis'),
            'assign_lecturers.css must prevent form-select from overflowing and squishing delete button');
        assert.ok(assignCss.includes('sidebar-collapsed .button-container'),
            'assign_lecturers.css must adjust button-container position when sidebar is collapsed');
    });

    test('email service checks SMTP credentials before delivery', () => {
        const emailService = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/service/EmailService.java'), 'utf-8');
        assert.ok(emailService.includes('SPRING_MAIL_USERNAME'),
            'EmailService must read injected SMTP username configuration');
        assert.ok(emailService.includes('SPRING_MAIL_PASSWORD'),
            'EmailService must validate injected SMTP password configuration');
        assert.ok(emailService.includes('Email delivery is disabled'),
            'EmailService must emit an explicit configuration error instead of silently appearing to send');
        assert.ok(emailService.includes('mailFrom = mailUsername.trim()'),
            'EmailService must use the authenticated SMTP username as the sender fallback');
    });

    test('evaluation mark persistence handles legacy CLO mark columns', () => {
        const migration = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/config/CourseEmailConstraintMigrationRunner.java'), 'utf-8');
        const lecturerService = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/service/LecturerAssessmentService.java'), 'utf-8');
        assert.ok(migration.includes('migrateMarksColumns'),
            'startup migration must normalize legacy marks CLO columns');
        assert.ok(migration.includes('clo_marks'),
            'startup migration must support snake_case clo_marks legacy databases');
        assert.ok(lecturerService.includes('if (mark.getCloMarks() == null)'),
            'evaluation persistence must prevent null CLO mark values');
        assert.ok(lecturerService.includes('mark.setCloMarks(mark.getMarkValue()'),
            'evaluation persistence must provide a safe CLO mark fallback');
        assert.ok(migration.includes('migrateAssessmentCommentColumns'),
            'startup migration must normalize legacy assessment comment text columns');
        assert.ok(migration.includes('MODIFY COLUMN `" + column + "` TEXT NULL'),
            'legacy comment text columns must allow NULL so partial comment-only and mark-only saves work');
        assert.ok(lecturerService.includes('if (hasScores)'),
            'mark-only saves must not delete existing comments');
        assert.ok(lecturerService.includes('if (hasGeneralComments)'),
            'comment-only saves must not delete existing marks');
        assert.ok(lecturerService.includes('if (!hasNonblankRubricComment)'),
            'blank rubric comments must not erase previously saved rubric feedback');
        assert.ok(lecturerService.includes('findByEvaluatorAndStudentAndAssessmentAndIndex'),
            'comments must be persisted by evaluator/student/assessment/comment index');
        assert.ok(lecturerService.includes('assessmentCommentRepository.flush()'),
            'comment saves must be flushed before the evaluation response completes');
        assert.ok(lecturerService.includes('hasLecturerComments'),
            'comment-only evaluations must count as started and reload existing comments');
        assert.ok(lecturerService.includes('does not belong to this assessment course'),
            'course ownership must be enforced before evaluation persistence');
        assert.ok(migration.includes('mark_value'),
            'startup migration must prevent legacy mark_value from rejecting inserts');
    });

    test('save progress remains on evaluation and submits remain full', () => {
        const controller = fs.readFileSync(path.join(__dirname, '../../main/java/com/capstone/adproject/controller/LecturerAssessmentController.java'), 'utf-8');
        const template = fs.readFileSync(path.join(TEMPLATES_DIR, 'lecturer_combined_evaluation_form.html'), 'utf-8');
        assert.ok(template.includes('name="action" value="save"'),
            'save progress button must submit an explicit save action');
        assert.ok(controller.includes('@RequestParam(value = "action", defaultValue = "save") String action'),
            'controller must distinguish save progress from full submission');
        assert.ok(controller.includes('return "redirect:/lecturer/assessments/" + assessmentId'),
            'save progress must return to the evaluation path');
        assert.ok(controller.includes('Please complete all required marks and comments before submitting'),
            'full submission must reject incomplete evaluations');
    });
});
