// The conformance runner (section 12). It reads spec/conformance/canonical/ and spec/conformance/evaluate/ in
// place (VEC-108, VEC-109), never copies them, and never writes there (VEC-110). Every vector is one test.
//
// A vector that the runner cannot execute is a runner failure (VEC-111) and fails its test, so no vector is
// skipped silently. The final line of each directory group is printed as a pass count.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { after, describe, test } from 'node:test';
import {
  RunnerFailure,
  api,
  mismatches,
  conformanceDir,
  json,
  listFiles,
  loadVector,
  relativePath,
  sha,
} from './support.mjs';

const { TierwiseError, ERROR_CODES, createEvaluator, loadBundle } = api;

/** The timeout that every stub provider is registered with; no vector depends on its value (SVC-48). */
const STUB_TIMEOUT_MS = 1000;
const isMap = (value) => typeof value === 'object' && value !== null && !Array.isArray(value);
const own = (object, name) => Object.prototype.hasOwnProperty.call(object, name);

// ---------------------------------------------------------------------------
// Stub providers (VEC-37 to VEC-47)
// ---------------------------------------------------------------------------

class StubProviders {
  calls = {}; // level -> node -> count
  unlisted = [];

  /** Builds the provider function of one level from its stub. */
  provider(level, stub) {
    if (!isMap(stub)) throw new RunnerFailure(`the stub of level ${level} is not a mapping (VEC-37)`);
    for (const node of Object.keys(stub)) this.checkOutcome(level, node, stub[node]);
    return async (nodeId) => this.invoke(level, nodeId, stub);
  }

  checkOutcome(level, node, outcome) {
    const where = `the outcome of ${level}/${node}`;
    if (!isMap(outcome) || Object.keys(outcome).length !== 1) {
      throw new RunnerFailure(`${where} must have exactly one of values and fail (VEC-41)`);
    }
    if (own(outcome, 'values')) {
      if (!isMap(outcome.values)) throw new RunnerFailure(`${where}: values must be a mapping (VEC-41)`);
    } else if (own(outcome, 'fail')) {
      if (outcome.fail !== 'unavailable' && outcome.fail !== 'timeout') {
        throw new RunnerFailure(`${where}: fail must be unavailable or timeout (VEC-43)`);
      }
    } else {
      throw new RunnerFailure(`${where} must have exactly one of values and fail (VEC-41)`);
    }
  }

  invoke(level, nodeId, stub) {
    this.calls[level] ??= {};
    this.calls[level][nodeId] = (this.calls[level][nodeId] ?? 0) + 1; // VEC-47
    if (!own(stub, nodeId)) {
      this.unlisted.push(`${level}/${nodeId}`); // VEC-46
      throw new Error(`the stub for level ${level} does not list node ${nodeId}`);
    }
    const outcome = stub[nodeId];
    if (own(outcome, 'fail')) {
      // A timeout outcome fails at once, without waiting for a wall-clock interval (VEC-45). The evaluator
      // treats a rejection and an expired timeout alike (SVC-23, SVC-39).
      throw new Error(`stub ${outcome.fail} for ${level}/${nodeId}`);
    }
    return outcome.values; // Passed through without conversion (VEC-42).
  }

  count(level, node) {
    return this.calls[level]?.[node] ?? 0;
  }
}

// ---------------------------------------------------------------------------
// Evaluate vectors (VEC-21 to VEC-68)
// ---------------------------------------------------------------------------

const TOP_LEVEL = new Set([
  'id', 'specVersion', 'clauses', 'description', 'call', 'sync', 'bundle', 'context', 'key', 'feature',
  'input', 'providers', 'exact', 'expect',
]);
const RESOLVE_FIELDS = new Set(['key', 'strategy', 'kind', 'value', 'bundleVersion', 'skippedScopes', 'contributions', 'source']);
const DECIDE_FIELDS = new Set(['feature', 'effect', 'bundleVersion', 'skippedScopes', 'binding', 'policy', 'reason', 'trace']);

function string(doc, name) {
  if (typeof doc[name] !== 'string') throw new RunnerFailure(`${name} must be a string`);
  return doc[name];
}

function bool(doc, name) {
  if (!own(doc, name)) return false;
  if (typeof doc[name] !== 'boolean') throw new RunnerFailure(`${name} must be a boolean`);
  return doc[name];
}

