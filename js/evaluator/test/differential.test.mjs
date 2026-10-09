// The JavaScript side of the cross-language differential run (requirements section 12, implementation plan 4.4).
//
// The Java harness (java/tierwise-core, DifferentialHarnessTest) writes one JSON object per line into cases.jsonl. Each
// line holds the bundle as base64, the call and its inputs, the stubs of the providers, and the Java expectation: the
// result, or the error code, and the listener events. This file replays every case through the built evaluator and
// compares the results, the codes and the events. It reads the files and writes none.
//
// The run is skipped unless TIERWISE_DIFF_DIR names the directory of the case files. scripts/differential.sh sets it.
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { api, json, mismatches } from './support.mjs';

const DIR = process.env.TIERWISE_DIFF_DIR;
const MAX_REPORTED = 10;

const hasOwn = (object, name) => Object.prototype.hasOwnProperty.call(object, name);

/** Lists the case files of the directory, sorted by name. */
function caseFiles(dir) {
  return readdirSync(dir)
    .filter((name) => name.endsWith('.jsonl'))
    .sort()
    .map((name) => join(dir, name));
}

/**
 * The provider of one registered level. It answers from the stub of the case, as the Java stub does: a listed node
 * returns its values or fails as the outcome says, and an unlisted node fails as an unavailable source (VEC-43, VEC-45).
 */
function stubFunction(stub) {
  return async (nodeId) => {
    if (!hasOwn(stub, nodeId)) throw new Error(`the stub does not list node ${nodeId}`);
    const outcome = stub[nodeId];
    if (hasOwn(outcome, 'fail')) throw new Error(`the stub fails: ${outcome.fail}`);
    return outcome.values;
  };
}

/** The plain JSON form of a value: undefined members are dropped, as they are in the Java records. */
const plain = (value) => JSON.parse(JSON.stringify(value));

/** Replays one case and returns the outcome (a result or an error code) and the listener events. */
async function replay(record) {
  const events = [];
  const providers = {};
  for (const [level, registration] of Object.entries(record.providers)) {
    providers[level] = { fn: stubFunction(registration.stub), timeoutMs: registration.timeoutMs };
  }
  const options = { providers, listener: (event) => events.push(plain(event)) };
  let outcome;
  try {
    const bundle = api.loadBundle(Buffer.from(record.bundle, 'base64'));
    const evaluator = api.createEvaluator(bundle, options);
    const context = hasOwn(record, 'context') ? record.context : undefined;
    const facts = hasOwn(record, 'facts') ? record.facts : undefined;
    let result;
    if (record.call === 'resolve') {
      result = await evaluator.resolve(context, record.key);
    } else if (record.call === 'resolveLocal') {
      result = evaluator.resolveSync(context, record.key);
    } else {
      result = evaluator.decideSync(context, record.feature, facts);
    }
    outcome = { result: plain(result) };
  } catch (error) {
    if (!(error instanceof api.TierwiseError)) throw error;
    outcome = { error: error.code };
  }
  return { outcome, events };
}

/** Compares one replayed case with its Java expectation and returns the differences. */
function compare(record, actual) {
  const problems = [];
  const expected = record.expect;
  if (hasOwn(expected, 'error')) {
    if (!hasOwn(actual.outcome, 'error')) {
      problems.push(`expected error ${expected.error}, got a result`);
    } else if (actual.outcome.error !== expected.error) {
      problems.push(`expected error ${expected.error}, got ${actual.outcome.error}`);
    }
  } else if (hasOwn(actual.outcome, 'error')) {
    problems.push(`expected a result, got error ${actual.outcome.error}`);
  } else {
    const differences = mismatches(expected.result, actual.outcome.result, true);
    if (differences.length > 0) {
      problems.push(`result differs: ${differences.slice(0, 3).join('; ')}`);
    } else if (json.canonicalJson(expected.result) !== json.canonicalJson(actual.outcome.result)) {
      problems.push('result differs in its canonical form');
    }
  }
  const expectedEvents = record.events.map((event) => json.canonicalJson(event)).sort();
  const actualEvents = actual.events.map((event) => json.canonicalJson(event)).sort();
  if (JSON.stringify(expectedEvents) !== JSON.stringify(actualEvents)) {
    problems.push(`listener events differ: expected ${expectedEvents.join(' ')}; got ${actualEvents.join(' ')}`);
  }
  return problems;
}

describe('differential replay of the Java cases', { skip: DIR ? false : 'TIERWISE_DIFF_DIR is unset' }, () => {
  const files = DIR ? caseFiles(DIR) : [];

  test('the directory holds at least one case file', () => {
    assert.ok(files.length > 0, `no .jsonl case files in ${DIR}`);
  });

  for (const file of files) {
    test(`replays ${file}`, async (t) => {
      const lines = readFileSync(file, 'utf8').split('\n').filter((line) => line.length > 0);
      const failures = [];
      const kinds = { results: 0, errors: 0 };
      for (const line of lines) {
        const record = JSON.parse(line);
        if (hasOwn(record.expect, 'error')) kinds.errors += 1;
        else kinds.results += 1;
        let actual;
        try {
          actual = await replay(record);
        } catch (error) {
          failures.push(`${record.id} (${record.call}): unexpected ${error?.name ?? 'error'}: ${error?.message}`);
          continue;
        }
        const problems = compare(record, actual);
        if (problems.length > 0) failures.push(`${record.id} (${record.call}): ${problems.join(' | ')}`);
      }
      t.diagnostic(`${lines.length} cases, ${kinds.results} results, ${kinds.errors} error codes`);
      assert.deepEqual(
        failures.slice(0, MAX_REPORTED),
        [],
        `${failures.length} of ${lines.length} cases differ in ${file} (first ${MAX_REPORTED} shown)`,
      );
    });
  }
});
