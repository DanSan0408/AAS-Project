const { test, describe } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

describe('IAMS Branding & Loading Animation Test Suite', () => {
    const ROOT_DIR = path.resolve(__dirname, '../../../..');
    const DOCS_BRANDING_DIR = path.join(ROOT_DIR, 'docs/branding');
    const ASSETS_DIR = path.join(DOCS_BRANDING_DIR, 'assets');
    const STATIC_CSS_DIR = path.join(ROOT_DIR, 'adproject/src/main/resources/static/css');
    const STATIC_JS_DIR = path.join(ROOT_DIR, 'adproject/src/main/resources/static/js');
    const TEMPLATES_DIR = path.join(ROOT_DIR, 'adproject/src/main/resources/templates');

    test('docs/branding/BRAND_GUIDELINES.md defines official logo, palette, typography, and loading animation specs', () => {
        const guidelinesPath = path.join(DOCS_BRANDING_DIR, 'BRAND_GUIDELINES.md');
        assert.ok(fs.existsSync(guidelinesPath), 'BRAND_GUIDELINES.md must exist under docs/branding/');
        const content = fs.readFileSync(guidelinesPath, 'utf-8');

        // Logo checks
        assert.ok(content.includes('IAMS_logo2.png'), 'Must document IAMS_logo2.png as primary system mark');
        assert.ok(content.includes('Clear Space Rule'), 'Must specify clear space guidelines');
        assert.ok(content.includes('Minimum Digital Scale'), 'Must specify minimum scale rules');

        // Palette checks
        assert.ok(content.includes('#4A0018'), 'Must document Institutional Maroon (#4A0018)');
        assert.ok(content.includes('#F8A617'), 'Must document Institutional Gold (#F8A617)');
        assert.ok(content.includes('#047857'), 'Must document Evaluator Emerald (#047857)');
        assert.ok(content.includes('#002B5B'), 'Must document Supervisor Navy (#002B5B)');
        assert.ok(content.includes('#7A1E2D'), 'Must document Student Crimson (#7A1E2D)');

        // Loading animation checks
        assert.ok(content.includes('Official Loading Animation System'), 'Must document loading animation system');
        assert.ok(content.includes('IAMSLoading.show'), 'Must document programmatic JavaScript API');
        assert.ok(content.includes('prefers-reduced-motion'), 'Must document accessibility compliance');
    });

    test('docs/branding/assets contains all official IAMS & partner branding marks', () => {
        const requiredAssets = ['IAMS_logo2.png', 'IAMS_logo.jpeg', 'utmlogo.png', 'logoUTM.png'];
        for (const asset of requiredAssets) {
            const assetPath = path.join(ASSETS_DIR, asset);
            assert.ok(fs.existsSync(assetPath), `Asset ${asset} must exist in docs/branding/assets/`);
            const stat = fs.statSync(assetPath);
            assert.ok(stat.size > 0, `Asset ${asset} must not be empty`);
        }
    });

    test('docs/branding/index.html provides interactive branding showcase and live loading demo', () => {
        const indexPath = path.join(DOCS_BRANDING_DIR, 'index.html');
        assert.ok(fs.existsSync(indexPath), 'index.html must exist under docs/branding/');
        const content = fs.readFileSync(indexPath, 'utf-8');

        assert.ok(content.includes('IAMS_logo2.png'), 'Showcase must display IAMS_logo2.png');
        assert.ok(content.includes('IAMS BRAND IDENTITY'), 'Must feature brand header');
        assert.ok(content.includes('triggerFullscreenDemo'), 'Must provide interactive fullscreen demo trigger');
        assert.ok(content.includes('loading.css'), 'Must link loading.css');
        assert.ok(content.includes('loading.js'), 'Must link loading.js');
    });

    test('loading.css implements responsive IAMS animation keyframes, themes, and reduced-motion fallback', () => {
        const cssPath = path.join(STATIC_CSS_DIR, 'loading.css');
        assert.ok(fs.existsSync(cssPath), 'loading.css must exist');
        const css = fs.readFileSync(cssPath, 'utf-8');

        // Core class checks
        assert.ok(css.includes('.iams-loading-screen') || css.includes('#loading-screen'), 'Must define loading screen selector');
        assert.ok(css.includes('.iams-loading-visual'), 'Must define visual container');
        assert.ok(css.includes('.iams-loading-logo'), 'Must style IAMS logo');
        assert.ok(css.includes('.iams-loading-glow'), 'Must define breathing glow aura');
        assert.ok(css.includes('.iams-loading-ring'), 'Must define orbital ring');
        assert.ok(css.includes('.iams-loading-progress'), 'Must define indeterminate shimmer progress bar');

        // Keyframe checks
        assert.ok(css.includes('@keyframes iamsLogoBreathe'), 'Must define logo breathing keyframes');
        assert.ok(css.includes('@keyframes iamsGlowPulse'), 'Must define glow pulsing keyframes');
        assert.ok(css.includes('@keyframes iamsRingSpin'), 'Must define orbital ring spinning keyframes');
        assert.ok(css.includes('@keyframes iamsIndeterminateBar'), 'Must define progress bar keyframes');

        // Theme and accessibility checks
        assert.ok(css.includes('html[data-theme="dark"]'), 'Must support dark theme adaptiveness');
        assert.ok(css.includes('html[data-theme="warm"]'), 'Must support warm theme adaptiveness');
        assert.ok(css.includes('@media (prefers-reduced-motion: reduce)'), 'Must support reduced motion accessibility');
    });

    test('loading.js defines IAMSLoading API, backwards-compatible UTMLoading, and auto-injection', () => {
        const jsPath = path.join(STATIC_JS_DIR, 'loading.js');
        assert.ok(fs.existsSync(jsPath), 'loading.js must exist');
        const js = fs.readFileSync(jsPath, 'utf-8');

        assert.ok(js.includes('IAMSLoading'), 'Must define IAMSLoading controller');
        assert.ok(js.includes('window.IAMSLoading'), 'Must expose IAMSLoading globally');
        assert.ok(js.includes('window.UTMLoading'), 'Must expose UTMLoading alias for backwards compatibility');
        assert.ok(js.includes('IAMS_logo2.png'), 'Must reference IAMS_logo2.png as default logoUrl');
        assert.ok(js.includes('show:'), 'Must provide show() method');
        assert.ok(js.includes('hide:'), 'Must provide hide() method');
        assert.ok(js.includes('setMessage:'), 'Must provide setMessage() method');
        assert.ok(js.includes('createInline:'), 'Must provide createInline() component loader method');
        assert.ok(js.includes('_createScreenElement'), 'Must implement DOM auto-injection helper');
    });

    test('fragments/loading_screen.html provides pre-rendered Thymeleaf fragment with IAMS_logo2.png', () => {
        const fragmentPath = path.join(TEMPLATES_DIR, 'fragments/loading_screen.html');
        assert.ok(fs.existsSync(fragmentPath), 'fragments/loading_screen.html must exist');
        const content = fs.readFileSync(fragmentPath, 'utf-8');

        assert.ok(content.includes('th:fragment="loadingScreen"'), 'Must define loadingScreen fragment');
        assert.ok(content.includes('IAMS_logo2.png'), 'Must link IAMS_logo2.png');
        assert.ok(content.includes('id="loading-screen"'), 'Must define id="loading-screen"');
        assert.ok(content.includes('iams-loading-ring'), 'Must include orbital ring');
        assert.ok(content.includes('iams-loading-progress'), 'Must include progress bar');
    });

    test('loading animation is safely disabled without blocking page navigation', () => {
        const jsPath = path.join(STATIC_JS_DIR, 'loading.js');
        const js = fs.readFileSync(jsPath, 'utf-8');
        assert.ok(js.includes('enablePageTransitions: false'), 'Page transitions must be disabled');
        assert.ok(js.includes('autoInject: false'), 'Auto-inject must be disabled');

        const cssPath = path.join(STATIC_CSS_DIR, 'loading.css');
        const css = fs.readFileSync(cssPath, 'utf-8');
        assert.ok(css.includes('display: none !important'), 'Loading overlay must be hidden with display: none !important');

        const criticalPath = path.join(STATIC_CSS_DIR, 'critical.css');
        const critical = fs.readFileSync(criticalPath, 'utf-8');
        assert.ok(!critical.includes('body > *:not(#loading-screen):not(script) {\n    opacity: 0'),
            'Body content must not be hidden initially on load');
        assert.ok(critical.includes('body > *:not(#loading-screen):not(script) {\n    opacity: 1 !important;\n    visibility: visible !important;\n    pointer-events: auto !important;'),
            'Body content must be immediately visible with pointer-events: auto');
    });

    test('manage-assessments route and security mappings exist for admin accessibility', () => {
        const adminCtrlPath = path.join(__dirname, '../../main/java/com/capstone/adproject/controller/AdminController.java');
        const adminCtrl = fs.readFileSync(adminCtrlPath, 'utf-8');
        assert.ok(adminCtrl.includes('"/manage-assessments"') && adminCtrl.includes('"/assessments/manage"'),
            'AdminController must map /manage-assessments and /assessments/manage');

        const authCtrlPath = path.join(__dirname, '../../main/java/com/capstone/adproject/controller/AuthController.java');
        const authCtrl = fs.readFileSync(authCtrlPath, 'utf-8');
        assert.ok(authCtrl.includes('"/manage-assessments"'),
            'AuthController must map root /manage-assessments');

        const rubricCtrlPath = path.join(__dirname, '../../main/java/com/capstone/adproject/controller/RubricController.java');
        const rubricCtrl = fs.readFileSync(rubricCtrlPath, 'utf-8');
        assert.ok(rubricCtrl.includes('"/manage"') && rubricCtrl.includes('"/manage-assessments"'),
            'RubricController must map /manage and /manage-assessments');

        const secPath = path.join(__dirname, '../../main/java/com/capstone/adproject/config/SecurityConfig.java');
        const sec = fs.readFileSync(secPath, 'utf-8');
        assert.ok(sec.includes('"/manage-assessments"'),
            'SecurityConfig must authorize /manage-assessments');
    });

    test('user-editing-container and user-editing-main prevent horizontal trimming in group assignment', () => {
        const groupCssPath = path.join(STATIC_CSS_DIR, 'group.css');
        const groupCss = fs.readFileSync(groupCssPath, 'utf-8');
        assert.ok(groupCss.includes('user-editing-container') && groupCss.includes('overflow-x: auto'),
            'group.css user-editing-container must have overflow-x: auto');
        assert.ok(groupCss.includes('user-editing-main') && groupCss.includes('min-width: 0'),
            'group.css user-editing-main must have min-width: 0 to prevent flex blowout');
        assert.ok(groupCss.includes('user-editing-main') && groupCss.includes('overflow-x: auto'),
            'group.css user-editing-main must have overflow-x: auto');

        const groupEditCss = fs.readFileSync(path.join(STATIC_CSS_DIR, 'group_edit.css'), 'utf-8');
        assert.ok(groupEditCss.includes('user-editing-main') && groupEditCss.includes('min-width: 0'),
            'group_edit.css user-editing-main must have min-width: 0');

        const manageUserCss = fs.readFileSync(path.join(STATIC_CSS_DIR, 'manageUser.css'), 'utf-8');
        assert.ok(manageUserCss.includes('user-editing-main') && manageUserCss.includes('min-width: 0'),
            'manageUser.css user-editing-main must have min-width: 0');
    });

    test('manage-assessments.html uses null-safe bindings and never serializes raw JPA objects in script blocks', () => {
        const templatePath = path.join(TEMPLATES_DIR, 'manage-assessments.html');
        const template = fs.readFileSync(templatePath, 'utf-8');

        assert.ok(!template.includes('/*[[${newAssessment}]]*/'),
            'manage-assessments.html must not serialize raw newAssessment object in javascript inline block');
        assert.ok(template.includes('currentTitle = /*[[${newAssessment != null && newAssessment.title != null ? newAssessment.title : \'Untitled Assessment\'}]]*/'),
            'manage-assessments.html must use safe primitive property expression for title');
        assert.ok(template.includes('assessment.requiresSubmission == true'),
            'manage-assessments.html must use null-safe boolean comparisons for requiresSubmission');
    });
});
