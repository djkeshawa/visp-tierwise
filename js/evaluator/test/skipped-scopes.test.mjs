// skippedScopes is a property of the context: it never names the root level (PROV-57), holds at most one entry for
// each scope (PROV-59), and is the same in every result computed from one context (PROV-60).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import fc from 'fast-check';
import { api, codeOf, codeOfAsync, loadContent } from './support.mjs';
import { changed } from './fixtures.mjs';

const { createEvaluator, TierwiseError } = api;

// The base content with two scopes closed under `ignore`: the level `mid` and the axis `lane`.
const content = changed((c) => {
  c.hierarchy.scopes.mid.onUnknownNode = 'ignore';
});
const loaded = loadContent(content);
const KEYS = Object.keys(content.keys);
const FEATURES = Object.keys(content.features);
const FACTS = { facts: { 'parent.flag': true, 'whole.kind': 'b' }, children: { part: [{ 'part.tag': 'x' }] } };

function evaluator() {
  return createEvaluator(loaded, { providers: { low: { fn: async () => ({ 'k.min': 3 }), timeoutMs: 200 } } });
}

const contextArb = fc
  .record({
    mid: fc.constantFrom(undefined, 'm1', 'm2', 'm9', 'M1'),
    low: fc.constantFrom(undefined, 'n1'),
    lane: fc.constantFrom(undefined, 'l1', 'l2', 'l9', 'L1'),
  })
  .map(({ mid, low, lane }) => {
    const levels = {};
    if (mid !== undefined) levels.mid = mid;
    if (low !== undefined) levels.low = low;
    return lane === undefined ? { levels } : { levels, axes: { lane } };
  });

/** The skippedScopes of every result that one context gives, over every key and feature and every variant. */
async function everySkippedScopes(tw, context) {
  const lists = [];
  for (const key of KEYS) {
    lists.push((await tw.resolve(context, key)).skippedScopes);
    try {
      lists.push(tw.resolveSync(context, key).skippedScopes);
    } catch (error) {
      if (!(error instanceof TierwiseError) || error.code !== 'E_PROVIDER_REQUIRED') throw error;
    }
  }
  for (const feature of FEATURES) {
    lists.push(tw.decideSync(context, feature, feature === 'f.one' ? FACTS : { facts: { 'parent.flag': true } }).skippedScopes);
    lists.push((await tw.decide(context, feature, {})).skippedScopes);
  }
  return lists;
}

test('PROV-57: a context that names the root level raises E_CONTEXT_INVALID, so skippedScopes never names it', async () => {
  const tw = evaluator();
  for (const context of [{ levels: { top: '' } }, { levels: { top: 'x', mid: 'm9' } }, { axes: { top: 'x' } }]) {
    assert.equal(codeOf(() => tw.resolveSync(context, 'k.all')), 'E_CONTEXT_INVALID', JSON.stringify(context));
    assert.equal(await codeOfAsync(tw.resolve(context, 'k.all')), 'E_CONTEXT_INVALID', JSON.stringify(context));
    assert.equal(codeOf(() => tw.decideSync(context, 'f.one', FACTS)), 'E_CONTEXT_INVALID', JSON.stringify(context));
  }
  await fc.assert(
    fc.asyncProperty(contextArb, async (context) => {
      for (const list of await everySkippedScopes(tw, context)) {
        assert.ok(list.every((entry) => entry.name !== 'top'), JSON.stringify(list));
      }
    }),
    { numRuns: 60 },
  );
});

test('PROV-59: skippedScopes holds at most one entry for each scope', async () => {
  const tw = evaluator();
  const both = tw.resolveSync({ levels: { mid: 'm9' }, axes: { lane: 'l9' } }, 'k.over');
  assert.deepEqual(both.skippedScopes, [
    { scope: 'level', name: 'mid', node: 'm9', reason: 'UNKNOWN_NODE' },
    { scope: 'axis', name: 'lane', node: 'l9', reason: 'UNKNOWN_NODE' },
  ]);
  await fc.assert(
    fc.asyncProperty(contextArb, async (context) => {
      for (const list of await everySkippedScopes(tw, context)) {
        const names = list.map((entry) => `${entry.scope}:${entry.name}`);
        assert.equal(new Set(names).size, names.length, JSON.stringify(list));
      }
    }),
    { numRuns: 60 },
  );
});

test('PROV-60: every result computed from one context reports the same skippedScopes', async () => {
  const tw = evaluator();
  await fc.assert(
    fc.asyncProperty(contextArb, async (context) => {
      const expected = [];
      const mid = context.levels.mid;
      if (mid !== undefined && mid !== 'm1' && mid !== 'm2') {
        expected.push({ scope: 'level', name: 'mid', node: mid, reason: 'UNKNOWN_NODE' });
      }
      const lane = context.axes?.lane;
      if (lane !== undefined && lane !== 'l1' && lane !== 'l2') {
        expected.push({ scope: 'axis', name: 'lane', node: lane, reason: 'UNKNOWN_NODE' });
      }
      const lists = await everySkippedScopes(tw, context);
      assert.ok(lists.length > KEYS.length + FEATURES.length);
      for (const list of lists) assert.deepEqual(list, expected);
    }),
    { numRuns: 80 },
  );
});
