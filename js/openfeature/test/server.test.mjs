// The server provider through @openfeature/server-sdk: flags by type, reasons, metadata and errors
// (docs/openfeature-mapping.md). The Java provider has the same tests over the same fixture.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { ErrorCode, OpenFeature, StandardResolutionReasons as Reason } from '@openfeature/server-sdk';
import { SUMMARY, api, context, evaluator } from './support.mjs';

const { TierwiseServerProvider, TierwiseMetadata: M } = api;

let domains = 0;
/** A client bound to a provider under a domain of its own, so that tests never share a provider. */
async function clientFor(provider) {
  const domain = `server-${(domains += 1)}`;
  await OpenFeature.setProviderAndWait(domain, provider);
  return OpenFeature.getClient(domain);
}

const fixed = () => new TierwiseServerProvider(evaluator());
const wholeFacts = (flag, kind) => ({ facts: { 'parent.flag': flag, 'whole.kind': kind } });

// ---- boolean ---------------------------------------------------------------------------------------

test('a boolean key that only the root level sets is STATIC', async () => {
  const d = await (await clientFor(fixed())).getBooleanDetails('k.all', false, context());
  assert.equal(d.value, true);
  assert.equal(d.reason, Reason.STATIC);
  assert.equal(d.errorCode, undefined);
  assert.equal(d.flagMetadata[M.FLAG_KIND], 'key');
  assert.equal(d.flagMetadata[M.STRATEGY], 'all');
  assert.equal(d.flagMetadata[M.CONTRIBUTIONS], `[${SUMMARY.top('IGNORED')}]`);
});

test('a boolean key that a named node contributes to is a TARGETING_MATCH', async () => {
  const d = await (await clientFor(fixed())).getBooleanDetails('k.all', false, context({ levels: { mid: 'm1' } }));
  assert.equal(d.value, true);
  assert.equal(d.reason, Reason.TARGETING_MATCH);
  assert.equal(d.flagMetadata[M.CONTRIBUTIONS], `[${SUMMARY.top('IGNORED')},${SUMMARY.mid('m1', 'IGNORED')}]`);
});

test('a veto shows in the contribution summary', async () => {
  const d = await (await clientFor(fixed())).getBooleanDetails('k.all', true, context({ levels: { mid: 'm2' } }));
  assert.equal(d.value, false);
  assert.ok(d.flagMetadata[M.CONTRIBUTIONS].includes(SUMMARY.mid('m2', 'VETO')));
});

test('a commutative key that nobody sets has its identity and the DEFAULT reason', async () => {
  const d = await (await clientFor(fixed())).getBooleanDetails('k.any', true, context());
  assert.equal(d.value, false, 'the identity of any is false, not the caller\'s default');
  assert.equal(d.reason, Reason.DEFAULT);
  assert.equal(d.flagMetadata[M.CONTRIBUTIONS], '[]');
});

// ---- number ----------------------------------------------------------------------------------------

test('an integer key reads as a number with its provenance', async () => {
  const d = await (await clientFor(fixed())).getNumberDetails('k.over', 0, context({ levels: { mid: 'm1' }, axes: { lane: 'l1' } }));
  assert.equal(d.value, 8);
  assert.equal(d.reason, Reason.TARGETING_MATCH);
  assert.equal(d.flagMetadata[M.KIND], 'conf');
  assert.equal(d.flagMetadata[M.STRATEGY], 'override');
  assert.equal(d.flagMetadata[M.SOURCE], 'axis:lane');
  assert.equal(d.flagMetadata[M.SOURCE_NODE], 'l1');
  assert.equal(
    d.flagMetadata[M.CONTRIBUTIONS],
    `[${SUMMARY.top('SET')},${SUMMARY.mid('m1', 'SET')},{"scope":"axis","name":"lane","node":"l1","effect":"SET"}]`,
  );
  assert.match(d.flagMetadata[M.BUNDLE_VERSION], /^sha256:[0-9a-f]{64}$/);
});

