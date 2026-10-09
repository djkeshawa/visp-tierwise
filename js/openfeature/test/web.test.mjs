// The web provider through @openfeature/web-sdk. Its evaluations are synchronous and its context is static, so
// the tests set the context on a domain and read flags without a per-call context. The mapping is the one of
// the server provider (server.test.mjs); these tests cover the web-specific parts and a sample of the shared ones.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ErrorCode, OpenFeature, StandardResolutionReasons as Reason } from '@openfeature/web-sdk';
import { SUMMARY, api, context, evaluator } from './support.mjs';

const { TierwiseWebProvider, TierwiseMetadata: M } = api;

let domains = 0;
/** A client bound to a provider and a static context under a domain of its own. */
async function clientFor(provider, ctx = context()) {
  const domain = `web-${(domains += 1)}`;
  await OpenFeature.setProviderAndWait(domain, provider, ctx);
  return OpenFeature.getClient(domain);
}

const fixed = () => new TierwiseWebProvider(evaluator());

test('the provider has a stable name and runs on the client', () => {
  const provider = fixed();
  assert.equal(provider.metadata.name, 'Tierwise');
  assert.equal(provider.runsOn, 'client');
});

test('evaluations are synchronous', async () => {
  const provider = fixed();
  const details = provider.resolveBooleanEvaluation('k.all', false, context());
  assert.ok(!(details instanceof Promise));
  assert.equal(details.value, true);
});

test('keys map by type with reasons and metadata', async () => {
  const client = await clientFor(fixed(), context({ levels: { mid: 'm1' }, axes: { lane: 'l1' } }));
  const bool = client.getBooleanDetails('k.all', false);
  assert.equal(bool.value, true);
  assert.equal(bool.reason, Reason.TARGETING_MATCH);
  assert.equal(bool.flagMetadata[M.CONTRIBUTIONS], `[${SUMMARY.top('IGNORED')},${SUMMARY.mid('m1', 'IGNORED')}]`);
  const number = client.getNumberDetails('k.over', 0);
  assert.equal(number.value, 8);
  assert.equal(number.flagMetadata[M.SOURCE], 'axis:lane');
  assert.equal(number.flagMetadata[M.KIND], 'conf');
  assert.equal(client.getStringValue('k.dec', 'x'), '1.5');
  assert.deepEqual(client.getObjectValue('k.inter', {}), ['u3', 'u1', 'u2']);
  assert.deepEqual(client.getObjectValue('k.deep', {}), { a: 10, b: { c: 'top', d: 'mid' } });
});

test('a root-only value is STATIC, a declared default is DEFAULT, an absent value is the caller\'s default', async () => {
  const client = await clientFor(fixed());
  assert.equal(client.getNumberDetails('k.over', 0).reason, Reason.STATIC);
  assert.equal(client.getNumberDetails('k.big', 0).reason, Reason.DEFAULT);
  const absent = client.getNumberDetails('k.max', 42);
  assert.equal(absent.value, 42);
  assert.equal(absent.flagMetadata[M.VALUE_ABSENT], true);
});

test('a feature is decided over the facts in the context', async () => {
  const client = await clientFor(fixed(), context({ facts: { facts: { 'parent.flag': true, 'whole.kind': 'a' } } }));
  const details = client.getStringDetails('f.one', 'none');
  assert.equal(details.value, 'ALLOW');
  assert.equal(details.reason, Reason.TARGETING_MATCH);
  assert.equal(details.variant, 'r1');
  assert.equal(details.flagMetadata[M.DECISION_REASON], 'RULE');
  assert.equal(details.flagMetadata[M.FLAG_KIND], 'feature');
  const none = await clientFor(fixed());
  const missing = none.getStringDetails('f.one', 'none');
  assert.equal(missing.value, 'DENY');
  assert.equal(missing.flagMetadata[M.MISSING_FACTS], '["parent.flag","whole.kind"]');
});

test('a context change is seen by the next evaluation', async () => {
  const domain = `web-${(domains += 1)}`;
  await OpenFeature.setProviderAndWait(domain, fixed(), context());
  const client = OpenFeature.getClient(domain);
  assert.equal(client.getNumberValue('k.over', 0), 1);
  await OpenFeature.setContext(domain, context({ levels: { mid: 'm1' } }));
  assert.equal(client.getNumberValue('k.over', 0), 6);
});

test('type mismatches and errors map like the server provider', async () => {
  const client = await clientFor(fixed());
  const mismatch = client.getBooleanDetails('k.over', true);
  assert.equal(mismatch.errorCode, ErrorCode.TYPE_MISMATCH);
  assert.equal(mismatch.value, true);
  assert.equal(client.getBooleanDetails('f.one', true).errorCode, ErrorCode.TYPE_MISMATCH);
  const missing = client.getStringDetails('nope', 'd');
  assert.equal(missing.errorCode, ErrorCode.FLAG_NOT_FOUND);
  assert.equal(missing.flagMetadata[M.TIERWISE_CODE], 'E_UNKNOWN_FEATURE');
  const badScope = await clientFor(fixed(), context({ levels: { nowhere: 'x' } }));
  const invalid = badScope.getNumberDetails('k.over', 0);
  assert.equal(invalid.errorCode, ErrorCode.INVALID_CONTEXT);
  assert.equal(invalid.flagMetadata[M.TIERWISE_CODE], 'E_UNKNOWN_SCOPE');
  const badFacts = await clientFor(fixed(), context({ facts: { facts: { 'parent.flag': 'true' } } }));
  const factsError = badFacts.getStringDetails('f.one', 'd');
  assert.equal(factsError.errorCode, ErrorCode.INVALID_CONTEXT);
  assert.equal(factsError.flagMetadata[M.TIERWISE_CODE], 'E_FACTS_INVALID');
});

test('a key that needs a service-sourced level cannot be evaluated synchronously', async () => {
  const served = new TierwiseWebProvider(evaluator(async () => ({ 'k.text': 'never used' })));
  const client = await clientFor(served, context({ levels: { low: 'n7' } }));
  const details = client.getStringDetails('k.text', 'd');
  assert.equal(details.value, 'd');
  assert.equal(details.errorCode, ErrorCode.GENERAL);
  assert.equal(details.flagMetadata[M.TIERWISE_CODE], 'E_PROVIDER_REQUIRED');
  // Without a node for the service-sourced level the key needs no provider call.
  const local = await clientFor(served, context());
  assert.equal(local.getStringValue('k.text', 'd'), 'd');
});

test('a source that returns no evaluator is PROVIDER_NOT_READY, and the source is read on every evaluation', () => {
  let current;
  const provider = new TierwiseWebProvider(() => current);
  assert.equal(provider.resolveStringEvaluation('k.root', 'd', {}).errorCode, ErrorCode.PROVIDER_NOT_READY);
  current = evaluator();
  assert.equal(provider.resolveStringEvaluation('k.root', 'd', {}).value, 'root');
});

test('the constructor rejects anything but an evaluator or a function', () => {
  for (const bad of [undefined, null, 5, 'x', {}]) assert.throws(() => new TierwiseWebProvider(bad), TypeError);
});
