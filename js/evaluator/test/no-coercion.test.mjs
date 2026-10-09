// No coercion before a comparison (RULE-38, NORM-17, NORM-18): values of different types are never equal, a fact
// value of the wrong type is invalid rather than converted, and decimals compare as canonical strings (RULE-37).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { api, codeOf, distUrl, loadContent } from './support.mjs';
import { changed } from './fixtures.mjs';

const { createEvaluator } = api;
const { scalarEquals } = await import(new URL('values.js', distUrl).href);

/** A feature over `whole` with one rule `fact eq value` that allows; otherwise it denies. */
function eqFeature(fact, value) {
  return {
    decidedPer: 'whole',
    returns: { enum: ['ALLOW', 'DENY'] },
    hitPolicy: 'FIRST',
    ifFactMissing: 'DENY',
    bindingSettableAt: ['top'],
    policies: {
      p: {
        settings: {},
        default: 'DENY',
        requiredFacts: [fact],
        rules: [{ id: 'r', name: 'r', when: [{ fact, op: 'eq', value }], then: 'ALLOW' }],
      },
    },
    bindings: { top: { '': { policy: 'p', settings: {} } } },
  };
}

const content = changed((c) => {
  c.features['f.flag'] = eqFeature('parent.flag', true);
  c.features['f.count'] = eqFeature('whole.count', 7);
  c.features['f.price'] = eqFeature('whole.price', '1.5');
  c.features['f.note'] = eqFeature('whole.note', 'true');
  c.features['f.kind'] = eqFeature('whole.kind', 'a');
});
const tw = createEvaluator(loadContent(content), { providers: { low: { fn: async () => ({}), timeoutMs: 50 } } });

function decide(feature, fact, value) {
  return tw.decideSync({}, feature, { facts: { [fact]: value } });
}

test('RULE-38: values of different types are never equal', () => {
  const pairs = [
    [true, 'true'],
    [false, 'false'],
    [true, 1],
    [false, 0],
    [false, ''],
    [7, '7'],
    [1.5, '1.5'],
    [0, '0'],
    ['null', null],
    [0, null],
  ];
  for (const [left, right] of pairs) {
    assert.equal(scalarEquals(left, right), false, `${JSON.stringify(left)} vs ${JSON.stringify(right)}`);
    assert.equal(scalarEquals(right, left), false, `${JSON.stringify(right)} vs ${JSON.stringify(left)}`);
  }
  for (const value of [true, 7, '1.5', 'true']) assert.equal(scalarEquals(value, value), true);
});

test('RULE-38: a fact value of another type is invalid, never coerced to the declared type', () => {
  const cases = [
    ['f.flag', 'parent.flag', 'true'],
    ['f.flag', 'parent.flag', 1],
    ['f.count', 'whole.count', '7'],
    ['f.count', 'whole.count', true],
    ['f.price', 'whole.price', 1.5],
    ['f.note', 'whole.note', true],
    ['f.kind', 'whole.kind', 0],
  ];
  for (const [feature, fact, value] of cases) {
    assert.equal(codeOf(() => decide(feature, fact, value)), 'E_FACTS_INVALID', `${fact} = ${JSON.stringify(value)}`);
  }
  assert.equal(decide('f.flag', 'parent.flag', true).effect, 'ALLOW');
  assert.equal(decide('f.count', 'whole.count', 7).effect, 'ALLOW');
  assert.equal(decide('f.note', 'whole.note', 'true').effect, 'ALLOW');
  assert.equal(decide('f.note', 'whole.note', 'True').effect, 'DENY', 'strings compare exactly');
});

test('RULE-38: decimals compare as canonical strings, and no number is a decimal (RULE-37, TYPE-25)', () => {
  for (const value of ['1.5', '1.50', '+1.5', '01.5', '1.500000']) {
    assert.equal(decide('f.price', 'whole.price', value).effect, 'ALLOW', value);
  }
  for (const value of ['1.05', '15', '-1.5', '1.51']) {
    assert.equal(decide('f.price', 'whole.price', value).effect, 'DENY', value);
  }
  for (const value of [1.5, 15e-1]) {
    assert.equal(codeOf(() => decide('f.price', 'whole.price', value)), 'E_FACTS_INVALID', String(value));
  }
});

test('RULE-38: a bundle whose condition literal has another type than its fact is not loaded', () => {
  const mistyped = [
    ['parent.flag', 'true'],
    ['whole.count', '7'],
    ['whole.price', 15], // a bundle holds no fraction at all (DET-4), and no number is a decimal
    ['whole.note', true],
    ['whole.kind', 1],
  ];
  for (const [fact, value] of mistyped) {
    const bundle = changed((c) => {
      c.features['f.bad'] = eqFeature(fact, value);
    });
    assert.equal(codeOf(() => loadContent(bundle)), 'E_BUNDLE_INTEGRITY', `${fact} eq ${JSON.stringify(value)}`);
  }
});
