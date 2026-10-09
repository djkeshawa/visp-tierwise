// Provider outcomes that no evaluate vector can observe (VEC-125): multi-key requests (SVC-20, SVC-29, SVC-30,
// SVC-37), provider results that are not mappings (SVC-25), the order and concurrency of provider calls (SVC-21),
// levels that are not service-sourced (SVC-6), wall-clock timeouts (SVC-40, SVC-41), and the absence of deny
// entries for order-dependent keys (PROV-49).
//
// The public evaluator resolves one key at a time, so a multi-key request goes through the internal
// Resolver.resolveMany of the build output, wired as createEvaluator wires it.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import fc from 'fast-check';
import { api, codeOf, distUrl, loadContent } from './support.mjs';
import { bundle, changed } from './fixtures.mjs';

const internal = (file) => import(new URL(file, distUrl).href);
const { modelOf } = await internal('bundle.js');
const { ContextValidator } = await internal('context.js');
const { EventSink, ProviderCalls, checkOptions } = await internal('providers.js');
const { Resolver } = await internal('resolve.js');
const { createEvaluator } = api;

const loaded = bundle();
const WITH_LOW = { levels: { mid: 'm1', low: 'n1' } };
const FAILURE_EFFECTS = ['UNAVAILABLE', 'INVALID'];

/** A resolver wired as createEvaluator wires one, so that a test can make a multi-key request. */
function resolverOf(target, options) {
  const model = modelOf(target);
  const checked = checkOptions(model, options);
  const events = new EventSink(checked.listener, target.bundleVersion);
  const providers = new ProviderCalls(model, checked.registrations, events);
  return new Resolver(model, target.bundleVersion, new ContextValidator(model), providers);
}

/** A multi-key resolver over the base bundle whose provider for `low` answers with `reply`. */
function many(reply) {
  const calls = [];
  const events = [];
  const resolver = resolverOf(loaded, {
    providers: {
      low: {
        fn: async (node) => {
          calls.push(node);
          return reply(node);
        },
        timeoutMs: 500,
      },
    },
    listener: (event) => events.push(event),
  });
  return { resolver, calls, events };
}

/** A public evaluator over the base bundle whose provider for `low` answers with `reply`. */
function single(reply) {
  return createEvaluator(loaded, { providers: { low: { fn: async () => reply(), timeoutMs: 500 } } });
}

const lowEntry = (result) => result.contributions.find((entry) => entry.name === 'low');

/** The base content with a second service-sourced level, `leaf`, below `low`. */
const twoLevels = loadContent(
  changed((c) => {
    c.hierarchy.levels.push('leaf');
    c.hierarchy.scopes.leaf = { nodes: 'open', values: 'service' };
    c.keys['k.all'].settableAt = ['top', 'mid', 'low', 'leaf', 'lane'];
    c.keys['k.min'].settableAt = ['top', 'mid', 'low', 'leaf'];
    c.keys['k.leaf'] = { type: { type: 'boolean' }, combine: 'all', settableAt: ['top', 'leaf'], onSourceFailure: 'deny' };
  }),
);
const BOTH = { levels: { mid: 'm1', low: 'n1', leaf: 'f1' } };

// ---------------------------------------------------------------------------
// SVC-20: a multi-key request is one evaluation call
// ---------------------------------------------------------------------------

test('SVC-20: a request for several keys is one evaluation call, so the provider of a level is called once', async () => {
  const reply = () => ({ 'k.all': false, 'k.inter': ['u1'], 'k.min': 7 });
  const { resolver, calls } = many(reply);
  const results = await resolver.resolveMany(WITH_LOW, ['k.all', 'k.inter', 'k.min', 'k.over', 'k.all']);
  assert.deepEqual(calls, ['n1'], 'one call for the whole request');
  assert.deepEqual(results.map((r) => r.key), ['k.all', 'k.inter', 'k.min', 'k.over', 'k.all']);
  assert.equal(results[0].value, false);
  assert.deepEqual(results[1].value, ['u1']);
  assert.equal(results[2].value, 7);
  assert.deepEqual(results[4], results[0]);
  const tw = single(reply);
  for (const result of results) {
    assert.deepEqual(result, await tw.resolve(WITH_LOW, result.key), `${result.key} as a single-key resolve`);
  }
});