test('a value from the root level alone is STATIC and names its source', async () => {
  const d = await (await clientFor(fixed())).getNumberDetails('k.over', 0, context());
  assert.equal(d.value, 1);
  assert.equal(d.reason, Reason.STATIC);
  assert.equal(d.flagMetadata[M.SOURCE], 'level:top');
  assert.equal(M.SOURCE_NODE in d.flagMetadata, false);
});

test('a declared default is the DEFAULT reason', async () => {
  const d = await (await clientFor(fixed())).getNumberDetails('k.big', 0, context());
  assert.equal(d.value, 3_000_000_000);
  assert.equal(d.reason, Reason.DEFAULT);
  assert.equal(d.flagMetadata[M.SOURCE], 'DEFAULT');
});

test('a key without a value resolves to the caller\'s default', async () => {
  const client = await clientFor(fixed());
  const none = await client.getNumberDetails('k.max', 42, context());
  assert.equal(none.value, 42);
  assert.equal(none.reason, Reason.DEFAULT);
  assert.equal(none.errorCode, undefined);
  assert.equal(none.flagMetadata[M.VALUE_ABSENT], true);
  const some = await client.getNumberDetails('k.max', 42, context({ axes: { lane: 'l1' } }));
  assert.equal(some.value, 3);
  assert.equal(some.reason, Reason.TARGETING_MATCH);
  assert.equal(M.VALUE_ABSENT in some.flagMetadata, false);
});

// ---- string ----------------------------------------------------------------------------------------

test('a string key and a decimal key read as strings', async () => {
  const client = await clientFor(fixed());
  assert.equal(await client.getStringValue('k.root', 'x', context()), 'root');
  assert.equal(await client.getStringValue('k.root', 'x', context({ levels: { mid: 'm2' } })), 'mid');
  assert.equal(await client.getStringValue('k.dec', 'x', context()), '1.5', 'a decimal is its canonical string');
});

test('a decimal is not a number', async () => {
  const d = await (await clientFor(fixed())).getNumberDetails('k.dec', 0.5, context());
  assert.equal(d.value, 0.5);
  assert.equal(d.errorCode, ErrorCode.TYPE_MISMATCH);
});

test('a service-sourced value comes from the provider function and is a TARGETING_MATCH', async () => {
  const served = new TierwiseServerProvider(evaluator(async (node) => ({ 'k.text': `from-${node}` })));
  const d = await (await clientFor(served)).getStringDetails('k.text', 'x', context({ levels: { low: 'n7' } }));
  assert.equal(d.value, 'from-n7');
  assert.equal(d.reason, Reason.TARGETING_MATCH);
  assert.equal(d.flagMetadata[M.SOURCE], 'level:low');
  assert.equal(d.flagMetadata[M.SOURCE_NODE], 'n7');
});

test('a source failure is not an error and shows in the summary', async () => {
  const failing = new TierwiseServerProvider(evaluator(async () => { throw new Error('down'); }));
  const d = await (await clientFor(failing)).getStringDetails('k.text', 'x', context({ levels: { low: 'n7' } }));
  assert.equal(d.value, 'd', 'skip leaves the declared default');
  assert.equal(d.errorCode, undefined);
  assert.equal(d.reason, Reason.DEFAULT);
  assert.ok(d.flagMetadata[M.CONTRIBUTIONS].includes('"effect":"UNAVAILABLE","onSourceFailure":"skip"'));
});

// ---- object ----------------------------------------------------------------------------------------

test('a set reads as a list in universe order', async () => {
  const d = await (await clientFor(fixed())).getObjectDetails('k.inter', {}, context({ levels: { mid: 'm2' } }));
  assert.deepEqual(d.value, ['u3', 'u1']);
  assert.equal(d.reason, Reason.TARGETING_MATCH);
});

test('a list and an object read as JSON values', async () => {
  const client = await clientFor(fixed());
  assert.deepEqual(await client.getObjectValue('k.list', {}, context()), ['a', 'b']);
  assert.deepEqual(await client.getObjectValue('k.deep', {}, context({ levels: { mid: 'm1' } })), {
    a: 10,
    b: { c: 'top', d: 'mid' },
  });
});

// ---- type mismatches -------------------------------------------------------------------------------

