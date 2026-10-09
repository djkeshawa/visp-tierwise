// Value equality at runtime (NORM-18). Conditions compare scalars only (RULE-35 to RULE-38), and FACT-35 rejects a
// FactSet value of the wrong type before any comparison, so no vector reaches a comparison of two types. This test
// checks the comparison that decide uses directly (spec/conformance/enforcers.d/E6.yaml).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { distUrl } from './support.mjs';

const { scalarEquals } = await import(new URL('values.js', distUrl).href);

test('NORM-18: values of different declared types never compare equal', () => {
  const samples = [true, false, 0, 1, 7, -1, '', '0', '1', 'true', 'false', '1.5', 'a'];
  for (const left of samples) {
    for (const right of samples) {
      if (typeof left !== typeof right) {
        assert.equal(scalarEquals(left, right), false, `${JSON.stringify(left)} and ${JSON.stringify(right)}`);
      }
    }
  }
  // A boolean against its integer or string form, an integer against its decimal string, and an enum or string
  // against a number with the same text.
  assert.equal(scalarEquals(true, 1), false);
  assert.equal(scalarEquals(false, 0), false);
  assert.equal(scalarEquals(true, 'true'), false);
  assert.equal(scalarEquals(1, '1'), false);
  assert.equal(scalarEquals('1.5', 1.5), false);
  // null is never a value (TYPE-76), so it equals nothing, not even null.
  assert.equal(scalarEquals(null, null), false);
  assert.equal(scalarEquals(null, false), false);
  assert.equal(scalarEquals(0, null), false);
});

test('NORM-18: values of one type compare by value, and nothing is coerced (NORM-17, RULE-38)', () => {
  assert.equal(scalarEquals(7, 7), true);
  assert.equal(scalarEquals(false, false), true);
  assert.equal(scalarEquals('1.5', '1.5'), true);
  assert.equal(scalarEquals('a', 'A'), false);
  assert.equal(scalarEquals('é', 'é'), false, 'strings are compared as code points (NORM-6)');
});