test('SVC-20: a multi-key request calls each service-sourced level at most once', async () => {
  const calls = { low: [], leaf: [] };
  const resolver = resolverOf(twoLevels, {
    providers: {
      low: { fn: async (node) => (calls.low.push(node), { 'k.all': true }), timeoutMs: 500 },
      leaf: { fn: async (node) => (calls.leaf.push(node), { 'k.leaf': false }), timeoutMs: 500 },
    },
  });
  const [all, leaf, min] = await resolver.resolveMany(BOTH, ['k.all', 'k.leaf', 'k.min']);
  assert.deepEqual(calls, { low: ['n1'], leaf: ['f1'] });
  assert.equal(all.value, true);
  assert.equal(leaf.value, false);
  assert.equal(min.value, 30);
});

// ---------------------------------------------------------------------------
// SVC-25: a provider result that is not a mapping
// ---------------------------------------------------------------------------

test('SVC-25: a result that is null, a list or a scalar is UNAVAILABLE for each key being resolved at the level', async () => {
  for (const [name, reply] of [
    ['null', null],
    ['undefined', undefined],
    ['a list', [{ 'k.all': true }]],
    ['an empty list', []],
    ['a string', 'k.all'],
    ['a number', 5],
    ['a boolean', true],
  ]) {
    const { resolver, events } = many(() => reply);
    const [all, inter, union, over] = await resolver.resolveMany(WITH_LOW, ['k.all', 'k.inter', 'k.union', 'k.over']);
    assert.deepEqual(lowEntry(all), { scope: 'level', name: 'low', node: 'n1', value: false, effect: 'UNAVAILABLE', onSourceFailure: 'deny' }, name);
    assert.deepEqual(lowEntry(inter), { scope: 'level', name: 'low', node: 'n1', value: [], effect: 'UNAVAILABLE', onSourceFailure: 'deny' }, name);
    assert.deepEqual(lowEntry(union), { scope: 'level', name: 'low', node: 'n1', effect: 'UNAVAILABLE', onSourceFailure: 'skip' }, name);
    assert.equal(lowEntry(over), undefined, `${name}: k.over is not settable at low, so it has no outcome there`);
    assert.equal(all.value, false, name);
    assert.deepEqual(inter.value, [], name);
    assert.deepEqual(union.value, ['u1'], name);
    assert.deepEqual(
      events.map((e) => `${e.type} ${e.key}`).sort(),
      ['SOURCE_UNAVAILABLE k.all', 'SOURCE_UNAVAILABLE k.inter', 'SOURCE_UNAVAILABLE k.union'],
      name,
    );
  }
});

// ---------------------------------------------------------------------------
// SVC-29, SVC-30, SVC-37: entries that a multi-key request ignores or rejects
// ---------------------------------------------------------------------------

test('SVC-29: an entry for a key being resolved that is not settable at the level is ignored, with no value and no failure', async () => {
  const alone = await single(() => ({})).resolve(WITH_LOW, 'k.over');
  for (const ignored of [9, 'not an integer', null, [1], { a: 1 }, true]) {
    const { resolver } = many(() => ({ 'k.all': false, 'k.over': ignored }));
    const [all, over] = await resolver.resolveMany(WITH_LOW, ['k.all', 'k.over']);
    assert.equal(all.value, false, JSON.stringify(ignored));
    assert.equal(over.value, 6, 'the value of mid m1, not the ignored entry');
    assert.deepEqual(over.source, { scope: 'level', name: 'mid', node: 'm1' });
    assert.ok(over.contributions.every((entry) => !FAILURE_EFFECTS.includes(entry.effect)), 'no source failure');
    assert.deepEqual(over, alone, 'the same result as when no provider is called');
  }
});

