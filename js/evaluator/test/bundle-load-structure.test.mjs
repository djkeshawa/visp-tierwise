// load bundle, step 4 of EVAL-14: one test for each structural clause of BUNDLE-118 to BUNDLE-136, covering every
// case that the clause names. No invalid-bundle file holds these faults in v0.1 (ADR 0016), so these tests are their
// enforcers (spec/conformance/enforcers.d/E7.yaml). Each case changes one thing in the valid base content, and the
// digest is recomputed, so a structural check is the only fault.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { api, bundleBytes, codeOf, json } from './support.mjs';
import { changed, content } from './fixtures.mjs';

const { loadBundle } = api;

/** Loads a content object (digest recomputed) and returns the error code, or null when it loads. */
const loadCode = (c) => codeOf(() => loadBundle(bundleBytes(c)));

/** Asserts that each named change of the base content raises E_BUNDLE_INTEGRITY. */
function rejectsEach(cases) {
  for (const [name, change] of cases) {
    assert.equal(loadCode(changed(change)), 'E_BUNDLE_INTEGRITY', name);
  }
}

const p1 = (c) => c.features['f.one'].policies.p1;

test('the base content loads, so each case below has one fault', () => {
  assert.equal(loadCode(content()), null);
});

test('BUNDLE-118: a content member or value outside the forms of BUNDLE-14 to BUNDLE-108, or a bundle-alone rule, raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['an extra content member (BUNDLE-14)', (c) => { c.extra = {}; }],
    ['a missing content member (BUNDLE-15)', (c) => { delete c.entities; }],
    ['an extra hierarchy member', (c) => { c.hierarchy.extra = []; }],
    ['an unknown values source', (c) => { c.hierarchy.scopes.mid.values = 'remote'; }],
    ['an entity without hasMany', (c) => { delete c.entities.part.hasMany; }],
    ['a fact with an extra member', (c) => { c.facts['parent.flag'].extra = 1; }],
    ['an unknown type name', (c) => { c.facts['parent.flag'].type = { type: 'float' }; }],
    ['a key with an extra member', (c) => { c.keys['k.all'].owner = 'x'; }],
    ['an unknown strategy', (c) => { c.keys['k.all'].combine = 'sum'; }],
    ['a feature with an extra member', (c) => { c.features['f.one'].extra = 1; }],
    ['returns with both forms', (c) => { c.features['f.one'].returns = { enum: ['ALLOW'], object: { a: { type: 'integer' } } }; }],
    ['an unknown hit policy', (c) => { c.features['f.one'].hitPolicy = 'ANY'; }],
    ['a policy without rules', (c) => { delete p1(c).rules; }],
    ['a setting with both forms', (c) => { p1(c).settings.s_val.listOf = 'part.tag'; }],
    ['a rule with an extra member', (c) => { p1(c).rules[0].extra = 1; }],
    ['an unknown operator', (c) => { p1(c).rules[0].when[0].op = 'gt'; }],
    ['a binding without settings', (c) => { delete c.features['f.one'].bindings.mid.m1.settings; }],
    ['a null outside an effect', (c) => { c.keys['k.over'].default = null; }],
    // Rules of other clause areas that the bundle alone can show to be broken.
    ['no starting value (MERGE-7)', (c) => { delete c.keys['k.over'].default; delete c.values.top['']['k.over']; }],
    ['an empty settableAt (KEY-7)', (c) => { c.keys['k.all'].settableAt = []; }],
    ['no root binding (BIND-13)', (c) => { delete c.features['f.one'].bindings.top; }],
    ['an entity-graph cycle (ENTITY-14)', (c) => { c.entities.parent.belongsTo = ['whole']; }],
    ['an empty level name (SCOPE-8)', (c) => {
      c.hierarchy = { levels: ['top', ''], axes: [], scopes: { '': { nodes: 'open', values: 'files' } } };
      c.keys = {}; c.values = {}; c.features = {}; c.facts = {}; c.entities = {};
    }],
    ['deny on a non-narrowing strategy (SVC-11)', (c) => { c.keys['k.union'].onSourceFailure = 'deny'; }],
    ['deny under min without a lower bound (SVC-12)', (c) => { delete c.keys['k.min'].type.min; }],
  ]);
});

