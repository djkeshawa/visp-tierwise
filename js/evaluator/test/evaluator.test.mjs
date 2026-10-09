// create evaluator, resolve and decide over a small bundle: the options (EVAL-15, EVAL-28), the check order
// (EVAL-16 to EVAL-20), providers and their outcomes (section 6), the listener (EVAL-41 to EVAL-45), result shapes
// (section 9) and the immutability rules (EVAL-46 to EVAL-52).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { api, codeOf, codeOfAsync, loadContent } from './support.mjs';
import { bundle, changed } from './fixtures.mjs';

const { createEvaluator } = api;
const loaded = bundle();

/** A provider that records its calls and returns what `reply` computes. */
function provider(reply = () => ({})) {
  const calls = [];
  const fn = async (node) => {
    calls.push(node);
    return reply(node);
  };
  return { fn, calls };
}

/** An evaluator over the base bundle with a provider for its service-sourced level. */
function evaluator(reply, extra = {}) {
  const p = provider(reply);
  const events = [];
  const tw = createEvaluator(loaded, {
    providers: { low: { fn: p.fn, timeoutMs: 200 } },
    listener: (event) => events.push(event),
    ...extra,
  });
  return { tw, calls: p.calls, events };
}

const MID_LANE = { levels: { mid: 'm1' }, axes: { lane: 'l1' } };

// ---------------------------------------------------------------------------
// create evaluator
// ---------------------------------------------------------------------------

test('createEvaluator is synchronous, calls no provider and emits one load event (EVAL-5, EVAL-6, EVAL-43)', () => {
  const { tw, calls, events } = evaluator();
  assert.ok(!(tw instanceof Promise));
  assert.equal(calls.length, 0);
  assert.deepEqual(events, [{ type: 'BUNDLE_LOADED', bundleVersion: loaded.bundleVersion }]);
  assert.equal(tw.bundleVersion, loaded.bundleVersion);
  assert.ok(Object.isFrozen(tw), 'the evaluator is fixed when it is created (EVAL-48)');
});

test('a missing provider raises E_PROVIDER_MISSING (SVC-14, EVAL-26)', () => {
  assert.equal(codeOf(() => createEvaluator(loaded)), 'E_PROVIDER_MISSING');
  assert.equal(codeOf(() => createEvaluator(loaded, {})), 'E_PROVIDER_MISSING');
  assert.equal(codeOf(() => createEvaluator(loaded, { providers: {} })), 'E_PROVIDER_MISSING');
});

test('options that are present but not an object raise E_EVALUATOR_OPTIONS (EVAL-28)', () => {
  for (const options of [null, 5, 'x', true, [], () => ({}), [{ providers: {} }]]) {
    assert.equal(codeOf(() => createEvaluator(loaded, options)), 'E_EVALUATOR_OPTIONS', String(options));
  }
});

test('unknown options and malformed members raise E_EVALUATOR_OPTIONS (EVAL-28)', () => {
  const ok = { fn: async () => ({}), timeoutMs: 10 };
  for (const options of [
    { provider: { low: ok } },
    { providers: null },
    { providers: [] },
    { providers: 'x' },
    { providers: { low: ok }, listener: 5 },
    { providers: { low: ok }, listener: null },
    { providers: { low: ok }, extra: 1 },
  ]) {
    assert.equal(codeOf(() => createEvaluator(loaded, options)), 'E_EVALUATOR_OPTIONS', JSON.stringify(options));
  }
});

test('a provider registration needs a function and a positive integer timeout (SVC-38, SVC-48)', () => {
  const fn = async () => ({});
  for (const registration of [
    fn, // a bare function has no timeout (SVC-48)
    {},
    { fn },
    { timeoutMs: 10 },
    { fn, timeoutMs: 0 },
    { fn, timeoutMs: -1 },
    { fn, timeoutMs: 1.5 },
    { fn, timeoutMs: '10' },
    { fn, timeoutMs: NaN },
    { fn, timeoutMs: Infinity },
    { fn, timeoutMs: null },
    { fn, timeoutMs: 2 ** 53 },
    { fn: 'x', timeoutMs: 10 },
    { fn, timeoutMs: 10, retries: 3 },
    null,
    5,
  ]) {
    assert.equal(codeOf(() => createEvaluator(loaded, { providers: { low: registration } })), 'E_EVALUATOR_OPTIONS');
  }
  assert.equal(codeOf(() => createEvaluator(loaded, { providers: { low: { fn, timeoutMs: 1 } } })), null);
  assert.equal(codeOf(() => createEvaluator(loaded, { providers: { low: { fn, timeoutMs: 2 ** 31 + 5 } } })), null);
});

test('a provider for a level that is undeclared or not service-sourced raises E_EVALUATOR_OPTIONS (EVAL-27)', () => {
  const ok = { fn: async () => ({}), timeoutMs: 10 };
  for (const level of ['mid', 'top', 'lane', 'nowhere', 'constructor', '__proto__']) {
    assert.equal(codeOf(() => createEvaluator(loaded, { providers: { low: ok, [level]: ok } })), 'E_EVALUATOR_OPTIONS', level);
  }
});

test('E_EVALUATOR_OPTIONS takes precedence over E_PROVIDER_MISSING (EVAL-15)', () => {
  const ok = { fn: async () => ({}), timeoutMs: 10 };
  assert.equal(codeOf(() => createEvaluator(loaded, { providers: { lwo: ok } })), 'E_EVALUATOR_OPTIONS');
  assert.equal(codeOf(() => createEvaluator(loaded, { providers: { low: { fn: ok.fn } } })), 'E_EVALUATOR_OPTIONS');
});

test('a listener that throws is ignored, also at creation (EVAL-44)', async () => {
  const tw = createEvaluator(loaded, {
    providers: { low: { fn: async () => { throw new Error('down'); }, timeoutMs: 50 } },
    listener: () => {
      throw new Error('listener failure');
    },
  });
  const result = await tw.resolve({ levels: { mid: 'm1', low: 'n' } }, 'k.all');
  assert.equal(result.value, false);
});

test('an asynchronous listener that rejects is ignored', async () => {
  const unhandled = [];
  const record = (reason) => unhandled.push(reason);
  process.on('unhandledRejection', record);
  try {
    const tw = createEvaluator(loaded, {
      providers: { low: { fn: async () => { throw new Error('down'); }, timeoutMs: 50 } },
      listener: async () => {
        throw new Error('async listener failure');
      },
    });
    await tw.resolve({ levels: { low: 'n' } }, 'k.all');
    await new Promise((resolve) => setTimeout(resolve, 20));
    assert.deepEqual(unhandled, []);
  } finally {
    process.off('unhandledRejection', record);
  }
});

