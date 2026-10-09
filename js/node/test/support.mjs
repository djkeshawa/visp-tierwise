// Shared helpers for the tests of @tierwise/node: the built package, the conformance bundles on disk, a serialiser that
// derives new bundles from them, and a node:http stub server that speaks the ETag contract (ETag = bundleVersion).
//
// The tests run over the built output in dist/, never over src/, so they check what ships. The names in the bundles
// are those of the conformance suite; the tests never depend on a sample domain for their meaning.
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { fileURLToPath } from 'node:url';

/** The public API, as ESM. */
export const api = await import(new URL('../dist/esm/index.js', import.meta.url).href);

const bundlesDir = fileURLToPath(new URL('../../../spec/conformance/bundles/', import.meta.url));

/** The root level of the conformance bundles that the tests derive from. */
export const ROOT = 'global';

/** The text of a conformance bundle. The file is the canonical serialisation, so it is also the bundle's bytes. */
export function conformanceText(name) {
  return readFileSync(bundlesDir + name, 'utf8');
}

/**
 * The serialisation with sorted keys. It matches JCS for the ASCII content of these bundles, which have no floats. It
 * is used only to derive test data, and the conformance check below proves that it matches the files.
 */
export function canonical(value) {
  return JSON.stringify(sortKeys(value));
}

function sortKeys(value) {
  if (Array.isArray(value)) return value.map(sortKeys);
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map((key) => [key, sortKeys(value[key])]));
  }
  return value;
}

/** A bundle as the tests use it: its version and its text. */
export function fromText(text) {
  return { bundleVersion: JSON.parse(text).bundleVersion, text };
}

/** A bundle for the content, with its digest (BUNDLE-5, BUNDLE-113). */
export function bundleFor(content) {
  const bundleVersion = `sha256:${createHash('sha256').update(canonical(content)).digest('hex')}`;
  return { bundleVersion, text: canonical({ bundleVersion, content }) };
}

/** The bundle that is the given one with a root value changed, so that its version and its results both differ. */
export function withRootValue(bundle, key, value) {
  const content = JSON.parse(bundle.text).content;
  content.values[ROOT] ??= {};
  content.values[ROOT][''] ??= {};
  content.values[ROOT][''][key] = value;
  return bundleFor(content);
}

/** Sleeps for the given number of milliseconds. */
export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Waits until the predicate holds, or fails after the timeout. */
export async function waitFor(predicate, timeoutMs = 3000) {
  const deadline = Date.now() + timeoutMs;
  while (!predicate()) {
    if (Date.now() > deadline) throw new Error('the condition did not hold in time');
    await sleep(5);
  }
}

/**
 * A node:http server that serves one bundle at /bundle.json, with the ETag contract of the Tierwise server: the ETag
 * is the bundleVersion, and a request whose If-None-Match names it gets 304.
 *
 * Modes: "serve" (the default), "no-etag" (always 200), "always-304", "down" (the connection is cut), "hang" (no answer)
 * and "status" (the given HTTP status, with no bundle).
 */
export async function startStub() {
  const stub = {
    url: '',
    mode: 'serve',
    status: 500,
    body: Buffer.alloc(0),
    tag: undefined,
    requests: [],
    /** Serves a bundle; its ETag is its bundleVersion. */
    set(bundle) {
      stub.body = Buffer.from(bundle.text, 'utf8');
      stub.tag = `"${bundle.bundleVersion}"`;
    },
    /** Serves bytes that are not a bundle, with an ETag that matches nothing. */
    setRaw(text) {
      stub.body = Buffer.from(text, 'utf8');
      stub.tag = '"raw"';
    },
    setMode(mode, status = 500) {
      stub.mode = mode;
      stub.status = status;
    },
    async close() {
      await new Promise((resolve) => {
        server.close(() => resolve());
        server.closeAllConnections();
      });
    },
  };

  const server = createServer((request, response) => {
    stub.requests.push({ method: request.method, ifNoneMatch: request.headers['if-none-match'] });
    if (stub.mode === 'down') {
      request.socket.destroy();
      return;
    }
    if (stub.mode === 'hang') return;
    if (stub.mode === 'status') {
      response.writeHead(stub.status, { 'Content-Type': 'text/plain' });
      response.end('no bundle here');
      return;
    }
    if (stub.mode === 'always-304') {
      response.writeHead(304, { ETag: stub.tag });
      response.end();
      return;
    }
    if (stub.mode !== 'no-etag' && request.headers['if-none-match'] === stub.tag) {
      response.writeHead(304, { ETag: stub.tag });
      response.end();
      return;
    }
    response.writeHead(200, { ETag: stub.tag, 'Content-Type': 'application/json' });
    response.end(stub.body);
  });

  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  stub.url = `http://127.0.0.1:${server.address().port}/bundle.json`;
  return stub;
}

/** The quoted entity tag that the loader sends for a bundle version. */
export const etagOf = (bundleVersion) => `"${bundleVersion}"`;