test('BUNDLE-118: the version values are excluded, because BUNDLE-115 and BUNDLE-137 govern them', () => {
  assert.equal(loadCode(changed((c) => { c.bundleFormatVersion = '2'; })), 'E_BUNDLE_UNSUPPORTED');
  assert.equal(loadCode(changed((c) => { c.specVersion = 1; })), 'E_BUNDLE_UNSUPPORTED');
});

test('BUNDLE-119: a nodes, requiredFacts or when array out of code point order raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['nodes', (c) => { c.hierarchy.scopes.mid.nodes = ['m2', 'm1']; }],
    ['requiredFacts', (c) => { p1(c).requiredFacts = ['part.tag', 'parent.flag', 'whole.kind']; }],
    ['when', (c) => { p1(c).rules[0].when.reverse(); }],
  ]);
});

test('BUNDLE-120: a fact name that the bundle does not declare raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['in a condition', (c) => {
      p1(c).rules[0].when[1] = { fact: 'whole.ghost', op: 'eq', value: 'a' };
      p1(c).requiredFacts = ['parent.flag', 'part.tag', 'whole.ghost'];
    }],
    ['in a valueOf setting', (c) => { p1(c).settings.s_val.valueOf = 'whole.ghost'; }],
    ['in a listOf setting', (c) => { p1(c).settings.s_list.listOf = 'part.ghost'; }],
    ['in requiredFacts', (c) => { p1(c).requiredFacts = ['parent.flag', 'part.ghost', 'part.tag', 'whole.kind']; }],
  ]);
});

test('BUNDLE-121: an entity name that the bundle does not declare raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['in decidedPer', (c) => { c.features['f.one'].decidedPer = 'ghost'; }],
    ['in a fact entity', (c) => { c.facts['ghost.x'] = { entity: 'ghost', type: { type: 'boolean' } }; }],
    ['in belongsTo', (c) => { c.entities.whole.belongsTo = ['ghost', 'parent']; }],
    ['in hasMany', (c) => { c.entities.whole.hasMany = ['ghost', 'part']; }],
  ]);
});

test('BUNDLE-122: a fact whose entity differs from the prefix of its name raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['another declared entity', (c) => { c.facts['parent.flag'].entity = 'part'; }],
    ['a prefix of the entity name', (c) => { c.facts['par.flag'] = { entity: 'parent', type: { type: 'boolean' } }; }],
  ]);
});

test('BUNDLE-123: a binding whose policy the same feature does not declare raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['an undeclared policy', (c) => { c.features['f.one'].bindings.mid.m1.policy = 'p9'; }],
    ['a policy of another feature', (c) => { c.features['f.one'].bindings.mid.m2.policy = 'q'; }],
  ]);
});

test('BUNDLE-124: a binding setting that its policy does not declare raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['an undeclared setting', (c) => { c.features['f.one'].bindings.mid.m1.settings.ghost = 'a'; }],
    ['a setting of another policy', (c) => { c.features['f.one'].bindings.mid.m2.settings.s_val = 'a'; }],
  ]);
});

test('BUNDLE-125: a setting reference that names no setting of the same policy raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['an undeclared setting', (c) => { p1(c).rules[0].when[1].value = '$ghost'; }],
    ['a setting of another policy', (c) => {
      c.features['f.one'].policies.p2.settings.s_two = { valueOf: 'whole.kind', default: 'a' };
      p1(c).rules[0].when[1].value = '$s_two';
    }],
  ]);
});

test('BUNDLE-126: a setting reference where RULE does not allow it, including inside a literal list, raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['a listOf setting as an eq operand', (c) => { p1(c).rules[0].when[1].value = '$s_list'; }],
    ['a valueOf setting as an all_in operand', (c) => { p1(c).rules[1].when[1].value = '$s_val'; }],
    ['a valueOf setting as an in operand', (c) => { p1(c).rules[1].when[1] = { fact: 'part.tag', op: 'in', value: '$s_val' }; }],
    ['a setting of another fact', (c) => {
      p1(c).settings.s_other = { valueOf: 'parent.flag', default: true };
      p1(c).rules[0].when[1].value = '$s_other';
    }],
    ['a reference inside a literal list', (c) => { p1(c).rules[1].when[1].value = ['$s_list']; }],
  ]);
});