test('a bundle that loadBundle did not return is outside the contract', () => {
  assert.throws(() => createEvaluator({ bundleVersion: loaded.bundleVersion }, { providers: {} }), TypeError);
  assert.throws(() => createEvaluator(null), TypeError);
  assert.throws(() => createEvaluator(undefined), TypeError);
});

// ---------------------------------------------------------------------------
// Context validation (section 2.7 to 2.10)
// ---------------------------------------------------------------------------

const badContexts = [
  ['null', null, 'E_CONTEXT_INVALID'],
  ['a list', [], 'E_CONTEXT_INVALID'],
  ['a string', 'x', 'E_CONTEXT_INVALID'],
  ['a number', 5, 'E_CONTEXT_INVALID'],
  ['an instance of a class', new (class Context {})(), 'E_CONTEXT_INVALID'],
  ['an unknown member', { levels: {}, extra: {} }, 'E_CONTEXT_INVALID'],
  ['levels that is null', { levels: null }, 'E_CONTEXT_INVALID'],
  ['axes that is a list', { axes: [] }, 'E_CONTEXT_INVALID'],
  ['a node ID that is a number', { levels: { mid: 5 } }, 'E_CONTEXT_INVALID'],
  ['a node ID that is null', { levels: { mid: null } }, 'E_CONTEXT_INVALID'],
  ['a node ID that is an object', { levels: { mid: { id: 'm1' } } }, 'E_CONTEXT_INVALID'],
  ['an empty node ID', { levels: { low: '' } }, 'E_CONTEXT_INVALID'],
  ['a node ID of 129 code points', { levels: { low: 'x'.repeat(129) } }, 'E_CONTEXT_INVALID'],
  ['a node ID with a control character', { levels: { low: 'a\u0007b' } }, 'E_CONTEXT_INVALID'],
  ['a node ID with a C1 control character', { levels: { low: 'a\u0085b' } }, 'E_CONTEXT_INVALID'],
  ['a node ID with DEL', { levels: { low: 'a\u007fb' } }, 'E_CONTEXT_INVALID'],
  ['a node ID with an unpaired surrogate', { levels: { low: 'a\ud800' } }, 'E_CONTEXT_INVALID'],
  ['the root level in levels', { levels: { top: 'x' } }, 'E_CONTEXT_INVALID'],
  ['the root level in axes', { axes: { top: 'x' } }, 'E_CONTEXT_INVALID'],
  ['an undeclared level', { levels: { nowhere: 'x' } }, 'E_UNKNOWN_SCOPE'],
  ['an undeclared axis', { axes: { nowhere: 'x' } }, 'E_UNKNOWN_SCOPE'],
  ['a level under axes', { axes: { mid: 'm1' } }, 'E_UNKNOWN_SCOPE'],
  ['an axis under levels', { levels: { lane: 'l1' } }, 'E_UNKNOWN_SCOPE'],
  ['a scope name with another case', { levels: { Mid: 'm1' } }, 'E_UNKNOWN_SCOPE'],
  ['a name that is a prototype property', { levels: { constructor: 'x' } }, 'E_UNKNOWN_SCOPE'],
  ['an unlisted node in a closed catalog with error', { levels: { mid: 'm3' } }, 'E_UNKNOWN_NODE'],
  ['a node ID that is not NFC-equal to a catalog member (CTX-7)', { levels: { mid: 'ḿ' } }, 'E_UNKNOWN_NODE'],
];

for (const [name, context, code] of badContexts) {
  test(`a context with ${name} raises ${code}`, async () => {
    const { tw, calls } = evaluator();
    assert.equal(codeOf(() => tw.resolveSync(context, 'k.over')), code);
    assert.equal(codeOf(() => tw.decideSync(context, 'f.one', {})), code);
    assert.equal(await codeOfAsync(tw.resolve(context, 'k.all')), code);
    assert.equal(await codeOfAsync(tw.decide(context, 'f.one', {})), code);
    assert.equal(calls.length, 0, 'no provider call starts before the context has passed (CTX-10)');
  });
}

test('naming the root level wins over an unknown name, whatever the order of the entries (CTX-11, EVAL-19)', () => {
  const { tw } = evaluator();
  assert.equal(codeOf(() => tw.resolveSync({ levels: { nowhere: 'x', top: 'y' } }, 'k.over')), 'E_CONTEXT_INVALID');
  assert.equal(codeOf(() => tw.resolveSync({ levels: { top: 'y', nowhere: 'x' } }, 'k.over')), 'E_CONTEXT_INVALID');
  assert.equal(codeOf(() => tw.resolveSync({ levels: { top: 'y' }, axes: { nowhere: 'x' } }, 'k.over')), 'E_CONTEXT_INVALID');
  assert.equal(codeOf(() => tw.resolveSync({ levels: { nowhere: 'x' }, axes: { top: 'y' } }, 'k.over')), 'E_CONTEXT_INVALID');
});

test('the first failing step of CTX-9 decides (CTX-9, EVAL-18)', () => {
  const { tw } = evaluator();
  // shape before names, names before node IDs, node IDs before admission
  assert.equal(codeOf(() => tw.resolveSync({ levels: { nowhere: 5 } }, 'k.over')), 'E_CONTEXT_INVALID');
  assert.equal(codeOf(() => tw.resolveSync({ levels: { nowhere: 'x', mid: '' } }, 'k.over')), 'E_UNKNOWN_SCOPE');
  assert.equal(codeOf(() => tw.resolveSync({ levels: { mid: 'm3', low: '' } }, 'k.over')), 'E_CONTEXT_INVALID');
});

test('an omitted context, omitted members and empty maps are the empty context (EVAL-54, CTX-2)', () => {
  const { tw } = evaluator();
  const expected = tw.resolveSync({}, 'k.over');
  assert.deepEqual(tw.resolveSync(undefined, 'k.over'), expected);
  assert.deepEqual(tw.resolveSync({ levels: {}, axes: {} }, 'k.over'), expected);
  assert.deepEqual(tw.resolveSync({ levels: undefined }, 'k.over'), expected);
  assert.equal(expected.value, 1);
});

