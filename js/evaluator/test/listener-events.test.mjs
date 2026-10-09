// Listener events, which conformance vectors do not assert (EVAL-53, ADR 0014): the provider events of resolve
// (EVAL-41, SVC-43 to SVC-47), the operations that report none (EVAL-42), the load event (EVAL-43), delivery and
// failing listeners (EVAL-44), what is never reported (EVAL-45), and the event payload and concurrency (EVAL-53).
//
// SVC-47 concerns a multi-key request, which goes through the internal Resolver.resolveMany of the build output.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { api, codeOf, codeOfAsync, distUrl } from './support.mjs';
import { bundle } from './fixtures.mjs';

const internal = (file) => import(new URL(file, distUrl).href);
const { modelOf } = await internal('bundle.js');
const { ContextValidator } = await internal('context.js');
const { EventSink, ProviderCalls, checkOptions } = await internal('providers.js');
const { Resolver } = await internal('resolve.js');
const { createEvaluator } = api;

const loaded = bundle();
const WITH_LOW = { levels: { mid: 'm1', low: 'n1' } };
const SOURCE_FAILURES = ['SOURCE_UNAVAILABLE', 'SOURCE_INVALID'];
const lowEffect = (result) => result.contributions.find((c) => c.name === 'low')?.effect;

/** A public evaluator whose provider for `low` answers with `reply`; the load event is dropped from `events`. */
function evaluator(reply, timeoutMs = 500) {
  const events = [];
  const tw = createEvaluator(loaded, {
    providers: { low: { fn: async () => reply(), timeoutMs } },
    listener: (event) => events.push(event),
  });
  events.length = 0;
  return { tw, events };
}

/** A multi-key resolver wired as createEvaluator wires one, with a recording listener. */
function many(reply) {
  const events = [];
  const model = modelOf(loaded);
  const checked = checkOptions(model, {
    providers: { low: { fn: async () => reply(), timeoutMs: 500 } },
    listener: (event) => events.push(event),
  });
  const sink = new EventSink(checked.listener, loaded.bundleVersion);
  const resolver = new Resolver(model, loaded.bundleVersion, new ContextValidator(model), new ProviderCalls(model, checked.registrations, sink));
  return { resolver, events };
}

/** The type and key of each event, sorted: the spec fixes which events are reported, not their order. */
const brief = (events) => events.map((e) => `${e.type} ${e.key}`).sort();
const event = (type, key) => ({ type, bundleVersion: loaded.bundleVersion, level: 'low', node: 'n1', key });

// ---------------------------------------------------------------------------
// EVAL-41 and SVC-43 to SVC-47: the provider events of resolve
// ---------------------------------------------------------------------------

test('EVAL-41: resolve reports to a configured listener the provider events of the calls it makes', async () => {
  const { tw, events } = evaluator(() => ({ 'k.min': 'bad', 'k.ghost': 1 }));
  await tw.resolve(WITH_LOW, 'k.min');
  const asText = (list) => list.map((e) => JSON.stringify(e)).sort();
  assert.deepEqual(asText(events), asText([event('SOURCE_INVALID', 'k.min'), event('UNKNOWN_KEY', 'k.ghost')]));
  await tw.resolve({ levels: { mid: 'm1' } }, 'k.min');
  assert.equal(events.length, 2, 'a resolve that calls no provider reports no provider event');
  const quiet = createEvaluator(loaded, { providers: { low: { fn: async () => ({ 'k.min': 'bad' }), timeoutMs: 500 } } });
  assert.equal(lowEffect(await quiet.resolve(WITH_LOW, 'k.min')), 'INVALID', 'with no listener, resolve still records the failure');
});

test('SVC-43: the source failures reported are exactly the UNAVAILABLE and INVALID outcomes', async () => {
  const mixed = many(() => ({ 'k.all': 'no', 'k.inter': ['u1'], 'k.min': null, 'k.ghost': 1, 'k.over': 3 }));
  await mixed.resolver.resolveMany(WITH_LOW, ['k.all', 'k.inter', 'k.min', 'k.union', 'k.over']);
  const failures = mixed.events.filter((e) => SOURCE_FAILURES.includes(e.type));
  assert.deepEqual(brief(failures), ['SOURCE_INVALID k.all', 'SOURCE_INVALID k.min'], 'k.inter is valid and k.union is omitted');
  for (const reply of [() => { throw new Error('down'); }, () => null, () => [1]]) {
    const failing = many(reply);
    await failing.resolver.resolveMany(WITH_LOW, ['k.all', 'k.inter', 'k.min', 'k.union', 'k.over']);
    assert.deepEqual(brief(failing.events), [
      'SOURCE_UNAVAILABLE k.all',
      'SOURCE_UNAVAILABLE k.inter',
      'SOURCE_UNAVAILABLE k.min',
      'SOURCE_UNAVAILABLE k.union',
    ], 'one failure for each key being resolved at low; k.over is not settable there');
  }
});

