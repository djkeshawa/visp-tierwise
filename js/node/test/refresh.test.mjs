// Refresh against a stub server: the first load, the swap to a new version, ETag and 304 handling, polling, the
// last-known-good bundle when a check fails, timeouts, and staleness (requirements §8 and §12).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { api, conformanceText, etagOf, fromText, sleep, startStub, waitFor, withRootValue } from './support.mjs';

const { createBundleLoader } = api;

/** The conformance bundle with one root value, and the same bundle with that value changed. */
const LOAD_A = fromText(conformanceText('edge-load.json'));
const LOAD_B = withRootValue(LOAD_A, 'example.min_safe', -5);
const VALUE_A = -9007199254740991;
const VALUE_B = -5;

/** A stub server and a loader for it. The poll interval is long, so only the calls in a test check the server. */
async function setup(t, options = {}) {
  const stub = await startStub();
  t.after(() => stub.close());
  const loader = createBundleLoader({ url: stub.url, intervalMs: 60_000, ...options });
  t.after(() => loader.stop());
  return { stub, loader };
}

test('start loads the bundle, and calls are served from it', async (t) => {
  const { stub, loader } = await setup(t);
  stub.set(LOAD_A);
  await loader.start();

  assert.equal(loader.current().bundleVersion, LOAD_A.bundleVersion);
  const result = await loader.resolve(undefined, 'example.min_safe');
  assert.equal(result.value, VALUE_A);
  assert.equal(result.bundleVersion, LOAD_A.bundleVersion);

  const status = loader.status();
  assert.equal(status.bundleVersion, LOAD_A.bundleVersion);
  assert.equal(typeof status.loadedAt, 'number');
  assert.equal(typeof status.lastCheckedAt, 'number');
  assert.equal(status.stale, false);
  assert.equal(status.refreshFailures, 0);
  assert.equal(stub.requests.length, 1);
  assert.equal(stub.requests[0].ifNoneMatch, undefined, 'the first request sends no entity tag');
});

test('refresh swaps in a new version, and the new evaluator serves later calls', async (t) => {
  const errors = [];
  const { stub, loader } = await setup(t, { onError: (error) => errors.push(error) });
  stub.set(LOAD_A);
  await loader.start();

  stub.set(LOAD_B);
  await loader.refresh();

  assert.equal(loader.status().bundleVersion, LOAD_B.bundleVersion);
  assert.equal(loader.current().bundleVersion, LOAD_B.bundleVersion);
  assert.equal((await loader.resolve(undefined, 'example.min_safe')).value, VALUE_B);
  assert.equal(stub.requests[1].ifNoneMatch, etagOf(LOAD_A.bundleVersion), 'the held version is sent as the ETag');
  assert.deepEqual(errors, []);
});

test('a 304 keeps the same evaluator and confirms the version', async (t) => {
  const { stub, loader } = await setup(t);
  stub.set(LOAD_A);
  await loader.start();
  const before = loader.current();
  const confirmedAt = loader.status().lastCheckedAt;

  await sleep(5);
  await loader.refresh();

  assert.equal(stub.requests.length, 2);
  assert.equal(stub.requests[1].ifNoneMatch, etagOf(LOAD_A.bundleVersion));
  assert.strictEqual(loader.current(), before, 'a 304 does not build a new evaluator');
  assert.ok(loader.status().lastCheckedAt > confirmedAt, 'a 304 confirms the version');
});

test('a 200 for the version already held does not build a new evaluator', async (t) => {
  const { stub, loader } = await setup(t);
  stub.setMode('no-etag');
  stub.set(LOAD_A);
  await loader.start();
  const before = loader.current();

  await loader.refresh();

  assert.equal(stub.requests.length, 2);
  assert.strictEqual(loader.current(), before, 'the same bundleVersion keeps the evaluator');
});

test('polling picks up a new version without a call', async (t) => {
  const { stub, loader } = await setup(t, { intervalMs: 20 });
  stub.set(LOAD_A);
  await loader.start();

  stub.set(LOAD_B);
  await waitFor(() => loader.status().bundleVersion === LOAD_B.bundleVersion);

  assert.equal((await loader.resolve(undefined, 'example.min_safe')).value, VALUE_B);
  assert.ok(stub.requests.length >= 2, 'the loader polled the server after the start');
});

