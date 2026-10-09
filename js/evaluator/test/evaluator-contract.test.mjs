// The operation contract that no vector can observe: no operation selects one scope (EVAL-13), operations change
// neither the bundle, the evaluator nor their inputs (EVAL-46 to EVAL-50), results do not depend on other calls,
// before or at the same time (EVAL-51, EVAL-52), a failure is raised and never returned (PROV-9), and an omitted or
// non-string key or feature is an unknown name at the lookup step (EVAL-37).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { isDeepStrictEqual } from 'node:util';
import fc from 'fast-check';
import { api, codeOf, codeOfAsync, distUrl } from './support.mjs';
import { bundle } from './fixtures.mjs';

const { createEvaluator, TierwiseError } = api;
const { modelOf } = await import(new URL('bundle.js', distUrl).href);
const loaded = bundle();

/** A provider whose answer depends only on the node, after a node-dependent delay, so results are repeatable. */
async function provider(node) {
  await new Promise((resolve) => setTimeout(resolve, node === 'n1' ? 4 : 1));
  if (node === 'down') throw new Error('down');
  if (node === 'bad') return { 'k.min': 'not an integer', 'k.all': 3 };
  return { 'k.all': node === 'n1', 'k.min': 7, 'k.text': `t-${node}`, 'k.union': ['u2'], 'k.inter': ['u1'], 'k.deep': { a: 3 } };
}

function evaluator(fn = provider) {
  return createEvaluator(loaded, { providers: { low: { fn, timeoutMs: 500 } } });
}

/** Runs one call and returns its outcome: the result, or the code of the TierwiseError it raises. */
async function outcome(tw, call) {
  try {
    switch (call.op) {
      case 'resolve':
        return { result: await tw.resolve(call.context, call.name) };
      case 'resolveSync':
        return { result: tw.resolveSync(call.context, call.name) };
      case 'decide':
        return { result: await tw.decide(call.context, call.name, call.facts) };
      default:
        return { result: tw.decideSync(call.context, call.name, call.facts) };
    }
  } catch (error) {
    if (error instanceof TierwiseError) return { code: error.code };
    throw error;
  }
}

const CONTEXTS = [
  {},
  { levels: { mid: 'm1' } },
  { levels: { mid: 'm2', low: 'n1' } },
  { levels: { low: 'n2' }, axes: { lane: 'l1' } },
  { levels: { low: 'down' } },
  { levels: { low: 'bad', mid: 'm1' } },
  { levels: { mid: 'm3' } },
  { axes: { lane: 'l9' } },
];
const KEYS = ['k.all', 'k.any', 'k.min', 'k.max', 'k.union', 'k.inter', 'k.over', 'k.hier', 'k.deep', 'k.text', 'k.nope'];
const FEATURES = ['f.one', 'f.obj', 'f.null', 'f.nope'];
const FACT_SETS = [
  {},
  { facts: { 'parent.flag': true, 'whole.kind': 'b' } },
  { facts: { 'parent.flag': false }, children: { part: [{ 'part.tag': 'x' }, { 'part.tag': 'y' }] } },
  { facts: { 'parent.flag': true, 'whole.kind': 'a' }, children: { part: [] } },
  { facts: { 'whole.kind': 'nope' } },
];

const callArb = fc.oneof(
  fc.record({
    op: fc.constantFrom('resolve', 'resolveSync'),
    context: fc.constantFrom(...CONTEXTS),
    name: fc.constantFrom(...KEYS),
  }),
  fc.record({
    op: fc.constantFrom('decide', 'decideSync'),
    context: fc.constantFrom(...CONTEXTS),
    name: fc.constantFrom(...FEATURES),
    facts: fc.constantFrom(...FACT_SETS),
  }),
);

// ---------------------------------------------------------------------------
// EVAL-13
// ---------------------------------------------------------------------------