test('a skipped scope is recorded, in level then axis order, and contributes nothing (CTX-14 to CTX-16, PROV-54 to PROV-61)', () => {
  const { tw } = evaluator();
  const skipped = tw.resolveSync({ levels: { mid: 'm1' }, axes: { lane: 'l9' } }, 'k.over');
  assert.deepEqual(skipped.skippedScopes, [{ scope: 'axis', name: 'lane', node: 'l9', reason: 'UNKNOWN_NODE' }]);
  const omitted = tw.resolveSync({ levels: { mid: 'm1' } }, 'k.over');
  assert.deepEqual({ ...skipped, skippedScopes: [] }, omitted);
  const decided = tw.decideSync({ axes: { lane: 'zz' } }, 'f.one', { facts: { 'parent.flag': true, 'whole.kind': 'a' }, children: { part: [{ 'part.tag': 'x' }] } });
  assert.deepEqual(decided.skippedScopes, [{ scope: 'axis', name: 'lane', node: 'zz', reason: 'UNKNOWN_NODE' }]);
});

test('an open catalog admits any well-formed node ID, compared as received (CTX-13, CTX-8)', async () => {
  const { tw, calls } = evaluator(() => ({}));
  const decomposed = 'é';
  await tw.resolve({ levels: { low: decomposed } }, 'k.all');
  await tw.resolve({ levels: { low: 'é' } }, 'k.all');
  assert.deepEqual(calls, [decomposed, 'é'], 'the node ID reaches the provider exactly as supplied (SVC-15)');
});

// ---------------------------------------------------------------------------
// Key and feature lookup, check order
// ---------------------------------------------------------------------------

test('an undeclared, omitted, empty or non-string key raises E_UNKNOWN_KEY (EVAL-34, EVAL-37)', async () => {
  const { tw } = evaluator();
  for (const key of ['nope', '', undefined, null, 5, {}, [], 'constructor', '__proto__', 'toString', 'hasOwnProperty', 'K.OVER']) {
    assert.equal(codeOf(() => tw.resolveSync({}, key)), 'E_UNKNOWN_KEY', String(key));
    assert.equal(await codeOfAsync(tw.resolve({}, key)), 'E_UNKNOWN_KEY', String(key));
  }
});

test('an undeclared, omitted, empty or non-string feature raises E_UNKNOWN_FEATURE (EVAL-35, EVAL-37)', async () => {
  const { tw } = evaluator();
  for (const feature of ['nope', '', undefined, null, 5, {}, 'constructor', '__proto__', 'f.ONE']) {
    assert.equal(codeOf(() => tw.decideSync({}, feature, {})), 'E_UNKNOWN_FEATURE', String(feature));
    assert.equal(await codeOfAsync(tw.decide({}, feature, {})), 'E_UNKNOWN_FEATURE', String(feature));
  }
});

test('the context is validated before the key and the feature are looked up (EVAL-16, EVAL-17, EVAL-18)', () => {
  const { tw } = evaluator();
  assert.equal(codeOf(() => tw.resolveSync({ levels: { nowhere: 'x' } }, 'nope')), 'E_UNKNOWN_SCOPE');
  assert.equal(codeOf(() => tw.decideSync({ levels: { nowhere: 'x' } }, 'nope', {})), 'E_UNKNOWN_SCOPE');
  assert.equal(codeOf(() => tw.decideSync({}, 'nope', { facts: { 'ghost.x': 1 } })), 'E_UNKNOWN_FEATURE');
});

test('the synchronous resolve raises E_PROVIDER_REQUIRED only where a provider call is needed (SVC-42, EVAL-11)', () => {
  const { tw, calls } = evaluator();
  const withLow = { levels: { low: 'n' } };
  assert.equal(codeOf(() => tw.resolveSync(withLow, 'k.all')), 'E_PROVIDER_REQUIRED');
  assert.equal(codeOf(() => tw.resolveSync(withLow, 'k.deep')), 'E_PROVIDER_REQUIRED');
  // A key that is not settable at the service-sourced level needs no call.
  assert.equal(tw.resolveSync(withLow, 'k.over').value, 1);
  // No node for the level, no call.
  assert.equal(tw.resolveSync({}, 'k.all').value, true);
  // Steps 1 and 2 come before step 3.
  assert.equal(codeOf(() => tw.resolveSync({ levels: { low: 5 } }, 'k.all')), 'E_CONTEXT_INVALID');
  assert.equal(codeOf(() => tw.resolveSync(withLow, 'nope')), 'E_UNKNOWN_KEY');
  assert.equal(calls.length, 0);
});

test('the synchronous variant returns the same result as resolve where no provider is needed (EVAL-10)', async () => {
  const { tw } = evaluator();
  for (const context of [{}, MID_LANE, { levels: { mid: 'm2' } }, { axes: { lane: 'zz' } }]) {
    for (const key of ['k.over', 'k.hier', 'k.any', 'k.min', 'k.max', 'k.inter', 'k.union', 'k.deep']) {
      assert.deepEqual(tw.resolveSync(context, key), await tw.resolve(context, key), key);
    }
  }
});

test('decideSync returns the same result as decide, for every input (EVAL-12)', async () => {
  const { tw } = evaluator();
  const inputs = [
    { facts: { 'parent.flag': true, 'whole.kind': 'a' }, children: { part: [{ 'part.tag': 'x' }] } },
    { facts: {} },
    undefined,
    { facts: { 'parent.flag': 'true' } },
  ];
  for (const input of inputs) {
    const sync = (() => { try { return tw.decideSync(MID_LANE, 'f.one', input); } catch (error) { return error.code; } })();
    const async_ = await tw.decide(MID_LANE, 'f.one', input).catch((error) => error.code);
    assert.deepEqual(sync, async_);
  }
});

// ---------------------------------------------------------------------------
// Resolved results (section 9.2 to 9.5) and the merge strategies (section 5)
// ---------------------------------------------------------------------------

test('an override key: the axis value wins the max comparison, and every contribution is listed (AXIS-7, AXIS-8, PROV-50)', () => {
  const { tw } = evaluator();
  assert.deepEqual(tw.resolveSync(MID_LANE, 'k.over'), {
    key: 'k.over',
    strategy: 'override',
    kind: 'conf',
    value: 8,
    bundleVersion: loaded.bundleVersion,
    skippedScopes: [],
    contributions: [
      { scope: 'level', name: 'top', value: 1, effect: 'SET' },
      { scope: 'level', name: 'mid', node: 'm1', value: 6, effect: 'SET' },
      { scope: 'axis', name: 'lane', node: 'l1', value: 8, effect: 'SET' },
    ],
    source: { scope: 'axis', name: 'lane', node: 'l1' },
  });
});