test('BUNDLE-127: an undeclared scope name, or a non-level name in bindings or bindingSettableAt, raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['in settableAt', (c) => { c.keys['k.any'].settableAt = ['top', 'mid', 'nowhere']; }],
    ['in bindingSettableAt', (c) => { c.features['f.one'].bindingSettableAt = ['top', 'mid', 'nowhere']; }],
    ['in values', (c) => { c.values.nowhere = { x: { 'k.over': 1 } }; }],
    ['in bindings', (c) => { c.features['f.one'].bindings.nowhere = { x: { policy: 'p1', settings: {} } }; }],
    ['an axis in bindings', (c) => { c.features['f.one'].bindings.lane = { l1: { policy: 'p1', settings: {} } }; }],
    ['an axis in bindingSettableAt', (c) => { c.features['f.one'].bindingSettableAt = ['top', 'mid', 'lane']; }],
  ]);
});

test('BUNDLE-128: a bindingSettableAt that omits the root level or names a service-sourced level raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['no root level', (c) => {
      c.features['f.one'].bindingSettableAt = ['mid'];
    }],
    ['a service-sourced level', (c) => { c.features['f.one'].bindingSettableAt = ['top', 'mid', 'low']; }],
  ]);
});

test('BUNDLE-129: a node key that a closed catalog does not admit, an empty node key under a non-root scope, or an empty node ID raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['a values node outside the catalog', (c) => { c.values.mid.m3 = { 'k.over': 1 }; }],
    ['a bindings node outside the catalog', (c) => { c.features['f.one'].bindings.mid.m9 = { policy: 'p1', settings: {} }; }],
    ['an empty values node key under a level', (c) => { c.values.mid[''] = { 'k.over': 1 }; }],
    ['an empty values node key under an axis', (c) => { c.values.lane[''] = { 'k.over': 1 }; }],
    ['an empty bindings node key under a non-root level', (c) => { c.features['f.one'].bindings.mid[''] = { policy: 'p1', settings: {} }; }],
    ['an empty node ID in a nodes array', (c) => { c.hierarchy.scopes.mid.nodes = ['', 'm1', 'm2']; }],
  ]);
});

test('BUNDLE-130: a values key that is undeclared or not settable at its scope raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['an undeclared key', (c) => { c.values.top[''].ghost = 1; }],
    ['a key not settable at an axis', (c) => { c.values.lane.l1['k.min'] = 1; }],
    ['a key not settable at a level', (c) => { c.values.mid.m1['k.text'] = 'x'; }],
  ]);
});

test('BUNDLE-131: a value invalid for its key type, or a setting default or binding value invalid for its fact type, raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['a value of the wrong type', (c) => { c.values.top['']['k.over'] = '1'; }],
    ['a value above the declared maximum', (c) => { c.values.top['']['k.min'] = 101; }],
    ['an object value with an undeclared field', (c) => { c.values.mid.m1['k.deep'] = { zzz: 1 }; }],
    ['a valueOf default of the wrong type', (c) => { p1(c).settings.s_val.default = 'c'; }],
    ['a listOf default with an invalid element', (c) => { p1(c).settings.s_list.default = ['w']; }],
    ['a binding value of the wrong type', (c) => { c.features['f.one'].bindings.mid.m1.settings.s_val = true; }],
    ['a binding list with an invalid element', (c) => { c.features['f.one'].bindings.mid.m1.settings.s_list = ['x', 'w']; }],
  ]);
});

test('BUNDLE-132: two rules of one policy that share an id raise E_BUNDLE_INTEGRITY', () => {
  rejectsEach([['a repeated id', (c) => { p1(c).rules[1].id = 'r1'; }]]);
  // The same id in two policies is allowed.
  assert.equal(loadCode(changed((c) => { c.features['f.obj'].policies.q.rules[0].id = 'r1'; })), null);
});