test('a key read as the wrong type is a TYPE_MISMATCH with the caller\'s default', async () => {
  const client = await clientFor(fixed());
  const none = context();
  const cases = [
    await client.getBooleanDetails('k.over', true, none),
    await client.getStringDetails('k.all', 'dflt', none),
    await client.getNumberDetails('k.root', 9, none),
    await client.getObjectDetails('k.root', { v: 1 }, none),
    await client.getObjectDetails('k.over', { v: 1 }, none),
    await client.getBooleanDetails('k.list', false, none),
  ];
  const defaults = [true, 'dflt', 9, { v: 1 }, { v: 1 }, false];
  cases.forEach((d, index) => {
    assert.equal(d.errorCode, ErrorCode.TYPE_MISMATCH, d.flagKey);
    assert.equal(d.reason, Reason.ERROR);
    assert.deepEqual(d.value, defaults[index]);
    assert.ok(d.errorMessage.length > 0);
  });
});

// ---- features --------------------------------------------------------------------------------------

test('a matching rule is a TARGETING_MATCH with the rule as variant', async () => {
  const d = await (await clientFor(fixed())).getStringDetails('f.one', 'none', context({ facts: wholeFacts(true, 'a') }));
  assert.equal(d.value, 'ALLOW');
  assert.equal(d.reason, Reason.TARGETING_MATCH);
  assert.equal(d.variant, 'r1');
  assert.equal(d.errorCode, undefined);
  assert.equal(d.flagMetadata[M.FLAG_KIND], 'feature');
  assert.equal(d.flagMetadata[M.DECISION_REASON], 'RULE');
  assert.equal(d.flagMetadata[M.RULE_ID], 'r1');
  assert.equal(d.flagMetadata[M.RULE_NAME], 'first rule');
  assert.equal(d.flagMetadata[M.POLICY], 'p1');
  assert.equal(d.flagMetadata[M.BINDING_LEVEL], 'top');
  assert.equal(M.BINDING_NODE in d.flagMetadata, false);
  assert.match(d.flagMetadata[M.BUNDLE_VERSION], /^sha256:/);
});

test('the policy default is the DEFAULT reason', async () => {
  const d = await (await clientFor(fixed())).getStringDetails('f.one', 'none', context({ facts: wholeFacts(false, 'a') }));
  assert.equal(d.value, 'DENY');
  assert.equal(d.reason, Reason.DEFAULT);
  assert.equal(d.variant, undefined);
  assert.equal(d.flagMetadata[M.DECISION_REASON], 'DEFAULT');
  assert.equal(M.RULE_ID in d.flagMetadata, false);
});

test('a missing fact is the DEFAULT reason with the facts in the metadata', async () => {
  const client = await clientFor(fixed());
  const d = await client.getStringDetails('f.one', 'none', context());
  assert.equal(d.value, 'DENY', 'the feature\'s if_fact_missing effect');
  assert.equal(d.reason, Reason.DEFAULT);
  assert.equal(d.flagMetadata[M.DECISION_REASON], 'MISSING_FACT');
  assert.equal(d.flagMetadata[M.MISSING_FACTS], '["parent.flag","whole.kind"]');
  const nulled = await client.getStringDetails('f.one', 'none', context({ facts: { facts: { 'parent.flag': null, 'whole.kind': 'a' } } }));
  assert.equal(nulled.flagMetadata[M.DECISION_REASON], 'MISSING_FACT', 'a null fact is missing');
});

test('integer, child and decimal facts are accepted', async () => {
  const facts = {
    facts: { 'parent.flag': true, 'whole.kind': 'a', 'whole.count': 3, 'whole.price': '9.99' },
    children: { part: [{ 'part.tag': 'x', 'part.qty': 5 }, { 'part.tag': 'y' }] },
  };
  const d = await (await clientFor(fixed())).getStringDetails('f.one', 'none', context({ facts }));
  assert.equal(d.errorCode, undefined, d.errorMessage);
  assert.equal(d.value, 'ALLOW');
});