test('SVC-30: an ignored entry adds no contribution entry for any label, and no deny value at that level', async () => {
  for (const ignored of [false, true, 'bad', null]) {
    const resolver = resolverOf(twoLevels, {
      providers: {
        low: { fn: async () => ({ 'k.all': true, 'k.leaf': ignored }), timeoutMs: 500 },
        leaf: { fn: async () => ({}), timeoutMs: 500 },
      },
    });
    const [, leaf] = await resolver.resolveMany(BOTH, ['k.all', 'k.leaf']);
    assert.deepEqual(leaf.contributions, [], `${JSON.stringify(ignored)}: no entry at low, and leaf left the key out`);
    assert.equal(leaf.value, true, 'k.leaf is a deny key, and no deny value contributes at low');
  }
});

test('SVC-37: an ignored or invalid entry changes the outcome of no other key', async () => {
  const { resolver } = many(() => ({ 'k.all': false, 'k.min': 'bad', 'k.over': 'x', 'k.inter': ['u1'], 'k.text': null }));
  const [all, min, over, inter, text] = await resolver.resolveMany(WITH_LOW, ['k.all', 'k.min', 'k.over', 'k.inter', 'k.text']);
  assert.equal(lowEntry(min).effect, 'INVALID');
  assert.equal(lowEntry(text).effect, 'INVALID');
  assert.deepEqual(all, await single(() => ({ 'k.all': false })).resolve(WITH_LOW, 'k.all'));
  assert.deepEqual(inter, await single(() => ({ 'k.inter': ['u1'] })).resolve(WITH_LOW, 'k.inter'));
  assert.deepEqual(over, await single(() => ({})).resolve(WITH_LOW, 'k.over'));
  assert.deepEqual(min, await single(() => ({ 'k.min': 'bad' })).resolve(WITH_LOW, 'k.min'));
});

// ---------------------------------------------------------------------------
// SVC-6: levels that are not service-sourced
// ---------------------------------------------------------------------------

test('SVC-6: no source failure is recorded for a level that is not service-sourced', async () => {
  const keys = ['k.all', 'k.any', 'k.inter', 'k.union', 'k.min', 'k.max', 'k.over', 'k.hier', 'k.deep', 'k.text'];
  const events = [];
  const tw = createEvaluator(loaded, {
    providers: { low: { fn: async () => { throw new Error('down'); }, timeoutMs: 100 } },
    listener: (event) => events.push(event),
  });
  for (const context of [{}, { levels: { mid: 'm1' }, axes: { lane: 'l1' } }, { levels: { mid: 'm2' }, axes: { lane: 'l2' } }]) {
    for (const key of keys) {
      const result = await tw.resolve(context, key);
      for (const entry of result.contributions) {
        assert.ok(!FAILURE_EFFECTS.includes(entry.effect), `${key} at ${entry.name}`);
        assert.ok(!('onSourceFailure' in entry), `${key} at ${entry.name}`);
      }
    }
  }
  for (const key of keys) {
    const result = await tw.resolve({ levels: { mid: 'm1', low: 'n1' }, axes: { lane: 'l1' } }, key);
    const failed = result.contributions.filter((entry) => FAILURE_EFFECTS.includes(entry.effect)).map((entry) => entry.name);
    assert.ok(failed.every((name) => name === 'low'), `${key}: only the service-sourced level fails`);
  }
  assert.ok(events.slice(1).every((event) => event.level === 'low'));
});

// ---------------------------------------------------------------------------
// SVC-21: the order and concurrency of provider calls
// ---------------------------------------------------------------------------

