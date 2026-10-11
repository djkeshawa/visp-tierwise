// Packaging of the built package: the ESM build imports only this package's own files, the evaluator and one Node
// built-in; the runtime dependency is the evaluator alone; the CommonJS build loads and serves a snapshot; and the
// conformance bundles used by the tests are canonical, so that the derived test bundles are real bundles.
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { canonical, conformanceText } from './support.mjs';

const distEsm = fileURLToPath(new URL('../dist/esm/', import.meta.url));
const distCjs = fileURLToPath(new URL('../dist/cjs/', import.meta.url));
const manifest = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));

/** The module specifiers that a built file imports or requires. */
function specifiersOf(source) {
  const found = [];
  for (const match of source.matchAll(/\bfrom\s*['"]([^'"]+)['"]/g)) found.push(match[1]);
  for (const match of source.matchAll(/\bimport\s*['"]([^'"]+)['"]/g)) found.push(match[1]);
  for (const match of source.matchAll(/\brequire\s*\(\s*['"]([^'"]+)['"]\s*\)/g)) found.push(match[1]);
  return found;
}

test('the built ESM imports only its own files, the evaluator and the one Node built-in it uses', () => {
  const files = readdirSync(distEsm).filter((name) => name.endsWith('.js'));
  assert.ok(files.length > 5, 'the build output is present');
  for (const name of files) {
    for (const specifier of specifiersOf(readFileSync(join(distEsm, name), 'utf8'))) {
      const own = /^\.\/[a-z]+\.js$/.test(specifier);
      const allowed = specifier === '@visplabs/evaluator' || specifier === 'node:fs/promises';
      assert.ok(own || allowed, `${name} imports ${specifier}`);
    }
  }
});

test('the runtime dependency is the evaluator alone', () => {
  assert.deepEqual(Object.keys(manifest.dependencies ?? {}), ['@visplabs/evaluator']);
});

test('the CommonJS build loads, and it serves a snapshot', async () => {
  const require = createRequire(import.meta.url);
  const cjs = require(join(distCjs, 'index.js'));
  assert.equal(typeof cjs.createBundleLoader, 'function');
  assert.equal(typeof cjs.BundleLoaderError, 'function');

  const text = conformanceText('edge-load.json');
  // The server is never reached: the snapshot serves the calls, and the stop ends the background check.
  const loader = cjs.createBundleLoader({ url: 'http://127.0.0.1:9/bundle.json', intervalMs: 60_000, snapshot: { bytes: text } });
  try {
    await loader.start();
    assert.equal(loader.resolveSync(undefined, 'example.min_safe').value, -9007199254740991);
  } finally {
    loader.stop();
  }
});

test('the conformance bundles used by the tests are in canonical form', () => {
  for (const name of ['edge-load.json', 'edge-hierarchy-service.json', 'edge-features-small.json']) {
    const text = conformanceText(name);
    assert.equal(canonical(JSON.parse(text)), text, `${name} is the JCS serialisation of its envelope`);
  }
});