test('EVAL-13: no operation has a parameter that selects one scope or one node', async () => {
  const tw = evaluator();
  assert.deepEqual(Object.keys(tw).sort(), ['bundleVersion', 'decide', 'decideSync', 'resolve', 'resolveSync']);
  assert.equal(tw.resolve.length, 2, 'resolve takes the context and the key');
  assert.equal(tw.resolveSync.length, 2, 'resolveSync takes the context and the key');
  assert.equal(tw.decide.length, 3, 'decide takes the context, the feature and the FactSet');
  assert.equal(tw.decideSync.length, 3, 'decideSync takes the context, the feature and the FactSet');
  assert.deepEqual(Object.keys(api).sort(), [
    'ERROR_CODES',
    'SUPPORTED_BUNDLE_FORMAT_VERSION',
    'SUPPORTED_SPEC_VERSION',
    'TierwiseError',
    'createEvaluator',
    'loadBundle',
  ]);
  // An extra argument that names a scope or a node is not read: the combined result is the same.
  const context = { levels: { mid: 'm1' } };
  const whole = tw.resolveSync(context, 'k.deep');
  for (const extra of ['mid', { scope: 'level', name: 'mid', node: 'm1' }, { level: 'top' }]) {
    assert.deepEqual(tw.resolveSync(context, 'k.deep', extra), whole);
    assert.deepEqual(await tw.resolve(context, 'k.deep', extra), whole);
  }
  assert.ok(whole.contributions.length > 1, 'a scope\'s own value appears only as a contribution');
  const facts = { facts: { 'parent.flag': true, 'whole.kind': 'b' } };
  const decision = tw.decideSync(context, 'f.one', facts);
  assert.deepEqual(tw.decideSync(context, 'f.one', facts, { level: 'top' }), decision);
});

// ---------------------------------------------------------------------------
// EVAL-46 to EVAL-50
// ---------------------------------------------------------------------------

test('EVAL-46: an operation does not modify the loaded bundle', async () => {
  const model = modelOf(loaded);
  const before = snapshot(model);
  const shell = { ...loaded };
  const tw = evaluator();
  for (const context of CONTEXTS) {
    for (const key of KEYS) {
      for (const outcomeOf of [await outcome(tw, { op: 'resolve', context, name: key }), await outcome(tw, { op: 'resolveSync', context, name: key })]) {
        tryToChange(outcomeOf.result);
      }
    }
    for (const feature of FEATURES) {
      for (const facts of FACT_SETS) tryToChange((await outcome(tw, { op: 'decideSync', context, name: feature, facts })).result);
    }
  }
  assert.ok(isDeepStrictEqual(snapshot(model), before), 'the model of the bundle is unchanged');
  assert.deepEqual({ ...loaded }, shell);
  assert.ok(Object.isFrozen(loaded));
});

/** A deep copy that records every member and element, with the same shape whatever the prototypes of the input. */
function snapshot(value) {
  if (typeof value !== 'object' || value === null) return value;
  if (value instanceof Map) return { map: [...value.entries()].map(([key, entry]) => [key, snapshot(entry)]) };
  if (value instanceof Set) return { set: [...value.values()].map(snapshot) };
  if (Array.isArray(value)) return value.map(snapshot);
  return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, snapshot(entry)]));
}

/** Tries to change every reachable part of a result; a frozen part throws, which is fine. */
function tryToChange(value) {
  if (typeof value !== 'object' || value === null) return;
  for (const name of Object.keys(value)) {
    tryToChange(value[name]);
    try {
      if (Array.isArray(value)) value.length = 0;
      else value[name] = 'changed';
    } catch {
      // a frozen result cannot be changed
    }
  }
}

test('EVAL-47: a returned value shares no mutable state with the provider map, the bundle or the evaluator', async () => {
  const reply = { 'k.deep': { a: 3 }, 'k.union': ['u2'] };
  const tw = evaluator(async () => reply);
  const context = { levels: { mid: 'm1', low: 'n2' } };
  const deep = await tw.resolve(context, 'k.deep');
  const union = await tw.resolve(context, 'k.union');
  reply['k.deep'].a = 99;
  reply['k.union'].push('u3');
  assert.equal(deep.value.a, 3, 'a change to the provider map after the call does not reach the result');
  assert.deepEqual(union.value, ['u1', 'u2']);
  tryToChange(deep);
  tryToChange(union);
  assert.deepEqual(reply, { 'k.deep': { a: 99 }, 'k.union': ['u2', 'u3'] }, 'a change to the result does not reach the provider map');
  const decision = tw.decideSync({ levels: { mid: 'm1' } }, 'f.obj', { facts: { 'parent.flag': true } });
  tryToChange(decision);
  assert.deepEqual(tw.decideSync({ levels: { mid: 'm1' } }, 'f.obj', { facts: { 'parent.flag': true } }).effect, { level: 2, note: 'n' });
});

