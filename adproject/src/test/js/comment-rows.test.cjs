const { test, describe } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const ROOT = path.resolve(__dirname, '../../main/resources');
const SCRIPT = fs.readFileSync(path.join(ROOT, 'static/js/comment-rows.js'), 'utf-8');

/** A very small DOM: only what the script touches. */
class El {
    constructor(cls = '', attrs = {}) {
        this.classes = new Set(cls.split(/\s+/).filter(Boolean));
        this.attrs = { ...attrs };
        this.children = [];
        this.parent = null;
        this.hidden = false;
        this.textContent = '';
        this.scrollHeight = 0;
        this.clientHeight = 0;
        const self = this;
        this.classList = {
            contains: (c) => self.classes.has(c),
            toggle: (c) => { if (self.classes.has(c)) { self.classes.delete(c); return false; } self.classes.add(c); return true; },
        };
    }
    add(child) { child.parent = this; this.children.push(child); return child; }
    setAttribute(k, v) { this.attrs[k] = String(v); }
    getAttribute(k) { return this.attrs[k] === undefined ? null : this.attrs[k]; }
    matches(sel) { return sel.startsWith('.') && this.classes.has(sel.slice(1)); }
    closest(sel) { for (let e = this; e; e = e.parent) { if (e.matches(sel)) return e; } return null; }
    all(sel, out = []) { if (this.matches(sel)) out.push(this); this.children.forEach((c) => c.all(sel, out)); return out; }
    querySelector(sel) { return this.all(sel)[0] || null; }
    querySelectorAll(sel) { return this.all(sel); }
}

function row(kind, { overflow = false } = {}) {
    const r = new El('comment-row', { 'data-kind': kind });
    const main = r.add(new El('comment-row-main'));
    const text = main.add(new El('comment-row-text'));
    text.scrollHeight = overflow ? 200 : 60;
    text.clientHeight = 100;
    const more = main.add(new El('comment-row-more', { 'aria-expanded': 'false' }));
    more.hidden = true;
    more.textContent = 'Read more';
    return r;
}

function setup(rows, { filter = false } = {}) {
    const body = new El('body');
    const block = body.add(new El('assessment-comments-block'));
    rows.forEach((r) => block.add(r));
    let buttons = [];
    let empty = null;
    if (filter) {
        const specs = [['', 'All'], ['self team', 'You'], ['peer', 'Peers']];
        buttons = specs.map(([kinds, label]) => {
            const b = body.add(new El('comment-filter-btn', { 'data-kinds': kinds, 'aria-pressed': kinds === '' ? 'true' : 'false' }));
            b.textContent = label;
            return b;
        });
        empty = body.add(new El('comment-filter-empty'));
        empty.hidden = true;
    }
    const listeners = {};
    const windowListeners = {};
    const document = {
        readyState: 'complete',
        querySelectorAll: (sel) => body.all(sel),
        querySelector: (sel) => body.all(sel)[0] || null,
        addEventListener: (t, fn) => { listeners[t] = fn; },
    };
    const sandbox = {
        document,
        window: { addEventListener: (t, fn) => { windowListeners[t] = fn; }, requestAnimationFrame: (fn) => fn() },
    };
    vm.runInNewContext(SCRIPT, sandbox);
    const click = (target) => listeners.click({ target });
    return { rows, block, buttons, empty, click, windowListeners };
}

const more = (r) => r.querySelector('.comment-row-more');

describe('comment rows: read more', () => {
    test('a short comment has no Read more button, a clamped one has', () => {
        const short = row('peer');
        const long = row('peer', { overflow: true });
        setup([short, long]);
        assert.equal(more(short).hidden, true);
        assert.equal(more(long).hidden, false);
    });

    test('clicking expands the comment and clicking again collapses it, with the label and aria-expanded following', () => {
        const long = row('peer', { overflow: true });
        const { click } = setup([long]);

        click(more(long));
        assert.equal(long.classes.has('is-expanded'), true);
        assert.equal(more(long).getAttribute('aria-expanded'), 'true');
        assert.equal(more(long).textContent, 'Show less');

        click(more(long));
        assert.equal(long.classes.has('is-expanded'), false);
        assert.equal(more(long).getAttribute('aria-expanded'), 'false');
        assert.equal(more(long).textContent, 'Read more');
    });

    test('an expanded comment keeps its button after the window is resized', () => {
        const long = row('peer', { overflow: true });
        const { click, windowListeners } = setup([long]);
        click(more(long));
        long.querySelector('.comment-row-text').scrollHeight = 10; // now it would fit
        windowListeners.resize();
        assert.equal(more(long).hidden, false, 'so the person can collapse it again');
    });
});