function parseEvaluateVector(doc, file) {
  for (const name of Object.keys(doc)) {
    if (!TOP_LEVEL.has(name)) throw new RunnerFailure(`unknown field ${name} (VEC-19)`);
  }
  if (doc.specVersion !== '0.1') throw new RunnerFailure(`unsupported specVersion ${doc.specVersion} (VEC-15)`);
  const call = string(doc, 'call');
  if (call !== 'resolve' && call !== 'decide') throw new RunnerFailure('call must be resolve or decide (VEC-21)');
  const bundle = resolve(dirname(file), string(doc, 'bundle'));
  if (!bundle.startsWith(join(conformanceDir, 'bundles'))) throw new RunnerFailure('the bundle lies outside bundles/ (VEC-23)');
  const vector = { id: string(doc, 'id'), call, sync: bool(doc, 'sync'), exact: bool(doc, 'exact'), bundle };
  if (call === 'resolve') {
    vector.key = string(doc, 'key');
    if (own(doc, 'feature') || own(doc, 'input')) throw new RunnerFailure('a resolve vector has neither feature nor input (VEC-29, VEC-32)');
  } else {
    vector.feature = string(doc, 'feature');
    if (own(doc, 'key')) throw new RunnerFailure('a decide vector has no key (VEC-29)');
    if (!own(doc, 'input')) throw new RunnerFailure('a decide vector has input (VEC-30)');
    vector.input = doc.input;
  }
  // The runner passes a context with both members; a member the vector omits is an empty map (VEC-28).
  if (own(doc, 'context') && !isMap(doc.context)) throw new RunnerFailure('context must be a mapping (VEC-28)');
  vector.context = { ...(doc.context ?? {}) };
  for (const member of ['levels', 'axes']) {
    if (!own(vector.context, member)) vector.context[member] = {}; // A null member is kept: it is not a map (CTX-2).
  }
  vector.providers = {};
  if (own(doc, 'providers')) {
    if (!isMap(doc.providers)) throw new RunnerFailure('providers must be a mapping (VEC-37)');
    for (const level of Object.keys(doc.providers)) {
      if (doc.providers[level] !== 'none' && !isMap(doc.providers[level])) {
        throw new RunnerFailure(`the stub of ${level} is not a mapping or none (VEC-37)`);
      }
      vector.providers[level] = doc.providers[level];
    }
  }
  if (!isMap(doc.expect)) throw new RunnerFailure('expect must be a mapping (VEC-48)');
  const resultFields = call === 'resolve' ? RESOLVE_FIELDS : DECIDE_FIELDS;
  vector.expectResult = {};
  for (const name of Object.keys(doc.expect)) {
    if (resultFields.has(name)) vector.expectResult[name] = doc.expect[name];
    else if (name !== 'error' && name !== 'providerCalls') throw new RunnerFailure(`unknown field expect.${name} (VEC-19)`);
  }
  const hasResult = Object.keys(vector.expectResult).length > 0;
  if (own(doc.expect, 'error')) {
    if (hasResult) throw new RunnerFailure('expect has both error and a result field (VEC-48)');
    if (typeof doc.expect.error !== 'string' || !ERROR_CODES.includes(doc.expect.error)) {
      throw new RunnerFailure('expect.error is not a code an evaluation operation may raise (VEC-51)');
    }
    vector.expectError = doc.expect.error;
  } else if (!hasResult) {
    throw new RunnerFailure('expect has neither error nor a result field (VEC-49)');
  }
  if (own(doc.expect, 'providerCalls')) {
    if (!isMap(doc.expect.providerCalls)) throw new RunnerFailure('providerCalls must be a mapping (VEC-65)');
    vector.providerCalls = {};
    for (const level of Object.keys(doc.expect.providerCalls)) {
      const nodes = doc.expect.providerCalls[level];
      if (!isMap(nodes)) throw new RunnerFailure(`providerCalls.${level} must be a mapping (VEC-65)`);
      vector.providerCalls[level] = {};
      for (const node of Object.keys(nodes)) {
        if (!Number.isInteger(nodes[node]) || nodes[node] < 0) throw new RunnerFailure('providerCalls counts are non-negative integers (VEC-65)');
        vector.providerCalls[level][node] = nodes[node];
      }
    }
  }
  return vector;
}

/** The service-sourced levels that a bundle declares, read without validation; empty when it cannot be read. */
function serviceLevels(bytes) {
  try {
    const scopes = JSON.parse(new TextDecoder().decode(bytes)).content.hierarchy.scopes;
    return { known: true, service: new Set(Object.keys(scopes).filter((name) => scopes[name].values === 'service')) };
  } catch {
    return { known: false, service: new Set() };
  }
}