test('skipped scopes are listed in the metadata', async () => {
  const d = await (await clientFor(fixed())).getStringDetails(
    'f.one',
    'none',
    context({ levels: { mid: 'm1' }, axes: { lane: 'unlisted' }, facts: wholeFacts(true, 'a') }),
  );
  assert.equal(d.value, 'ALLOW');
  assert.equal(d.flagMetadata[M.SKIPPED_SCOPES], '[{"scope":"axis","name":"lane","node":"unlisted","reason":"UNKNOWN_NODE"}]');
});

test('an object effect reads as an object through an object flag', async () => {
  const d = await (await clientFor(fixed())).getObjectDetails('f.obj', {}, context({ facts: { facts: { 'parent.flag': true } } }));
  assert.deepEqual(d.value, { level: 2, note: 'n' });
  assert.equal(d.reason, Reason.TARGETING_MATCH);
  assert.equal(d.variant, 'on');
});

test('a feature read as the wrong type is a TYPE_MISMATCH', async () => {
  const client = await clientFor(fixed());
  const facts = context({ facts: wholeFacts(true, 'a') });
  const asObject = await client.getObjectDetails('f.one', { v: 'dflt' }, facts);
  assert.equal(asObject.errorCode, ErrorCode.TYPE_MISMATCH);
  assert.deepEqual(asObject.value, { v: 'dflt' });
  assert.equal(asObject.flagMetadata[M.FLAG_KIND], 'feature');
  const asString = await client.getStringDetails('f.obj', 'dflt', context({ facts: { facts: { 'parent.flag': true } } }));
  assert.equal(asString.errorCode, ErrorCode.TYPE_MISMATCH);
  assert.equal(asString.value, 'dflt');
});

test('a feature asked for as a boolean or number is a TYPE_MISMATCH, not a missing flag', async () => {
  const client = await clientFor(fixed());
  const asBoolean = await client.getBooleanDetails('f.one', true, context());
  assert.equal(asBoolean.errorCode, ErrorCode.TYPE_MISMATCH);
  assert.equal(asBoolean.value, true);
  assert.ok(asBoolean.errorMessage.includes('feature'));
  assert.equal((await client.getNumberDetails('f.one', 1, context())).errorCode, ErrorCode.TYPE_MISMATCH);
});

test('the null effect of a nullable enum cannot be carried, so it resolves to the default', async () => {
  const client = await clientFor(fixed());
  const off = await client.getStringDetails('f.null', 'dflt', context({ facts: { facts: { 'parent.flag': false } } }));
  assert.equal(off.value, 'dflt');
  assert.equal(off.reason, Reason.DEFAULT);
  assert.equal(off.errorCode, undefined);
  assert.equal(off.flagMetadata[M.EFFECT_NULL], true);
  const on = await client.getStringDetails('f.null', 'dflt', context({ facts: { facts: { 'parent.flag': true } } }));
  assert.equal(on.value, 'YES');
  assert.equal(M.EFFECT_NULL in on.flagMetadata, false);
});

test('a key is looked up before a feature', async () => {
  const calls = [];
  const stub = {
    resolve: async () => ({ key: 'shared', strategy: 'override', value: 'from-key', bundleVersion: 'sha256:x', skippedScopes: [], contributions: [], source: 'DEFAULT' }),
    resolveSync: () => { throw new Error('unused'); },
    decide: async () => { calls.push('decide'); throw new Error('unused'); },
    decideSync: () => { throw new Error('unused'); },
  };
  assert.equal(await (await clientFor(new TierwiseServerProvider(stub))).getStringValue('shared', 'x', context()), 'from-key');
  assert.deepEqual(calls, [], 'a key that exists is never looked up as a feature');
});

// ---- errors ----------------------------------------------------------------------------------------

function assertError(d, errorCode, tierwiseCode, fallback) {
  assert.equal(d.errorCode, errorCode, d.errorMessage);
  assert.equal(d.reason, Reason.ERROR);
  assert.deepEqual(d.value, fallback, 'the caller\'s default comes back');
  assert.equal(d.flagMetadata[M.TIERWISE_CODE], tierwiseCode);
  assert.ok(d.errorMessage.includes(tierwiseCode), d.errorMessage);
}

