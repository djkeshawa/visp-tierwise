// The options of createBundleLoader: the checks at the call, and the injected fetch function (requirements §8).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { api, conformanceText, etagOf, fromText } from './support.mjs';

const { createBundleLoader } = api;

const GOOD = Object.freeze({ url: 'http://127.0.0.1:9/bundle.json' });
const LOAD_A = fromText(conformanceText('edge-load.json'));

test('invalid options are refused with a TypeError at the call', () => {
  const bad = [
    undefined,
    null,
    'bundle.json',
    {},
    { url: '' },
    { url: 42 },
    { url: 'bundle.json' },
    { url: 'ftp://127.0.0.1/bundle.json' },
    { ...GOOD, intervalMs: 0 },
    { ...GOOD, intervalMs: 1.5 },
    { ...GOOD, intervalMs: '30000' },
    { ...GOOD, intervalMs: 2 ** 31 },
    { ...GOOD, staleAfterMs: -1 },
    { ...GOOD, requestTimeoutMs: 0 },
    { ...GOOD, requestTimeoutMs: 2 ** 31 },
    { ...GOOD, fetch: 'fetch' },
    { ...GOOD, onError: 'report' },
    { ...GOOD, evaluatorOptions: [] },
    { ...GOOD, evaluatorOptions: 'options' },
    { ...GOOD, snapshot: {} },
    { ...GOOD, snapshot: { bytes: 42 } },
    { ...GOOD, snapshot: { path: '' } },
    { ...GOOD, snapshot: { bytes: 'x', path: 'y' } },
    { ...GOOD, snapshot: 'bundle.json' },
  ];
  for (const options of bad) {
    assert.throws(() => createBundleLoader(options), TypeError, JSON.stringify(options));
  }
});

test('valid options are accepted, including the largest timer delay', () => {
  const loader = createBundleLoader({ ...GOOD, intervalMs: 2 ** 31 - 1, requestTimeoutMs: 2 ** 31 - 1, staleAfterMs: 1e15 });
  assert.equal(loader.current(), undefined);
  loader.stop();
});

test('a URL with the https scheme is accepted', () => {
  createBundleLoader({ url: 'https://bundles.example.test/tierwise/bundle.json' }).stop();
});

test('an injected fetch function receives the requests, and the held version is sent as the ETag', async () => {
  const calls = [];
  const etag = etagOf(LOAD_A.bundleVersion);
  const fetch = async (input, init) => {
    calls.push({ input, headers: init.headers });
    if (calls.length === 1) {
      return new Response(Buffer.from(LOAD_A.text, 'utf8'), { status: 200, headers: { ETag: etag } });
    }
    return new Response(null, { status: 304, headers: { ETag: etag } });
  };
  const loader = createBundleLoader({ url: 'http://bundles.test/bundle.json', fetch, intervalMs: 60_000 });
  await loader.start();
  await loader.refresh();
  loader.stop();

  assert.equal(calls.length, 2);
  assert.equal(calls[0].input, 'http://bundles.test/bundle.json');
  assert.equal(calls[0].headers['If-None-Match'], undefined);
  assert.equal(calls[1].headers['If-None-Match'], etag);
  assert.equal(loader.status().bundleVersion, LOAD_A.bundleVersion);
  assert.equal(loader.status().refreshFailures, 0);
});

test('a failing fetch function becomes a BundleLoaderError, and a throwing onError is ignored', async () => {
  const fetch = async () => {
    throw new TypeError('the connection was refused');
  };
  const loader = createBundleLoader({
    url: 'http://bundles.test/bundle.json',
    fetch,
    intervalMs: 60_000,
    onError: () => {
      throw new Error('the callback itself failed');
    },
  });

  await assert.rejects(loader.start(), (error) => {
    return (
      error.name === 'BundleLoaderError' &&
      error.code === 'E_BUNDLE_FETCH' &&
      error.message === 'E_BUNDLE_FETCH: the request failed: the connection was refused' &&
      error.cause instanceof TypeError
    );
  });
  loader.stop();
});