test('an override key: the hierarchy wins a tie, and the default applies with no contribution (AXIS-8, PROV-51, PROV-48)', () => {
  const { tw } = evaluator();
  const result = tw.resolveSync({ levels: { mid: 'm1' } }, 'k.over');
  assert.equal(result.value, 6);
  assert.deepEqual(result.source, { scope: 'level', name: 'mid', node: 'm1' });
  const none = tw.resolveSync({}, 'k.hier');
  assert.equal(none.value, 'dflt');
  assert.equal(none.source, 'DEFAULT');
  assert.deepEqual(none.contributions, []);
  assert.ok(!('kind' in none), 'kind is absent when the key opts into none (PROV-14)');
});

test('hierarchy_wins and axis_wins choose between H and A (AXIS-7)', () => {
  const { tw } = evaluator();
  // k.hier is settable at top, mid and lane: lane l1 sets 'laneval'; with no level value only A is present.
  assert.equal(tw.resolveSync({ axes: { lane: 'l1' } }, 'k.hier').value, 'laneval');
  assert.deepEqual(tw.resolveSync({ axes: { lane: 'l1' } }, 'k.hier').source, { scope: 'axis', name: 'lane', node: 'l1' });
});

test('commutative keys: values, labels and absent results (MERGE-5, MERGE-6, MERGE-17, PROV-38)', () => {
  const { tw } = evaluator();
  const min = tw.resolveSync({ levels: { mid: 'm1' } }, 'k.min');
  assert.equal(min.value, 30);
  assert.deepEqual(min.contributions.map((c) => [c.name, c.value, c.effect]), [['top', 50, 'NARROWED'], ['mid', 30, 'NARROWED']]);
  assert.ok(!('source' in min), 'no source for a commutative key (PROV-45)');
  const absent = tw.resolveSync({}, 'k.max');
  assert.equal(absent.value, null, 'a max key with no contribution is absent (MERGE-6)');
  assert.deepEqual(absent.contributions, []);
  assert.equal(tw.resolveSync(MID_LANE, 'k.max').value, 3);
  const inter = tw.resolveSync({ levels: { mid: 'm2' } }, 'k.inter');
  assert.deepEqual(inter.value, ['u3', 'u1'], 'a set result is in universe order (TYPE-51)');
  assert.deepEqual(inter.contributions.map((c) => c.effect), ['IGNORED', 'NARROWED']);
  assert.deepEqual(tw.resolveSync({}, 'k.union').value, [], 'the identity of union');
  assert.deepEqual(tw.resolveSync({ levels: { mid: 'm1' } }, 'k.union').value, ['u1']);
  assert.equal(tw.resolveSync({}, 'k.any').value, false, 'the identity of any');
  const any = tw.resolveSync({ levels: { mid: 'm2' } }, 'k.any');
  assert.deepEqual(any.contributions, [{ scope: 'level', name: 'mid', node: 'm2', value: true, effect: 'WIDENED' }]);
  assert.equal(tw.resolveSync({}, 'k.inter').value.length, 3);
});

test('a deep_merge key merges fields in order and names the last term as its source (MERGE-22 to MERGE-25, PROV-53)', () => {
  const { tw } = evaluator();
  const none = tw.resolveSync({}, 'k.deep');
  assert.deepEqual(none.value, { a: 10, b: { c: 'top' } });
  assert.deepEqual(none.source, { scope: 'level', name: 'top' });
  const merged = tw.resolveSync({ levels: { mid: 'm1' } }, 'k.deep');
  assert.deepEqual(merged.value, { a: 10, b: { c: 'top', d: 'mid' } });
  assert.deepEqual(merged.source, { scope: 'level', name: 'mid', node: 'm1' });
  assert.deepEqual(merged.contributions[1].value, { b: { d: 'mid' } }, 'a contribution holds the scope\'s own, partial value (PROV-28)');
});

test('a result never shares state with the bundle or with an earlier result (EVAL-47, EVAL-50)', () => {
  const { tw } = evaluator();
  const first = tw.resolveSync({ levels: { mid: 'm1' } }, 'k.deep');
  first.value.b.c = 'changed';
  first.value.extra = 1;
  first.contributions[0].value.b.c = 'changed too';
  first.contributions.length = 0;
  const second = tw.resolveSync({ levels: { mid: 'm1' } }, 'k.deep');
  assert.deepEqual(second.value, { a: 10, b: { c: 'top', d: 'mid' } });
  assert.equal(second.contributions.length, 2);
  assert.deepEqual(second.contributions[0].value, { a: 10, b: { c: 'top' } });
  const set = tw.resolveSync({}, 'k.inter');
  set.value.length = 0;
  assert.equal(tw.resolveSync({}, 'k.inter').value.length, 3);
});

test('the inputs are never modified (EVAL-49)', async () => {
  const deepFreeze = (value) => {
    if (typeof value === 'object' && value !== null) {
      Object.values(value).forEach(deepFreeze);
      Object.freeze(value);
    }
    return value;
  };
  const { tw } = evaluator(() => deepFreeze({ 'k.all': true }));
  const context = deepFreeze({ levels: { mid: 'm1', low: 'n' }, axes: { lane: 'l1' } });
  const facts = deepFreeze({ facts: { 'parent.flag': true, 'whole.kind': 'b' }, children: { part: [{ 'part.tag': 'x' }] } });
  assert.equal((await tw.resolve(context, 'k.all')).value, true);
  assert.equal(tw.decideSync(context, 'f.one', facts).effect, 'ALLOW');
});

// ---------------------------------------------------------------------------
// Providers (section 6)
// ---------------------------------------------------------------------------

const WITH_LOW = { levels: { mid: 'm1', low: 'n1' } };

