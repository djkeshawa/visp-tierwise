// load bundle: the deny checks of SVC-11 and SVC-12 (made under BUNDLE-118), which the Java KeyReader also makes.
// A key whose onSourceFailure is deny must have a narrowing strategy (all, intersect or min), and a min key with deny
// must have a min member in its integer type. Each negative case adds one key to the base content, and the digest is
// recomputed, so the deny rule is the only fault. Each control changes only the rule under test.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { codeOf, loadContent } from './support.mjs';
import { changed, content } from './fixtures.mjs';

/** Loads the base content with one extra key, and returns the error code, or null when it loads. */
const loadWithKey = (name, key) => codeOf(() => loadContent(changed((c) => { c.keys[name] = key; })));

// The base content has the file-sourced levels top, mid and the service-sourced level low, then the axis lane.
// A key settable at top and low is settable at a service-sourced level, so it carries onSourceFailure (BUNDLE-59).
const BOOL = { type: { type: 'boolean' } };
const INT = (extra = {}) => ({ type: { type: 'integer', ...extra } });
const SET = { type: { type: 'set', universe: ['u1', 'u2'] } };
const OBJ = {
  type: { type: 'object', fields: { a: { type: 'integer' } } },
};

test('the base content, whose deny keys are all, intersect and min with a min bound, loads', () => {
  assert.equal(codeOf(() => loadContent(content())), null);
});

test('deny on a narrowing strategy with its minimum loads: all, intersect and min (SVC-11, SVC-12)', () => {
  assert.equal(loadWithKey('k.new.all', { ...BOOL, combine: 'all', settableAt: ['top', 'low'], onSourceFailure: 'deny' }), null);
  assert.equal(loadWithKey('k.new.inter', { ...SET, combine: 'intersect', settableAt: ['top', 'low'], onSourceFailure: 'deny' }), null);
  assert.equal(
    loadWithKey('k.new.min', { ...INT({ min: 0 }), combine: 'min', settableAt: ['top', 'low'], onSourceFailure: 'deny' }),
    null,
  );
});

// ---------------------------------------------------------------------------
// SVC-11: deny needs a narrowing strategy
// ---------------------------------------------------------------------------

test('deny on each non-narrowing strategy is rejected with E_BUNDLE_INTEGRITY (SVC-11)', () => {
  const cases = {
    any: { ...BOOL, combine: 'any', settableAt: ['top', 'low'], onSourceFailure: 'deny' },
    union: { ...SET, combine: 'union', settableAt: ['top', 'low'], onSourceFailure: 'deny' },
    max: { ...INT(), combine: 'max', settableAt: ['top', 'low'], onSourceFailure: 'deny' },
    override: { ...INT(), combine: 'override', default: 1, settableAt: ['top', 'low'], onSourceFailure: 'deny' },
    deep_merge: { ...OBJ, combine: 'deep_merge', default: { a: 1 }, settableAt: ['top', 'low'], onSourceFailure: 'deny' },
  };
  for (const [strategy, key] of Object.entries(cases)) {
    assert.equal(loadWithKey(`k.new.${strategy}`, key), 'E_BUNDLE_INTEGRITY', `deny on ${strategy}`);
  }
});

test('skip on each non-narrowing strategy still loads, so only deny is restricted (SVC-11)', () => {
  const cases = {
    any: { ...BOOL, combine: 'any', settableAt: ['top', 'low'], onSourceFailure: 'skip' },
    union: { ...SET, combine: 'union', settableAt: ['top', 'low'], onSourceFailure: 'skip' },
    max: { ...INT(), combine: 'max', settableAt: ['top', 'low'], onSourceFailure: 'skip' },
    override: { ...INT(), combine: 'override', default: 1, settableAt: ['top', 'low'], onSourceFailure: 'skip' },
  };
  for (const [strategy, key] of Object.entries(cases)) {
    assert.equal(loadWithKey(`k.new.${strategy}`, key), null, `skip on ${strategy}`);
  }
});

test('a non-narrowing key with no service-sourced scope has no deny rule to break (SVC-11 applies only to service levels)', () => {
  // settableAt names only file-sourced scopes, so the key carries no onSourceFailure (BUNDLE-59) and loads.
  assert.equal(loadWithKey('k.new.file', { ...INT(), combine: 'override', default: 1, settableAt: ['top', 'mid'] }), null);
  assert.equal(loadWithKey('k.new.file.any', { ...BOOL, combine: 'any', settableAt: ['top', 'mid'] }), null);
});

// ---------------------------------------------------------------------------
// SVC-12: deny under min needs a min member in the integer type
// ---------------------------------------------------------------------------

test('deny under min with an integer type that has no min member is rejected (SVC-12)', () => {
  assert.equal(
    loadWithKey('k.new.min.nobound', { ...INT(), combine: 'min', settableAt: ['top', 'low'], onSourceFailure: 'deny' }),
    'E_BUNDLE_INTEGRITY',
  );
});

test('deny under min with only a max member is still rejected, because the deny value is the min member (SVC-12, SVC-9)', () => {
  assert.equal(
    loadWithKey('k.new.min.maxonly', { ...INT({ max: 10 }), combine: 'min', settableAt: ['top', 'low'], onSourceFailure: 'deny' }),
    'E_BUNDLE_INTEGRITY',
  );
});

test('min without deny does not need a min member (SVC-12 applies only to deny)', () => {
  assert.equal(
    loadWithKey('k.new.min.skip', { ...INT(), combine: 'min', settableAt: ['top', 'low'], onSourceFailure: 'skip' }),
    null,
  );
});

test('min with a min member and deny loads, and a min member of zero is still a bound', () => {
  assert.equal(
    loadWithKey('k.new.min.zero', { ...INT({ min: 0 }), combine: 'min', settableAt: ['top', 'low'], onSourceFailure: 'deny' }),
    null,
  );
});
