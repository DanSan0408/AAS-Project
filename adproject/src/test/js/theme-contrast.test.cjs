const { test, describe } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

/*
 * Reads the colour tokens of the three themes (light, dark, warm) straight from system-ui.css and checks the pairs that
 * pages actually put together: text on surfaces, text on a brand fill, brand colour as text, and status colours on
 * their soft backgrounds. WCAG AA: 4.5:1 for normal text, 3:1 for large text and UI shapes.
 */
const CSS = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/css/system-ui.css'), 'utf-8');

function blockAfter(startIndex) {
    const open = CSS.indexOf('{', startIndex);
    const close = CSS.indexOf('\n}', open);
    return CSS.slice(open + 1, close);
}

function tokensOf(text) {
    const map = {};
    for (const m of text.matchAll(/--([a-z0-9-]+):\s*([^;]+);/g)) {
        map[m[1]] = m[2].trim();
    }
    return map;
}

const lightStart = CSS.lastIndexOf('{', CSS.indexOf('IAMS Standard System Palette: Light Mode'));
const light = tokensOf(blockAfter(lightStart - 1));
const dark = tokensOf(blockAfter(CSS.indexOf('html[data-theme="dark"],\n:root[data-theme="dark"] {') - 1));
const warm = tokensOf(blockAfter(CSS.indexOf('html[data-theme="warm"],\n:root[data-theme="warm"] {') - 1));

function value(theme, name, depth = 0) {
    let v = theme[name] !== undefined ? theme[name] : light[name];
    if (v === undefined) throw new Error(`token --${name} is not defined`);
    const alias = /^var\(--([a-z0-9-]+)\)$/.exec(v);
    if (alias && depth < 8) return value(theme, alias[1], depth + 1);
    return v;
}

function parseColour(text) {
    let m = /^#([0-9a-f]{3})$/i.exec(text);
    if (m) return { r: parseInt(m[1][0] + m[1][0], 16), g: parseInt(m[1][1] + m[1][1], 16), b: parseInt(m[1][2] + m[1][2], 16), a: 1 };
    m = /^#([0-9a-f]{6})$/i.exec(text);
    if (m) return { r: parseInt(m[1].slice(0, 2), 16), g: parseInt(m[1].slice(2, 4), 16), b: parseInt(m[1].slice(4, 6), 16), a: 1 };
    m = /^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+)\s*)?\)$/.exec(text);
    if (m) return { r: +m[1], g: +m[2], b: +m[3], a: m[4] === undefined ? 1 : +m[4] };
    throw new Error(`cannot parse colour "${text}"`);
}

function over(fg, bg) {
    return { r: fg.r * fg.a + bg.r * (1 - fg.a), g: fg.g * fg.a + bg.g * (1 - fg.a), b: fg.b * fg.a + bg.b * (1 - fg.a), a: 1 };
}

function luminance({ r, g, b }) {
    const lin = (c) => { const s = c / 255; return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4; };
    return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

function ratio(theme, fgName, bgName, onto = 'ui-bg-surface') {
    const base = parseColour(value(theme, onto));
    const bg = over(parseColour(value(theme, bgName)), base);
    const fg = over(parseColour(value(theme, fgName)), bg);
    const [hi, lo] = [luminance(fg), luminance(bg)].sort((a, b) => b - a);
    return (hi + 0.05) / (lo + 0.05);
}

const THEMES = { light, dark, warm };
const ROLE_FILLS = { admin: 'ui-brand', lecturer: 'lecturer-primary', student: 'student-primary', supervisor: 'supervisor-primary' };

describe('theme colours meet WCAG AA', () => {
    for (const [name, theme] of Object.entries(THEMES)) {
        describe(`${name} theme`, () => {
            test('body, secondary and muted text are readable on the page and on cards', () => {
                for (const bg of ['ui-bg-canvas', 'ui-bg-surface', 'ui-bg-subtle']) {
                    for (const fg of ['ui-text-primary', 'ui-text-body-color', 'ui-text-secondary', 'ui-text-muted']) {
                        const r = ratio(theme, fg, bg);
                        assert.ok(r >= 4.5, `${name}: --${fg} on --${bg} is ${r.toFixed(2)}:1, needs 4.5`);
                    }
                }
            });

            for (const [role, fill] of Object.entries(ROLE_FILLS)) {
                test(`${role}: text on its brand fill is readable`, () => {
                    const r = ratio(theme, 'ui-on-brand', fill);
                    assert.ok(r >= 4.5, `${name}/${role}: --ui-on-brand on --${fill} is ${r.toFixed(2)}:1, needs 4.5`);
                });
                test(`${role}: its colour is readable as text on a card`, () => {
                    const r = ratio(theme, fill, 'ui-bg-surface');
                    assert.ok(r >= 4.5, `${name}/${role}: --${fill} on card is ${r.toFixed(2)}:1, needs 4.5`);
                });
            }

            for (const status of ['success', 'warning', 'danger', 'info']) {
                test(`${status} text is readable on its soft background`, () => {
                    const r = ratio(theme, `ui-${status}`, `ui-${status}-soft`);
                    assert.ok(r >= 4.5, `${name}: --ui-${status} on --ui-${status}-soft is ${r.toFixed(2)}:1, needs 4.5`);
                });
            }
        });
    }

    test('in the dark theme every role has its own colour', () => {
        const fills = Object.values(ROLE_FILLS).map((token) => value(dark, token).toLowerCase());
        assert.equal(new Set(fills).size, fills.length, `dark role colours repeat: ${fills.join(', ')}`);
    });
});
