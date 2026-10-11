// The pure parts of the mapping, and the shape of the published package.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import { ErrorCode } from '@openfeature/core';
import { api, evaluatorApi, mapping } from './support.mjs';

test('every evaluation code maps to its OpenFeature code', () => {
  const expected = {
    E_UNKNOWN_KEY: ErrorCode.FLAG_NOT_FOUND,
    E_UNKNOWN_FEATURE: ErrorCode.FLAG_NOT_FOUND,
    E_CONTEXT_INVALID: ErrorCode.INVALID_CONTEXT,
    E_UNKNOWN_SCOPE: ErrorCode.INVALID_CONTEXT,
    E_UNKNOWN_NODE: ErrorCode.INVALID_CONTEXT,
    E_FACTS_INVALID: ErrorCode.INVALID_CONTEXT,
    E_PROVIDER_REQUIRED: ErrorCode.GENERAL,
    E_PROVIDER_MISSING: ErrorCode.GENERAL,
    E_EVALUATOR_OPTIONS: ErrorCode.GENERAL,
    E_BUNDLE_INTEGRITY: ErrorCode.GENERAL,
    E_BUNDLE_UNSUPPORTED: ErrorCode.GENERAL,
    E_SOMETHING_NEW: ErrorCode.GENERAL,
  };
  for (const [code, mapped] of Object.entries(expected)) assert.equal(mapping.errorCodeOf(code), mapped, code);
  for (const code of evaluatorApi.ERROR_CODES) assert.ok(code in expected, `${code} has a documented mapping`);
});

test('convert accepts exactly the kind each target asks for', () => {
  const accepts = (target, value) => mapping.convert(target, value).ok;
  assert.equal(accepts('boolean', true), true);
  assert.equal(accepts('boolean', 'true'), false);
  assert.equal(accepts('string', 'x'), true);
  assert.equal(accepts('string', 1), false);
  assert.equal(accepts('number', 7), true);
  assert.equal(accepts('number', -(2 ** 53 - 1)), true);
  assert.equal(accepts('number', 2 ** 53), false, 'beyond the safe integers');
  assert.equal(accepts('number', 1.5), false);
  assert.equal(accepts('number', '7'), false);
  assert.equal(accepts('object', ['a']), true);
  assert.equal(accepts('object', { a: 1 }), true);
  assert.equal(accepts('object', 'a'), false);
  assert.equal(accepts('object', true), false);
  assert.equal(mapping.convert('boolean', 5).message, 'the value is an integer, not boolean');
});

test('only string and object evaluations read features', () => {
  assert.deepEqual(['boolean', 'string', 'number', 'object'].map(mapping.readsFeatures), [false, true, false, true]);
});

test('a Tierwise error is recognised by name and code', () => {
  assert.equal(mapping.isTierwiseError(new evaluatorApi.TierwiseError('E_UNKNOWN_KEY', 'x')), true);
  assert.equal(mapping.isTierwiseError(new Error('x')), false);
  assert.equal(mapping.isTierwiseError(Object.assign(new Error('x'), { name: 'TierwiseError' })), false, 'no code');
  assert.equal(mapping.isTierwiseError('E_UNKNOWN_KEY'), false);
  assert.equal(mapping.isTierwiseError(null), false);
});

test('the context is read from the two attributes only', () => {
  assert.deepEqual(mapping.contextOf(undefined), {});
  assert.deepEqual(mapping.contextOf({ targetingKey: 'u', mid: 'm1' }), {});
  assert.deepEqual(mapping.contextOf({ 'tierwise.levels': { mid: 'm1' }, 'tierwise.axes': { lane: 'l1' } }), {
    levels: { mid: 'm1' },
    axes: { lane: 'l1' },
  });
  assert.deepEqual(mapping.contextOf({ 'tierwise.levels': undefined }), {});
  assert.equal(mapping.factsOf(undefined), undefined);
  assert.equal(mapping.factsOf({}), undefined);
  assert.deepEqual(mapping.factsOf({ 'tierwise.facts': { facts: {} } }), { facts: {} });
});

test('the contribution summary is written in a fixed member order', () => {
  const resolved = {
    key: 'k',
    strategy: 'all',
    value: true,
    bundleVersion: 'sha256:x',
    skippedScopes: [],
    contributions: [
      { scope: 'level', name: 'top', value: true, effect: 'IGNORED' },
      { scope: 'level', name: 'low', node: 'n"1', value: false, effect: 'UNAVAILABLE', onSourceFailure: 'deny' },
      { scope: 'axis', name: 'lane', node: 'l1', effect: 'INVALID', onSourceFailure: 'skip' },
    ],
  };
  assert.equal(
    mapping.resolvedDetails(resolved, false, 'boolean').flagMetadata.contributions,
    '[{"scope":"level","name":"top","effect":"IGNORED"},' +
      '{"scope":"level","name":"low","node":"n\\"1","effect":"UNAVAILABLE","onSourceFailure":"deny"},' +
      '{"scope":"axis","name":"lane","node":"l1","effect":"INVALID","onSourceFailure":"skip"}]',
    'the same string as MappingUnitTest.java expects',
  );
});

test('the package exports the two providers, the attribute names and the metadata names', () => {
  assert.deepEqual(
    Object.keys(api).sort(),
    ['AXES_ATTRIBUTE', 'FACTS_ATTRIBUTE', 'LEVELS_ATTRIBUTE', 'PROVIDER_NAME', 'TierwiseMetadata', 'TierwiseServerProvider', 'TierwiseWebProvider'],
  );
  assert.ok(Object.isFrozen(api.TierwiseMetadata));
});

test('the CommonJS build works with the CommonJS SDK', async () => {
  const require = createRequire(import.meta.url);
  const cjs = require('../dist/cjs/index.js');
  const { OpenFeature } = require('@openfeature/server-sdk');
  const { evaluator, context } = await import('./support.mjs');
  await OpenFeature.setProviderAndWait('cjs', new cjs.TierwiseServerProvider(evaluator()));
  const details = await OpenFeature.getClient('cjs').getNumberDetails('k.over', 0, context({ levels: { mid: 'm1' } }));
  assert.equal(details.value, 6);
  assert.equal(details.flagMetadata.bundleVersion.slice(0, 7), 'sha256:');
});

test('the package declares the OpenFeature SDKs as peer dependencies and nothing else at run time', () => {
  const manifest = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));
  assert.deepEqual(Object.keys(manifest.dependencies), ['@visporg/evaluator']);
  assert.deepEqual(Object.keys(manifest.peerDependencies).sort(), ['@openfeature/core', '@openfeature/server-sdk', '@openfeature/web-sdk']);
});

test('the build imports only the evaluator and @openfeature/core', () => {
  const imports = new Set();
  for (const file of ['index', 'mapping', 'metadata', 'browser', 'server', 'source']) {
    const text = readFileSync(new URL(`../dist/esm/${file}.js`, import.meta.url), 'utf8');
    for (const match of text.matchAll(/from\s+'([^']+)'/g)) imports.add(match[1]);
  }
  const external = [...imports].filter((name) => !name.startsWith('./')).sort();
  assert.deepEqual(external, ['@openfeature/core', '@visporg/evaluator']);
});