test('the provider is called once, with the node as supplied, even when another scope wins (SVC-15, SVC-17 to SVC-19)', async () => {
  const { tw, calls } = evaluator(() => ({ 'k.text': 'from provider' }));
  const result = await tw.resolve({ levels: { low: 'n1' } }, 'k.text');
  assert.deepEqual(calls, ['n1']);
  assert.equal(result.value, 'from provider');
  assert.deepEqual(result.source, { scope: 'level', name: 'low', node: 'n1' });
  // The provider wins here because it is the most specific level. The root has no value for k.text, so the call was
  // not needed for the result of a context with no low node, and it must not happen.
  await tw.resolve({}, 'k.text');
  assert.deepEqual(calls, ['n1']);
});

test('a provider is not called for a key that is not settable at its level, or when the node is skipped (SVC-17)', async () => {
  const { tw, calls } = evaluator();
  await tw.resolve(WITH_LOW, 'k.any');
  await tw.resolve(WITH_LOW, 'k.over');
  await tw.resolve({ levels: { mid: 'm1' } }, 'k.all');
  await tw.resolve({ levels: { mid: 'm1' }, axes: { lane: 'zz' } }, 'k.all');
  assert.deepEqual(calls, []);
  await tw.decide(WITH_LOW, 'f.one', {});
  assert.deepEqual(calls, [], 'decide calls no provider (SVC-22)');
});

test('values from a provider are validated and contribute (SVC-26, SVC-31)', async () => {
  const { tw, calls } = evaluator(() => ({ 'k.inter': ['u2', 'u3'], 'k.all': true, 'k.min': 7 }));
  const inter = await tw.resolve(WITH_LOW, 'k.inter');
  assert.deepEqual(inter.value, ['u3', 'u2'], 'the provider\'s set is returned in universe order');
  assert.deepEqual(inter.contributions[1], { scope: 'level', name: 'low', node: 'n1', value: ['u3', 'u2'], effect: 'NARROWED' });
  const all = await tw.resolve(WITH_LOW, 'k.all');
  assert.equal(all.contributions[2].effect, 'IGNORED', 'a value equal to the identity is IGNORED (SVC-33)');
  assert.equal((await tw.resolve(WITH_LOW, 'k.min')).value, 7);
  assert.equal(calls.length, 3);
});

test('an omitted key is not a failure and an unknown key changes nothing (SVC-27, SVC-36, SVC-46)', async () => {
  const { tw, events } = evaluator(() => ({ 'k.ghost': null, 'k.all': undefined === 1 ? 1 : true }));
  const unknown = await tw.resolve(WITH_LOW, 'k.inter');
  assert.deepEqual(unknown.contributions.map((c) => c.name), ['top'], 'the provider left k.inter out');
  assert.deepEqual(events.filter((e) => e.type !== 'BUNDLE_LOADED').map((e) => [e.type, e.level, e.node, e.key]), [
    ['UNKNOWN_KEY', 'low', 'n1', 'k.ghost'],
  ]);
});

test('an invalid entry is a failure for its key only, whatever its value, including null (SVC-34, SVC-35)', async () => {
  for (const [key, bad] of [
    ['k.min', 101],
    ['k.min', -1],
    ['k.min', 1.5],
    ['k.min', '7'],
    ['k.min', null],
    ['k.min', undefined],
    ['k.min', NaN],
    ['k.min', 2 ** 53],
    ['k.all', 'true'],
    ['k.all', 1],
    ['k.inter', ['u9']],
    ['k.inter', ['u1', 'u1']],
    ['k.inter', 'u1'],
    ['k.inter', [1]],
    ['k.deep', { zzz: 1 }],
    ['k.deep', { a: 'x' }],
    ['k.deep', 5],
    ['k.deep', []],
    ['k.text', 5],
    ['k.text', '\ud800'],
  ]) {
    const { tw, events } = evaluator(() => ({ [key]: bad }));
    const result = await tw.resolve(WITH_LOW, key);
    const entry = result.contributions.find((c) => c.name === 'low');
    assert.equal(entry.effect, 'INVALID', `${key}=${String(bad)}`);
    assert.ok(['skip', 'deny'].includes(entry.onSourceFailure));
    assert.equal(events.filter((e) => e.type === 'SOURCE_INVALID').length, 1);
  }
});

test('an entry for another declared key is not validated and not reported (SVC-28)', async () => {
  const { tw, events } = evaluator(() => ({ 'k.all': true, 'k.min': 'garbage', 'k.inter': null }));
  const result = await tw.resolve(WITH_LOW, 'k.all');
  assert.equal(result.contributions.length, 3);
  assert.deepEqual(events.map((e) => e.type), ['BUNDLE_LOADED']);
});

test('a provider failure follows the key\'s source-failure behaviour (SVC-8, SVC-9, SVC-23, PROV-39, PROV-41)', async () => {
  const failures = [
    ['throws synchronously', () => { throw new Error('down'); }],
    ['rejects', async () => { throw new Error('down'); }],
    ['returns null', () => null],
    ['returns a list', () => []],
    ['returns a string', () => 'text'],
    ['returns a number', () => 5],
    ['returns a Map', () => new Map([['k.all', true]])],
    ['returns an instance of a class', () => new (class Reply {})()],
    ['returns a throwing getter', () => {
      const thrower = { get: () => { throw new Error('getter'); }, enumerable: true };
      return Object.defineProperties({}, Object.fromEntries(['k.all', 'k.inter', 'k.min', 'k.union', 'k.deep'].map((key) => [key, thrower])));
    }],
  ];
  for (const [name, reply] of failures) {
    const { tw, events } = evaluator(reply);
    const lowEntry = (result) => result.contributions.find((entry) => entry.name === 'low');
    const all = await tw.resolve(WITH_LOW, 'k.all');
    assert.deepEqual(lowEntry(all), { scope: 'level', name: 'low', node: 'n1', value: false, effect: 'UNAVAILABLE', onSourceFailure: 'deny' }, name);
    assert.equal(all.value, false, name);
    const inter = await tw.resolve(WITH_LOW, 'k.inter');
    assert.deepEqual(inter.value, [], name);
    assert.equal(lowEntry(inter).value.length, 0);
    const min = await tw.resolve(WITH_LOW, 'k.min');
    assert.equal(min.value, 0, `${name}: the deny value of min is the declared minimum`);
    const union = await tw.resolve(WITH_LOW, 'k.union');
    assert.deepEqual(lowEntry(union), { scope: 'level', name: 'low', node: 'n1', effect: 'UNAVAILABLE', onSourceFailure: 'skip' }, name);
    assert.deepEqual(union.value, ['u1'], `${name}: skip contributes no value`);
    const deep = await tw.resolve(WITH_LOW, 'k.deep');
    assert.ok(!('value' in lowEntry(deep)), name);
    assert.equal(events.filter((e) => e.type === 'SOURCE_UNAVAILABLE').length, 5, name); // one event for each affected key (EVAL-44)
  }
});