test('a server error keeps the last good bundle and reports the failure', async (t) => {
  const errors = [];
  const { stub, loader } = await setup(t, { onError: (error) => errors.push(error) });
  stub.set(LOAD_A);
  await loader.start();
  const before = loader.current();

  stub.setMode('status', 500);
  await assert.rejects(loader.refresh(), { code: 'E_BUNDLE_FETCH', status: 500 });

  assert.strictEqual(loader.current(), before, 'the last good evaluator is kept');
  assert.equal((await loader.resolve(undefined, 'example.min_safe')).value, VALUE_A);
  assert.equal(errors.length, 1);
  assert.equal(errors[0].code, 'E_BUNDLE_FETCH');
  assert.equal(loader.status().refreshFailures, 1);
});

test('an unreachable server keeps the last good bundle, and the loader recovers', async (t) => {
  const { stub, loader } = await setup(t);
  stub.set(LOAD_A);
  await loader.start();

  stub.setMode('down');
  await assert.rejects(loader.refresh(), { code: 'E_BUNDLE_FETCH', status: undefined });
  assert.equal(loader.status().bundleVersion, LOAD_A.bundleVersion);

  stub.setMode('serve');
  stub.set(LOAD_B);
  await loader.refresh();
  assert.equal(loader.status().bundleVersion, LOAD_B.bundleVersion);
  assert.equal(loader.status().refreshFailures, 1);
});

test('a request that does not finish in time fails and keeps the last good bundle', async (t) => {
  const { stub, loader } = await setup(t, { requestTimeoutMs: 50 });
  stub.set(LOAD_A);
  await loader.start();
  const before = loader.current();

  stub.setMode('hang');
  await assert.rejects(loader.refresh(), (error) => {
    return error.code === 'E_BUNDLE_FETCH' && /did not complete within 50 ms/.test(error.message);
  });

  assert.strictEqual(loader.current(), before);
});

test('a response that is not a bundle is refused, and the last good bundle is kept', async (t) => {
  const { stub, loader } = await setup(t);
  stub.set(LOAD_A);
  await loader.start();
  const before = loader.current();

  stub.setRaw('{"not":"a bundle"}');
  await assert.rejects(loader.refresh(), { code: 'E_BUNDLE_INTEGRITY' });

  assert.strictEqual(loader.current(), before);
});

test('a bundle that needs a provider that is not configured is refused, and the last good bundle is kept', async (t) => {
  const errors = [];
  const { stub, loader } = await setup(t, { onError: (error) => errors.push(error) });
  stub.set(LOAD_A);
  await loader.start();
  const before = loader.current();

  // The service-sourced level of this bundle has no provider in the options, so the evaluator cannot be made.
  stub.set(fromText(conformanceText('edge-hierarchy-service.json')));
  await assert.rejects(loader.refresh(), { code: 'E_PROVIDER_MISSING' });

  assert.strictEqual(loader.current(), before);
  assert.equal(errors[0].code, 'E_PROVIDER_MISSING');
  assert.equal(loader.status().bundleVersion, LOAD_A.bundleVersion);
});

test('a 304 that arrives before any bundle is held is an error', async (t) => {
  const { stub, loader } = await setup(t);
  stub.set(LOAD_A);
  stub.setMode('always-304');

  await assert.rejects(loader.start(), { code: 'E_BUNDLE_FETCH' });
  assert.equal(loader.current(), undefined);
});

test('staleness grows while checks fail, and clears on the next confirmed check', async (t) => {
  const { stub, loader } = await setup(t, { staleAfterMs: 50 });
  stub.set(LOAD_A);
  await loader.start();
  assert.equal(loader.status().stale, false);

  stub.setMode('down');
  await sleep(80);
  await loader.refresh().catch(() => undefined);
  const stale = loader.status();
  assert.equal(stale.stale, true);
  assert.ok(stale.stalenessMs >= 50, `staleness ${stale.stalenessMs} ms`);
  assert.equal(stale.bundleVersion, LOAD_A.bundleVersion, 'the bundle is still served while stale');

  stub.setMode('serve');
  await loader.refresh();
  assert.equal(loader.status().stale, false);
});

test('before any check, the status reports no bundle and counts as stale', async (t) => {
  const { loader } = await setup(t);
  const status = loader.status();

  assert.equal(status.bundleVersion, undefined);
  assert.equal(status.loadedAt, undefined);
  assert.equal(status.lastCheckedAt, undefined);
  assert.equal(status.stalenessMs, undefined);
  assert.equal(status.stale, true);
  assert.equal(status.refreshFailures, 0);
  assert.equal(loader.current(), undefined);
});