test('SVC-44: a timeout is reported as an UNAVAILABLE source failure', async () => {
  const { tw, events } = evaluator(() => new Promise(() => {}), 15);
  const result = await tw.resolve(WITH_LOW, 'k.all');
  assert.equal(lowEffect(result), 'UNAVAILABLE');
  assert.deepEqual(events, [event('SOURCE_UNAVAILABLE', 'k.all')]);
});

test('SVC-45: the unknown keys reported are exactly the entries for undeclared keys, whatever their value', async () => {
  const { tw, events } = evaluator(() => ({ 'k.all': true, 'zz.unknown': 1, 'aa.unknown': null, 'K.ALL': false, 'k.min': 'bad', 'k.over': 1 }));
  await tw.resolve(WITH_LOW, 'k.all');
  assert.deepEqual(events.map((e) => e.type), ['UNKNOWN_KEY', 'UNKNOWN_KEY', 'UNKNOWN_KEY']);
  assert.deepEqual(events.map((e) => e.key).sort(), ['K.ALL', 'aa.unknown', 'zz.unknown']);
  assert.ok(events.every((e) => e.level === 'low' && e.node === 'n1'));
});

test('SVC-46: a key that the provider result leaves out is reported neither as a source failure nor as an unknown key', async () => {
  const one = evaluator(() => ({}));
  await one.tw.resolve(WITH_LOW, 'k.all');
  assert.deepEqual(one.events, []);
  const several = many(() => ({ 'k.inter': ['u1'] }));
  await several.resolver.resolveMany(WITH_LOW, ['k.all', 'k.inter', 'k.min', 'k.deep']);
  assert.deepEqual(several.events, []);
});

test('SVC-47: an ignored entry is reported, and is neither a source failure nor an unknown key', async () => {
  const { resolver, events } = many(() => ({ 'k.all': true, 'k.over': 'not an integer', 'k.any': null }));
  await resolver.resolveMany(WITH_LOW, ['k.all', 'k.over', 'k.any']);
  assert.deepEqual(brief(events), ['IGNORED_ENTRY k.any', 'IGNORED_ENTRY k.over']);
  assert.ok(events.every((e) => e.level === 'low' && e.node === 'n1' && e.bundleVersion === loaded.bundleVersion));
  const notRequested = many(() => ({ 'k.all': true, 'k.over': 'x' }));
  await notRequested.resolver.resolveMany(WITH_LOW, ['k.all']);
  assert.deepEqual(notRequested.events, [], 'an entry for a declared key that is not being resolved is not reported (SVC-28)');
});

// ---------------------------------------------------------------------------
// EVAL-42: operations that report no provider event
// ---------------------------------------------------------------------------

test('EVAL-42: decide and the synchronous variants of resolve and decide report no provider event', async () => {
  const { tw, events } = evaluator(() => { throw new Error('down'); });
  await tw.decide(WITH_LOW, 'f.one', {});
  tw.decideSync(WITH_LOW, 'f.one', {});
  tw.resolveSync(WITH_LOW, 'k.over');
  tw.resolveSync({ levels: { mid: 'm1' } }, 'k.all');
  assert.equal(codeOf(() => tw.resolveSync(WITH_LOW, 'k.all')), 'E_PROVIDER_REQUIRED');
  assert.deepEqual(events, []);
  await tw.resolve(WITH_LOW, 'k.all');
  assert.deepEqual(brief(events), ['SOURCE_UNAVAILABLE k.all'], 'the same provider does report through resolve');
});

// ---------------------------------------------------------------------------
// EVAL-43: the load event
// ---------------------------------------------------------------------------

test('EVAL-43: create evaluator emits one load event before it returns, and none when it raises an error', () => {
  const seen = [];
  let returned = false;
  createEvaluator(loaded, {
    providers: { low: { fn: async () => ({}), timeoutMs: 10 } },
    listener: (e) => seen.push({ ...e, beforeReturn: !returned }),
  });
  returned = true;
  assert.deepEqual(seen, [{ type: 'BUNDLE_LOADED', bundleVersion: loaded.bundleVersion, beforeReturn: true }]);
  const failed = [];
  const listener = (e) => failed.push(e);
  for (const [options, code] of [
    [{ listener }, 'E_PROVIDER_MISSING'],
    [{ providers: { low: { fn: async () => ({}), timeoutMs: 0 } }, listener }, 'E_EVALUATOR_OPTIONS'],
    [{ providers: { low: { fn: async () => ({}) } }, listener }, 'E_EVALUATOR_OPTIONS'],
    [{ providers: { low: { fn: async () => ({}), timeoutMs: 10 }, mid: { fn: async () => ({}), timeoutMs: 10 } }, listener }, 'E_EVALUATOR_OPTIONS'],
  ]) {
    assert.equal(codeOf(() => createEvaluator(loaded, options)), code);
  }
  assert.deepEqual(failed, []);
});

// ---------------------------------------------------------------------------
// EVAL-44: delivery, granularity and failing listeners
// ---------------------------------------------------------------------------

