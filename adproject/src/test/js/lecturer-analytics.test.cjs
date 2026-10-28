const { test } = require('node:test');
const assert = require('node:assert/strict');
const { analyse, comparisonCsv } = require('../../main/resources/static/js/lecturer-analytics.js');
const criteria = [
    { id: 's1', rubricId: 1, rubricName: 'Design', label: 'Evidence', possible: 10, scope: 'GROUP', clo: 1 },
    { id: 's2', rubricId: 1, rubricName: 'Design', label: 'Clarity', possible: 30, scope: 'GROUP', clo: 1 },
    { id: 'r2', rubricId: 2, rubricName: 'Presentation', label: 'Presentation', possible: 20, scope: 'INDIVIDUAL', clo: 2 }
];
const score = (lecturer, target, criterion, earned, overrides = {}) => ({ lecturerId: lecturer, lecturerName: 'Lecturer ' + lecturer, targetId: target, targetName: target, teamId: 10, teamName: 'Team A', studentId: null, criterionId: criterion, earned, status: 'FINAL', ...overrides });
const snapshot = scores => ({ assessmentId: 4, assessmentTitle: 'Review', criteria, scores });
test('sums available rating marks before averaging target percentages equally', () => {
    const data = snapshot([score(1, 'GROUP:10', 's1', 10), score(1, 'GROUP:10', 's2', 15), score(1, 'GROUP:20', 's1', 10, { teamId: 20 })]);
    const r = analyse(data);
    assert.equal(r.targets[0].percentage, 62.5);
    assert.equal(r.lecturers[0].value, 81.25);
    assert.equal(r.mean, 81.25);
});
test('matched gaps compare identical targets and criteria, not unrelated workloads', () => {
    const r = analyse(snapshot([score(1, 'GROUP:10', 's1', 8), score(2, 'GROUP:10', 's1', 6), score(2, 'GROUP:20', 's1', 0, { teamId: 20 }), score(1, 'GROUP:10', 's2', 30)]));
    assert.equal(r.pairs.length, 1); assert.equal(r.pairs[0].value, 20); assert.equal(r.pairs[0].count, 1);
    assert.equal(r.lecturers[0].values.length, 1);
});
test('shared-criteria filter removes unmatched targets and unshared rubric criteria', () => {
    const data = snapshot([score(1, 'GROUP:10', 's1', 8), score(2, 'GROUP:10', 's1', 6), score(2, 'GROUP:20', 's1', 0), score(1, 'GROUP:10', 's2', 30)]);
    const r = analyse(data, { shared: true });
    assert.equal(r.facts.length, 2); assert.equal(r.lecturers[0].value, 80);
    assert.equal(analyse(data, { shared: true, lecturers: ['1'] }).facts.length, 0);
});
test('filters compose across team, lecturer, rubric, CLO, type, student and status', () => {
    const data = snapshot([score(1, 'INDIVIDUAL:1', 'r2', 10, { studentId: 1 }), score(2, 'INDIVIDUAL:1', 'r2', 15, { studentId: 1 }), score(1, 'GROUP:10', 's1', 8), score(1, 'INDIVIDUAL:2', 'r2', 2, { studentId: 2, status: 'SUBMITTED' })]);
    const r = analyse(data, { team: '10', lecturers: ['1'], rubric: '2', clo: '2', scope: 'INDIVIDUAL', student: '1', status: 'FINAL' });
    assert.equal(r.facts.length, 1); assert.equal(r.mean, 50);
    assert.equal(analyse(data, { lecturers: [] }).facts.length, 0);
    assert.equal(analyse(data, { team: '999' }).mean, null);
});
test('group and individual target units are separate and missing lecturers are not zero', () => {
    const r = analyse(snapshot([score(1, 'GROUP:10', 's1', 0), score(2, 'INDIVIDUAL:1', 'r2', 20, { studentId: 1 })]));
    assert.equal(r.teams.length, 2); assert.equal(r.pairs.length, 0);
    assert.equal(r.rubrics[0].lecturers.size, 1); assert.equal(r.lecturers[0].value, 0);
});
test('empty and invalid data cannot produce NaN or fabricated zero scores', () => {
    assert.equal(analyse(snapshot([])).mean, null);
    assert.equal(analyse(snapshot([score(1, 'x', 's1', NaN), score(1, 'x', 'missing', 5), score(1, 'x', 's1', 100)])).facts.length, 0);
});
test('unassigned team is filterable and individual student filter excludes group scores', () => {
    const data = snapshot([score(1, 'INDIVIDUAL:1', 'r2', 10, { studentId: 1, teamId: null }), score(1, 'GROUP:10', 's1', 8)]);
    assert.equal(analyse(data, { team: 'unassigned' }).facts.length, 1);
    assert.equal(analyse(data, { student: '1' }).facts.length, 1);
});
test('export includes all filtered rows, escapes quotes and neutralises formulas', () => {
    const data = snapshot([score(1, 'GROUP:10', 's1', 8, { lecturerName: '=CMD("unsafe")', teamName: '@team' })]);
    const csv = comparisonCsv(data, analyse(data));
    assert.ok(csv.startsWith('\uFEFF')); assert.ok(csv.includes('"\'=CMD(""unsafe"")"')); assert.ok(csv.includes('"\'@team"')); assert.ok(csv.includes('"80.00"'));
    assert.equal(comparisonCsv(data, analyse(data, { lecturers: [] })).split('\r\n').length, 1);
});
test('agreement counts every lecturer pair, inclusively within ten percentage points', () => {
    const r = analyse(snapshot([score(1, 'GROUP:10', 's1', 8), score(2, 'GROUP:10', 's1', 7), score(3, 'GROUP:10', 's1', 5)]));
    assert.equal(r.pairedRatings, 3);
    assert.equal(r.matchingPairs, 1);
    assert.ok(Math.abs(r.agreementRate - 100 / 3) < 1e-9);
    assert.equal(r.sharedCells, 1); assert.equal(r.medianSpread, 30);
    assert.equal(r.pairs[0].points.length, 1);
    assert.equal(r.pairs[0].points[0].x, 80);
    assert.equal(r.pairs[0].points[0].y, 50);
});
test('typical spread weights shared criteria once rather than the number of lecturer pairs', () => {
    const r = analyse(snapshot([score(1, 'GROUP:10', 's1', 8), score(2, 'GROUP:10', 's1', 7), score(3, 'GROUP:10', 's1', 5), score(1, 'GROUP:20', 's1', 5), score(2, 'GROUP:20', 's1', 5)]));
    assert.equal(r.sharedCells, 2); assert.equal(r.medianSpread, 15);
    assert.equal(r.matchingPairs, 2); assert.equal(r.pairedRatings, 4); assert.equal(r.agreementRate, 50);
});
test('box plot quartiles interpolate target percentages and single evaluations stay defined', () => {
    const data = snapshot([score(1, 'GROUP:1', 's1', 0), score(1, 'GROUP:2', 's1', 2), score(1, 'GROUP:3', 's1', 8), score(1, 'GROUP:4', 's1', 10), score(2, 'GROUP:1', 's1', 5)]);
    const r = analyse(data); const a = r.lecturers[0], b = r.lecturers[1];
    assert.equal(a.q1, 15); assert.equal(a.median, 50); assert.equal(a.q3, 85);
    assert.equal(a.low, 0); assert.equal(a.high, 100);
    assert.equal(b.q1, 50); assert.equal(b.median, 50); assert.equal(b.q3, 50);
});
test('new comparison metrics return unavailable, not zero, without any overlapping work', () => {
    const data = snapshot([score(1, 'GROUP:1', 's1', 8), score(2, 'GROUP:2', 's1', 8)]);
    const r = analyse(data); assert.equal(r.agreementRate, null); assert.equal(r.medianSpread, null); assert.equal(r.sharedCells, 0);
    const filtered = analyse(data, { lecturers: [] }); assert.equal(filtered.agreementRate, null); assert.equal(filtered.medianSpread, null);
});
