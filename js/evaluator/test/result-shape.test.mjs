// The shape of a result is an engine guarantee: no evaluator option, preset or project setting changes it (PROV-2).
// A serialised result is an unordered JSON object, so only member order may differ between equal results (PROV-85).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { api, codeOf, json, loadContent, mismatches } from './support.mjs';
import { changed, content } from './fixtures.mjs';

const { createEvaluator } = api;
const loaded = loadContent(content());
const DECISION_MEMBERS = ['binding', 'bundleVersion', 'effect', 'feature', 'policy', 'reason', 'skippedScopes', 'trace'];
const CONTEXTS = [{}, { levels: { mid: 'm1' } }, { levels: { mid: 'm2', low: 'n1' }, axes: { lane: 'l9' } }];
const DECISIONS = [
  ['f.one', { facts: { 'parent.flag': true, 'whole.kind': 'b' }, children: { part: [{ 'part.tag': 'x' }] } }],
  ['f.one', {}],
  ['f.obj', { facts: { 'parent.flag': true } }],
  ['f.null', { facts: { 'parent.flag': false } }],
];

/** Evaluators over one bundle that differ only in their options. */
function variants() {
  const fast = async () => ({ 'k.min': 3, 'k.text': 'p' });
  const slow = () => new Promise((resolve) => setTimeout(() => resolve({ 'k.min': 3 }), 30));
  return [
    createEvaluator(loaded, { providers: { low: { fn: fast, timeoutMs: 200 } } }),
    createEvaluator(loaded, { providers: { low: { fn: fast, timeoutMs: 1 } }, listener: () => {} }),
    createEvaluator(loaded, {
      providers: { low: { fn: slow, timeoutMs: 5 } },
      listener: () => {
        throw new Error('listener failure');
      },
    }),
    createEvaluator(loaded, { providers: { low: { fn: () => Promise.reject(new Error('down')), timeoutMs: 9999 } } }),
  ];
}

test('PROV-2: an evaluator option leaves the member set of a Decision unchanged', async () => {
  for (const tw of variants()) {
    for (const context of CONTEXTS) {
      for (const [feature, facts] of DECISIONS) {
        assert.deepEqual(Object.keys(tw.decideSync(context, feature, facts)).sort(), DECISION_MEMBERS);
        assert.deepEqual(Object.keys(await tw.decide(context, feature, facts)).sort(), DECISION_MEMBERS);
      }
    }
  }
  const [first, ...others] = variants();
  for (const tw of others) {
    for (const context of CONTEXTS) {
      for (const [feature, facts] of DECISIONS) {
        assert.deepEqual(tw.decideSync(context, feature, facts), first.decideSync(context, feature, facts));
      }
    }
  }
});

test('PROV-2: an evaluator option leaves the member set of a Resolved result unchanged', async () => {
  const evaluators = variants();
  for (const key of Object.keys(content().keys)) {
    for (const context of CONTEXTS) {
      const shapes = [];
      for (const tw of evaluators) {
        shapes.push(Object.keys(await tw.resolve(context, key)).sort());
        shapes.push(Object.keys(tw.resolveSync({ levels: { mid: 'm1' } }, key)).sort());
      }
      const asyncShapes = shapes.filter((_, index) => index % 2 === 0);
      const syncShapes = shapes.filter((_, index) => index % 2 === 1);
      for (const shape of asyncShapes) assert.deepEqual(shape, asyncShapes[0], key);
      for (const shape of syncShapes) assert.deepEqual(shape, syncShapes[0], key);
    }
  }
});

test('PROV-2: a bundle cannot carry a preset or a project setting, so neither can reach a result', () => {
  for (const member of ['presets', 'settings', 'lint', 'naming', 'resultShape']) {
    const bundle = changed((c) => {
      c[member] = {};
    });
    assert.equal(codeOf(() => loadContent(bundle)), 'E_BUNDLE_INTEGRITY', member);
  }
  const withPresetOnKey = changed((c) => {
    c.keys['k.all'].preset = 'x';
  });
  assert.equal(codeOf(() => loadContent(withPresetOnKey)), 'E_BUNDLE_INTEGRITY');
  const withSettingOnFeature = changed((c) => {
    c.features['f.one'].provenance = false;
  });
  assert.equal(codeOf(() => loadContent(withSettingOnFeature)), 'E_BUNDLE_INTEGRITY');
});

/** A deep copy whose object members are in reverse order; array elements keep their order. */
function reversed(value) {
  if (Array.isArray(value)) return value.map(reversed);
  if (typeof value !== 'object' || value === null) return value;
  return Object.fromEntries(Object.entries(value).reverse().map(([name, member]) => [name, reversed(member)]));
}

test('PROV-85: a serialised result compares equal when only the order of its object members differs', async () => {
  const tw = variants()[0];
  const results = [
    tw.decideSync({ levels: { mid: 'm1' } }, ...DECISIONS[0]),
    tw.decideSync({ axes: { lane: 'l9' } }, ...DECISIONS[2]),
    await tw.resolve({ levels: { mid: 'm1', low: 'n1' }, axes: { lane: 'l1' } }, 'k.deep'),
    tw.resolveSync({ axes: { lane: 'l9' } }, 'k.over'),
  ];
  for (const result of results) {
    const text = JSON.stringify(result);
    const reordered = JSON.stringify(reversed(result));
    assert.notEqual(text, reordered, 'the two serialisations differ in member order');
    const a = JSON.parse(text);
    const b = JSON.parse(reordered);
    assert.deepEqual(a, b);
    assert.deepEqual(json.canonicalBytes(a), json.canonicalBytes(b));
    assert.deepEqual(mismatches(a, b, true), [], 'the conformance matcher compares members by name');
    assert.deepEqual(mismatches(b, a, true), []);
  }
  // Array order stays significant: reversing the elements of an array is a different result.
  const noMatch = { facts: { 'parent.flag': false, 'whole.kind': 'a' }, children: { part: [{ 'part.tag': 'z' }] } };
  const decision = JSON.parse(JSON.stringify(tw.decideSync({}, 'f.one', noMatch)));
  assert.equal(decision.trace.length, 2);
  const swapped = { ...decision, trace: [...decision.trace].reverse() };
  assert.notDeepEqual(mismatches(decision, swapped, true), []);
});