describe('comment rows: student filter', () => {
    test('a filter shows only the matching kinds and marks its button pressed', () => {
        const { rows, buttons, click } = setup([row('self'), row('peer'), row('lecturer'), row('team')], { filter: true });

        click(buttons[2]); // Peers
        assert.deepEqual(rows.map((r) => r.hidden), [true, false, true, true]);
        assert.deepEqual(buttons.map((b) => b.getAttribute('aria-pressed')), ['false', 'false', 'true']);

        click(buttons[1]); // You = self + team
        assert.deepEqual(rows.map((r) => r.hidden), [false, true, true, false]);
    });

    test('All shows everything again', () => {
        const { rows, buttons, click } = setup([row('self'), row('peer')], { filter: true });
        click(buttons[2]);
        click(buttons[0]);
        assert.deepEqual(rows.map((r) => r.hidden), [false, false]);
    });

    test('an assessment block with no visible row is hidden, and a filter with no matches says so', () => {
        const { block, empty, buttons, click } = setup([row('self'), row('self')], { filter: true });

        click(buttons[2]); // Peers: nothing matches
        assert.equal(block.hidden, true);
        assert.equal(empty.hidden, false);

        click(buttons[0]);
        assert.equal(block.hidden, false);
        assert.equal(empty.hidden, true);
    });
});

describe('comment rows: pages and styles', () => {
    const read = (rel) => fs.readFileSync(path.join(ROOT, rel), 'utf-8');

    test('the three comment pages load the row styles and script', () => {
        for (const page of ['templates/student_comments.html', 'templates/comment_view.html', 'templates/lecturer_comment_view.html']) {
            const html = read(page);
            assert.match(html, /css\/comment_rows\.css/, page);
            assert.match(html, /js\/comment-rows\.js/, page);
            assert.match(html, /fragments\/comment_rows/, `${page} uses the shared row fragment`);
        }
    });

    test('the row stylesheet uses theme tokens only, so every theme works', () => {
        const css = read('static/css/comment_rows.css').replace(/\/\*[\s\S]*?\*\//g, '');
        assert.equal(/#[0-9a-fA-F]{3,8}\b/.test(css), false, 'no hard-coded hex colours');
        assert.equal(/\brgba?\(/.test(css), false, 'no hard-coded rgb colours');
    });

    test('rows stack on a narrow screen, and the buttons have a visible keyboard focus', () => {
        const css = read('static/css/comment_rows.css');
        assert.match(css, /@media \(max-width: 720px\)[\s\S]*\.comment-row\s*\{[^}]*grid-template-columns:\s*minmax\(0, 1fr\)/);
        assert.match(css, /\.comment-row-more:focus-visible/);
        assert.match(css, /\.comment-filter-btn:focus-visible/);
    });

    test('long comments are clamped, not cut off: the full text is one click away', () => {
        const css = read('static/css/comment_rows.css');
        assert.match(css, /\.comment-row-text\s*\{[^}]*-webkit-line-clamp:\s*4/);
        assert.match(css, /\.comment-row\.is-expanded \.comment-row-text/);
    });
});

describe('google sign-in settings reach production', () => {
    test('the tracked production properties map the Google environment variables (application.properties is local only)', () => {
        const prod = fs.readFileSync(path.join(ROOT, 'application-prod.properties'), 'utf-8');
        assert.match(prod, /app\.google\.client-id=\$\{GOOGLE_CLIENT_ID:\}/);
        assert.match(prod, /app\.google\.client-secret=\$\{GOOGLE_CLIENT_SECRET:\}/);
        assert.match(prod, /app\.google\.redirect-uri=\$\{GOOGLE_REDIRECT_URI:\}/);
    });
});