test('a provider that does not return in time is unavailable, and a late result is not used (SVC-39, SVC-40, SVC-41)', async () => {
  const unhandled = [];
  const record = (reason) => unhandled.push(reason);
  process.on('unhandledRejection', record);
  try {
    let release;
    const gate = new Promise((resolve) => { release = resolve; });
    const slow = createEvaluator(loaded, {
      providers: { low: { fn: () => gate.then(() => ({ 'k.all': true })), timeoutMs: 20 } },
    });
    const started = Date.now();
    const result = await slow.resolve(WITH_LOW, 'k.all');
    assert.ok(Date.now() - started < 1000);
    assert.equal(result.value, false);
    assert.equal(result.contributions[2].effect, 'UNAVAILABLE');
    release();
    await new Promise((resolve) => setTimeout(resolve, 10));
    // A provider that rejects after its timeout has expired does not raise an unhandled rejection.
    const late = createEvaluator(loaded, {
      providers: { low: { fn: () => new Promise((_, reject) => setTimeout(() => reject(new Error('late')), 40)), timeoutMs: 10 } },
    });
    assert.equal((await late.resolve(WITH_LOW, 'k.all')).contributions[2].effect, 'UNAVAILABLE');
    await new Promise((resolve) => setTimeout(resolve, 60));
    assert.deepEqual(unhandled, []);
  } finally {
    process.off('unhandledRejection', record);
  }
});

test('a provider that returns a plain value synchronously is accepted', async () => {
  const tw = createEvaluator(loaded, { providers: { low: { fn: () => ({ 'k.min': 3 }), timeoutMs: 50 } } });
  assert.equal((await tw.resolve(WITH_LOW, 'k.min')).value, 3);
});

test('a provider with a null-prototype result is accepted', async () => {
  const reply = Object.assign(Object.create(null), { 'k.min': 4 });
  const tw = createEvaluator(loaded, { providers: { low: { fn: async () => reply, timeoutMs: 50 } } });
  assert.equal((await tw.resolve(WITH_LOW, 'k.min')).value, 4);
});

test('concurrent calls on one evaluator give the results they give alone (EVAL-51, EVAL-52, SVC-21)', async () => {
  let active = 0;
  let peak = 0;
  const tw = createEvaluator(loaded, {
    providers: {
      low: {
        fn: async (node) => {
          active++;
          peak = Math.max(peak, active);
          await new Promise((resolve) => setTimeout(resolve, node === 'slow' ? 30 : 1));
          active--;
          return { 'k.min': node === 'slow' ? 1 : 2 };
        },
        timeoutMs: 500,
      },
    },
  });
  const contexts = ['slow', 'fast', 'slow', 'fast', 'other'].map((low) => ({ levels: { mid: 'm1', low } }));
  const together = await Promise.all(contexts.map((context) => tw.resolve(context, 'k.min')));
  const alone = [];
  for (const context of contexts) alone.push(await tw.resolve(context, 'k.min'));
  assert.deepEqual(together, alone);
  assert.ok(peak > 1, 'the calls overlapped');
});

test('a context error is raised before any provider call, and a result does not wait for a provider it does not need', async () => {
  const { tw, calls } = evaluator();
  assert.equal(await codeOfAsync(tw.resolve({ levels: { low: 'n', mid: 'm3' } }, 'k.all')), 'E_UNKNOWN_NODE');
  assert.equal(await codeOfAsync(tw.resolve({ levels: { low: 'n' } }, 'nope')), 'E_UNKNOWN_KEY');
  assert.deepEqual(calls, []);
});

test('listener events carry the type, the bundle version, and where they apply, level, node and key (EVAL-53)', async () => {
  const { tw, events } = evaluator(() => { throw new Error('down'); });
  await tw.resolve(WITH_LOW, 'k.min');
  assert.deepEqual(events.slice(1), [
    { type: 'SOURCE_UNAVAILABLE', bundleVersion: loaded.bundleVersion, level: 'low', node: 'n1', key: 'k.min' },
  ]);
  tw.decideSync(WITH_LOW, 'f.one', {});
  tw.resolveSync({}, 'k.over');
  assert.equal(events.length, 2, 'decide and the synchronous variants report no provider event (EVAL-42)');
  await codeOfAsync(tw.resolve({ levels: { mid: 'bad' } }, 'k.min'));
  assert.equal(events.length, 2, 'a raised error is not reported (EVAL-45)');
});

// ---------------------------------------------------------------------------
// Decisions (sections 3, 7, 8)
// ---------------------------------------------------------------------------

const FACTS_A = { facts: { 'parent.flag': true, 'whole.kind': 'a' }, children: { part: [{ 'part.tag': 'x' }] } };

test('a rule matches: the effect, reason, binding, policy and trace (RULE-47 to RULE-49, PROV-62 to PROV-84)', () => {
  const { tw } = evaluator();
  assert.deepEqual(tw.decideSync({}, 'f.one', FACTS_A), {
    feature: 'f.one',
    effect: 'ALLOW',
    bundleVersion: loaded.bundleVersion,
    skippedScopes: [],
    binding: { level: 'top' },
    policy: 'p1',
    reason: { type: 'RULE', id: 'r1', name: 'first rule' },
    trace: [{ id: 'r1', matched: true }],
  });
});

test('no rule matches: the default, with the first failing condition of each rule (RULE-50, RULE-54, RULE-55)', () => {
  const { tw } = evaluator();
  const result = tw.decideSync({ levels: { mid: 'm1' } }, 'f.one', FACTS_A);
  assert.equal(result.effect, 'DENY');
  assert.deepEqual(result.reason, { type: 'DEFAULT' });
  assert.deepEqual(result.binding, { level: 'mid', node: 'm1' });
  assert.deepEqual(result.trace, [
    { id: 'r1', matched: false, failed: { fact: 'whole.kind', op: 'eq' } },
    { id: 'r2', matched: false, failed: { fact: 'parent.flag', op: 'eq' } },
  ]);
});

