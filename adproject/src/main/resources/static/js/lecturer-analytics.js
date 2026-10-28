(function (global) {
    'use strict';
    const mean = values => values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
    const key = (...parts) => JSON.stringify(parts);
    function quantile(values, q) {
        if (!values.length) return null;
        const sorted = [...values].sort((a, b) => a - b);
        const position = (sorted.length - 1) * q;
        const low = Math.floor(position), fraction = position - low;
        return sorted[low] + (sorted[Math.min(low + 1, sorted.length - 1)] - sorted[low]) * fraction;
    }
    const byName = (a, b) => a.name.localeCompare(b.name, undefined, { numeric: true });
    function aggregate(facts, dimensions) {
        const buckets = new Map();
        facts.forEach(f => {
            const id = dimensions(f);
            if (!buckets.has(id)) buckets.set(id, { ...f, earned: 0, possible: 0, count: 0 });
            const b = buckets.get(id);
            b.earned += f.earned;
            b.possible += f.criterion.possible;
            b.count++;
        });
        return [...buckets.values()].map(b => ({ ...b, percentage: b.earned / b.possible * 100 }));
    }
    function analyse(snapshot, filters = {}) {
        const criteria = new Map(snapshot.criteria.map(c => [c.id, c]));
        const selected = filters.lecturers == null ? null : new Set(filters.lecturers.map(String));
        let facts = snapshot.scores.map(s => ({ ...s, criterion: criteria.get(s.criterionId) })).filter(f => {
            const c = f.criterion;
            return c && Number.isFinite(c.possible) && c.possible > 0 && Number.isFinite(f.earned)
                && f.earned >= 0 && f.earned <= c.possible
                && (!filters.team || (f.teamId == null ? 'unassigned' : String(f.teamId)) === filters.team)
                && (!filters.scope || c.scope === filters.scope)
                && (!filters.rubric || String(c.rubricId) === filters.rubric)
                && (!filters.clo || String(c.clo ?? 'none') === filters.clo)
                && (!filters.student || (c.scope === 'INDIVIDUAL' && String(f.studentId) === filters.student))
                && (!filters.status || f.status === filters.status)
                && (!selected || selected.has(String(f.lecturerId)));
        });
        const cellKey = f => key(f.targetId, f.criterionId);
        if (filters.shared) {
            const shared = new Map();
            facts.forEach(f => {
                const k = cellKey(f);
                if (!shared.has(k)) shared.set(k, new Set());
                shared.get(k).add(f.lecturerId);
            });
            facts = facts.filter(f => shared.get(cellKey(f)).size >= 2);
        }
        const targets = aggregate(facts, f => key(f.lecturerId, f.targetId));
        const breakdown = aggregate(facts, f => key(f.lecturerId, f.targetId, f.criterion.rubricId))
            .sort((a, b) => a.lecturerName.localeCompare(b.lecturerName) || a.targetName.localeCompare(b.targetName) || a.criterion.rubricName.localeCompare(b.criterion.rubricName));
        const lecturerMap = new Map();
        targets.forEach(t => {
            if (!lecturerMap.has(t.lecturerId)) lecturerMap.set(t.lecturerId, { id: t.lecturerId, name: t.lecturerName, values: [] });
            lecturerMap.get(t.lecturerId).values.push(t.percentage);
        });
        const lecturers = [...lecturerMap.values()].sort(byName).map(l => ({ ...l, value: mean(l.values), low: Math.min(...l.values), high: Math.max(...l.values), median: quantile(l.values, 0.5), q1: quantile(l.values, 0.25), q3: quantile(l.values, 0.75) }));
        const cells = new Map();
        facts.forEach(f => {
            const k = cellKey(f);
            if (!cells.has(k)) cells.set(k, []);
            cells.get(k).push(f);
        });
        const pairMap = new Map();
        const spreads = [];
        let matchingPairs = 0, pairedRatings = 0;
        cells.forEach(rows => {
            rows.sort((a, b) => a.lecturerId - b.lecturerId);
            if (rows.length >= 2) {
                const percentages = rows.map(f => f.earned / f.criterion.possible * 100);
                spreads.push(Math.max(...percentages) - Math.min(...percentages));
            }
            for (let i = 0; i < rows.length; i++) for (let j = i + 1; j < rows.length; j++) {
                const a = rows[i], b = rows[j];
                if (a.lecturerId === b.lecturerId) continue;
                const k = key(a.lecturerId, b.lecturerId);
                if (!pairMap.has(k)) pairMap.set(k, { id: k, name: a.lecturerName + ' ↔ ' + b.lecturerName, aName: a.lecturerName, bName: b.lecturerName, gaps: [], points: [] });
                const x = a.earned / a.criterion.possible * 100, y = b.earned / b.criterion.possible * 100;
                const gap = Math.abs(x - y);
                pairMap.get(k).gaps.push(gap);
                pairMap.get(k).points.push({ x, y, target: a.targetName, criterion: a.criterion.label });
                pairedRatings++;
                if (gap <= 10 + 1e-9) matchingPairs++;
            }
        });
        const pairs = [...pairMap.values()].map(p => ({ id: p.id, name: p.name, aName: p.aName, bName: p.bName, points: p.points, value: mean(p.gaps), count: p.gaps.length })).sort((a, b) => b.value - a.value || byName(a, b));
        const teams = new Map();
        targets.forEach(t => {
            const k = key(t.teamId, t.criterion.scope);
            if (!teams.has(k)) teams.set(k, { name: t.teamName + ' · ' + (t.criterion.scope === 'GROUP' ? 'Group' : 'Individual'), lecturers: new Map() });
            const group = teams.get(k);
            if (!group.lecturers.has(t.lecturerId)) group.lecturers.set(t.lecturerId, { id: t.lecturerId, name: t.lecturerName, values: [] });
            group.lecturers.get(t.lecturerId).values.push(t.percentage);
        });
        const teamRows = [...teams.values()].sort(byName).map(t => ({ name: t.name, lecturers: [...t.lecturers.values()].sort(byName).map(l => ({ ...l, value: mean(l.values) })) }));
        const rubricMap = new Map();
        breakdown.forEach(b => {
            const r = b.criterion;
            if (!rubricMap.has(r.rubricId)) rubricMap.set(r.rubricId, { id: r.rubricId, name: r.rubricName, lecturers: new Map() });
            const map = rubricMap.get(r.rubricId).lecturers;
            if (!map.has(b.lecturerId)) map.set(b.lecturerId, []);
            map.get(b.lecturerId).push(b.percentage);
        });
        const rubrics = [...rubricMap.values()].sort(byName);
        return { facts, targets, breakdown, lecturers, pairs, teams: teamRows, rubrics, mean: mean(targets.map(t => t.percentage)), agreementRate: pairedRatings ? matchingPairs / pairedRatings * 100 : null, pairedRatings, matchingPairs, medianSpread: quantile(spreads, 0.5), sharedCells: spreads.length };
    }
    function csvCell(value) {
        let text = String(value ?? '');
        // Keep names beginning with spreadsheet formula characters as literal text.
        if (/^[\s]*[=+@-]/.test(text) || /^[\t\r\n]/.test(text)) text = "'" + text;
        return '"' + text.replace(/"/g, '""') + '"';
    }
    function comparisonCsv(snapshot, result) {
        const rows = [['Assessment', 'Lecturer', 'Team', 'Target', 'Type', 'Rubric', 'Criteria scored', 'Awarded', 'Available', 'Score (%)']];
        result.breakdown.forEach(r => rows.push([snapshot.assessmentTitle, r.lecturerName, r.teamName, r.targetName, r.criterion.scope, r.criterion.rubricName, r.count, r.earned, r.possible, r.percentage.toFixed(2)]));
        return '\uFEFF' + rows.map(row => row.map(csvCell).join(',')).join('\r\n');
    }
    const api = { analyse, comparisonCsv };
    global.IamsLecturerAnalytics = api;
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    if (typeof document === 'undefined') return;

    function boot() {
        const root = document.getElementById('lecturer-analytics');
        if (!root) return;
        const get = id => document.getElementById('analytics-' + id);
        const palette = ['#7a1e2d', '#047857', '#2563eb', '#9333ea', '#b45309', '#0e7490', '#475569', '#be185d'];
        let snapshot = null, result = null, request = null, rowLimit = 100, teamLimit = 8;
        let wanted = new URLSearchParams(location.search);
        const colors = new Map();
        const percent = n => n == null ? '—' : n.toFixed(1) + '%';
        const number = n => Number(n.toFixed(2)).toLocaleString();
        function element(tag, text, className) {
            const node = document.createElement(tag);
            if (text != null) node.textContent = text;
            if (className) node.className = className;
            return node;
        }
        function options(select, items, first) {
            const previous = select.value;
            select.replaceChildren(new Option(first, ''));
            items.forEach(item => select.add(new Option(item.label, String(item.id))));
            if ([...select.options].some(o => o.value === previous)) select.value = previous;
        }
        function readFilters() {
            const filters = {};
            ['team', 'scope', 'rubric', 'clo', 'student', 'status'].forEach(name => { filters[name] = get(name).value; });
            filters.shared = get('shared').checked;
            filters.lecturers = [...get('lecturer-list').querySelectorAll('input:checked')].map(input => input.value);
            return filters;
        }
        function updateStudentOptions() {
            const f = readFilters();
            const students = new Map();
            snapshot.scores.forEach(s => {
                if (s.studentId != null && (!f.team || (s.teamId == null ? 'unassigned' : String(s.teamId)) === f.team)) students.set(String(s.studentId), s.studentName);
            });
            options(get('student'), [...students].map(([id, label]) => ({ id, label })).sort((a, b) => a.label.localeCompare(b.label)), 'All students');
            get('student').disabled = f.scope === 'GROUP';
            if (get('student').disabled) get('student').value = '';
        }
        function populate() {
            options(get('team'), snapshot.teams, 'All teams');
            const rubrics = new Map(snapshot.criteria.map(c => [String(c.rubricId), c.rubricName]));
            options(get('rubric'), [...rubrics].map(([id, label]) => ({ id, label })), 'All rubrics');
            const clos = [...new Set(snapshot.criteria.map(c => c.clo == null ? 'none' : String(c.clo)))].sort((a, b) => Number(a) - Number(b));
            options(get('clo'), clos.map(id => ({ id, label: id === 'none' ? 'No CLO' : 'CLO ' + id })), 'All CLOs');
            const lecturers = new Map(snapshot.scores.map(s => [String(s.lecturerId), s.lecturerName]));
            const list = get('lecturer-list');
            list.replaceChildren(element('legend', 'Lecturers to compare', 'sr-only'));
            colors.clear();
            [...lecturers].sort((a, b) => a[1].localeCompare(b[1])).forEach(([id, name], i) => {
                colors.set(id, palette[i % palette.length]);
                const label = element('label', null, 'analytics-lecturer-choice');
                const input = element('input'); input.type = 'checkbox'; input.value = id;
                input.checked = !wanted.has('lecturer') || wanted.getAll('lecturer').includes(id);
                const dot = element('span', null, 'analytics-legend-dot'); dot.style.backgroundColor = colors.get(id);
                label.append(input, dot, element('span', name)); list.append(label);
            });
            if (!lecturers.size) list.append(element('p', 'No lecturer scores for this assessment.'));
            ['team', 'scope', 'rubric', 'clo', 'status'].forEach(name => {
                const value = wanted.get(name) || '';
                if ([...get(name).options].some(o => o.value === value)) get(name).value = value;
            });
            get('shared').checked = wanted.get('shared') === '1';
            updateStudentOptions();
            const student = wanted.get('student');
            if (!get('student').disabled && [...get('student').options].some(o => o.value === student)) get('student').value = student;
            if (['rubric', 'clo', 'status', 'student', 'shared'].some(name => wanted.get(name))) get('more-filters').open = true;
        }
        function svgNode(tag, attrs = {}, text) {
            const node = document.createElementNS('http://www.w3.org/2000/svg', tag);
            Object.entries(attrs).forEach(([name, value]) => node.setAttribute(name, String(value)));
            if (text != null) node.textContent = text;
            return node;
        }
        function chart(container, rows, unit = '%') {
            container.replaceChildren();
            if (!rows.length) { container.append(element('p', 'No shared evaluations for these lecturers. Select at least two lecturers who assessed the same work.', 'analytics-chart-note')); return; }
            const width = 720, left = 245, right = 80, top = 30, rowHeight = 46, plot = width - left - right;
            const svg = svgNode('svg', { viewBox: `0 0 ${width} ${top + rows.length * rowHeight + 12}`, role: 'img', 'aria-label': rows.map(r => `${r.name}: ${r.value.toFixed(1)} ${unit}`).join('; ') });
            [0, 25, 50, 75, 100].forEach(tick => {
                const x = left + tick / 100 * plot;
                svg.append(svgNode('line', { x1: x, y1: 23, x2: x, y2: top + rows.length * rowHeight, stroke: '#e2e8f0' }), svgNode('text', { x, y: 14, 'text-anchor': 'middle', class: 'analytics-axis' }, tick + (unit === '%' ? '%' : '')));
            });
            rows.forEach((r, i) => {
                const y = top + i * rowHeight;
                const label = svgNode('text', { x: 0, y: y + 18, class: 'analytics-chart-label' }, r.name.length > 31 ? r.name.slice(0, 30) + '…' : r.name);
                label.append(svgNode('title', {}, r.name));
                const bar = svgNode('rect', { x: left, y: y + 3, width: Math.max(0, r.value / 100 * plot), height: 22, rx: 4, fill: r.color || '#7a1e2d' });
                const description = `${r.name}: ${r.value.toFixed(1)}${unit}. ${r.count ?? r.values?.length ?? 0} ${unit === '%' ? 'target evaluations' : 'matched criteria'}.`;
                bar.append(svgNode('title', {}, description));
                svg.append(label, bar, svgNode('text', { x: width - right + 12, y: y + 19, class: 'analytics-chart-value' }, r.value.toFixed(1) + unit));
                if (r.low != null) {
                    const x1 = left + r.low / 100 * plot, x2 = left + r.high / 100 * plot;
                    svg.append(svgNode('line', { x1, y1: y + 30, x2, y2: y + 30, stroke: '#64748b', 'stroke-width': 2 }), svgNode('circle', { cx: x1, cy: y + 30, r: 3, fill: '#64748b' }), svgNode('circle', { cx: x2, cy: y + 30, r: 3, fill: '#64748b' }));
                }
            });
            container.append(svg);
        }
        function teamChart() {
            const box = get('team-chart'); box.replaceChildren();
            result.teams.slice(0, teamLimit).forEach(team => {
                const section = element('section', null, 'analytics-team-section');
                section.append(element('h3', team.name));
                const graph = element('div'); section.append(graph); box.append(section);
                chart(graph, team.lecturers.map(l => ({ ...l, color: colors.get(String(l.id)) })));
            });
            if (teamLimit < result.teams.length) {
                box.append(element('p', `Showing ${teamLimit} of ${result.teams.length} team/type groups.`, 'analytics-chart-note'));
                const more = element('button', 'Show more teams', 'analytics-reset'); more.type = 'button';
                more.addEventListener('click', () => { teamLimit += 8; teamChart(); }); box.append(more);
            }
        }
        function rubricChart() {
            const box = get('rubric-chart');
            const table = element('table', null, 'analytics-table analytics-heatmap');
            table.append(element('caption', 'Mean rubric percentage by lecturer', 'sr-only'));
            const header = element('tr'); header.append(element('th', 'Rubric'));
            result.lecturers.forEach(l => header.append(element('th', l.name)));
            const thead = element('thead'); thead.append(header); table.append(thead);
            const tbody = element('tbody');
            result.rubrics.forEach(r => {
                const tr = element('tr'); const heading = element('th', r.name); heading.scope = 'row'; tr.append(heading);
                result.lecturers.forEach(l => {
                    const values = r.lecturers.get(l.id), value = values ? mean(values) : null;
                    const td = element('td', percent(value));
                    if (value != null) { td.style.backgroundColor = `rgba(74, 0, 24, ${0.04 + value / 100 * 0.16})`; td.title = `${values.length} target evaluations`; }
                    else td.title = 'No scored criteria for this rubric';
                    tr.append(td);
                }); tbody.append(tr);
            });
            table.append(tbody); box.replaceChildren(table);
        }
        function distributionChart() {
            const box = get('distribution-chart'); box.replaceChildren();
            const width = 720, left = 205, plot = 430, top = 35, height = top + result.lecturers.length * 58 + 25;
            const svg = svgNode('svg', { viewBox: `0 0 ${width} ${height}`, role: 'img', 'aria-label': result.lecturers.map(l => `${l.name}: median ${l.median.toFixed(1)}%, middle half ${l.q1.toFixed(1)}% to ${l.q3.toFixed(1)}%, range ${l.low.toFixed(1)}% to ${l.high.toFixed(1)}%, ${l.values.length} evaluations`).join('; ') });
            const x = value => left + value / 100 * plot;
            [0, 25, 50, 75, 100].forEach(tick => svg.append(svgNode('line', { x1: x(tick), x2: x(tick), y1: 25, y2: height - 15, stroke: '#e2e8f0' }), svgNode('text', { x: x(tick), y: 15, 'text-anchor': 'middle', class: 'analytics-axis' }, tick + '%')));
            result.lecturers.forEach((l, i) => {
                const y = top + i * 58 + 17, color = colors.get(String(l.id));
                const label = svgNode('text', { x: 0, y: y + 4, class: 'analytics-chart-label' }, l.name.length > 26 ? l.name.slice(0, 25) + '…' : l.name);
                label.append(svgNode('title', {}, l.name));
                svg.append(label, svgNode('text', { x: 0, y: y + 22, class: 'analytics-axis' }, l.values.length + ' evaluations'));
                svg.append(svgNode('line', { x1: x(l.low), x2: x(l.high), y1: y, y2: y, stroke: color, 'stroke-width': 2 }));
                [l.low, l.high].forEach(value => svg.append(svgNode('line', { x1: x(value), x2: x(value), y1: y - 7, y2: y + 7, stroke: color, 'stroke-width': 2 })));
                const rectangle = svgNode('rect', { x: x(l.q1), y: y - 12, width: Math.max(1, x(l.q3) - x(l.q1)), height: 24, rx: 3, fill: color, 'fill-opacity': 0.2, stroke: color });
                rectangle.append(svgNode('title', {}, `Q1 ${l.q1.toFixed(1)}%; median ${l.median.toFixed(1)}%; Q3 ${l.q3.toFixed(1)}%`));
                svg.append(rectangle, svgNode('line', { x1: x(l.median), x2: x(l.median), y1: y - 12, y2: y + 12, stroke: color, 'stroke-width': 3 }));
            });
            box.append(svg, element('p', 'Box: middle 50% · Centre line: median · Whiskers: minimum and maximum. Quartiles use linear interpolation; a single evaluation is shown as one line.', 'analytics-chart-note'));
        }
        function alignmentChart() {
            const box = get('alignment-chart'), picker = get('pair');
            const previous = picker.value;
            picker.replaceChildren();
            result.pairs.forEach(p => picker.add(new Option(p.name, p.id)));
            if (result.pairs.some(p => p.id === previous)) picker.value = previous;
            picker.disabled = !result.pairs.length;
            box.replaceChildren();
            const pair = result.pairs.find(p => p.id === picker.value);
            if (!pair) { box.append(element('p', 'Select two or more lecturers with scores for the same target and criterion to see their rating alignment.', 'analytics-chart-note')); return; }
            const width = 640, height = 420, left = 80, top = 20, plotWidth = 500, plotHeight = 310;
            const x = v => left + v / 100 * plotWidth, y = v => top + plotHeight - v / 100 * plotHeight;
            const svg = svgNode('svg', { viewBox: `0 0 ${width} ${height}`, role: 'img', 'aria-label': `${pair.name}: ${pair.count} paired criterion ratings. Points on the diagonal are equal ratings; points above it indicate a higher rating from ${pair.bName}.` });
            [0, 25, 50, 75, 100].forEach(tick => {
                svg.append(svgNode('line', { x1: x(tick), x2: x(tick), y1: top, y2: top + plotHeight, stroke: '#e2e8f0' }), svgNode('line', { x1: left, x2: left + plotWidth, y1: y(tick), y2: y(tick), stroke: '#e2e8f0' }));
                svg.append(svgNode('text', { x: x(tick), y: top + plotHeight + 22, 'text-anchor': 'middle', class: 'analytics-axis' }, tick + '%'), svgNode('text', { x: left - 10, y: y(tick) + 4, 'text-anchor': 'end', class: 'analytics-axis' }, tick + '%'));
            });
            svg.append(svgNode('line', { x1: x(0), x2: x(100), y1: y(0), y2: y(100), stroke: '#94a3b8', 'stroke-dasharray': '6 5', 'stroke-width': 2 }));
            const visible = pair.points.slice(0, 2000);
            visible.forEach(p => {
                const dot = svgNode('circle', { cx: x(p.x), cy: y(p.y), r: 5, fill: Math.abs(p.x - p.y) <= 10 + 1e-9 ? '#047857' : '#7a1e2d', 'fill-opacity': 0.55, stroke: '#fff', 'stroke-width': 0.7 });
                dot.append(svgNode('title', {}, `${p.target} / ${p.criterion}: ${pair.aName} ${p.x.toFixed(1)}%, ${pair.bName} ${p.y.toFixed(1)}%`)); svg.append(dot);
            });
            svg.append(svgNode('text', { x: left + plotWidth / 2, y: height - 25, 'text-anchor': 'middle', class: 'analytics-chart-label' }, pair.aName.length > 45 ? pair.aName.slice(0, 44) + '…' : pair.aName));
            const label = svgNode('text', { transform: `translate(20 ${top + plotHeight / 2}) rotate(-90)`, 'text-anchor': 'middle', class: 'analytics-chart-label' }, pair.bName.length > 38 ? pair.bName.slice(0, 37) + '…' : pair.bName);
            svg.append(label);
            box.append(svg, element('p', `${pair.count} matched ratings · Green: within 10 pp · Maroon: more than 10 pp. Overlapping points can represent several criteria.${visible.length < pair.count ? ' Showing the first 2,000 points; metrics use every matched rating.' : ''}`, 'analytics-chart-note'));
        }
        function breakdownTable() {
            const tbody = get('breakdown'); tbody.replaceChildren();
            result.breakdown.slice(0, rowLimit).forEach(r => {
                const tr = element('tr');
                [r.lecturerName, r.criterion.scope === 'GROUP' ? r.targetName : `${r.teamName} / ${r.targetName}`, r.criterion.scope === 'GROUP' ? 'Group' : 'Individual', r.criterion.rubricName, r.count, `${number(r.earned)} / ${number(r.possible)}`, percent(r.percentage)].forEach(text => tr.append(element('td', String(text))));
                tbody.append(tr);
            });
            get('row-count').textContent = `${result.breakdown.length} rows`;
            get('show-more').hidden = rowLimit >= result.breakdown.length;
        }
        function render() {
            if (!snapshot) return;
            const filters = readFilters();
            result = analyse(snapshot, filters);
            const selected = filters.lecturers.length;
            const total = get('lecturer-list').querySelectorAll('input').length;
            get('lecturer-label').textContent = selected === total ? `All lecturers (${total})` : `${selected} lecturers selected`;
            get('content').hidden = false;
            get('empty').hidden = !!result.facts.length;
            get('visuals').hidden = !result.facts.length;
            get('export').disabled = !result.facts.length;
            get('kpi-lecturers').textContent = result.lecturers.length;
            get('kpi-targets').textContent = new Set(result.targets.map(t => t.targetId)).size;
            get('kpi-mean').textContent = percent(result.mean);
            get('kpi-gap').textContent = result.pairs.length ? result.pairs[0].value.toFixed(1) + ' pp' : '—';
            get('kpi-agreement').textContent = percent(result.agreementRate);
            get('kpi-spread').textContent = result.medianSpread == null ? '—' : result.medianSpread.toFixed(1) + ' pp';
            get('agreement-sample').textContent = result.pairedRatings ? `${result.matchingPairs} of ${result.pairedRatings} paired ratings within 10 pp` : 'No matched ratings in this selection';
            get('spread-sample').textContent = result.sharedCells ? `Across ${result.sharedCells} shared target–criterion combinations` : 'No criteria scored by multiple lecturers';
            get('status-message').textContent = `${snapshot.assessmentTitle} · ${result.targets.length} lecturer–target evaluations · ${result.facts.length} scored criteria. Draft and peer/self evaluations excluded.`;
            get('excluded-note').textContent = snapshot.excludedRows ? `${snapshot.excludedRows} stored score rows were excluded because their criteria, scores, status or current team could not be used for this comparison.` : '';
            if (result.facts.length) {
                chart(get('average-chart'), result.lecturers.map(l => ({ ...l, color: colors.get(String(l.id)) })));
                chart(get('gap-chart'), result.pairs, ' pp'); teamChart(); rubricChart(); distributionChart(); alignmentChart(); breakdownTable();
            }
            const params = new URLSearchParams(); params.set('assessmentId', get('assessment').value);
            ['team', 'scope', 'rubric', 'clo', 'student', 'status'].forEach(name => { if (filters[name]) params.set(name, filters[name]); });
            if (filters.shared) params.set('shared', '1');
            if (selected !== total) (selected ? filters.lecturers : ['none']).forEach(id => params.append('lecturer', id));
            history.replaceState(null, '', location.pathname + '?' + params);
            wanted = params;
        }
        async function load() {
            if (request) request.abort();
            const controller = new AbortController(); request = controller;
            const assessmentId = get('assessment').value;
            snapshot = null; result = null;
            get('error').hidden = true; get('content').hidden = true; get('export').disabled = true;
            if (!assessmentId) { root.removeAttribute('aria-busy'); snapshot = null; get('status-message').textContent = 'No assessments are available in the active course. Create or select a course assessment to start comparing lecturer evaluations.'; return; }
            root.setAttribute('aria-busy', 'true'); get('status-message').textContent = 'Loading lecturer evaluations…';
            try {
                const url = new URL(root.dataset.endpoint, location.href); url.searchParams.set('assessmentId', assessmentId);
                const response = await fetch(url, { signal: controller.signal, headers: { Accept: 'application/json' }, cache: 'no-store' });
                if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw new Error('Unable to load lecturer evaluations. Your session or active course may have changed. Refresh the page or try again.');
                const data = await response.json();
                if (!Array.isArray(data.criteria) || !Array.isArray(data.scores) || !Array.isArray(data.teams)) throw new Error('The analytics response was incomplete. Please try again.');
                if (controller.signal.aborted) return;
                snapshot = data; rowLimit = 100; teamLimit = 8; populate(); render();
            } catch (error) {
                if (controller.signal.aborted) return;
                snapshot = null; result = null;
                get('status-message').textContent = 'Comparison unavailable.';
                get('error-text').textContent = error.message;
                get('error').hidden = false;
            } finally { if (request === controller) root.removeAttribute('aria-busy'); }
        }
        get('filters').addEventListener('submit', e => e.preventDefault());
        get('assessment').addEventListener('change', () => { wanted = new URLSearchParams(); load(); });
        get('filters').addEventListener('change', e => {
            if (e.target === get('assessment') || !snapshot) return;
            if (e.target === get('scope') || e.target === get('team')) updateStudentOptions();
            rowLimit = 100; teamLimit = 8; render();
        });
        ['all', 'no'].forEach(action => get(action + '-lecturers').addEventListener('click', () => {
            get('lecturer-list').querySelectorAll('input').forEach(input => { input.checked = action === 'all'; }); render();
        }));
        get('reset').addEventListener('click', () => {
            ['team', 'scope', 'rubric', 'clo', 'student', 'status'].forEach(name => { get(name).value = ''; });
            get('shared').checked = false;
            get('lecturer-list').querySelectorAll('input').forEach(input => { input.checked = true; });
            if (snapshot) { updateStudentOptions(); rowLimit = 100; teamLimit = 8; render(); }
        });
        get('retry').addEventListener('click', load);
        get('pair').addEventListener('change', alignmentChart);
        get('print').addEventListener('click', () => { if (result) { rowLimit = result.breakdown.length; teamLimit = result.teams.length; breakdownTable(); teamChart(); } global.print(); });
        get('show-more').addEventListener('click', () => { rowLimit += 100; breakdownTable(); });
        get('export').addEventListener('click', () => {
            if (!snapshot || !result || !result.facts.length) return;
            const url = URL.createObjectURL(new Blob([comparisonCsv(snapshot, result)], { type: 'text/csv;charset=utf-8;' }));
            const link = element('a'); link.href = url; link.download = `lecturer-comparison-${snapshot.assessmentId}.csv`; link.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        });
        root.querySelectorAll('.analytics-chart, .analytics-table-wrap').forEach(box => {
            box.tabIndex = 0;
            box.setAttribute('aria-label', box.closest('.analytics-panel')?.querySelector('h2')?.textContent || 'Comparison chart');
        });
        const picker = root.querySelector('.analytics-lecturer-picker');
        document.addEventListener('click', e => { if (!picker.contains(e.target)) picker.open = false; });
        picker.addEventListener('keydown', e => { if (e.key === 'Escape') { picker.open = false; picker.querySelector('summary').focus(); } });
        load();
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
    else boot();
})(typeof window !== 'undefined' ? window : globalThis);
