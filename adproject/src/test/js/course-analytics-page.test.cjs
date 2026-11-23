const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.resolve(__dirname, '../../main/resources');
const html = fs.readFileSync(path.join(ROOT, 'templates/course_analytics.html'), 'utf-8');
const css = fs.readFileSync(path.join(ROOT, 'static/css/course_analytics.css'), 'utf-8');

test('the outcome target form does not reuse the class of the target marker on the outcome bars', () => {
    // .ca-target is absolutely positioned (the thin line on each bar); on a form it squashes the box into a strip over the page.
    assert.match(css, /\.ca-target\s*\{[^}]*position:\s*absolute/);
    const forms = html.match(/<form[^>]*>/g) || [];
    forms.forEach(tag => assert.doesNotMatch(tag, /class="[^"]*\bca-target\b/, tag));
    assert.match(html, /class="ca-filters ca-clo-form"/);
});

test('the filters, the target box and the downloads are separate forms, so none can submit the others', () => {
    const forms = html.match(/<form[^>]*>/g) || [];
    const methods = forms.map(f => (/method="(\w+)"/.exec(f) || [])[1]);
    assert.deepEqual(methods, ['get', 'post', 'get'], 'filters (get), target (post), download (get)');
    assert.match(forms[1], /clo-target/);
    assert.match(forms[2], /course-analytics\/export/);
});

test('the download buttons keep the outlined look in the dark and warm themes (no general submit-button rule overrides them)', () => {
    const system = fs.readFileSync(path.join(ROOT, 'static/css/system-ui.css'), 'utf-8');
    assert.ok((system.match(/:not\(\.ca-download\)/g) || []).length >= 2, 'excluded from the dark and warm submit-button rules');
});