test('EVAL-48: changing the options after creation changes neither the provider, the timeout nor the listener', async () => {
  const seen = [];
  const late = [];
  const slowSeven = async (node) => {
    await new Promise((resolve) => setTimeout(resolve, 20));
    if (node === 'down') throw new Error('down');
    return { 'k.min': 7 };
  };
  const options = { providers: { low: { fn: slowSeven, timeoutMs: 500 } }, listener: (event) => seen.push(event.type) };
  const tw = createEvaluator(loaded, options);
  options.providers.low.fn = async () => ({ 'k.min': 1 });
  options.providers.low.timeoutMs = 1;
  options.listener = (event) => late.push(event);
  options.providers.lane = { fn: async () => ({}), timeoutMs: 5 };
  assert.equal((await tw.resolve({ levels: { low: 'n1' } }, 'k.min')).value, 7, 'the original provider and timeout are used');
  await tw.resolve({ levels: { low: 'down' } }, 'k.min');
  assert.deepEqual(seen, ['BUNDLE_LOADED', 'SOURCE_UNAVAILABLE'], 'the original listener receives the events');
  assert.deepEqual(late, [], 'the listener set after creation is never called');
  assert.throws(() => {
    tw.resolve = () => null;
  }, TypeError);
  assert.equal(typeof tw.resolve({}, 'k.all').then, 'function');
});

test('EVAL-49: an operation does not modify the context, the FactSet or a provider map (property)', async () => {
  await fc.assert(
    fc.asyncProperty(callArb, async (call) => {
      const reply = { 'k.all': true, 'k.min': 7, 'k.deep': { a: 3 }, 'k.union': ['u2'], 'k.text': 'p' };
      const replyBefore = structuredClone(reply);
      const tw = evaluator(async () => reply);
      const context = structuredClone(call.context);
      const facts = structuredClone(call.facts);
      await outcome(tw, { ...call, context, facts });
      assert.deepEqual(context, call.context);
      assert.deepEqual(facts, call.facts);
      assert.deepEqual(reply, replyBefore);
    }),
    { numRuns: 150 },
  );
});

test('EVAL-50: a change that a caller makes to a returned result does not change a later result (property)', async () => {
  await fc.assert(
    fc.asyncProperty(callArb, async (call) => {
      const tw = evaluator();
      const first = await outcome(tw, call);
      const reference = structuredClone(first);
      tryToChange(first.result);
      assert.deepEqual(await outcome(tw, call), reference);
    }),
    { numRuns: 150 },
  );
});

// ---------------------------------------------------------------------------
// EVAL-51, EVAL-52
// ---------------------------------------------------------------------------

test('EVAL-51: a call gives the same outcome whatever calls ran before it (property)', async () => {
  await fc.assert(
    fc.asyncProperty(fc.array(callArb, { maxLength: 8 }), callArb, async (before, call) => {
      const alone = await outcome(evaluator(), call);
      const shared = evaluator();
      for (const other of before) await outcome(shared, other);
      assert.deepEqual(await outcome(shared, call), alone);
      // A second evaluator over the same loaded bundle is not affected by the first.
      assert.deepEqual(await outcome(evaluator(), call), alone);
    }),
    { numRuns: 100 },
  );
});

test('EVAL-52: concurrent calls on one evaluator, and on evaluators sharing one bundle, give their outcomes alone (property)', async () => {
  await fc.assert(
    fc.asyncProperty(fc.array(callArb, { minLength: 2, maxLength: 12 }), async (calls) => {
      const alone = [];
      for (const call of calls) alone.push(await outcome(evaluator(), call));
      const first = evaluator();
      const second = evaluator();
      const together = await Promise.all(calls.map((call, index) => outcome(index % 2 === 0 ? first : second, call)));
      assert.deepEqual(together, alone);
      const sameEvaluator = await Promise.all(calls.map((call) => outcome(first, call)));
      assert.deepEqual(sameEvaluator, alone);
    }),
    { numRuns: 40 },
  );
});