test('the binding replaces all others, and a setting comes only from the selected binding (BIND-17, BIND-18, BIND-23, BIND-24)', () => {
  const { tw } = evaluator();
  const input = { facts: { 'parent.flag': false, 'whole.kind': 'a' }, children: { part: [{ 'part.tag': 'x' }, { 'part.tag': 'y' }] } };
  assert.equal(tw.decideSync({ levels: { mid: 'm1' } }, 'f.one', input).reason.id, 'r2', 'the mid binding\'s list contains x and y');
  assert.equal(tw.decideSync({}, 'f.one', input).reason.type, 'DEFAULT', 'the root binding uses the default list, which is empty');
  const m2 = tw.decideSync({ levels: { mid: 'm2' } }, 'f.one', {});
  assert.deepEqual([m2.effect, m2.policy, m2.reason, m2.trace], ['ALLOW', 'p2', { type: 'DEFAULT' }, []]);
  // An axis carries no binding.
  assert.deepEqual(tw.decideSync({ axes: { lane: 'l1' } }, 'f.one', FACTS_A).binding, { level: 'top' });
  // A level with no binding for its node falls back to the next less specific candidate.
  assert.deepEqual(tw.decideSync({ levels: { low: 'anything' } }, 'f.one', FACTS_A).binding, { level: 'top' });
});

test('all_in and any_in over an empty collection are false, and a membership in an empty list is false (RULE-33, RULE-34)', () => {
  const { tw } = evaluator();
  const input = (children) => ({ facts: { 'parent.flag': false, 'whole.kind': 'a' }, children });
  assert.equal(tw.decideSync({ levels: { mid: 'm1' } }, 'f.one', input({ part: [] })).reason.type, 'DEFAULT');
  assert.equal(tw.decideSync({ levels: { mid: 'm1' } }, 'f.one', input({ part: [{ 'part.tag': 'x' }] })).reason.id, 'r2');
  assert.equal(tw.decideSync({ levels: { mid: 'm1' } }, 'f.one', input({ part: [{ 'part.tag': 'x' }, { 'part.tag': 'z' }] })).reason.type, 'DEFAULT', 'a mixed collection under all_in');
});

test('a missing fact returns ifFactMissing with every missing fact of the precheck set, sorted (RULE-40 to RULE-45)', () => {
  const { tw } = evaluator();
  const missing = tw.decideSync({}, 'f.one', { facts: {} });
  assert.deepEqual(missing.reason, { type: 'MISSING_FACT', facts: ['parent.flag', 'part.tag', 'whole.kind'] });
  assert.equal(missing.effect, 'DENY');
  assert.deepEqual(missing.trace, []);
  assert.deepEqual(missing.binding, { level: 'top' });
  assert.equal(missing.policy, 'p1');
  const partial = tw.decideSync({}, 'f.one', { facts: { 'parent.flag': null, 'whole.kind': 'a' }, children: { part: [{ 'part.tag': 'x' }, {}] } });
  assert.deepEqual(partial.reason.facts, ['parent.flag', 'part.tag'], 'null is missing, and one child map lacking a fact makes it missing (FACT-19)');
  assert.deepEqual(tw.decideSync({}, 'f.one').reason.facts.length, 3, 'an omitted FactSet has every fact missing (EVAL-54)');
  assert.equal(tw.decideSync({ levels: { mid: 'm2' } }, 'f.one', {}).reason.type, 'DEFAULT', 'a policy with no rules needs no fact');
});

test('an empty child collection is present, an omitted one is missing (FACT-17, FACT-18)', () => {
  const { tw } = evaluator();
  const base = { facts: { 'parent.flag': true, 'whole.kind': 'a' } };
  assert.deepEqual(tw.decideSync({}, 'f.one', base).reason.facts, ['part.tag']);
  assert.equal(tw.decideSync({}, 'f.one', { ...base, children: { part: [] } }).reason.type, 'RULE');
});

test('effects of the object form and the nullable form (FEAT-9, FEAT-11, PROV-66)', () => {
  const { tw } = evaluator();
  assert.deepEqual(tw.decideSync({}, 'f.obj', { facts: { 'parent.flag': true } }).effect, { level: 2, note: 'n' });
  assert.deepEqual(tw.decideSync({}, 'f.obj', { facts: { 'parent.flag': false } }).effect, { level: 1 });
  assert.deepEqual(tw.decideSync({}, 'f.obj', { facts: {} }).effect, { level: 0 });
  assert.equal(tw.decideSync({}, 'f.null', { facts: { 'parent.flag': true } }).effect, 'YES');
  assert.equal(tw.decideSync({}, 'f.null', { facts: { 'parent.flag': false } }).effect, null);
  assert.equal(tw.decideSync({}, 'f.null', { facts: {} }).effect, null);
  const first = tw.decideSync({}, 'f.obj', { facts: { 'parent.flag': true } });
  first.effect.level = 99;
  first.effect.note = 'changed';
  assert.deepEqual(tw.decideSync({}, 'f.obj', { facts: { 'parent.flag': true } }).effect, { level: 2, note: 'n' });
});

// ---------------------------------------------------------------------------
// FactSet validation (section 3.5 to 3.7)
// ---------------------------------------------------------------------------