async function runEvaluateVector(vector) {
  const bytes = readFileSync(vector.bundle);
  const levels = serviceLevels(bytes);
  if (levels.known) {
    const optionsError = vector.expectError === 'E_EVALUATOR_OPTIONS';
    for (const level of Object.keys(vector.providers)) {
      if (!levels.service.has(level) && !optionsError) {
        throw new RunnerFailure(`providers names ${level}, which is not a service-sourced level (VEC-38)`);
      }
    }
    for (const level of Object.keys(vector.providerCalls ?? {})) {
      if (!levels.service.has(level)) throw new RunnerFailure(`providerCalls names ${level}, which is not a service-sourced level (VEC-38)`);
    }
  }
  // Every service-sourced level gets a stub, an empty one when the vector names none (VEC-39); `none` registers nothing.
  const stubs = new StubProviders();
  const providers = {};
  for (const level of new Set([...levels.service, ...Object.keys(vector.providers)])) {
    const stub = own(vector.providers, level) ? vector.providers[level] : {};
    if (stub !== 'none') providers[level] = { fn: stubs.provider(level, stub), timeoutMs: STUB_TIMEOUT_MS };
  }

  let errorCode = null;
  let result = null;
  try {
    const evaluator = createEvaluator(loadBundle(bytes), { providers });
    if (vector.call === 'resolve') {
      result = vector.sync
        ? evaluator.resolveSync(vector.context, vector.key)
        : await evaluator.resolve(vector.context, vector.key);
    } else {
      result = vector.sync
        ? evaluator.decideSync(vector.context, vector.feature, vector.input)
        : await evaluator.decide(vector.context, vector.feature, vector.input);
    }
  } catch (error) {
    if (!(error instanceof TierwiseError)) throw error;
    errorCode = error.code;
  }

  if (stubs.unlisted.length > 0) {
    throw new RunnerFailure(`a provider was invoked for a node that the stub does not list: ${stubs.unlisted} (VEC-46)`);
  }
  if (vector.sync && vector.call === 'resolve' && Object.keys(stubs.calls).length > 0) {
    throw new RunnerFailure('the synchronous variant invoked a provider (VEC-26)');
  }

  const problems = [];
  if (vector.expectError !== undefined) {
    if (errorCode === null) problems.push(`expected error ${vector.expectError}, but the operation returned a result`);
    else if (errorCode !== vector.expectError) problems.push(`expected error ${vector.expectError}, got ${errorCode}`);
  } else if (errorCode !== null) {
    problems.push(`expected a result, got error ${errorCode}`);
  } else {
    problems.push(...mismatches(vector.expectResult, result, vector.exact));
  }
  if (vector.providerCalls !== undefined || vector.exact) {
    const named = vector.providerCalls ?? {};
    for (const level of Object.keys(named)) {
      for (const node of Object.keys(named[level])) {
        const got = stubs.count(level, node);
        if (got !== named[level][node]) problems.push(`providerCalls ${level}/${node}: expected ${named[level][node]}, got ${got}`);
      }
    }
    if (vector.exact) {
      for (const level of Object.keys(stubs.calls)) {
        for (const node of Object.keys(stubs.calls[level])) {
          if (!own(named[level] ?? {}, node)) problems.push(`providerCalls ${level}/${node}: invoked but not named by exact expect (VEC-67)`);
        }
      }
    }
  }
  assert.equal(problems.length, 0, `${vector.id}:\n  ${problems.join('\n  ')}`);
}

// ---------------------------------------------------------------------------
// Canonical vectors (VEC-75 to VEC-89)
// ---------------------------------------------------------------------------

const CANONICAL_FIELDS = new Set(['id', 'specVersion', 'clauses', 'description', 'primitive', 'input', 'expect']);
const MAX_SAFE = 9007199254740991;

function bytesOf(field, what) {
  if (!isMap(field) || Object.keys(field).length !== 1) throw new RunnerFailure(`${what} takes exactly one of utf8 and hex (VEC-84)`);
  if (typeof field.utf8 === 'string') {
    if (json.canonicalJson === undefined || /[\ud800-\udfff]/.test(field.utf8.replace(/[\ud800-\udbff][\udc00-\udfff]/g, ''))) {
      throw new RunnerFailure(`${what}: unpaired surrogate (VEC-79)`);
    }
    return new TextEncoder().encode(field.utf8);
  }
  if (typeof field.hex === 'string') {
    if (!/^(?:[0-9a-f]{2})*$/.test(field.hex)) throw new RunnerFailure(`${what}.hex is not lower-case hexadecimal of even length (VEC-84)`);
    return Uint8Array.from(field.hex.match(/../g) ?? [], (pair) => parseInt(pair, 16));
  }
  throw new RunnerFailure(`${what} takes exactly one of utf8 and hex (VEC-84)`);
}

