// The codes that the evaluation operations raise (EVAL-38): only the eleven codes of requirements §6.2, and each
// operation only the codes whose triggers EVAL-24 to EVAL-37 give it. A vector observes a code only at its trigger,
// so these tests are the enforcer of EVAL-38 (spec/conformance/enforcers.d/E6.yaml). Every input that the
// generators build is either valid or a caller error, so an operation must return a result or raise one of its codes.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import fc from 'fast-check';
import { api, bundleBytes } from './support.mjs';
import { content } from './fixtures.mjs';

const { createEvaluator, loadBundle, TierwiseError, ERROR_CODES } = api;

const ELEVEN = [
  'E_BUNDLE_INTEGRITY',
  'E_BUNDLE_UNSUPPORTED',
  'E_PROVIDER_MISSING',
  'E_PROVIDER_REQUIRED',
  'E_CONTEXT_INVALID',
  'E_UNKNOWN_SCOPE',
  'E_UNKNOWN_NODE',
  'E_UNKNOWN_KEY',
  'E_UNKNOWN_FEATURE',
  'E_FACTS_INVALID',
  'E_EVALUATOR_OPTIONS',
];

// The codes that each operation may raise, from the triggers of EVAL-24 to EVAL-37.
const LOAD = new Set(['E_BUNDLE_INTEGRITY', 'E_BUNDLE_UNSUPPORTED']); // EVAL-24, EVAL-25
const CREATE = new Set(['E_EVALUATOR_OPTIONS', 'E_PROVIDER_MISSING']); // EVAL-26 to EVAL-28
const CONTEXT = ['E_CONTEXT_INVALID', 'E_UNKNOWN_SCOPE', 'E_UNKNOWN_NODE']; // EVAL-30 to EVAL-33
const RESOLVE = new Set([...CONTEXT, 'E_UNKNOWN_KEY']); // EVAL-34, EVAL-37
const RESOLVE_SYNC = new Set([...RESOLVE, 'E_PROVIDER_REQUIRED']); // EVAL-29
const DECIDE = new Set([...CONTEXT, 'E_UNKNOWN_FEATURE', 'E_FACTS_INVALID']); // EVAL-35 to EVAL-37

/** Runs a call and returns the code it raises, or null. Any other exception fails the test. */
async function codeRaised(run) {
  try {
    await run();
    return null;
  } catch (error) {
    if (error instanceof TierwiseError) return error.code;
    throw new assert.AssertionError({ message: `a non-evaluation error was raised: ${error}` });
  }
}

test('EVAL-38: the package declares exactly the eleven evaluation codes', () => {
  assert.deepEqual([...ERROR_CODES].sort(), [...ELEVEN].sort());
});

test('EVAL-38: load bundle raises only E_BUNDLE_INTEGRITY and E_BUNDLE_UNSUPPORTED (EVAL-24, EVAL-25)', async () => {
  const good = bundleBytes(content());
  await fc.assert(
    fc.asyncProperty(fc.nat(good.length - 1), fc.integer({ min: 0, max: 255 }), fc.nat(good.length), async (at, byte, cut) => {
      const flipped = new Uint8Array(good);
      flipped[at] = byte;
      for (const input of [flipped, good.slice(0, cut)]) {
        const code = await codeRaised(() => loadBundle(input));
        assert.ok(code === null || LOAD.has(code), `load bundle raised ${code}`);
      }
    }),
    { numRuns: 300 },
  );
  for (const [member, value] of [['specVersion', '0.2'], ['bundleFormatVersion', 2]]) {
    const c = content();
    c[member] = value;
    assert.equal(await codeRaised(() => loadBundle(bundleBytes(c))), 'E_BUNDLE_UNSUPPORTED');
  }
});

test('EVAL-38: create evaluator raises only E_EVALUATOR_OPTIONS and E_PROVIDER_MISSING (EVAL-26 to EVAL-28)', async () => {
  const loaded = loadBundle(bundleBytes(content()));
  const fn = () => ({});
  const registration = fc.oneof(
    fc.constant({ fn, timeoutMs: 100 }),
    fc.record({ fn: fc.constantFrom(fn, 'x', null), timeoutMs: fc.oneof(fc.integer({ min: -2, max: 2 }), fc.double(), fc.constant('100')) }),
    fc.constantFrom(null, 'x', [], {}),
  );
  const providers = fc.dictionary(fc.constantFrom('low', 'mid', 'top', 'lane', 'nowhere', ''), registration, { maxKeys: 3 });
  const options = fc.oneof(
    fc.record({ providers }, { requiredKeys: [] }),
    fc.record({ providers, listener: fc.constantFrom(() => {}, 'x', null), extra: fc.constant(1) }, { requiredKeys: [] }),
    fc.constantFrom(undefined, null, 'x', 5, []),
  );
  await fc.assert(
    fc.asyncProperty(options, async (opts) => {
      const code = await codeRaised(() => createEvaluator(loaded, opts));
      assert.ok(code === null || CREATE.has(code), `create evaluator raised ${code}`);
    }),
    { numRuns: 300 },
  );
});