const ok = { 'parent.flag': true, 'whole.kind': 'a' };
const kid = { part: [{ 'part.tag': 'x' }] };
const badFactSets = [
  ['null', null],
  ['a list', []],
  ['a string', 'x'],
  ['a number', 5],
  ['an unknown member', { facts: ok, extra: {} }],
  ['facts that is null', { facts: null }],
  ['facts that is a list', { facts: [] }],
  ['children that is null', { children: null }],
  ['children that is a list', { children: [] }],
  ['an undeclared fact (also with a null value)', { facts: { ...ok, 'ghost.x': null } }],
  ['a fact name with another case', { facts: { 'Parent.flag': true } }],
  ['a child fact among the facts', { facts: { ...ok, 'part.tag': 'x' } }],
  ['a fact of a child among the facts, with null', { facts: { ...ok, 'part.qty': null } }],
  ['a fact name that is a prototype property', JSON.parse('{"facts":{"__proto__":true}}')],
  ['a string for a boolean fact', { facts: { 'parent.flag': 'true' } }],
  ['a number for a boolean fact', { facts: { 'parent.flag': 1 } }],
  ['a number for a decimal fact', { facts: { 'whole.price': 1.5 } }],
  ['an integer for a decimal fact', { facts: { 'whole.price': 1 } }],
  ['a decimal with an exponent', { facts: { 'whole.price': '1e3' } }],
  ['a decimal with a trailing dot', { facts: { 'whole.price': '1.' } }],
  ['a decimal with a leading dot', { facts: { 'whole.price': '.5' } }],
  ['a decimal with a space', { facts: { 'whole.price': ' 1' } }],
  ['a decimal with a non-ASCII digit', { facts: { 'whole.price': '١' } }],
  ['an empty decimal', { facts: { 'whole.price': '' } }],
  ['an enum member with another case', { facts: { 'whole.kind': 'A' } }],
  ['an enum value that is not listed', { facts: { 'whole.kind': 'c' } }],
  ['a string for an integer fact', { facts: { 'whole.count': '5' } }],
  ['a float for an integer fact', { facts: { 'whole.count': 5.5 } }],
  ['NaN for an integer fact', { facts: { 'whole.count': NaN } }],
  ['Infinity for an integer fact', { facts: { 'whole.count': Infinity } }],
  ['an integer above 2^53 - 1', { facts: { 'whole.count': 2 ** 53 } }],
  ['an integer below -(2^53 - 1)', { facts: { 'whole.count': -(2 ** 53) } }],
  ['an object for a string fact', { facts: { 'whole.note': {} } }],
  ['a string with an unpaired surrogate', { facts: { 'whole.note': '\udc00' } }],
  ['a list for a single-value fact', { facts: { 'whole.note': ['a'] } }],
  ['a child entity that is not a child', { facts: ok, children: { parent: [] } }],
  ['the decided entity under children', { facts: ok, children: { whole: [] } }],
  ['children for an undeclared entity', { facts: ok, children: { ghost: [] } }],
  ['a child collection that is null', { facts: ok, children: { part: null } }],
  ['a child collection that is not a list', { facts: ok, children: { part: {} } }],
  ['a child element that is null', { facts: ok, children: { part: [null] } }],
  ['a child element that is a list', { facts: ok, children: { part: [[]] } }],
  ['a child element that is a string', { facts: ok, children: { part: ['x'] } }],
  ['a hole in a child list', { facts: ok, children: { part: [{ 'part.tag': 'x' }, , { 'part.tag': 'y' }] } }],
  ['a fact of the decided entity in a child map', { facts: ok, children: { part: [{ 'whole.kind': 'a' }] } }],
  ['a fact of an ancestor in a child map', { facts: ok, children: { part: [{ 'parent.flag': true }] } }],
  ['an undeclared fact in a child map (also null)', { facts: ok, children: { part: [{ 'part.ghost': null }] } }],
  ['an invalid value in a child map', { facts: ok, children: { part: [{ 'part.tag': 'w' }] } }],
  ['an integer above the declared maximum in a child map', { facts: ok, children: { part: [{ 'part.qty': 1001 }] } }],
];

for (const [name, input] of badFactSets) {
  test(`a FactSet with ${name} raises E_FACTS_INVALID (FACT-22 to FACT-42)`, async () => {
    const { tw } = evaluator();
    assert.equal(codeOf(() => tw.decideSync({}, 'f.one', input)), 'E_FACTS_INVALID');
    assert.equal(await codeOfAsync(tw.decide({}, 'f.one', input)), 'E_FACTS_INVALID');
  });
}

test('a FactSet failure beats a missing fact, however the rules are arranged (FACT-44, EVAL-18)', () => {
  const { tw } = evaluator();
  assert.equal(codeOf(() => tw.decideSync({}, 'f.one', { facts: { 'parent.flag': 'true' } })), 'E_FACTS_INVALID');
  assert.equal(codeOf(() => tw.decideSync({ levels: { mid: 'm2' } }, 'f.one', { facts: { 'ghost.x': 1 } })), 'E_FACTS_INVALID', 'also for a policy that reads no fact');
});

test('valid FactSets: decimals are canonicalised, undefined entries are absent, -0 is zero', () => {
  const { tw } = evaluator();
  const rule = (facts) => tw.decideSync({}, 'f.one', { facts: { ...ok, ...facts }, children: kid }).reason.type;
  assert.equal(rule({ 'whole.price': '+001.500', 'whole.count': 2 ** 53 - 1, 'whole.note': '' }), 'RULE');
  assert.equal(rule({ 'whole.count': -(2 ** 53 - 1) }), 'RULE');
  assert.equal(rule({ 'whole.count': -0 }), 'RULE');
  assert.equal(rule({ 'whole.note': undefined }), 'RULE', 'an entry set to undefined is absent');
  assert.equal(tw.decideSync({}, 'f.one', { facts: ok, children: undefined }).reason.type, 'MISSING_FACT');
  assert.equal(tw.decideSync({}, 'f.one', { facts: undefined, children: kid }).reason.type, 'MISSING_FACT');
});

test('decimals compare as canonical strings, also through a setting (RULE-37, NORM-15, NORM-16)', () => {
  const c = changed((x) => {
    const policy = x.features['f.one'].policies.p1;
    policy.settings.s_price = { valueOf: 'whole.price', default: '1.5' };
    policy.settings.s_prices = { listOf: 'whole.price', default: ['0.25', '1.5'] };
    policy.requiredFacts = ['whole.price'];
    policy.rules = [
      { id: 'literal', name: 'literal', when: [{ fact: 'whole.price', op: 'eq', value: '-2.5' }], then: 'DENY' },
      { id: 'setting', name: 'setting', when: [{ fact: 'whole.price', op: 'eq', value: '$s_price' }], then: 'ALLOW' },
      { id: 'list', name: 'list', when: [{ fact: 'whole.price', op: 'in', value: '$s_prices' }], then: 'DENY' },
    ];
  });
  const tw = createEvaluator(loadContent(c), { providers: { low: { fn: async () => ({}), timeoutMs: 10 } } });
  const reasonFor = (price) => tw.decideSync({}, 'f.one', { facts: { ...ok, 'whole.price': price }, children: kid }).reason;
  assert.equal(reasonFor('1.50').id, 'setting');
  assert.equal(reasonFor('+1.5000').id, 'setting');
  assert.equal(reasonFor('001.5').id, 'setting');
  assert.equal(reasonFor('-002.50').id, 'literal');
  assert.equal(reasonFor('.25'.replace('.', '0.')).id, 'list');
  assert.equal(reasonFor('-0.0').type, 'DEFAULT');
  assert.equal(reasonFor('0').type, 'DEFAULT');
});
