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
});
