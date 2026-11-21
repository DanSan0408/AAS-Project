const { test, describe } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SCRIPT = fs.readFileSync(
    path.resolve(__dirname, '../../main/resources/static/js/existing-user-suggest.js'), 'utf-8');

/** A very small DOM: just what the script touches. */
class El {
    constructor(tag) {
        this.tagName = tag;
        this.children = [];
        this.attrs = {};
        this.listeners = {};
        this._text = '';
        this.value = '';
        this.hidden = false;
        this.className = '';
    }
    set textContent(v) { this._text = v; if (v === '') this.children = []; }
    get textContent() { return this._text + this.children.map((c) => c.textContent).join(''); }
    appendChild(c) { this.children.push(c); return c; }
    setAttribute(k, v) { this.attrs[k] = v; }
    getAttribute(k) { return this.attrs[k] === undefined ? null : this.attrs[k]; }
    addEventListener(type, fn) { this.listeners[type] = fn; }
    find(pred, out = []) { if (pred(this)) out.push(this); this.children.forEach((c) => c.find(pred, out)); return out; }
}

function setup({ idValue = '', responses = [] } = {}) {
    const form = new El('form');
    ['data-suggest-url', 'data-suggest-type', 'data-add-url', 'data-add-field', 'data-noun'].forEach((k, i) => {
        form.attrs[k] = ['/admin/users/suggestions', 'student', '/admin/manage-students/add-existing', 'studentId', 'student'][i];
    });
    const id = new El('input'); id.value = idValue;
    const csrf = new El('input'); csrf.name = '_csrf'; csrf.value = 'token-123';
    const email = new El('input');
    const username = new El('input');
    const panel = new El('div'); panel.hidden = true;

    const timers = [];
    const calls = [];
    const document = {
        querySelector: (sel) => (sel === 'form[data-suggest-url]' ? form : null),
        getElementById: (name) => ({ userSuggestions: panel, email, username }[name] || null),
        createElement: (tag) => new El(tag),
    };
    form.querySelector = (sel) => (sel === 'input[name="id"]' ? id : sel === 'input[name="_csrf"]' ? csrf : null);
    const sandbox = {
        document,
        window: {
            setTimeout: (fn) => { timers.push(fn); return timers.length; },
            clearTimeout: (n) => { if (n) timers[n - 1] = null; },
        },
        fetch: (url) => {
            calls.push(url);
            const body = responses.shift() || [];
            return Promise.resolve({ ok: true, json: () => Promise.resolve(body) });
        },
        AbortController: class { constructor() { this.signal = {}; } abort() {} },
        encodeURIComponent,
        Array, String,
    };
    vm.runInNewContext(SCRIPT, sandbox);
    const type = (field, text) => { field.value = text; field.listeners.input({ target: field }); };
    const flush = async () => { timers.filter(Boolean).forEach((fn) => fn()); await new Promise((r) => setImmediate(r)); };
    return { panel, email, username, type, flush, calls };
}

describe('existing user suggestions while adding a user', () => {
    test('nothing is requested for fewer than three characters', async () => {
        const t = setup();
        t.type(t.email, 'ab');
        await t.flush();
        assert.equal(t.calls.length, 0);
        assert.equal(t.panel.hidden, true);
    });

    test('three characters ask the server and list matches with an Add button', async () => {
        const t = setup({ responses: [[{ id: 7, username: 'Chen Wei', email: 'ch***@uni.edu.my', inThisCourse: false }]] });
        t.type(t.email, 'chen');
        await t.flush();

        assert.equal(t.calls.length, 1);
        assert.match(t.calls[0], /^\/admin\/users\/suggestions\?type=student&q=chen$/);
        assert.equal(t.panel.hidden, false);
        assert.match(t.panel.textContent, /Chen Wei/);
        assert.match(t.panel.textContent, /ch\*\*\*@uni\.edu\.my/);
        const forms = t.panel.find((n) => n.tagName === 'form');
        assert.equal(forms.length, 1);
        assert.equal(forms[0].action, '/admin/manage-students/add-existing');
        assert.equal(forms[0].method, 'post');
        const hidden = forms[0].find((n) => n.tagName === 'input');
        assert.deepEqual(hidden.map((n) => [n.name, n.value]), [['studentId', '7'], ['_csrf', 'token-123']]);
    });

    test('someone already in the course is labelled and cannot be added again', async () => {
        const t = setup({ responses: [[{ id: 8, username: 'Aina', email: 'ai***@uni.edu.my', inThisCourse: true }]] });
        t.type(t.username, 'aina');
        await t.flush();

        assert.match(t.panel.textContent, /Already in this course/);
        assert.equal(t.panel.find((n) => n.tagName === 'form').length, 0);
    });

    test('names are shown as text, never as markup', async () => {
        const t = setup({ responses: [[{ id: 9, username: '<img src=x onerror=alert(1)>', email: 'x@y', inThisCourse: false }]] });
        t.type(t.email, 'img');
        await t.flush();

        assert.equal(t.panel.find((n) => n.tagName === 'img').length, 0);
        assert.match(t.panel.textContent, /<img src=x onerror=alert\(1\)>/);
    });

    test('no matches hides the panel again', async () => {
        const t = setup({ responses: [[{ id: 7, username: 'Chen', email: 'ch***@a.b', inThisCourse: false }], []] });
        t.type(t.email, 'chen');
        await t.flush();
        assert.equal(t.panel.hidden, false);
        t.type(t.email, 'chenx');
        await t.flush();
        assert.equal(t.panel.hidden, true);
    });

    test('an edit form (it carries an id) never asks for suggestions', async () => {
        const t = setup({ idValue: '55' });
        assert.equal(t.email.listeners.input, undefined);
    });
});

describe('Add Student and Add Assessor pages are wired for the hint', () => {
    const TEMPLATES = path.resolve(__dirname, '../../main/resources/templates');
    for (const [file, type, field, addUrl] of [
        ['manage_students.html', 'student', 'studentId', '/admin/manage-students/add-existing'],
        ['manage_lecturers.html', 'lecturer', 'lecturerId', '/admin/manage-lecturers/add-existing'],
    ]) {
        test(`${file} opts in, has the panel and loads the script with the CSP nonce`, () => {
            const html = fs.readFileSync(path.join(TEMPLATES, file), 'utf-8');
            assert.match(html, /data-suggest-url="\/admin\/users\/suggestions"/);
            assert.ok(html.includes(`data-suggest-type="${type}"`));
            assert.ok(html.includes(`data-add-field="${field}"`));
            assert.ok(html.includes(`data-add-url="${addUrl}"`));
            assert.match(html, /id="userSuggestions"[^>]*aria-live="polite"[^>]*hidden/);
            assert.match(html, /existing-user-suggest\.js\}"[^>]*th:attr="nonce=\$\{cspNonce\}"/);
        });
    }
});