// ---------------------------------------------------------------------------
// PROV-9
// ---------------------------------------------------------------------------

const RESOLVE_CODES = ['E_CONTEXT_INVALID', 'E_UNKNOWN_SCOPE', 'E_UNKNOWN_NODE', 'E_UNKNOWN_KEY'];
const DECIDE_CODES = ['E_CONTEXT_INVALID', 'E_UNKNOWN_SCOPE', 'E_UNKNOWN_NODE', 'E_UNKNOWN_FEATURE', 'E_FACTS_INVALID'];
const ALLOWED = {
  resolve: RESOLVE_CODES,
  resolveSync: [...RESOLVE_CODES, 'E_PROVIDER_REQUIRED'],
  decide: DECIDE_CODES,
  decideSync: DECIDE_CODES,
};

const scopeName = fc.constantFrom('top', 'mid', 'low', 'lane', 'nowhere', '', 'constructor');
const nodeValue = fc.constantFrom('m1', 'm2', 'm3', 'l1', 'l9', 'n1', 'down', 'bad', '', 5, null, true, ['m1']);
const nodeMap = fc.dictionary(scopeName, nodeValue, { maxKeys: 3 });
const garbage = fc.constantFrom(undefined, null, 'x', 5, true, [], ['m1']);
const contextArb = fc.oneof(
  { arbitrary: fc.constantFrom(...CONTEXTS), weight: 4 },
  fc.record({ levels: nodeMap }),
  fc.record({ axes: nodeMap }),
  fc.record({ levels: nodeMap, axes: nodeMap }),
  fc.record({ levels: garbage }),
  garbage,
);
const factName = fc.constantFrom('parent.flag', 'whole.kind', 'whole.count', 'whole.price', 'part.tag', 'part.qty', 'nope');
const factValue = fc.constantFrom(true, false, 'a', 'b', 'x', 'nope', 3, 1.5, '1.5', -1, 2 ** 53, null, [], {});
const factMap = fc.dictionary(factName, factValue, { maxKeys: 3 });
const factSetArb = fc.oneof(
  { arbitrary: fc.constantFrom(...FACT_SETS), weight: 3 },
  fc.record({ facts: factMap }),
  fc.record({ facts: factMap, children: fc.record({ part: fc.array(factMap, { maxLength: 2 }) }) }),
  fc.record({ children: fc.dictionary(fc.constantFrom('part', 'whole', 'nope'), fc.oneof(fc.array(factMap, { maxLength: 2 }), garbage)) }),
  garbage,
);
const nameArb = fc.constantFrom(...KEYS, ...FEATURES, '', undefined, null, 5, {}, '__proto__');
const replyArb = fc.constantFrom('values', 'invalid', 'throw', 'reject', 'null', 'list', 'late');

function garbageProvider(reply) {
  return async () => {
    switch (reply) {
      case 'values':
        return { 'k.all': true, 'k.min': 7 };
      case 'invalid':
        return { 'k.min': 'x', 'k.inter': ['u9'], 'k.deep': { a: 'x' } };
      case 'throw':
        throw new Error('down');
      case 'reject':
        return Promise.reject(new Error('down'));
      case 'null':
        return null;
      case 'list':
        return ['k.all'];
      default:
        await new Promise((resolve) => setTimeout(resolve, 30));
        return { 'k.min': 7 };
    }
  };
}

