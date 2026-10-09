// FIND-9 and EVAL-22: an evaluation operation does not perform a validation, and an evaluation error is raised, not
// reported as a finding. Every failing resolve and decide, synchronous or not, throws or rejects with a TierwiseError
// whose code is one of EVAL-38, and no result or public function of the package carries or produces findings.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { api } from './support.mjs';
import { bundle } from './fixtures.mjs';

const { createEvaluator, TierwiseError, ERROR_CODES } = api;

function evaluator() {
  return createEvaluator(bundle(), { providers: { low: { fn: async () => ({}), timeoutMs: 200 } } });
}

const FACTS = { facts: { 'parent.flag': true, 'whole.kind': 'a' }, children: { part: [{ 'part.tag': 'x' }] } };

/** Failing calls: the operation, its arguments and the code it raises. */
const failing = [
  ['resolve', [{}, 'no.such.key'], 'E_UNKNOWN_KEY'],
  ['resolve', [{ levels: { nowhere: 'x' } }, 'k.over'], 'E_UNKNOWN_SCOPE'],
  ['resolve', [{ levels: { mid: 'm9' } }, 'k.over'], 'E_UNKNOWN_NODE'],
  ['resolve', [{ levels: { top: 'x' } }, 'k.over'], 'E_CONTEXT_INVALID'],
  ['decide', [{}, 'no.such.feature', FACTS], 'E_UNKNOWN_FEATURE'],
  ['decide', [{}, 'f.one', { facts: { 'parent.flag': 'not a boolean' } }], 'E_FACTS_INVALID'],
];

/** Requires that a value thrown by an evaluation operation is an evaluation error, never a finding. */
function assertEvaluationError(error, code) {
  assert.ok(error instanceof TierwiseError, `an evaluation error is raised as a TierwiseError: ${error}`);
  assert.equal(error.code, code);
  assert.ok(ERROR_CODES.includes(error.code), 'EVAL-38');
  for (const field of ['severity', 'file', 'path', 'line']) {
    assert.ok(!(field in error), `FIND-9: the error is not a finding, so it has no ${field}`);
  }
}

for (const [operation, args, code] of failing) {
  test(`FIND-9: a failing ${operation} raises ${code} instead of returning a finding`, async () => {
    const tw = evaluator();
    let thrown = null;
    try {
      tw[`${operation}Sync`](...args);
    } catch (error) {
      thrown = error;
    }
    assertEvaluationError(thrown, code);
    const pending = tw[operation](...args);
    assert.ok(pending instanceof Promise);
    await assert.rejects(pending, (error) => {
      assertEvaluationError(error, code);
      return true;
    });
  });
}

test('FIND-9: a successful resolve or decide returns a result that has no findings', async () => {
  const tw = evaluator();
  const results = [
    tw.resolveSync({}, 'k.over'),
    await tw.resolve({ levels: { mid: 'm1' } }, 'k.all'),
    tw.decideSync({}, 'f.one', FACTS),
    await tw.decide({ levels: { mid: 'm1' } }, 'f.one', FACTS),
  ];
  for (const result of results) {
    assert.equal(typeof result, 'object');
    for (const member of Object.keys(result)) {
      assert.ok(!/finding|severity|lint/i.test(member), `FIND-9: a result has no member ${member}`);
    }
  }
});

test('FIND-9: the package exposes no validation, so an evaluation cannot run one', () => {
  const exported = Object.keys(api).sort();
  for (const name of exported) {
    assert.ok(!/valid|lint|compile|finding|example/i.test(name), `an exported validation entry point: ${name}`);
  }
  const tw = evaluator();
  const operations = Object.keys(tw).filter((name) => typeof tw[name] === 'function').sort();
  assert.deepEqual(operations, ['decide', 'decideSync', 'resolve', 'resolveSync']);
});
