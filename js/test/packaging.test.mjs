// Checks that every package's ESM, CommonJS and type outputs exist and load through its exports map.
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';

const packages = ['evaluator', 'node', 'openfeature'];

for (const name of packages) {
  const root = new URL(`../${name}/`, import.meta.url);
  const manifest = JSON.parse(readFileSync(new URL('package.json', root), 'utf8'));
  const entry = manifest.exports['.'];

  test(`${manifest.name} loads as ESM`, async () => {
    const module = await import(new URL(entry.import, root).href);
    assert.equal(typeof module, 'object');
  });

  test(`${manifest.name} loads as CommonJS`, () => {
    const require = createRequire(new URL('package.json', root));
    const module = require(entry.require);
    assert.equal(typeof module, 'object');
    assert.equal(
      JSON.parse(readFileSync(new URL('dist/cjs/package.json', root), 'utf8')).type,
      'commonjs',
    );
  });

  test(`${manifest.name} ships type declarations`, () => {
    assert.ok(existsSync(new URL(entry.types, root)), entry.types);
  });
}