test('an unknown name is FLAG_NOT_FOUND for every type', async () => {
  const client = await clientFor(fixed());
  const none = context();
  assertError(await client.getBooleanDetails('nope', true, none), ErrorCode.FLAG_NOT_FOUND, 'E_UNKNOWN_FEATURE', true);
  assertError(await client.getStringDetails('nope', 'd', none), ErrorCode.FLAG_NOT_FOUND, 'E_UNKNOWN_FEATURE', 'd');
  assertError(await client.getNumberDetails('nope', 4, none), ErrorCode.FLAG_NOT_FOUND, 'E_UNKNOWN_FEATURE', 4);
  assertError(await client.getObjectDetails('nope', { v: 1 }, none), ErrorCode.FLAG_NOT_FOUND, 'E_UNKNOWN_FEATURE', { v: 1 });
  assert.ok((await client.getStringDetails('nope', 'd', none)).errorMessage.includes("no key or feature is named 'nope'"));
});

test('context errors are INVALID_CONTEXT with the Tierwise code', async () => {
  const client = await clientFor(fixed());
  const number = (ctx) => client.getNumberDetails('k.over', 0, ctx);
  assertError(await number(context({ levels: { nowhere: 'x' } })), ErrorCode.INVALID_CONTEXT, 'E_UNKNOWN_SCOPE', 0);
  assertError(await number(context({ levels: { mid: 'm9' } })), ErrorCode.INVALID_CONTEXT, 'E_UNKNOWN_NODE', 0);
  assertError(await number(context({ axes: { mid: 'm1' } })), ErrorCode.INVALID_CONTEXT, 'E_UNKNOWN_SCOPE', 0);
  assertError(await number(context({ levels: { top: 'x' } })), ErrorCode.INVALID_CONTEXT, 'E_CONTEXT_INVALID', 0);
});

test('malformed tierwise.levels and tierwise.axes are INVALID_CONTEXT', async () => {
  const client = await clientFor(fixed());
  for (const malformed of ['mid=m1', 5, true, ['m1'], { mid: 5 }, { mid: true }, { mid: { x: 'y' } }, { mid: null }, new Date(0)]) {
    const d = await client.getNumberDetails('k.over', 0, { 'tierwise.levels': malformed });
    assertError(d, ErrorCode.INVALID_CONTEXT, 'E_CONTEXT_INVALID', 0);
  }
  assertError(await client.getNumberDetails('k.over', 0, { 'tierwise.axes': 'lane' }), ErrorCode.INVALID_CONTEXT, 'E_CONTEXT_INVALID', 0);
});

test('a context error wins over an unknown flag', async () => {
  const d = await (await clientFor(fixed())).getStringDetails('nope', 'd', context({ levels: { nowhere: 'x' } }));
  assertError(d, ErrorCode.INVALID_CONTEXT, 'E_UNKNOWN_SCOPE', 'd');
});

test('invalid facts are INVALID_CONTEXT for a feature', async () => {
  const client = await clientFor(fixed());
  const read = (facts) => client.getStringDetails('f.one', 'd', context({ facts }));
  assertError(await read({ facts: { 'parent.flag': 'true' } }), ErrorCode.INVALID_CONTEXT, 'E_FACTS_INVALID', 'd');
  assertError(await read({ facts: { 'ghost.fact': 1 } }), ErrorCode.INVALID_CONTEXT, 'E_FACTS_INVALID', 'd');
  assertError(await read({ surprise: {} }), ErrorCode.INVALID_CONTEXT, 'E_FACTS_INVALID', 'd');
  assertError(await read('facts'), ErrorCode.INVALID_CONTEXT, 'E_FACTS_INVALID', 'd');
  assertError(await read({ facts: { 'parent.flag': true, 'whole.note': new Date(0) } }), ErrorCode.INVALID_CONTEXT, 'E_FACTS_INVALID', 'd');
});

test('a key ignores the facts, the targeting key and other attributes', async () => {
  const client = await clientFor(fixed());
  const ignoredFacts = await client.getNumberDetails('k.over', 0, { 'tierwise.facts': 'not even a map of facts' });
  assert.equal(ignoredFacts.value, 1);
  assert.equal(ignoredFacts.errorCode, undefined);
  const others = await client.getNumberDetails('k.over', 0, { targetingKey: 'user-1', mid: 'm1', country: 'x' });
  assert.equal(others.value, 1, 'a bare attribute named like a level is not a level');
  assert.equal(others.reason, Reason.STATIC);
});