const OUTCOMES = {
  narrows: () => ({ 'k.all': false, 'k.min': 12, 'k.leaf': false, 'k.inter': ['u2'] }),
  identity: () => ({ 'k.all': true, 'k.min': 100, 'k.leaf': true }),
  invalid: () => ({ 'k.all': 'no', 'k.min': -1, 'k.leaf': null }),
  omits: () => ({}),
  unknown: () => ({ 'k.ghost': 1, 'k.over': 3 }),
  throws: () => {
    throw new Error('down');
  },
  'not a mapping': () => [],
};
const KEYS = ['k.all', 'k.min', 'k.leaf', 'k.inter', 'k.over'];

const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Runs one multi-key request over two service-sourced levels; each provider answers after its delay. */
async function runTwo(lowOutcome, leafOutcome, lowDelay, leafDelay) {
  const events = [];
  const answer = (outcome, ms) => async () => {
    if (ms > 0) await delay(ms);
    return OUTCOMES[outcome]();
  };
  const resolver = resolverOf(twoLevels, {
    providers: {
      low: { fn: answer(lowOutcome, lowDelay), timeoutMs: 1000 },
      leaf: { fn: answer(leafOutcome, leafDelay), timeoutMs: 1000 },
    },
    listener: (event) => events.push(event),
  });
  const results = await resolver.resolveMany(BOTH, KEYS);
  return { results, events: events.map((e) => JSON.stringify(e)).sort() };
}

test('SVC-21: property: a result does not depend on the order in which the provider calls finish', async () => {
  const outcome = fc.constantFrom(...Object.keys(OUTCOMES));
  await fc.assert(
    fc.asyncProperty(outcome, outcome, fc.nat(6), fc.nat(6), async (low, leaf, lowDelay, leafDelay) => {
      const immediate = await runTwo(low, leaf, 0, 0);
      assert.deepEqual(await runTwo(low, leaf, lowDelay + 8, leafDelay), immediate, 'low finishes last');
      assert.deepEqual(await runTwo(low, leaf, lowDelay, leafDelay + 8), immediate, 'leaf finishes last');
    }),
    { numRuns: 30 },
  );
});

test('SVC-21: property: concurrent evaluation calls give the result each gives alone', async () => {
  const outcome = fc.constantFrom(...Object.keys(OUTCOMES));
  await fc.assert(
    fc.asyncProperty(fc.array(fc.tuple(outcome, outcome, fc.nat(5), fc.nat(5)), { minLength: 2, maxLength: 5 }), async (calls) => {
      const alone = [];
      for (const [low, leaf] of calls) alone.push((await runTwo(low, leaf, 0, 0)).results);
      const together = await Promise.all(calls.map(([low, leaf, a, b]) => runTwo(low, leaf, a, b)));
      assert.deepEqual(together.map((run) => run.results), alone);
    }),
    { numRuns: 20 },
  );
});

// ---------------------------------------------------------------------------
// SVC-40, SVC-41: the timeout runs until the result is available
// ---------------------------------------------------------------------------

/** Blocks the thread for `ms` milliseconds, then returns `reply`. */
function busy(ms, reply) {
  const end = Date.now() + ms;
  while (Date.now() < end) {
    // A provider that does synchronous work holds the thread, so the evaluator's timer cannot fire meanwhile.
  }
  return reply;
}

test('SVC-40, SVC-41: a result that becomes available after the timeout is not used, even when the provider blocked the timer', async () => {
  for (const [name, fn] of [
    ['a synchronous provider', () => busy(60, { 'k.min': 3 })],
    ['an asynchronous provider', async () => busy(60, { 'k.min': 3 })],
    ['a provider that resolves after blocking', () => Promise.resolve().then(() => busy(60, { 'k.min': 3 }))],
  ]) {
    const events = [];
    const tw = createEvaluator(loaded, { providers: { low: { fn, timeoutMs: 15 } }, listener: (e) => events.push(e) });
    const result = await tw.resolve(WITH_LOW, 'k.min');
    assert.deepEqual(lowEntry(result), { scope: 'level', name: 'low', node: 'n1', value: 0, effect: 'UNAVAILABLE', onSourceFailure: 'deny' }, name);
    assert.equal(result.value, 0, `${name}: the deny value, not the late 3`);
    assert.deepEqual(events.slice(1).map((e) => e.type), ['SOURCE_UNAVAILABLE'], name);
  }
});