test('BUNDLE-133: a nodes array or listOf value that repeats an element raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['nodes', (c) => { c.hierarchy.scopes.mid.nodes = ['m1', 'm1', 'm2']; }],
    ['a listOf default', (c) => { p1(c).settings.s_list.default = ['x', 'x']; }],
    ['a listOf binding value', (c) => { c.features['f.one'].bindings.mid.m1.settings.s_list = ['x', 'x', 'y']; }],
  ]);
});

test('BUNDLE-134: a set value that repeats a member or has a member outside the universe raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['a repeated member', (c) => { c.values.top['']['k.inter'] = ['u3', 'u3']; }],
    ['a member outside the universe', (c) => { c.values.top['']['k.inter'] = ['u9']; }],
    ['a repeated member under union', (c) => { c.values.mid.m1['k.union'] = ['u1', 'u1']; }],
  ]);
});

test('BUNDLE-135: a decimal string that is not in canonical form raises E_BUNDLE_INTEGRITY', () => {
  const withDecimal = (value) => changed((c) => {
    c.keys['k.dec'] = { type: { type: 'decimal' }, combine: 'override', default: '1.5', settableAt: ['top'] };
    c.values.top['']['k.dec'] = value;
  });
  for (const canonical of ['2.5', '-2.5', '0', '10', '0.05']) {
    assert.equal(loadCode(withDecimal(canonical)), null, canonical);
  }
  for (const value of ['2.50', '02.5', '-0', '+2.5', '2.0', '0.0', '-0.0', '00']) {
    assert.equal(loadCode(withDecimal(value)), 'E_BUNDLE_INTEGRITY', value);
  }
  // A key default and a setting default are checked too.
  assert.equal(loadCode(changed((c) => {
    c.keys['k.dec'] = { type: { type: 'decimal' }, combine: 'override', default: '1.50', settableAt: ['top'] };
  })), 'E_BUNDLE_INTEGRITY', 'a key default');
  assert.equal(loadCode(changed((c) => { p1(c).settings.s_price = { valueOf: 'whole.price', default: '1.50' }; })), 'E_BUNDLE_INTEGRITY', 'a setting default');
  assert.equal(loadCode(changed((c) => { p1(c).settings.s_price = { valueOf: 'whole.price', default: '1.5' }; })), null);
});

test('BUNDLE-136: a requiredFacts that is not the distinct rule facts in code point order raises E_BUNDLE_INTEGRITY', () => {
  rejectsEach([
    ['a fact the rules do not use', (c) => { p1(c).requiredFacts = ['parent.flag', 'part.qty', 'part.tag', 'whole.kind']; }],
    ['a fact the rules use is missing', (c) => { p1(c).requiredFacts = ['parent.flag', 'part.tag']; }],
    ['out of order', (c) => { p1(c).requiredFacts = ['whole.kind', 'parent.flag', 'part.tag']; }],
    ['a repeated fact', (c) => { p1(c).requiredFacts = ['parent.flag', 'parent.flag', 'part.tag', 'whole.kind']; }],
    ['a fact for a policy with no rules', (c) => { c.features['f.one'].policies.p2.requiredFacts = ['parent.flag']; }],
  ]);
});

test('FACT-10: a bundle cannot carry the source member of a fact, so the evaluator validates a fact by its type alone', () => {
  // BUNDLE-109 drops the member and BUNDLE-49 rejects it, whatever its text.
  for (const source of ['doc', 'integer between 0 and 5', '']) {
    assert.equal(loadCode(changed((c) => { c.facts['whole.note'].source = source; })), 'E_BUNDLE_INTEGRITY', source);
  }
  const loaded = loadBundle(bundleBytes(content()));
  const tw = api.createEvaluator(loaded, { providers: { low: { fn: () => ({}), timeoutMs: 100 } } });
  // Any string is a valid string value; only the declared type decides.
  for (const note of ['', 'integer between 0 and 5', '{"type":"integer"}']) {
    assert.equal(codeOf(() => tw.decideSync({}, 'f.one', { facts: { 'whole.note': note } })), null, note);
  }
  assert.equal(codeOf(() => tw.decideSync({}, 'f.one', { facts: { 'whole.note': 5 } })), 'E_FACTS_INVALID');
  assert.ok(!json.canonicalJson(content()).includes('"source"'));
});