test('EVAL-44: each event is delivered before the operation returns, one for each affected key', async () => {
  const delivered = [];
  let settled = false;
  const tw = createEvaluator(loaded, {
    providers: { low: { fn: async () => { throw new Error('down'); }, timeoutMs: 500 } },
    listener: (e) => delivered.push({ key: e.key, beforeReturn: !settled }),
  });
  delivered.length = 0;
  const pending = tw.resolve(WITH_LOW, 'k.inter').then((result) => {
    settled = true;
    return result;
  });
  await pending;
  assert.deepEqual(delivered, [{ key: 'k.inter', beforeReturn: true }]);
  const { resolver, events } = many(() => ({ 'k.all': 'bad', 'k.min': 'bad', 'k.ghost': 1, 'k.over': 1 }));
  await resolver.resolveMany(WITH_LOW, ['k.all', 'k.min', 'k.over', 'k.all']);
  assert.deepEqual(brief(events), [
    'IGNORED_ENTRY k.over',
    'SOURCE_INVALID k.all',
    'SOURCE_INVALID k.min',
    'UNKNOWN_KEY k.ghost',
  ], 'one event for each affected key, even when a key is requested twice');
});

test('EVAL-44: a listener that throws changes no result and raises no error', async () => {
  const reply = async () => ({ 'k.all': 'bad', 'k.min': 7, 'k.ghost': 1 });
  const quiet = createEvaluator(loaded, { providers: { low: { fn: reply, timeoutMs: 500 } } });
  const throwing = createEvaluator(loaded, {
    providers: { low: { fn: reply, timeoutMs: 500 } },
    listener: () => {
      throw new Error('listener failure');
    },
  });
  for (const key of ['k.all', 'k.min', 'k.inter']) {
    assert.deepEqual(await throwing.resolve(WITH_LOW, key), await quiet.resolve(WITH_LOW, key), key);
  }
  assert.deepEqual(throwing.decideSync(WITH_LOW, 'f.one', {}), quiet.decideSync(WITH_LOW, 'f.one', {}));
});

// ---------------------------------------------------------------------------
// EVAL-45: what is never reported
// ---------------------------------------------------------------------------

test('EVAL-45: no raised error and no skipped scope is reported to the listener', async () => {
  const { tw, events } = evaluator(() => ({ 'k.all': true }));
  assert.equal(await codeOfAsync(tw.resolve({ levels: { mid: 'nope' } }, 'k.all')), 'E_UNKNOWN_NODE');
  assert.equal(await codeOfAsync(tw.resolve({ levels: { nowhere: 'x' } }, 'k.all')), 'E_UNKNOWN_SCOPE');
  assert.equal(await codeOfAsync(tw.resolve(WITH_LOW, 'k.nope')), 'E_UNKNOWN_KEY');
  assert.equal(await codeOfAsync(tw.decide(WITH_LOW, 'f.nope', {})), 'E_UNKNOWN_FEATURE');
  assert.equal(await codeOfAsync(tw.decide(WITH_LOW, 'f.one', { facts: { 'parent.flag': 'yes' } })), 'E_FACTS_INVALID');
  assert.equal(codeOf(() => tw.resolveSync(WITH_LOW, 'k.all')), 'E_PROVIDER_REQUIRED');
  const skipped = await tw.resolve({ levels: { mid: 'm1' }, axes: { lane: 'zz' } }, 'k.all');
  assert.equal(skipped.skippedScopes.length, 1);
  assert.deepEqual(events, []);
});

// ---------------------------------------------------------------------------
// EVAL-53: payload and concurrency
// ---------------------------------------------------------------------------

test('EVAL-53: the evaluator does not wait for a listener, and every event carries type and bundleVersion', async () => {
  const events = [];
  const tw = createEvaluator(loaded, {
    providers: {
      low: {
        fn: async (node) => {
          await new Promise((resolve) => setTimeout(resolve, node === 'slow' ? 20 : 1));
          return node === 'bad' ? null : { 'k.min': 'x', 'k.ghost': 1 };
        },
        timeoutMs: 500,
      },
    },
    // A listener that never settles: the evaluator must not serialise calls behind it.
    listener: (event) => {
      events.push(event);
      return new Promise(() => {});
    },
  });
  const nodes = ['slow', 'fast', 'bad', 'slow', 'fast'];
  const results = await Promise.all(nodes.map((low) => tw.resolve({ levels: { mid: 'm1', low } }, 'k.min')));
  assert.equal(results.length, nodes.length);
  assert.equal(events.length, 1 + 2 * 4 + 1, 'the load event, two for each node that answers, one for the bad node');
  for (const e of events) {
    assert.equal(typeof e.type, 'string');
    assert.equal(e.bundleVersion, loaded.bundleVersion);
    if (e.type === 'BUNDLE_LOADED') {
      assert.deepEqual(Object.keys(e).sort(), ['bundleVersion', 'type']);
    } else {
      assert.equal(e.level, 'low');
      assert.ok(nodes.includes(e.node));
      assert.equal(typeof e.key, 'string');
    }
  }
  const order = events.slice(1).map((e) => e.node);
  assert.ok(order.indexOf('fast') < order.indexOf('slow'), 'events of concurrent calls are not serialised in call order');
});