test('SVC-41: a result that is available before the timeout is used', async () => {
  const tw = createEvaluator(loaded, { providers: { low: { fn: () => busy(5, { 'k.min': 3 }), timeoutMs: 2000 } } });
  const result = await tw.resolve(WITH_LOW, 'k.min');
  assert.equal(result.value, 3);
  assert.equal(lowEntry(result).effect, 'NARROWED');
  const slowButInTime = createEvaluator(loaded, { providers: { low: { fn: () => delay(20).then(() => ({ 'k.min': 4 })), timeoutMs: 2000 } } });
  assert.equal((await slowButInTime.resolve(WITH_LOW, 'k.min')).value, 4);
});

// ---------------------------------------------------------------------------
// PROV-49: no deny failure entry for an order-dependent key
// ---------------------------------------------------------------------------

test('PROV-49: a bundle with deny on an order-dependent key does not load', () => {
  for (const combine of ['override', 'deep_merge']) {
    const content = changed((c) => {
      c.keys[`k.${combine}`] = {
        type: { type: 'object', fields: { a: { type: 'integer' } } },
        combine,
        default: { a: 1 },
        settableAt: ['top', 'low'],
        onSourceFailure: 'deny',
      };
    });
    assert.equal(codeOf(() => loadContent(content)), 'E_BUNDLE_INTEGRITY', combine);
    content.keys[`k.${combine}`].onSourceFailure = 'skip';
    assert.equal(codeOf(() => loadContent(content)), null, `${combine} with skip loads`);
  }
});

test('PROV-49: property: no Resolved result of an order-dependent key has a failure entry under deny', async () => {
  const replies = {
    valid: () => ({ 'k.text': 'v', 'k.deep': { a: 3 }, 'k.over': 4, 'k.hier': 'h' }),
    invalid: () => ({ 'k.text': 5, 'k.deep': { a: 'x' }, 'k.over': 'x', 'k.hier': 1 }),
    nulls: () => ({ 'k.text': null, 'k.deep': null }),
    omits: () => ({}),
    throws: () => {
      throw new Error('down');
    },
    'not a mapping': () => 'text',
    late: () => delay(200).then(() => ({ 'k.text': 'late' })),
  };
  const orderDependent = ['k.over', 'k.hier', 'k.deep', 'k.text'];
  const context = fc.record(
    { levels: fc.record({ mid: fc.constantFrom('m1', 'm2'), low: fc.constant('n1') }, { requiredKeys: [] }), axes: fc.record({ lane: fc.constantFrom('l1', 'l2', 'zz') }, { requiredKeys: [] }) },
    { requiredKeys: [] },
  );
  await fc.assert(
    fc.asyncProperty(fc.constantFrom(...Object.keys(replies)), context, fc.subarray(orderDependent, { minLength: 1 }), async (reply, ctx, keys) => {
      const resolver = resolverOf(loaded, { providers: { low: { fn: async () => replies[reply](), timeoutMs: 30 } } });
      for (const result of await resolver.resolveMany(ctx, keys)) {
        for (const entry of result.contributions) {
          assert.notEqual(entry.onSourceFailure, 'deny', `${result.key} at ${entry.name} with ${reply}`);
        }
        const failures = result.contributions.filter((entry) => FAILURE_EFFECTS.includes(entry.effect));
        assert.ok(failures.every((entry) => entry.onSourceFailure === 'skip' && !('value' in entry)));
      }
    }),
    { numRuns: 60 },
  );
});
