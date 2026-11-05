const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '../../../..');
const templateDir = path.join(repoRoot, 'adproject/src/main/resources/templates');
const cssDir = path.join(repoRoot, 'adproject/src/main/resources/static/css');
const jsDir = path.join(repoRoot, 'adproject/src/main/resources/static/js');

test('UI Enhancements & Themes Test Suite', async (t) => {

    await t.test('Login, forgot and reset password CSS files reference kl_view.png as background', () => {
        const loginCss = fs.readFileSync(path.join(cssDir, 'login.css'), 'utf8');
        const forgotCss = fs.readFileSync(path.join(cssDir, 'forgot.css'), 'utf8');
        const resetCss = fs.readFileSync(path.join(cssDir, 'reset_password.css'), 'utf8');

        assert.ok(loginCss.includes('/images/kl_view.png'), 'login.css must use kl_view.png');
        assert.ok(!loginCss.includes('backgroundKL.jpg'), 'login.css must no longer use backgroundKL.jpg');

        assert.ok(forgotCss.includes('/images/kl_view.png'), 'forgot.css must use kl_view.png');
        assert.ok(resetCss.includes('/images/kl_view.png'), 'reset_password.css must use kl_view.png');
    });

    await t.test('sidebar.html organizes navigation into collapsible Data Management & Course & Rubrics Management groups with icons', () => {
        const sidebarHtml = fs.readFileSync(path.join(templateDir, 'fragments/sidebar.html'), 'utf8');

        // Course & Rubrics Management group with icon
        assert.ok(sidebarHtml.includes('Course &amp; Rubrics Management'), 'Sidebar must define Course & Rubrics Management group');
        assert.ok(sidebarHtml.includes('sidebar-group-icon'), 'Sidebar must include group icons');
        assert.ok(sidebarHtml.includes('href="/admin/manage-courses"'), 'Must contain Manage Courses link');
        assert.ok(sidebarHtml.includes('href="/rubrics/manage"'), 'Must contain Manage Assessments link');

        // Data Management group with icon
        assert.ok(sidebarHtml.includes('Data Management'), 'Sidebar must define Data Management group');
        assert.ok(sidebarHtml.includes('href="/admin/data-views"'), 'Must contain Data Views link');
        assert.ok(sidebarHtml.includes('/admin/analytics/lecturer-comparison'), 'Must contain Data Analytics link');
        assert.ok(sidebarHtml.includes('/admin/progress-tracking'), 'Must contain Progress & Comments link');

        // Check CSS definitions for accordion
        const adminSidebarCss = fs.readFileSync(path.join(cssDir, 'admin_sidebar.css'), 'utf8');
        assert.ok(adminSidebarCss.includes('.sidebar-group'), 'admin_sidebar.css must define .sidebar-group');
        assert.ok(adminSidebarCss.includes('.sidebar-submenu'), 'admin_sidebar.css must define .sidebar-submenu');
        assert.ok(adminSidebarCss.includes('.sidebar-group.is-open'), 'admin_sidebar.css must handle .is-open state');
    });

    await t.test('Theme switcher supports light, dark, and warm themes with Hallmark-compliant segmented control', () => {
        const themeJs = fs.readFileSync(path.join(jsDir, 'theme-switcher.js'), 'utf8');
        const systemUiCss = fs.readFileSync(path.join(cssDir, 'system-ui.css'), 'utf8');
        const breadcrumbHtml = fs.readFileSync(path.join(templateDir, 'fragments/breadcrumb.html'), 'utf8');

        assert.ok(themeJs.includes('VALID_THEMES = [\'light\', \'dark\', \'warm\']'), 'Must define light, dark, warm themes');
        assert.ok(themeJs.includes('iams.theme'), 'Must persist in localStorage');

        // Check CSS rules for dark and warm
        assert.ok(systemUiCss.includes('data-theme="dark"'), 'system-ui.css must define data-theme="dark"');
        assert.ok(systemUiCss.includes('data-theme="warm"'), 'system-ui.css must define data-theme="warm"');
        assert.ok(systemUiCss.includes('.theme-segmented-control'), 'system-ui.css must style .theme-segmented-control');

        // Check breadcrumb toolbar integration
        assert.ok(breadcrumbHtml.includes('data-theme-set="light"'), 'Breadcrumb must have light theme button');
        assert.ok(breadcrumbHtml.includes('data-theme-set="dark"'), 'Breadcrumb must have dark theme button');
        assert.ok(breadcrumbHtml.includes('data-theme-set="warm"'), 'Breadcrumb must have warm theme button');
        assert.ok(breadcrumbHtml.includes('class="theme-segment-btn"'), 'Breadcrumb must use clean theme-segment-btn');
    });

    await t.test('Assessor workstation hotkeys script and simplified sidebar guide are integrated', () => {
        const hotkeysJs = fs.readFileSync(path.join(jsDir, 'assessor-hotkeys.js'), 'utf8');
        const evalHtml = fs.readFileSync(path.join(templateDir, 'lecturer_combined_evaluation_form.html'), 'utf8');
        const evalCss = fs.readFileSync(path.join(cssDir, 'lecturer_evaluation.css'), 'utf8');
        const lecturerSidebar = fs.readFileSync(path.join(templateDir, 'fragments/lecturer_sidebar.html'), 'utf8');
        const adminSidebar = fs.readFileSync(path.join(templateDir, 'fragments/sidebar.html'), 'utf8');

        assert.ok(hotkeysJs.includes('selectRatingInActiveCard'), 'Must implement rating selection logic');
        assert.ok(evalHtml.includes('assessor-hotkeys.js'), 'Evaluation form must load assessor-hotkeys.js');
        assert.ok(evalCss.includes('.keyboard-active-card'), 'lecturer_evaluation.css must style active card');

        // Sidebar question mark button beside logout
        assert.ok(lecturerSidebar.includes('hotkey-help-trigger'), 'Lecturer sidebar must contain hotkey guide button');
        assert.ok(lecturerSidebar.includes('hotkey-guide-popover'), 'Lecturer sidebar must contain guide popover');
        assert.ok(adminSidebar.includes('hotkey-help-trigger'), 'Admin sidebar must contain hotkey guide button');
        assert.ok(adminSidebar.includes('hotkey-guide-popover'), 'Admin sidebar must contain guide popover');
    });

    await t.test('admin_home.html contains Real-Time Presentation Progress Bar (SSE) with multi-assessment switcher and compact drawer', () => {
        const adminHomeHtml = fs.readFileSync(path.join(templateDir, 'admin_home.html'), 'utf8');
        const sseJs = fs.readFileSync(path.join(jsDir, 'live-progress-sse.js'), 'utf8');
        const adminHomeCss = fs.readFileSync(path.join(cssDir, 'admin_home.css'), 'utf8');

        assert.ok(adminHomeHtml.includes('live-progress-card'), 'admin_home.html must contain live-progress-card');
        assert.ok(adminHomeHtml.includes('btn-toggle-details'), 'admin_home.html must include Open Details button');
        assert.ok(adminHomeHtml.includes('live-progress-sse.js'), 'admin_home.html must load live-progress-sse.js');
        assert.ok(adminHomeHtml.includes('liveAssessmentSelect'), 'admin_home.html must include liveAssessmentSelect dropdown');
        assert.ok(adminHomeHtml.includes('btnToggleMultiSummary'), 'admin_home.html must include All Active multi-summary button');
        assert.ok(adminHomeHtml.includes('live-multi-summary-drawer'), 'admin_home.html must include compact multi-summary drawer');
        assert.ok(adminHomeHtml.includes('btn-switch-live'), 'admin_home.html must include switch live buttons');

        assert.ok(sseJs.includes('new EventSource'), 'live-progress-sse.js must connect to SSE');
        assert.ok(sseJs.includes('btn-toggle-details'), 'live-progress-sse.js must handle toggle details');
        assert.ok(sseJs.includes('switchAssessment'), 'live-progress-sse.js must support dynamic assessment switching');
        assert.ok(sseJs.includes('btnToggleMultiSummary'), 'live-progress-sse.js must handle multi-summary toggle');

        assert.ok(adminHomeCss.includes('.live-progress-card'), 'admin_home.css must style live-progress-card');
        assert.ok(adminHomeCss.includes('border-left: 5px solid var(--color-accent'), 'live-progress-card must follow managed courses indicator style');
        assert.ok(adminHomeCss.includes('.sse-live-dot'), 'admin_home.css must style sse-live-dot');
        assert.ok(adminHomeCss.includes('.live-assessment-select'), 'admin_home.css must style live-assessment-select');
        assert.ok(adminHomeCss.includes('.live-multi-summary-drawer'), 'admin_home.css must style live-multi-summary-drawer');
        assert.ok(adminHomeCss.includes('.btn-switch-live'), 'admin_home.css must style btn-switch-live');
    });

    await t.test('Universal contrast and visibility rules in system-ui.css and view_rubric.css for Dark and Warm modes', () => {
        const systemUiCss = fs.readFileSync(path.join(cssDir, 'system-ui.css'), 'utf8');
        const viewRubricCss = fs.readFileSync(path.join(cssDir, 'view_rubric.css'), 'utf8');

        // Dark mode contrast protections
        assert.ok(systemUiCss.includes('html[data-theme="dark"] [style*="color: #4A0018"]'), 'system-ui.css must rescue dark maroon inline colors in dark mode');
        assert.ok(systemUiCss.includes('html[data-theme="dark"] [style*="color: #1f2937"]'), 'system-ui.css must rescue charcoal inline colors in dark mode');
        assert.ok(systemUiCss.includes('html[data-theme="dark"] [style*="background-color: #f8f9fa"]'), 'system-ui.css must override light gray backgrounds in dark mode');
        assert.ok(systemUiCss.includes('html[data-theme="dark"] .comment-text'), 'system-ui.css must style comment-text in dark mode');

        // Dark mode role primary tokens & button text contrast
        assert.ok(systemUiCss.includes('--admin-primary: #f59e0b;'), 'Dark mode must define accessible amber admin primary');
        assert.ok(systemUiCss.includes('--lecturer-primary: #34d399;'), 'Dark mode must define accessible emerald lecturer primary');
        assert.ok(systemUiCss.includes('--supervisor-primary: #60a5fa;'), 'Dark mode must define accessible sky supervisor primary');
        assert.ok(systemUiCss.includes('--student-primary: #f59e0b;'), 'Dark mode must define accessible student primary');
        assert.ok(systemUiCss.includes('--ui-background: var(--ui-bg-canvas);'), 'body[data-ui-role] must reference dynamic canvas token');
        assert.ok(!systemUiCss.includes('#fb7185'), 'Dark mode must not contain disliked neon pink #fb7185');
        assert.ok(!systemUiCss.includes('#f43f5e'), 'Dark mode must not contain disliked neon rose #f43f5e');

        // Sidebar IAMS logo and user email contrast protections across all themes
        assert.ok(systemUiCss.includes(':is(.sidebar, aside) .logo :is(h1, h2, h3, span, a)'), 'Sidebar logo must have explicit high contrast selector');
        assert.ok(systemUiCss.includes(':is(.sidebar, aside) .user-profile :is(span, p, strong, small, em)'), 'Sidebar user profile must have explicit high contrast selector');
        assert.ok(systemUiCss.includes('html[data-theme="warm"] .sidebar .logo'), 'Warm mode must protect sidebar logo text contrast');
        assert.ok(systemUiCss.includes('html[data-theme="warm"] .sidebar .user-profile'), 'Warm mode must protect sidebar user profile text contrast');

        // Assessment Card Title visibility (Progress & Comments, Data Views)
        assert.ok(systemUiCss.includes('.card-header h3'), 'Card header h3 must have explicit text contrast rule');
        assert.ok(systemUiCss.includes('.assessment-card .card-header'), 'Assessment card header must be explicitly styled for dark/warm themes');

        // Dark view table overrides (prevent blinding white tables)
        assert.ok(systemUiCss.includes('Universal Dark Mode Table, Matrix, and Container overrides'), 'Dark mode must override white backgrounds on tables and containers');
        assert.ok(systemUiCss.includes('.sub-rubric-row'), 'Dark mode table overrides must cover sub-rubric-row');
        assert.ok(systemUiCss.includes('sticky-col'), 'Dark mode table overrides must cover sticky-col');

        // Accessible secondary buttons and breadcrumbs
        assert.ok(systemUiCss.includes('.btn-secondary'), 'system-ui.css must define secondary button styles');
        assert.ok(systemUiCss.includes('.alert-success'), 'system-ui.css must style high-contrast alerts');
        assert.ok(systemUiCss.includes('td::before'), 'system-ui.css must style responsive mobile table label contrast');

        // Warm mode contrast protections
        assert.ok(systemUiCss.includes('html[data-theme="warm"] [style*="color: #4A0018"]'), 'system-ui.css must style maroon text in warm mode');
        assert.ok(systemUiCss.includes('html[data-theme="warm"] [style*="color: #1f2937"]'), 'system-ui.css must style dark text in warm mode');
        assert.ok(systemUiCss.includes('html[data-theme="warm"] .comment-text'), 'system-ui.css must style comment-text in warm mode');

        // View rubrics page multi-theme
        assert.ok(viewRubricCss.includes('html[data-theme="dark"] body.view-rubric-page'), 'view_rubric.css must define dark mode');
        assert.ok(viewRubricCss.includes('html[data-theme="warm"] body.view-rubric-page'), 'view_rubric.css must define warm mode');
        assert.ok(viewRubricCss.includes('notes-display-box'), 'view_rubric.css must style notes-display-box');
    });

    await t.test('edit_overrides.html supports Reason input and Grade Override Audit Trail', () => {
        const overridesHtml = fs.readFileSync(path.join(templateDir, 'edit_overrides.html'), 'utf8');

        assert.ok(overridesHtml.includes('Reason for Adjustment'), 'Must have Reason for Adjustment column header');
        assert.ok(overridesHtml.includes('th:field="*{updates[__${stat.index}__].reason}"'), 'Must bind update reason input');
        assert.ok(overridesHtml.includes('id="overrideReasonPresets"'), 'Must include reason datalist presets');
        assert.ok(overridesHtml.includes('Grade Override Audit Trail'), 'Must include Grade Override Audit Trail card');
        assert.ok(overridesHtml.includes('th:each="log : ${recentAuditLogs}"'), 'Must loop over recentAuditLogs');
    });

    await t.test('admin_progress_tracking_view.html & admin_progress_tracking.css follow Hallmark anti-slop institutional design', () => {
        const trackingHtml = fs.readFileSync(path.join(templateDir, 'admin_progress_tracking_view.html'), 'utf8');
        const trackingCss = fs.readFileSync(path.join(cssDir, 'admin_progress_tracking.css'), 'utf8');
        const systemUiCss = fs.readFileSync(path.join(cssDir, 'system-ui.css'), 'utf8');

        // Comments & Moderation
        assert.ok(trackingHtml.includes('tracking-stats-summary'), 'Must use calm tabular stats summary');
        assert.ok(trackingHtml.includes('moderation-notice-bar'), 'Must use quiet moderation notice bar instead of cartoon banner');
        assert.ok(trackingHtml.includes('filter-segmented-tabs'), 'Must use clean segmented filter control');
        assert.ok(trackingHtml.includes('id="kpi-total"'), 'Must preserve #kpi-total JS hook');
        assert.ok(trackingHtml.includes('id="kpi-pending"'), 'Must preserve #kpi-pending JS hook');
        assert.ok(trackingHtml.includes('id="pill-count-pending"'), 'Must preserve #pill-count-pending JS hook');
        assert.ok(trackingHtml.includes('id="btnApproveAllPending"'), 'Must preserve #btnApproveAllPending JS hook');

        // Report Setup & Assessor Mapping
        assert.ok(trackingHtml.includes('submission-config-card'), 'Must use semantic submission-config-card');
        assert.ok(trackingHtml.includes('matrix-card'), 'Must use semantic matrix-card');
        assert.ok(trackingHtml.includes('matrix-table'), 'Must use clean matrix-table');
        assert.ok(trackingHtml.includes('matrix-guide-note'), 'Must use matrix-guide-note');
        assert.ok(trackingHtml.includes('col-target'), 'Must define col-target header');
        assert.ok(trackingHtml.includes('col-assessors'), 'Must define col-assessors header');
        assert.ok(trackingHtml.includes('col-submission'), 'Must define col-submission header');

        // Dark and Warm Theme Integrity
        assert.ok(trackingCss.includes('html[data-theme="dark"] .tracking-stats-summary'), 'Must define dark mode tracking summary');
        assert.ok(trackingCss.includes('html[data-theme="dark"] .matrix-table'), 'Must define dark mode matrix table');
        assert.ok(trackingCss.includes('html[data-theme="dark"] .comment-card-item'), 'Must define dark mode comment card');
        assert.ok(trackingCss.includes('html[data-theme="warm"] .tracking-stats-summary'), 'Must define warm mode tracking summary');
        assert.ok(trackingCss.includes('html[data-theme="warm"] .matrix-table'), 'Must define warm mode matrix table');

        // Institutional Header & Logo prestige (no neon pink slop)
        assert.ok(systemUiCss.includes('html[data-theme="dark"] .main-content .header'), 'system-ui.css must protect dark mode header from pink');
        assert.ok(systemUiCss.includes('html[data-theme="dark"] .sidebar .logo'), 'system-ui.css must protect dark mode logo from pink');
        assert.ok(systemUiCss.includes('html[data-theme="warm"] .main-content .header'), 'system-ui.css must protect warm mode header');
    });
});