/** Checks that a YAML value lies in the JSON data model of a jcs vector (VEC-79). */
function toJsonModel(value, at) {
  if (value === null || typeof value === 'boolean' || typeof value === 'string') return value;
  if (typeof value === 'number') {
    if (!Number.isInteger(value) || Math.abs(value) > MAX_SAFE) throw new RunnerFailure(`${at}: not an integer within +/-(2^53 - 1) (VEC-79)`);
    return value;
  }
  if (Array.isArray(value)) return value.map((element, index) => toJsonModel(element, `${at}[${index}]`));
  if (isMap(value)) {
    const out = {};
    for (const name of Object.keys(value)) {
      Object.defineProperty(out, name, { value: toJsonModel(value[name], `${at}.${name}`), enumerable: true, writable: true, configurable: true });
    }
    return out;
  }
  throw new RunnerFailure(`${at}: outside the JSON data model (VEC-79)`);
}

function runCanonicalVector(doc) {
  for (const name of Object.keys(doc)) {
    if (!CANONICAL_FIELDS.has(name)) throw new RunnerFailure(`unknown field ${name} (VEC-19)`);
  }
  if (doc.specVersion !== '0.1') throw new RunnerFailure('unsupported specVersion (VEC-15)');
  if (!own(doc, 'input') || !isMap(doc.expect)) throw new RunnerFailure('a canonical vector has input and expect');
  if (doc.primitive === 'jcs') {
    const expected = bytesOf(doc.expect.output, 'expect.output');
    const actual = json.canonicalBytes(toJsonModel(doc.input, 'input'));
    assert.deepEqual(Buffer.from(actual), Buffer.from(expected), 'JCS output differs');
  } else if (doc.primitive === 'sha256') {
    if (typeof doc.expect.digest !== 'string') throw new RunnerFailure('expect.digest must be a string (VEC-83)');
    assert.equal(sha.sha256Hex(bytesOf(doc.input, 'input')), doc.expect.digest);
  } else {
    throw new RunnerFailure('primitive must be jcs or sha256 (VEC-75)');
  }
}

// ---------------------------------------------------------------------------
// The tests
// ---------------------------------------------------------------------------

const counts = new Map(); // group -> { pass, fail }
function record(group, passed) {
  const entry = counts.get(group) ?? { pass: 0, fail: 0 };
  entry[passed ? 'pass' : 'fail']++;
  counts.set(group, entry);
}

/** Wraps a vector run so that the pass count of its directory is kept. */
async function counted(group, run) {
  try {
    await run();
    record(group, true);
  } catch (error) {
    record(group, false);
    throw error;
  }
}

const canonicalFiles = listFiles(join(conformanceDir, 'canonical'), '.yaml');
const evaluateFiles = listFiles(join(conformanceDir, 'evaluate'), '.yaml');

test('the suite is present and complete', () => {
  assert.ok(canonicalFiles.length >= 29, `canonical vectors: ${canonicalFiles.length}`);
  assert.ok(evaluateFiles.length >= 500, `evaluate vectors: ${evaluateFiles.length}`);
});

describe('canonical vectors', () => {
  for (const file of canonicalFiles) {
    const id = relativePath(join(conformanceDir, 'canonical'), file).replace(/\.yaml$/, '');
    const group = `canonical/${id.split('/').slice(0, id.split('/').length - 1).join('/')}`;
    test(id, () =>
      counted(group, () => {
        const doc = loadVector(file);
        assert.equal(doc.id, id, 'the id equals the path (VEC-12)');
        runCanonicalVector(doc);
      }),
    );
  }
});

describe('evaluate vectors', () => {
  for (const file of evaluateFiles) {
    const id = relativePath(join(conformanceDir, 'evaluate'), file).replace(/\.yaml$/, '');
    const parts = id.split('/');
    const group = `evaluate/${parts.length > 1 ? parts[0] : ''}`;
    test(id, () =>
      counted(group, async () => {
        const doc = loadVector(file);
        assert.equal(doc.id, id, 'the id equals the path (VEC-12)');
        await runEvaluateVector(parseEvaluateVector(doc, file));
      }),
    );
  }
});

after(() => {
  let pass = 0;
  let fail = 0;
  const lines = [...counts.entries()].sort(([a], [b]) => (a < b ? -1 : 1));
  for (const [group, entry] of lines) {
    pass += entry.pass;
    fail += entry.fail;
    console.log(`# conformance ${group}: ${entry.pass} passed, ${entry.fail} failed`);
  }
  console.log(`# conformance total: ${pass} passed, ${fail} failed`);
});