test('EVAL-38: resolve, resolveSync, decide and decideSync raise only the codes of their triggers (EVAL-29 to EVAL-37)', async () => {
  const loaded = loadBundle(bundleBytes(content()));
  const tw = createEvaluator(loaded, { providers: { low: { fn: async () => ({ 'k.all': 'not a boolean' }), timeoutMs: 100 } } });
  const scopes = fc.constantFrom('top', 'mid', 'low', 'lane', 'nowhere', 'Mid', '', 'constructor');
  const nodes = fc.oneof(fc.constantFrom('m1', 'm2', 'm3', 'l1', 'l9', 'n1', '', 'x'.repeat(129), 'a\u0007', 'a\ud800'), fc.constantFrom(5, null, {}));
  const scopeMap = fc.dictionary(scopes, nodes, { maxKeys: 3 });
  const context = fc.oneof(
    fc.record({ levels: scopeMap, axes: scopeMap }, { requiredKeys: [] }),
    fc.record({ levels: scopeMap, extra: fc.constant({}) }),
    fc.constantFrom(undefined, null, [], 'x', 5, { levels: null }, { axes: [] }),
  );
  const names = fc.constantFrom(undefined, null, 5, '', 'k.all', 'k.over', 'k.deep', 'k.text', 'f.one', 'f.obj', 'f.null', 'ghost');
  const factNames = fc.constantFrom('parent.flag', 'whole.kind', 'whole.count', 'whole.price', 'part.tag', 'ghost.x', '__proto__');
  const factValues = fc.constantFrom(true, 'a', 'b', 'x', 1, 1.5, '1.50', null, [], {}, 2 ** 53, '\udc00');
  const facts = fc.oneof(
    fc.record({ facts: fc.dictionary(factNames, factValues, { maxKeys: 4 }), children: fc.constantFrom(undefined, { part: [{ 'part.tag': 'x' }] }, { part: [null] }, { ghost: [] }, []) }, { requiredKeys: [] }),
    fc.constantFrom(undefined, null, [], 'x', { extra: 1 }),
  );
  await fc.assert(
    fc.asyncProperty(context, names, facts, async (ctx, name, factSet) => {
      for (const [operation, allowed, run] of [
        ['resolve', RESOLVE, () => tw.resolve(ctx, name)],
        ['resolveSync', RESOLVE_SYNC, () => tw.resolveSync(ctx, name)],
        ['decide', DECIDE, () => tw.decide(ctx, name, factSet)],
        ['decideSync', DECIDE, () => tw.decideSync(ctx, name, factSet)],
      ]) {
        const code = await codeRaised(run);
        assert.ok(code === null || allowed.has(code), `${operation} raised ${code}`);
      }
    }),
    { numRuns: 400 },
  );
});

test('EVAL-38: each operation reaches each of its own codes at its trigger', async () => {
  const loaded = loadBundle(bundleBytes(content()));
  assert.equal(await codeRaised(() => createEvaluator(loaded, {})), 'E_PROVIDER_MISSING');
  assert.equal(await codeRaised(() => createEvaluator(loaded, 'x')), 'E_EVALUATOR_OPTIONS');
  const tw = createEvaluator(loaded, { providers: { low: { fn: () => ({}), timeoutMs: 100 } } });
  const cases = [
    [() => tw.resolve({ levels: { top: 'x' } }, 'k.all'), 'E_CONTEXT_INVALID'],
    [() => tw.resolve({ levels: { nowhere: 'x' } }, 'k.all'), 'E_UNKNOWN_SCOPE'],
    [() => tw.resolve({ levels: { mid: 'm9' } }, 'k.all'), 'E_UNKNOWN_NODE'],
    [() => tw.resolve({}, 'ghost'), 'E_UNKNOWN_KEY'],
    [() => tw.resolveSync({ levels: { low: 'n1' } }, 'k.all'), 'E_PROVIDER_REQUIRED'],
    [() => tw.decide({}, 'ghost', {}), 'E_UNKNOWN_FEATURE'],
    [() => tw.decide({}, 'f.one', { facts: { 'parent.flag': 'yes' } }), 'E_FACTS_INVALID'],
  ];
  for (const [run, code] of cases) {
    assert.equal(await codeRaised(run), code);
  }
});
