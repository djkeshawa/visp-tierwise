// Startup and shutdown: fail fast without a snapshot, retry after a failed start, snapshots from bytes and from a file,
// the background check that follows a snapshot, and stop (requirements §8 and §12).
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { api, conformanceText, etagOf, fromText, sleep, startStub, waitFor, withRootValue } from './support.mjs';

const { createBundleLoader } = api;

const LOAD_A = fromText(conformanceText('edge-load.json'));
const LOAD_B = withRootValue(LOAD_A, 'example.min_safe', -5);
const VALUE_A = -9007199254740991;

async function setup(t, options = {}) {
  const stub = await startStub();
  t.after(() => stub.close());
  const errors = [];
  const loader = createBundleLoader({
    url: stub.url,
    intervalMs: 60_000,
    onError: (error) => errors.push(error),
    ...options,
  });
  t.after(() => loader.stop());
  return { stub, loader, errors };
}

test('start without a snapshot rejects when the server is down, and a second start can succeed', async (t) => {
  const { stub, loader, errors } = await setup(t);
  stub.setMode('down');

  await assert.rejects(loader.start(), { code: 'E_BUNDLE_FETCH' });
  assert.equal(errors.length, 1, 'the failed load is reported');
  await assert.rejects(loader.resolve(undefined, 'example.min_safe'), { code: 'E_BUNDLE_NOT_LOADED' });

  stub.setMode('serve');
  stub.set(LOAD_A);
  await loader.start();
  assert.equal((await loader.resolve(undefined, 'example.min_safe')).value, VALUE_A);
});

test('start fails fast on a bundle that is not valid, and does not poll', async (t) => {
  const stub = await startStub();
  t.after(() => stub.close());
  const loader = createBundleLoader({ url: stub.url, intervalMs: 20 });
  t.after(() => loader.stop());
  stub.setRaw('not a bundle');

  await assert.rejects(loader.start(), { code: 'E_BUNDLE_INTEGRITY' });
  await sleep(100);
  assert.equal(stub.requests.length, 1, 'a failed start arms no poll');
});

test('start resolves once, and a second call does not contact the server again', async (t) => {
  const { stub, loader } = await setup(t);
  stub.set(LOAD_A);

  const first = loader.start();
  const second = loader.start();
  await Promise.all([first, second]);
  await loader.start();

  assert.equal(stub.requests.length, 1);
});

test('a snapshot from bytes serves calls while the server is down, and the failed check is reported', async (t) => {
  const { stub, loader, errors } = await setup(t, { snapshot: { bytes: LOAD_A.text } });
  stub.setMode('down');

  await loader.start();
  assert.equal((await loader.resolve(undefined, 'example.min_safe')).value, VALUE_A);

  await waitFor(() => errors.length > 0);
  assert.equal(errors[0].code, 'E_BUNDLE_FETCH');
  const status = loader.status();
  assert.equal(status.bundleVersion, LOAD_A.bundleVersion);
  assert.equal(status.lastCheckedAt, undefined, 'a snapshot is not a confirmation');
  assert.equal(status.stale, true);
  assert.equal(status.stalenessMs, undefined);
  assert.equal(status.refreshFailures, 1);
});

test('a snapshot as a file path is read and served', async (t) => {
  const directory = mkdtempSync(join(tmpdir(), 'tierwise-node-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const path = join(directory, 'bundle.json');
  writeFileSync(path, LOAD_A.text);

  const { stub, loader, errors } = await setup(t, { snapshot: { path } });
  stub.setMode('down');
  await loader.start();

  assert.equal(loader.current().bundleVersion, LOAD_A.bundleVersion);
  assert.equal((await loader.resolve(undefined, 'example.min_safe')).value, VALUE_A);
  await waitFor(() => errors.length > 0);
});

test('a snapshot file that does not exist fails the start', async (t) => {
  const { loader } = await setup(t, { snapshot: { path: join(tmpdir(), 'tierwise-node-missing', 'bundle.json') } });
  await assert.rejects(loader.start(), { code: 'ENOENT' });
});

test('a snapshot that is not valid fails the start before any request is made', async (t) => {
  const { stub, loader } = await setup(t, { snapshot: { bytes: 'not a bundle' } });

  await assert.rejects(loader.start(), { code: 'E_BUNDLE_INTEGRITY' });
  assert.equal(stub.requests.length, 0);
});

test('after a snapshot, the first check finds a newer version and swaps to it', async (t) => {
  const { stub, loader } = await setup(t, { snapshot: { bytes: LOAD_A.text } });
  stub.set(LOAD_B);
  await loader.start();

  await waitFor(() => loader.status().bundleVersion === LOAD_B.bundleVersion);
  assert.equal((await loader.resolve(undefined, 'example.min_safe')).value, -5);
  assert.equal(stub.requests[0].ifNoneMatch, etagOf(LOAD_A.bundleVersion), 'the snapshot version is the ETag');
});

test('after a snapshot, the first check confirms the same version without a new evaluator', async (t) => {
  const { stub, loader } = await setup(t, { snapshot: { bytes: LOAD_A.text } });
  stub.set(LOAD_A);
  await loader.start();
  const fromSnapshot = loader.current();

  await waitFor(() => loader.status().lastCheckedAt !== undefined);

  assert.strictEqual(loader.current(), fromSnapshot);
  assert.equal(loader.status().stale, false);
});

test('polling starts after a snapshot start, and a later failure keeps the snapshot', async (t) => {
  const { stub, loader, errors } = await setup(t, { snapshot: { bytes: LOAD_A.text }, intervalMs: 20 });
  stub.setMode('down');
  await loader.start();

  await waitFor(() => stub.requests.length >= 3);
  assert.ok(errors.length >= 1);
  assert.equal(loader.status().bundleVersion, LOAD_A.bundleVersion);
});

test('stop ends polling, and no request follows it', async (t) => {
  const { stub, loader } = await setup(t, { intervalMs: 20 });
  stub.set(LOAD_A);
  await loader.start();
  await waitFor(() => stub.requests.length >= 3);

  loader.stop();
  await sleep(30);
  const count = stub.requests.length;
  await sleep(100);

  assert.equal(stub.requests.length, count);
});

test('stop aborts a first load in flight, and start rejects with E_LOADER_STOPPED', async (t) => {
  const { stub, loader, errors } = await setup(t);
  stub.setMode('hang');

  const starting = loader.start();
  await waitFor(() => stub.requests.length === 1);
  loader.stop();

  await assert.rejects(starting, { code: 'E_LOADER_STOPPED' });
  assert.equal(errors.length, 0, 'a stop is not reported as a failure');
  assert.equal(loader.status().refreshFailures, 0);
});

test('a stopped loader cannot be started or refreshed again, but keeps serving its last bundle', async (t) => {
  const { stub, loader } = await setup(t);
  stub.set(LOAD_A);
  await loader.start();

  loader.stop();
  loader.stop();

  await assert.rejects(loader.start(), { code: 'E_LOADER_STOPPED' });
  await assert.rejects(loader.refresh(), { code: 'E_LOADER_STOPPED' });
  assert.equal((await loader.resolve(undefined, 'example.min_safe')).value, VALUE_A);
});
