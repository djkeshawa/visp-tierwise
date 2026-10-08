// Tests that the spec checker passes on the repository and reports usage errors.
// The runs pass --no-write, so the tests leave spec/conformance/COVERAGE.md alone.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const CHECK = fileURLToPath(new URL('../check.mjs', import.meta.url));

test('check exits 0 on the repository without --strict', () => {
  const result = spawnSync(process.execPath, [CHECK, '--no-write'], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stdout + result.stderr);
  assert.match(result.stdout, /check passed/);
});

test('check exits 2 on an unknown argument', () => {
  const result = spawnSync(process.execPath, [CHECK, '--bogus'], { encoding: 'utf8' });
  assert.equal(result.status, 2);
  assert.match(result.stderr, /usage: node spec-tools\/check\.mjs/);
});
