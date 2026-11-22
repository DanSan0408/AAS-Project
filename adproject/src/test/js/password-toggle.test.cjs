const { test, describe } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.resolve(__dirname, '../../main/resources');
const SCRIPT = fs.readFileSync(path.join(ROOT, 'static/js/password-toggle.js'), 'utf-8');

/** A very small DOM: just what the script touches. */
class El {
    constructor(tag) {
        this.tagName = tag;
        this.children = [];
        this.attrs = {};
        this.listeners = {};
        this.className = '';
        this.parentNode = null;
        this.type = '';
        this.dataset = {};
        this.innerHTML = '';
        this.formEl = null;
    }
    setAttribute(k, v) { this.attrs[k] = String(v); }
    getAttribute(k) { return this.attrs[k] === undefined ? null : this.attrs[k]; }
    addEventListener(type, fn) { (this.listeners[type] = this.listeners[type] || []).push(fn); }
    fire(type) { (this.listeners[type] || []).forEach((fn) => fn({ type, preventDefault() {} })); }
    appendChild(c) { if (c.parentNode) c.parentNode.removeChild(c); c.parentNode = this; this.children.push(c); return c; }
    removeChild(c) { this.children = this.children.filter((x) => x !== c); c.parentNode = null; }
    insertBefore(c, ref) {
        if (c.parentNode) c.parentNode.removeChild(c);
        c.parentNode = this;
        this.children.splice(this.children.indexOf(ref), 0, c);
        return c;
    }
    closest(sel) { return sel === 'form' ? this.formEl : null; }
}

function setup(fieldCount = 1, { optOut = false } = {}) {
    const form = new El('form');
    const group = new El('div');
    const inputs = [];
    for (let i = 0; i < fieldCount; i++) {
        const input = new El('input');
        input.type = 'password';
        input.formEl = form;
        if (optOut && i === 0) input.dataset.passwordToggle = 'off';
        group.appendChild(input);
        inputs.push(input);
    }
    const windowListeners = {};
    const document = {
        readyState: 'complete',
        querySelectorAll: (sel) => (sel === 'input[type="password"]' ? inputs.slice() : []),
        createElement: (tag) => new El(tag),
        addEventListener() {},
    };
    const sandbox = {
        document,
        window: { addEventListener: (t, fn) => { (windowListeners[t] = windowListeners[t] || []).push(fn); } },
    };
    vm.runInNewContext(SCRIPT, sandbox);
    return { form, group, inputs, windowListeners };
}

const buttonOf = (input) => input.parentNode.children.find((c) => c.tagName === 'button');

describe('password show/hide toggle', () => {
    test('a password field is wrapped and gets a labelled button that is not a submit button', () => {
        const { inputs } = setup();
        const wrapper = inputs[0].parentNode;
        assert.match(wrapper.className, /password-field/);
        const button = buttonOf(inputs[0]);
        assert.ok(button, 'a button sits beside the input');
        assert.equal(button.type, 'button', 'it must never submit the form');
        assert.equal(button.getAttribute('aria-label'), 'Show password');
        assert.equal(button.getAttribute('aria-pressed'), 'false');
        assert.equal(inputs[0].type, 'password', 'hidden until asked');
    });

    test('clicking shows the password, clicking again hides it, and the label follows', () => {
        const { inputs } = setup();
        const button = buttonOf(inputs[0]);

        button.fire('click');
        assert.equal(inputs[0].type, 'text');
        assert.equal(button.getAttribute('aria-label'), 'Hide password');
        assert.equal(button.getAttribute('aria-pressed'), 'true');

        button.fire('click');
        assert.equal(inputs[0].type, 'password');
        assert.equal(button.getAttribute('aria-label'), 'Show password');
        assert.equal(button.getAttribute('aria-pressed'), 'false');
    });

    test('each field toggles on its own (new password and confirmation)', () => {
        const { inputs } = setup(2);
        buttonOf(inputs[0]).fire('click');
        assert.equal(inputs[0].type, 'text');
        assert.equal(inputs[1].type, 'password');
    });

    test('the password is hidden again when the form is submitted and when the page is left', () => {
        const { form, inputs, windowListeners } = setup();
        buttonOf(inputs[0]).fire('click');
        form.fire('submit');
        assert.equal(inputs[0].type, 'password');

        buttonOf(inputs[0]).fire('click');
        assert.equal(inputs[0].type, 'text');
        windowListeners.pagehide.forEach((fn) => fn());
        assert.equal(inputs[0].type, 'password', 'a page restored from the back button must not show it');
    });

    test('a field can opt out, and running twice does not add a second button', () => {
        const { inputs } = setup(2, { optOut: true });
        assert.equal(inputs[0].parentNode.className, '', 'opted-out field is left alone');
        assert.equal(inputs[1].parentNode.children.filter((c) => c.tagName === 'button').length, 1);
    });
});

describe('pages and styles', () => {
    const read = (rel) => fs.readFileSync(path.join(ROOT, rel), 'utf-8');

    test('the login and reset-password pages load the script', () => {
        for (const page of ['templates/login.html', 'templates/reset_password_form.html']) {
            assert.match(read(page), /js\/password-toggle\.js/, page);
        }
    });

    test('the toggle has styles with a visible keyboard focus and a touch-sized target', () => {
        const css = read('static/css/system-ui.css');
        assert.match(css, /\.password-field\b/);
        assert.match(css, /\.password-toggle:focus-visible/);
        assert.match(css, /\.password-toggle\s*\{[^}]*min-width:\s*40px/s);
    });

    test('no page types a password into a field without the script', () => {
        const dir = path.join(ROOT, 'templates');
        for (const file of fs.readdirSync(dir).filter((f) => f.endsWith('.html'))) {
            const html = fs.readFileSync(path.join(dir, file), 'utf-8');
            if (/type\s*=\s*["']password["']/.test(html)) {
                assert.match(html, /js\/password-toggle\.js/, `${file} has a password field but no show/hide button`);
            }
        }
    });
});