test('PROV-9: a failure is raised as an evaluation error with its code and never returned as a result (property)', async () => {
  await fc.assert(
    fc.asyncProperty(contextArb, nameArb, factSetArb, replyArb, async (context, name, facts, reply) => {
      const tw = createEvaluator(loaded, { providers: { low: { fn: garbageProvider(reply), timeoutMs: 10 } } });
      for (const op of ['resolve', 'resolveSync', 'decide', 'decideSync']) {
        const got = await outcome(tw, { op, context, name, facts });
        if ('code' in got) {
          assert.ok(ALLOWED[op].includes(got.code), `${op} raised ${got.code}`);
          continue;
        }
        const result = got.result;
        assert.equal(result.bundleVersion, loaded.bundleVersion);
        assert.ok(Array.isArray(result.skippedScopes));
        assert.notEqual(result instanceof Error, true);
        if (op.startsWith('resolve')) {
          assert.equal(result.key, name);
          assert.ok(Array.isArray(result.contributions));
          assert.ok('value' in result && result.value !== undefined, 'a Resolved result carries a value');
        } else {
          assert.equal(result.feature, name);
          assert.ok(Array.isArray(result.trace));
          assert.ok('effect' in result && result.effect !== undefined, 'a Decision carries an effect');
        }
      }
    }),
    { numRuns: 200 },
  );
});

// ---------------------------------------------------------------------------
// EVAL-37
// ---------------------------------------------------------------------------

test('EVAL-37: an omitted or non-string key or feature is an unknown name, also when the argument is left out', async () => {
  const tw = evaluator();
  assert.equal(codeOf(() => tw.resolveSync({})), 'E_UNKNOWN_KEY');
  assert.equal(codeOf(() => tw.resolveSync()), 'E_UNKNOWN_KEY');
  assert.equal(await codeOfAsync(tw.resolve({})), 'E_UNKNOWN_KEY');
  assert.equal(await codeOfAsync(tw.resolve()), 'E_UNKNOWN_KEY');
  assert.equal(codeOf(() => tw.decideSync({})), 'E_UNKNOWN_FEATURE');
  assert.equal(codeOf(() => tw.decideSync()), 'E_UNKNOWN_FEATURE');
  assert.equal(await codeOfAsync(tw.decide({})), 'E_UNKNOWN_FEATURE');
  for (const name of [true, 0, 1.5, Symbol('k.all'), 10n, ['k.all'], new String('k.all'), () => 'k.all']) {
    assert.equal(codeOf(() => tw.resolveSync({}, name)), 'E_UNKNOWN_KEY', String(typeof name));
    assert.equal(await codeOfAsync(tw.resolve({}, name)), 'E_UNKNOWN_KEY', String(typeof name));
    assert.equal(codeOf(() => tw.decideSync({}, name, {})), 'E_UNKNOWN_FEATURE', String(typeof name));
  }
});

test('EVAL-37: an omitted or non-string name is reported at the lookup step, after the context and before the rest', async () => {
  const calls = [];
  const tw = evaluator(async (node) => {
    calls.push(node);
    return {};
  });
  for (const name of [undefined, null, 5, '']) {
    // Step 1 (context validation) comes first.
    assert.equal(codeOf(() => tw.resolveSync({ levels: { nowhere: 'x' } }, name)), 'E_UNKNOWN_SCOPE');
    assert.equal(codeOf(() => tw.resolveSync({ levels: { mid: 'm3' } }, name)), 'E_UNKNOWN_NODE');
    assert.equal(codeOf(() => tw.resolveSync({ levels: { top: '' } }, name)), 'E_CONTEXT_INVALID');
    assert.equal(codeOf(() => tw.decideSync({ levels: { nowhere: 'x' } }, name, { facts: { nope: 1 } })), 'E_UNKNOWN_SCOPE');
    // Step 2 (lookup) comes before the provider check of the synchronous variant and before any provider call.
    assert.equal(codeOf(() => tw.resolveSync({ levels: { low: 'n1' } }, name)), 'E_UNKNOWN_KEY');
    assert.equal(await codeOfAsync(tw.resolve({ levels: { low: 'n1' } }, name)), 'E_UNKNOWN_KEY');
    // Feature lookup comes before FactSet validation.
    assert.equal(codeOf(() => tw.decideSync({}, name, { facts: { nope: 1 } })), 'E_UNKNOWN_FEATURE');
    assert.equal(codeOf(() => tw.decideSync({}, name, 'not a FactSet')), 'E_UNKNOWN_FEATURE');
  }
  assert.deepEqual(calls, []);
});
