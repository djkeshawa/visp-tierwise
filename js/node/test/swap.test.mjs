// The atomic swap and the evaluation helpers: a call that began before a swap finishes on the evaluator it began with,
// calls under concurrency always see one bundle, the helpers agree with a directly built evaluator, and the helpers
// refuse to run before a bundle is loaded (requirements §8 and §12).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createEvaluator, loadBundle } from '@tierwise/evaluator';
import { api, conformanceText, fromText, sleep, startStub, withRootValue } from './support.mjs';

const { createBundleLoader } = api;

const LOAD_A = fromText(conformanceText('edge-load.json'));
const LOAD_B = withRootValue(LOAD_A, 'example.min_safe', -5);

const FEATURE_BUNDLE = fromText(conformanceText('edge-features-small.json'));
const FEATURE = 'checkout.order_submission';

/** A service-sourced bundle, and the same bundle with a root value that changes the result for the same context. */
const SERVICE_A = fromText(conformanceText('edge-hierarchy-service.json'));
const SERVICE_B = withRootValue(SERVICE_A, 'checkout.open', false);
const ACCOUNT_CONTEXT = { levels: { account: 'a1' } };

async function setup(t, options = {}) {
  const stub = await startStub();
  t.after(() => stub.close());
  const loader = createBundleLoader({ url: stub.url, intervalMs: 60_000, ...options });
  t.after(() => loader.stop());
  return { stub, loader };
}

test('before a bundle is loaded, the helpers refuse to run', async (t) => {
  const { loader } = await setup(t);

  await assert.rejects(loader.resolve(undefined, 'example.min_safe'), { code: 'E_BUNDLE_NOT_LOADED' });
  assert.throws(() => loader.resolveSync(undefined, 'example.min_safe'), { code: 'E_BUNDLE_NOT_LOADED' });
  await assert.rejects(loader.decide(undefined, FEATURE, {}), { code: 'E_BUNDLE_NOT_LOADED' });
  assert.throws(() => loader.decideSync(undefined, FEATURE, {}), { code: 'E_BUNDLE_NOT_LOADED' });
});

test('the helpers return what a directly built evaluator returns for the same bundle', async (t) => {
  const { stub, loader } = await setup(t);
  stub.set(LOAD_A);
  await loader.start();
  const direct = createEvaluator(loadBundle(LOAD_A.text));

  assert.deepEqual(await loader.resolve(undefined, 'example.min_safe'), await direct.resolve(undefined, 'example.min_safe'));
  assert.deepEqual(loader.resolveSync(undefined, 'example.min_safe'), direct.resolveSync(undefined, 'example.min_safe'));
});

test('decide and decideSync return what a directly built evaluator returns', async (t) => {
  const { stub, loader } = await setup(t);
  stub.set(FEATURE_BUNDLE);
  await loader.start();
  const direct = createEvaluator(loadBundle(FEATURE_BUNDLE.text));

  const viaLoader = await loader.decide(undefined, FEATURE, {});
  assert.equal(viaLoader.feature, FEATURE);
  assert.deepEqual(viaLoader, await direct.decide(undefined, FEATURE, {}));
  assert.deepEqual(loader.decideSync(undefined, FEATURE, {}), direct.decideSync(undefined, FEATURE, {}));
});

test('a call that began before a swap finishes on the old evaluator, and later calls use the new one', async (t) => {
  const { stub, loader } = await setup(t, {
    evaluatorOptions: {
      providers: {
        // The provider is slow, so the call is still in flight when the swap happens.
        account: {
          fn: async () => {
            await sleep(80);
            return { 'checkout.open': true };
          },
          timeoutMs: 1000,
        },
      },
    },
  });
  stub.set(SERVICE_A);
  await loader.start();

  const inFlight = loader.resolve(ACCOUNT_CONTEXT, 'checkout.open');
  stub.set(SERVICE_B);
  await loader.refresh();
  const later = await loader.resolve(ACCOUNT_CONTEXT, 'checkout.open');
  const earlier = await inFlight;

  assert.equal(earlier.bundleVersion, SERVICE_A.bundleVersion);
  assert.equal(earlier.value, true, 'the old bundle has no root value, so the account value decides');
  assert.equal(later.bundleVersion, SERVICE_B.bundleVersion);
  assert.equal(later.value, false, 'the new bundle has a root value that is false');
});

test('under concurrency, every call sees one whole bundle, and both versions are seen', async (t) => {
  const { stub, loader } = await setup(t);
  stub.set(LOAD_A);
  await loader.start();

  const expectations = [];
  const rounds = 10;
  for (let round = 0; round < rounds; round += 1) {
    // Round 0 uses the bundle that start loaded; each later round swaps to the other one.
    const expected = round % 2 === 0 ? LOAD_A : LOAD_B;
    if (round > 0) {
      stub.set(expected);
      await loader.refresh();
    }
    for (let call = 0; call < 30; call += 1) {
      expectations.push({ version: expected.bundleVersion, result: loader.resolve(undefined, 'example.min_safe') });
    }
  }

  const results = await Promise.all(expectations.map((entry) => entry.result));
  const seen = new Set();
  results.forEach((result, index) => {
    const expectedVersion = expectations[index].version;
    assert.equal(result.bundleVersion, expectedVersion, `call ${index} began on ${expectedVersion}`);
    assert.equal(result.value, expectedVersion === LOAD_B.bundleVersion ? -5 : -9007199254740991);
    seen.add(result.bundleVersion);
  });
  assert.equal(seen.size, 2);
});

test('the listener of the evaluator options sees one load event per new bundle, and none for a 304', async (t) => {
  const events = [];
  const { stub, loader } = await setup(t, { evaluatorOptions: { listener: (event) => events.push(event) } });
  stub.set(LOAD_A);
  await loader.start();
  await loader.refresh();

  assert.equal(events.length, 1, 'a 304 builds no evaluator');
  assert.deepEqual(events[0], { type: 'BUNDLE_LOADED', bundleVersion: LOAD_A.bundleVersion });

  stub.set(LOAD_B);
  await loader.refresh();
  assert.equal(events.length, 2);
  assert.equal(events[1].bundleVersion, LOAD_B.bundleVersion);
});

test('the current evaluator is pinned by holding it, and it keeps serving after a swap', async (t) => {
  const { stub, loader } = await setup(t);
  stub.set(LOAD_A);
  await loader.start();
  const pinned = loader.current();

  stub.set(LOAD_B);
  await loader.refresh();

  assert.equal(loader.current().bundleVersion, LOAD_B.bundleVersion);
  assert.equal(pinned.bundleVersion, LOAD_A.bundleVersion);
  assert.equal((await pinned.resolve(undefined, 'example.min_safe')).value, -9007199254740991);
});
