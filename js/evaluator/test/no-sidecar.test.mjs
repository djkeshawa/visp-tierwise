// An evaluator never reads a provenance sidecar (BUNDLE-145), so it never reports E_PROVENANCE_INVALID
// (BUNDLE-153). The bundleVersion of every result comes from the bundle alone.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { api, bundleBytes, codeOf, codeOfAsync, distDir, listFiles } from './support.mjs';
import { content } from './fixtures.mjs';

const { createEvaluator, loadBundle } = api;
const bytes = bundleBytes(content());
const OTHER_VERSION = `sha256:${'0'.repeat(64)}`;
/** A sidecar for the bundle that names another version, and one that is malformed. */
const SIDECARS = [
  { bundleVersion: OTHER_VERSION, createdAt: '2026-01-01T00:00:00Z' },
  { createdAt: 'yesterday', extra: true },
  'not a sidecar',
];

test('BUNDLE-145: the evaluator API has no sidecar input', () => {
  assert.equal(loadBundle.length, 1, 'loadBundle takes the bundle bytes only');
  assert.equal(createEvaluator.length, 2, 'createEvaluator takes the bundle and the options only');
  const loaded = loadBundle(bytes);
  for (const sidecar of SIDECARS) {
    assert.deepEqual({ ...loadBundle(bytes, sidecar) }, { ...loaded }, 'a second argument to loadBundle is not read');
  }
  const low = { fn: async () => ({}), timeoutMs: 50 };
  for (const name of ['sidecar', 'provenance', 'bundleVersion', 'createdAt']) {
    const options = { providers: { low }, [name]: SIDECARS[0] };
    assert.equal(codeOf(() => createEvaluator(loaded, options)), 'E_EVALUATOR_OPTIONS', name);
  }
});

test('BUNDLE-145: the bundleVersion of every result comes from the bundle alone', async () => {
  const loaded = loadBundle(bytes);
  const tw = createEvaluator(loaded, { providers: { low: { fn: async () => ({ 'k.min': 7 }), timeoutMs: 50 } } });
  assert.equal(tw.bundleVersion, loaded.bundleVersion);
  const context = { levels: { mid: 'm1', low: 'n1' } };
  const facts = { facts: { 'parent.flag': true, 'whole.kind': 'b' }, children: { part: [{ 'part.tag': 'x' }] } };
  const results = [
    await tw.resolve(context, 'k.min'),
    tw.resolveSync({ levels: { mid: 'm1' } }, 'k.over'),
    await tw.decide(context, 'f.one', facts),
    tw.decideSync(context, 'f.obj', { facts: { 'parent.flag': true } }),
  ];
  for (const result of results) assert.equal(result.bundleVersion, loaded.bundleVersion);
  assert.notEqual(loaded.bundleVersion, OTHER_VERSION);
});

test('BUNDLE-153: the evaluator never reports E_PROVENANCE_INVALID', async () => {
  assert.ok(!api.ERROR_CODES.includes('E_PROVENANCE_INVALID'));
  for (const file of listFiles(distDir, '.js')) {
    const source = readFileSync(file, 'utf8');
    assert.ok(!source.includes('E_PROVENANCE_INVALID'), `${file} names E_PROVENANCE_INVALID`);
    assert.ok(!/provenance\.json|from ['"](node:)?fs['"]|require\(['"](node:)?fs['"]\)/.test(source), `${file} reads files`);
  }
  // Inputs that would break a sidecar rule are not sidecar inputs: the errors are the ones of the evaluator.
  const loaded = loadBundle(bytes);
  const tw = createEvaluator(loaded, { providers: { low: { fn: async () => ({}), timeoutMs: 50 } } });
  assert.equal(codeOf(() => tw.resolveSync(SIDECARS[0], 'k.min')), 'E_CONTEXT_INVALID');
  assert.equal(await codeOfAsync(tw.resolve({}, 'k.min.provenance.json')), 'E_UNKNOWN_KEY');
  assert.equal(codeOf(() => tw.decideSync({}, 'f.one', SIDECARS[2])), 'E_FACTS_INVALID');
});