// ---- the evaluator source --------------------------------------------------------------------------

test('a source that returns no evaluator is PROVIDER_NOT_READY', async () => {
  const provider = new TierwiseServerProvider(() => undefined);
  const d = await provider.resolveStringEvaluation('k.root', 'd', {});
  assert.equal(d.errorCode, ErrorCode.PROVIDER_NOT_READY);
  assert.equal(d.reason, Reason.ERROR);
  assert.equal(d.value, 'd');
  assert.equal((await new TierwiseServerProvider(() => null).resolveStringEvaluation('k.root', 'd', {})).errorCode, ErrorCode.PROVIDER_NOT_READY);
});

test('the source is read on every evaluation, so a bundle swap is seen', async () => {
  let current = evaluator(async () => ({ 'k.text': 'one' }));
  const provider = new TierwiseServerProvider(() => current);
  const low = context({ levels: { low: 'n' } });
  assert.equal((await provider.resolveStringEvaluation('k.text', 'd', low)).value, 'one');
  current = evaluator(async () => ({ 'k.text': 'two' }));
  assert.equal((await provider.resolveStringEvaluation('k.text', 'd', low)).value, 'two');
});

test('an error that is not a Tierwise error propagates to the SDK', async () => {
  const boom = new Error('no bundle yet');
  const provider = new TierwiseServerProvider(() => { throw boom; });
  await assert.rejects(provider.resolveStringEvaluation('k.root', 'd', {}), boom);
  const d = await (await clientFor(provider)).getStringDetails('k.root', 'd', {});
  assert.equal(d.errorCode, ErrorCode.GENERAL);
  assert.equal(d.value, 'd');
});

test('the constructor rejects anything but an evaluator or a function', () => {
  for (const bad of [undefined, null, 5, 'x', {}, { resolve() {} }, []]) {
    assert.throws(() => new TierwiseServerProvider(bad), TypeError, String(bad));
  }
});

test('a Tierwise error is recognised by name, so two copies of the evaluator work together', async () => {
  const foreign = new Error('E_UNKNOWN_KEY: nothing');
  foreign.name = 'TierwiseError';
  foreign.code = 'E_UNKNOWN_KEY';
  const stub = { resolve: async () => { throw foreign; }, decide: async () => { throw Object.assign(new Error('E_UNKNOWN_FEATURE: nothing'), { name: 'TierwiseError', code: 'E_UNKNOWN_FEATURE' }); }, resolveSync() {}, decideSync() {} };
  const d = await new TierwiseServerProvider(stub).resolveStringEvaluation('anything', 'd', {});
  assert.equal(d.errorCode, ErrorCode.FLAG_NOT_FOUND);
});

test('the provider has a stable name and runs on the server', () => {
  const provider = fixed();
  assert.equal(provider.metadata.name, 'Tierwise');
  assert.equal(provider.runsOn, 'server');
});

test('the attribute names are stable', () => {
  assert.equal(api.LEVELS_ATTRIBUTE, 'tierwise.levels');
  assert.equal(api.AXES_ATTRIBUTE, 'tierwise.axes');
  assert.equal(api.FACTS_ATTRIBUTE, 'tierwise.facts');
  assert.equal(api.PROVIDER_NAME, 'Tierwise');
});

test('contexts merge attribute by attribute across the OpenFeature layers', async () => {
  const domain = `server-merge-${(domains += 1)}`;
  await OpenFeature.setProviderAndWait(domain, fixed());
  const client = OpenFeature.getClient(domain);
  client.setContext(context({ levels: { mid: 'm1' } }));
  assert.equal(await client.getNumberValue('k.over', 0), 6);
  const merged = await client.getNumberDetails('k.over', 0, context({ axes: { lane: 'l1' } }));
  assert.equal(merged.value, 8, 'the client context supplies the level, the call supplies the axis');
  assert.equal(await client.getNumberValue('k.over', 0, context({ levels: { mid: 'm1' } })), 6);
});
