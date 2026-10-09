// create evaluator rules that a vector cannot express (VEC-45, VEC-125): it is synchronous (EVAL-5) and it calls no
// provider, not even later on its own, including for a service-sourced level whose provider holds values (EVAL-6).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { api, loadContent } from './support.mjs';
import { bundle, changed } from './fixtures.mjs';

const { createEvaluator } = api;
const loaded = bundle();

/** The base content with a second service-sourced level, `leaf`, below `low`. */
const twoLevels = loadContent(
  changed((c) => {
    c.hierarchy.levels.push('leaf');
    c.hierarchy.scopes.leaf = { nodes: 'open', values: 'service' };
    c.keys['k.all'].settableAt = ['top', 'mid', 'low', 'leaf', 'lane'];
  }),
);

test('EVAL-5: create evaluator is synchronous and returns its evaluator directly', () => {
  const tw = createEvaluator(loaded, { providers: { low: { fn: async () => ({ 'k.all': false }), timeoutMs: 50 } } });
  assert.equal(typeof tw.then, 'undefined', 'the evaluator is not a promise or a thenable');
  assert.equal(typeof tw.resolve, 'function');
  // The evaluator works in the same turn of the event loop, before any promise could settle.
  assert.equal(tw.resolveSync({ levels: { mid: 'm1' } }, 'k.min').value, 30);
  assert.equal(tw.decideSync({}, 'f.one', { facts: { 'parent.flag': true, 'whole.kind': 'a' }, children: { part: [{ 'part.tag': 'x' }] } }).effect, 'ALLOW');
});

test('EVAL-6: create evaluator calls no provider, including a provider that holds values', async () => {
  const calls = [];
  const recording = (level) => (node) => {
    calls.push(`${level}:${node}`);
    return { 'k.all': false };
  };
  const tw = createEvaluator(twoLevels, {
    providers: { low: { fn: recording('low'), timeoutMs: 50 }, leaf: { fn: recording('leaf'), timeoutMs: 50 } },
    listener: () => {},
  });
  assert.deepEqual(calls, [], 'no call while creating');
  await new Promise((resolve) => setTimeout(resolve, 20));
  assert.deepEqual(calls, [], 'no call deferred to later either');
  const result = await tw.resolve({ levels: { low: 'n1', leaf: 'f1' } }, 'k.all');
  assert.equal(result.value, false);
  assert.deepEqual(calls, ['low:n1', 'leaf:f1'], 'providers are called only by resolve (SVC-17)');
});

test('SVC-41: a timeout is valid from 1 ms up to 2^53-1 ms, and not above (ADR 0025)', () => {
  const fn = async () => ({});
  for (const timeoutMs of [1, Number.MAX_SAFE_INTEGER]) {
    assert.equal(typeof createEvaluator(loaded, { providers: { low: { fn, timeoutMs } } }).resolve, 'function', String(timeoutMs));
  }
  for (const timeoutMs of [2 ** 53, 2 ** 60, Number.MAX_VALUE]) {
    assert.throws(() => createEvaluator(loaded, { providers: { low: { fn, timeoutMs } } }), { code: 'E_EVALUATOR_OPTIONS' }, String(timeoutMs));
  }
});
