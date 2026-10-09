// load bundle: the check order of EVAL-14, the strict document rules (BUNDLE-111, BUNDLE-116) and the structural
// checks of BUNDLE-118 to BUNDLE-136. Each negative case changes one thing in an otherwise valid bundle, and the
// digest is recomputed, so the structural check is the only fault.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { api, bundleBytes, codeOf, json, sha } from './support.mjs';
import { bundle, changed, content } from './fixtures.mjs';

const { loadBundle } = api;
const decoder = new TextDecoder();
const encoder = new TextEncoder();

/** The canonical text of the base bundle. */
const baseText = () => decoder.decode(bundleBytes(content()));

/** Loads a content object (digest recomputed) and returns the error code, or null when it loads. */
const loadCode = (c) => codeOf(() => loadBundle(bundleBytes(c)));

test('a valid bundle loads and carries its version', () => {
  const loaded = bundle();
  assert.match(loaded.bundleVersion, /^sha256:[0-9a-f]{64}$/);
  assert.equal(loaded.bundleFormatVersion, '1');
  assert.equal(loaded.specVersion, '0.1');
  assert.ok(Object.isFrozen(loaded), 'the loaded bundle cannot be changed (EVAL-46)');
});

test('bytes, a Buffer, an ArrayBuffer and a string give the same bundle version', () => {
  const bytes = bundleBytes(content());
  const expected = loadBundle(bytes).bundleVersion;
  assert.equal(loadBundle(Buffer.from(bytes)).bundleVersion, expected);
  assert.equal(loadBundle(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)).bundleVersion, expected);
  assert.equal(loadBundle(decoder.decode(bytes)).bundleVersion, expected);
  const padded = new Uint8Array(bytes.length + 10);
  padded.set(bytes, 5);
  assert.equal(loadBundle(padded.subarray(5, 5 + bytes.length)).bundleVersion, expected);
});

test('loadBundle is synchronous (EVAL-3)', () => {
  assert.ok(!(loadBundle(bundleBytes(content())) instanceof Promise));
});

test('loadBundle takes bytes or a string only', () => {
  assert.throws(() => loadBundle(5), TypeError);
  assert.throws(() => loadBundle(null), TypeError);
  assert.throws(() => loadBundle({}), TypeError);
});

// ---------------------------------------------------------------------------
// Step 1 of EVAL-14: strict parsing and the envelope
// ---------------------------------------------------------------------------

test('a byte-order mark, a trailing newline and added whitespace are rejected (BUNDLE-3, BUNDLE-4, BUNDLE-8)', () => {
  const bytes = bundleBytes(content());
  const withBom = new Uint8Array([0xef, 0xbb, 0xbf, ...bytes]);
  assert.equal(codeOf(() => loadBundle(withBom)), 'E_BUNDLE_INTEGRITY');
  assert.equal(codeOf(() => loadBundle(`﻿${decoder.decode(bytes)}`)), 'E_BUNDLE_INTEGRITY');
  assert.equal(codeOf(() => loadBundle(`${decoder.decode(bytes)}\n`)), 'E_BUNDLE_INTEGRITY');
  assert.equal(codeOf(() => loadBundle(decoder.decode(bytes).replace('{"bundleVersion"', '{ "bundleVersion"'))), 'E_BUNDLE_INTEGRITY');
});

test('input that is not UTF-8 is rejected (BUNDLE-116)', () => {
  const bytes = [...bundleBytes(content())];
  assert.equal(codeOf(() => loadBundle(new Uint8Array([...bytes, 0xff]))), 'E_BUNDLE_INTEGRITY');
  assert.equal(codeOf(() => loadBundle(new Uint8Array([0xc0, 0xaf]))), 'E_BUNDLE_INTEGRITY'); // overlong
  assert.equal(codeOf(() => loadBundle(new Uint8Array([0xed, 0xa0, 0x80]))), 'E_BUNDLE_INTEGRITY'); // an encoded surrogate
  assert.equal(codeOf(() => loadBundle(new Uint8Array(0))), 'E_BUNDLE_INTEGRITY');
  assert.equal(codeOf(() => loadBundle('\ud800')), 'E_BUNDLE_INTEGRITY'); // a string with no UTF-8 form
});

test('malformed JSON is rejected', () => {
  for (const text of ['', '{', '[]', '"x"', '5', 'null', '{"bundleVersion":}', '{"a":1,}', "{'a':1}", '{"a":01}', 'nope']) {
    assert.equal(codeOf(() => loadBundle(text)), 'E_BUNDLE_INTEGRITY', text);
  }
});

test('a duplicate property name is rejected at any depth (BUNDLE-111)', () => {
  const text = baseText();
  const deep = text.replace('"nodes":["m1","m2"]', '"nodes":["m1","m2"],"nodes":["m1"]');
  assert.notEqual(deep, text);
  assert.equal(codeOf(() => loadBundle(deep)), 'E_BUNDLE_INTEGRITY');
  assert.equal(codeOf(() => loadBundle(text.replace('{"bundleVersion"', '{"content":{},"bundleVersion"'))), 'E_BUNDLE_INTEGRITY');
  // Two names that differ only after an escape are the same name.
  const escaped = text.replace('"nodes":["m1","m2"]', '"nodes":["m1","m2"],"\\u006eodes":[]');
  assert.equal(codeOf(() => loadBundle(escaped)), 'E_BUNDLE_INTEGRITY');
});

test('numbers other than integers within +/-(2^53 - 1) are rejected (BUNDLE-11, BUNDLE-12)', () => {
  const text = baseText();
  for (const number of ['1.5', '1.0', '1e2', '1E2', '-0', '01', '9007199254740992', '-9007199254740992', '123456789012345678901234567890', '+1', '.5']) {
    const broken = text.replace('"max":1000', `"max":${number}`);
    assert.notEqual(broken, text);
    assert.equal(codeOf(() => loadBundle(broken)), 'E_BUNDLE_INTEGRITY', number);
  }
  // The limits themselves are legal numbers, so the failure that remains is the digest.
  for (const number of ['9007199254740991', '-9007199254740991']) {
    const edge = text.replace('"max":1000', `"max":${number}`);
    assert.equal(codeOf(() => loadBundle(edge)), 'E_BUNDLE_INTEGRITY', 'digest mismatch');
  }
  const c = content();
  c.facts['part.qty'].type = { type: 'integer', min: -9007199254740991, max: 9007199254740991 };
  assert.equal(loadCode(c), null);
});

test('an unpaired surrogate is rejected, also when written as an escape (BUNDLE-13)', () => {
  const text = baseText();
  for (const escape of ['\\ud800', '\\udc00', '\\ud800x', '\\udbff\\u0041']) {
    assert.equal(codeOf(() => loadBundle(text.replace('"m1"', `"${escape}"`))), 'E_BUNDLE_INTEGRITY', escape);
  }
  // A pair written as two escapes is a valid string but not the canonical form, which writes it literally.
  assert.equal(codeOf(() => loadBundle(text.replace('"m1"', '"\\ud83d\\ude00"'))), 'E_BUNDLE_INTEGRITY');
});

test('a document that is not the exact JCS serialisation is rejected (BUNDLE-2, BUNDLE-9, BUNDLE-10)', () => {
  const c = content();
  const version = `sha256:${sha.sha256Hex(json.canonicalBytes(c))}`;
  const members = [`"bundleVersion":"${version}"`, `"content":${json.canonicalJson(c)}`];
  assert.equal(codeOf(() => loadBundle(`{${members.join(',')}}`)), null);
  assert.equal(codeOf(() => loadBundle(`{${members.reverse().join(',')}}`)), 'E_BUNDLE_INTEGRITY', 'members out of order');
  const text = baseText();
  assert.equal(codeOf(() => loadBundle(text.replace('"m1"', '"\\u006d1"'))), 'E_BUNDLE_INTEGRITY', 'a needless \\u escape');
  assert.equal(codeOf(() => loadBundle(text.replace('"m1"', '"m\\/1"'))), 'E_BUNDLE_INTEGRITY', 'an escaped slash');
});

test('the envelope has exactly bundleVersion and content (BUNDLE-1, BUNDLE-112)', () => {
  const c = content();
  const good = json.canonicalJson({ bundleVersion: `sha256:${sha.sha256Hex(json.canonicalBytes(c))}`, content: c });
  assert.equal(codeOf(() => loadBundle(good)), null);
  const version = `sha256:${sha.sha256Hex(json.canonicalBytes(c))}`;
  for (const envelope of [
    { bundleVersion: version, content: c, extra: 1 },
    { content: c },
    { bundleVersion: version },
    {},
    { bundleVersion: version, content: [] },
    { bundleVersion: version, content: null },
    { bundleVersion: version, content: 'x' },
  ]) {
    assert.equal(codeOf(() => loadBundle(json.canonicalBytes(envelope))), 'E_BUNDLE_INTEGRITY', JSON.stringify(envelope).slice(0, 60));
  }
});

// ---------------------------------------------------------------------------
// Steps 2 and 3 of EVAL-14: versions, then digest
// ---------------------------------------------------------------------------

test('unsupported versions raise E_BUNDLE_UNSUPPORTED, whatever their type (BUNDLE-115, BUNDLE-137)', () => {
  for (const [member, value] of [
    ['bundleFormatVersion', '2'],
    ['bundleFormatVersion', ''],
    ['bundleFormatVersion', 1],
    ['bundleFormatVersion', null],
    ['bundleFormatVersion', ['1']],
    ['specVersion', '0.2'],
    ['specVersion', '0.1.0'],
    ['specVersion', 0.1 === 0.1 ? 1 : 0],
    ['specVersion', null],
    ['specVersion', true],
    ['specVersion', {}],
  ]) {
    const c = changed((x) => {
      x[member] = value;
    });
    assert.equal(loadCode(c), 'E_BUNDLE_UNSUPPORTED', `${member}=${JSON.stringify(value)}`);
  }
});

test('a missing version member raises E_BUNDLE_INTEGRITY (EVAL-56)', () => {
  assert.equal(loadCode(changed((c) => delete c.specVersion)), 'E_BUNDLE_INTEGRITY');
  assert.equal(loadCode(changed((c) => delete c.bundleFormatVersion)), 'E_BUNDLE_INTEGRITY');
});

test('bundleFormatVersion is checked completely before specVersion (EVAL-56, ADR 0018)', () => {
  // A future format reports E_BUNDLE_UNSUPPORTED even when specVersion is missing.
  assert.equal(loadCode(changed((c) => { c.bundleFormatVersion = '2'; delete c.specVersion; })), 'E_BUNDLE_UNSUPPORTED');
  assert.equal(loadCode(changed((c) => { c.bundleFormatVersion = '2'; c.specVersion = '9'; })), 'E_BUNDLE_UNSUPPORTED');
  // A missing format version is integrity even when specVersion is unsupported.
  assert.equal(loadCode(changed((c) => { delete c.bundleFormatVersion; c.specVersion = '9'; })), 'E_BUNDLE_INTEGRITY');
  // A supported format with a missing specVersion is integrity.
  assert.equal(loadCode(changed((c) => { delete c.specVersion; })), 'E_BUNDLE_INTEGRITY');
});

test('the versions are checked before the digest (EVAL-14, BUNDLE-117)', () => {
  const c = changed((x) => {
    x.specVersion = '0.2';
  });
  const wrongDigest = `sha256:${'0'.repeat(64)}`;
  assert.equal(codeOf(() => loadBundle(bundleBytes(c, wrongDigest))), 'E_BUNDLE_UNSUPPORTED');
  assert.equal(codeOf(() => loadBundle(bundleBytes(content(), wrongDigest))), 'E_BUNDLE_INTEGRITY');
});

test('a bundleVersion that is not the digest of content is rejected (BUNDLE-5, BUNDLE-6, BUNDLE-113)', () => {
  const real = sha.sha256Hex(json.canonicalBytes(content()));
  for (const version of [
    `sha256:${'0'.repeat(64)}`,
    `sha256:${real.toUpperCase()}`,
    `sha256:${real.slice(1)}`,
    `sha256:${real}0`,
    real,
    `sha1:${real}`,
    5,
    null,
    ['x'],
  ]) {
    assert.equal(codeOf(() => loadBundle(bundleBytes(content(), version))), 'E_BUNDLE_INTEGRITY', String(version).slice(0, 20));
  }
  assert.equal(codeOf(() => loadBundle(bundleBytes(content(), `sha256:${real}`))), null);
});

test('the digest covers content alone, not the envelope (BUNDLE-6)', () => {
  const c = content();
  const expected = `sha256:${sha.sha256Hex(encoder.encode(json.canonicalJson(c)))}`;
  assert.equal(loadBundle(bundleBytes(c)).bundleVersion, expected);
});

// ---------------------------------------------------------------------------
// Step 4 of EVAL-14: structure (BUNDLE-14 to BUNDLE-136)
// ---------------------------------------------------------------------------

const REJECTED = [
  // content
  ['an extra content member', (c) => { c.extra = {}; }],
  ['a missing content member', (c) => { delete c.features; }],
  ['a null content member', (c) => { c.values = null; }],
  // hierarchy
  ['an extra hierarchy member', (c) => { c.hierarchy.extra = 1; }],
  ['no levels', (c) => { c.hierarchy.levels = []; }],
  ['a repeated level', (c) => { c.hierarchy.levels = ['top', 'mid', 'mid']; }],
  ['an axis that is also a level', (c) => { c.hierarchy.axes = ['mid']; }],
  ['a repeated axis', (c) => { c.hierarchy.axes = ['lane', 'lane']; }],
  ['an empty level name (SCOPE-8, beyond the Java reader)', (c) => {
    c.hierarchy = { levels: ['top', ''], axes: [], scopes: { '': { nodes: 'open', values: 'files' } } };
    c.keys = {}; c.values = {}; c.features = {}; c.facts = {}; c.entities = {};
  }],
  ['an empty axis name (SCOPE-8, beyond the Java reader)', (c) => {
    c.hierarchy = { levels: ['top'], axes: [''], scopes: { '': { nodes: 'open', values: 'files' } } };
    c.keys = {}; c.values = {}; c.features = {}; c.facts = {}; c.entities = {};
  }],
  ['a scope with no definition', (c) => { delete c.hierarchy.scopes.mid; }],
  ['a scope for the root level', (c) => { c.hierarchy.scopes.top = { nodes: 'open', values: 'files' }; }],
  ['a scope for an undeclared name', (c) => { c.hierarchy.scopes.extra = { nodes: 'open', values: 'files' }; }],
  ['scopes that is not an object', (c) => { c.hierarchy.scopes = []; }],
  ['a scope with an unknown values source', (c) => { c.hierarchy.scopes.mid.values = 'other'; }],
  ['a scope without a values member', (c) => { delete c.hierarchy.scopes.mid.values; }],
  ['a service-sourced axis', (c) => { c.hierarchy.scopes.lane.values = 'service'; }],
  ['an open catalog with onUnknownNode', (c) => { c.hierarchy.scopes.low.onUnknownNode = 'error'; }],
  ['a closed catalog without onUnknownNode', (c) => { delete c.hierarchy.scopes.mid.onUnknownNode; }],
  ['an unknown onUnknownNode value', (c) => { c.hierarchy.scopes.mid.onUnknownNode = 'skip'; }],
  ['nodes that is neither open nor an array', (c) => { c.hierarchy.scopes.mid.nodes = 'closed'; }],
  ['a catalog out of order', (c) => { c.hierarchy.scopes.mid.nodes = ['m2', 'm1']; }],
  ['a catalog that repeats a node', (c) => { c.hierarchy.scopes.mid.nodes = ['m1', 'm1']; }],
  ['a catalog with an empty node ID', (c) => { c.hierarchy.scopes.mid.nodes = ['', 'm1']; }],
  ['a catalog with a control character', (c) => { c.hierarchy.scopes.mid.nodes = ['m\u0085', 'm1'].sort(); }],
  ['a catalog with a node of 129 code points', (c) => { c.hierarchy.scopes.mid.nodes = ['m1', 'x'.repeat(129)]; }],
  // entities and facts
  ['an entity name with a dot', (c) => { c.entities['a.b'] = { belongsTo: [], hasMany: [] }; }],
  ['an empty entity name', (c) => { c.entities[''] = { belongsTo: [], hasMany: [] }; }],
  ['an edge to an undeclared entity', (c) => { c.entities.whole.belongsTo = ['nowhere']; }],
  ['a hasMany edge to an undeclared entity', (c) => { c.entities.whole.hasMany = ['nowhere']; }],
  ['edges out of order', (c) => { c.entities.whole.hasMany = ['whole', 'part']; c.entities.whole.belongsTo = []; }],
  ['an entity without hasMany', (c) => { delete c.entities.part.hasMany; }],
  ['a belongsTo cycle (beyond the Java reader)', (c) => { c.entities.parent.belongsTo = ['whole']; }],
  ['a self edge (beyond the Java reader)', (c) => { c.entities.part.hasMany = ['part']; }],
  ['a cycle through belongsTo and hasMany (ENTITY-17, beyond the Java reader)', (c) => {
    c.entities = { a: { belongsTo: ['c'], hasMany: ['b'] }, b: { belongsTo: [], hasMany: ['c'] }, c: { belongsTo: [], hasMany: [] } };
    c.facts = {};
    c.keys = {};
    c.values = {};
    c.features = {};
  }],
  ['a fact name without a dot', (c) => { c.facts.plain = { entity: 'parent', type: { type: 'boolean' } }; }],
  ['a fact name that ends with a dot', (c) => { c.facts['parent.'] = { entity: 'parent', type: { type: 'boolean' } }; }],
  ['a fact whose entity differs from its prefix', (c) => { c.facts['parent.flag'].entity = 'part'; }],
  ['a fact of an undeclared entity', (c) => { c.facts['ghost.x'] = { entity: 'ghost', type: { type: 'boolean' } }; }],
  ['a fact of type set', (c) => { c.facts['parent.flag'].type = { type: 'set', universe: ['a'] }; }],
  ['a fact of type object', (c) => { c.facts['parent.flag'].type = { type: 'object', fields: { a: { type: 'boolean' } } }; }],
  ['a fact with an extra member', (c) => { c.facts['parent.flag'].source = 'doc'; }],
  // type declarations
  ['an unknown type name', (c) => { c.facts['parent.flag'].type = { type: 'float' }; }],
  ['a type without type', (c) => { c.facts['parent.flag'].type = {}; }],
  ['a member the type does not define', (c) => { c.facts['parent.flag'].type = { type: 'boolean', min: 1 }; }],
  ['optional outside an object field', (c) => { c.facts['parent.flag'].type = { type: 'boolean', optional: true }; }],
  ['optional set to false', (c) => { c.keys['k.deep'].type.fields.a.optional = false; }],
  ['optional that is not a boolean', (c) => { c.keys['k.deep'].type.fields.a.optional = 'yes'; }],
  ['min greater than max', (c) => { c.facts['part.qty'].type = { type: 'integer', min: 5, max: 4 }; }],
  ['min that is not an integer', (c) => { c.facts['part.qty'].type.min = '0'; }],
  ['an empty enum', (c) => { c.facts['part.tag'].type.values = []; }],
  ['an enum that repeats a value', (c) => { c.facts['part.tag'].type.values = ['x', 'x']; }],
  ['an enum out of order', (c) => { c.facts['part.tag'].type.values = ['y', 'x']; }],
  ['an enum with a non-string value', (c) => { c.facts['part.tag'].type.values = [1]; }],
  ['an empty set universe', (c) => { c.keys['k.union'].type.universe = []; }],
  ['a set universe that repeats a member', (c) => { c.keys['k.union'].type.universe = ['u1', 'u1']; }],
  ['a list without items', (c) => { c.keys['k.text'] = { type: { type: 'list' }, combine: 'override', default: [], settableAt: ['top'] }; }],
  ['an object with no fields', (c) => { c.keys['k.deep'].type.fields = {}; }],
  ['an object field with an empty name', (c) => { c.keys['k.deep'].type.fields[''] = { type: 'integer' }; }],
  ['an object type without fields', (c) => { delete c.keys['k.deep'].type.fields; }],
  ['an unknown string format', (c) => { c.facts['whole.note'].type = { type: 'string', format: 'email' }; }],
  // keys
  ['a key without combine', (c) => { delete c.keys['k.all'].combine; }],
  ['an unknown strategy', (c) => { c.keys['k.all'].combine = 'sum'; }],
  ['a strategy that does not fit the type', (c) => { c.keys['k.all'].type = { type: 'integer' }; }],
  ['deep_merge on a non-object', (c) => { c.keys['k.text'].combine = 'deep_merge'; }],
  ['a default on a commutative key', (c) => { c.keys['k.any'].default = false; }],
  ['a default of the wrong type', (c) => { c.keys['k.over'].default = 'five'; }],
  ['a default outside the declared range', (c) => { c.keys['k.min'] = { type: { type: 'integer', max: 5 }, combine: 'override', default: 6, settableAt: ['top'] }; }],
  ['a key with an extra member', (c) => { c.keys['k.all'].owner = 'team'; }],
  ['a key without settableAt', (c) => { delete c.keys['k.all'].settableAt; }],
  ['an empty settableAt', (c) => { c.keys['k.all'].settableAt = []; }],
  ['a settableAt out of scope order', (c) => { c.keys['k.all'].settableAt = ['mid', 'top', 'low', 'lane']; }],
  ['a settableAt that repeats a scope', (c) => { c.keys['k.any'].settableAt = ['top', 'top']; }],
  ['a settableAt with an undeclared scope', (c) => { c.keys['k.any'].settableAt = ['top', 'nowhere']; }],
  ['combineAcrossAxes missing', (c) => { delete c.keys['k.over'].combineAcrossAxes; }],
  ['combineAcrossAxes on a commutative key', (c) => { c.keys['k.max'].combineAcrossAxes = 'max'; }],
  ['combineAcrossAxes on a key not settable at an axis', (c) => { c.keys['k.text'].combineAcrossAxes = 'axis_wins'; }],
  ['an unknown combineAcrossAxes value', (c) => { c.keys['k.over'].combineAcrossAxes = 'both'; }],
  ['onSourceFailure missing on a key settable at a service-sourced level', (c) => { delete c.keys['k.all'].onSourceFailure; }],
  ['onSourceFailure on a key not settable at a service-sourced level', (c) => { c.keys['k.any'].onSourceFailure = 'skip'; }],
  ['an unknown onSourceFailure value', (c) => { c.keys['k.all'].onSourceFailure = 'retry'; }],
  ['a kind that is not a string', (c) => { c.keys['k.over'].kind = 3; }],
  // values
  ['values for an undeclared scope', (c) => { c.values.nowhere = { x: { 'k.over': 1 } }; }],
  ['values for a service-sourced level', (c) => { c.values.low = { n: { 'k.text': 'x' } }; }],
  ['a scope entry with no node', (c) => { c.values.mid = {}; }],
  ['a node entry with no key', (c) => { c.values.mid.m1 = {}; }],
  ['a node that a closed catalog does not list', (c) => { c.values.mid.m3 = { 'k.over': 1 }; }],
  ['an empty node key under a non-root scope', (c) => { c.values.mid[''] = { 'k.over': 1 }; }],
  ['a non-empty node key under the root level', (c) => { c.values.top.n = { 'k.over': 1 }; }],
  ['a lane value for a node outside its catalog', (c) => { c.values.lane.l3 = { 'k.over': 1 }; }],
  ['a value for an undeclared key', (c) => { c.values.top[''].ghost = 1; }],
  ['a value for a key that is not settable there', (c) => { c.values.top[''].ghost = 1; delete c.values.top[''].ghost; c.values.lane.l1['k.min'] = 1; }],
  ['a value of the wrong type', (c) => { c.values.top['']['k.over'] = '1'; }],
  ['a null value', (c) => { c.values.top['']['k.over'] = null; }],
  ['a set value outside the universe', (c) => { c.values.top['']['k.inter'] = ['u9']; }],
  ['a set value that repeats a member', (c) => { c.values.top['']['k.inter'] = ['u3', 'u3']; }],
  ['a set value out of universe order', (c) => { c.values.top['']['k.inter'] = ['u1', 'u3']; }],
  ['an integer above its maximum', (c) => { c.values.top['']['k.min'] = 101; }],
  ['an object value with an undeclared field', (c) => { c.values.mid.m1['k.deep'] = { zzz: 1 }; }],
  ['an incomplete starting value (MERGE-12)', (c) => { delete c.keys['k.deep'].default; c.values.top['']['k.deep'] = { a: 1 }; }],
  ['no starting value (MERGE-7)', (c) => { delete c.keys['k.over'].default; delete c.values.top['']['k.over']; }],
  ['no root value for a deep_merge key with no default (MERGE-7)', (c) => { delete c.keys['k.deep'].default; delete c.values.top['']['k.deep']; }],
  // features
  ['a feature decided per an undeclared entity', (c) => { c.features['f.one'].decidedPer = 'ghost'; }],
  ['returns with both forms', (c) => { c.features['f.one'].returns = { enum: ['ALLOW', 'DENY'], object: { a: { type: 'integer' } } }; }],
  ['returns with neither form', (c) => { c.features['f.one'].returns = {}; }],
  ['a nullable object form', (c) => { c.features['f.obj'].returns.nullable = true; }],
  ['nullable set to false', (c) => { c.features['f.null'].returns.nullable = false; }],
  ['an enum returns out of order', (c) => { c.features['f.one'].returns.enum = ['DENY', 'ALLOW']; }],
  ['an unknown hit policy', (c) => { c.features['f.one'].hitPolicy = 'ANY'; }],
  ['a feature without ifFactMissing', (c) => { delete c.features['f.one'].ifFactMissing; }],
  ['an ifFactMissing outside the enum', (c) => { c.features['f.one'].ifFactMissing = 'MAYBE'; }],
  ['a null effect where returns is not nullable', (c) => { c.features['f.one'].ifFactMissing = null; }],
  ['an object effect that lacks a required field', (c) => { c.features['f.obj'].ifFactMissing = {}; }],
  ['an object effect with an undeclared field', (c) => { c.features['f.obj'].ifFactMissing = { level: 1, ghost: 2 }; }],
  ['an empty bindingSettableAt', (c) => { c.features['f.one'].bindingSettableAt = []; }],
  ['a bindingSettableAt without the root level', (c) => { c.features['f.one'].bindingSettableAt = ['mid']; }],
  ['a bindingSettableAt with a service-sourced level', (c) => { c.features['f.one'].bindingSettableAt = ['top', 'low']; }],
  ['a bindingSettableAt with an axis', (c) => { c.features['f.one'].bindingSettableAt = ['top', 'lane']; }],
  ['a bindingSettableAt out of level order', (c) => { c.features['f.one'].bindingSettableAt = ['mid', 'top']; }],
  ['a policy without rules', (c) => { delete c.features['f.one'].policies.p2.rules; }],
  ['a policy default outside the enum', (c) => { c.features['f.one'].policies.p2.default = 'NO'; }],
  ['a setting named policy', (c) => { c.features['f.one'].policies.p1.settings.policy = { valueOf: 'whole.kind', default: 'a' }; }],
  ['a setting name with a dollar sign', (c) => { c.features['f.one'].policies.p1.settings.$x = { valueOf: 'whole.kind', default: 'a' }; }],
  ['a setting name with a dot', (c) => { c.features['f.one'].policies.p1.settings['x.y'] = { valueOf: 'whole.kind', default: 'a' }; }],
  ['an empty setting name', (c) => { c.features['f.one'].policies.p1.settings[''] = { valueOf: 'whole.kind', default: 'a' }; }],
  ['a setting with both forms', (c) => { c.features['f.one'].policies.p1.settings.s_val.listOf = 'part.tag'; }],
  ['a setting with neither form', (c) => { delete c.features['f.one'].policies.p1.settings.s_val.valueOf; }],
  ['a setting without a default', (c) => { delete c.features['f.one'].policies.p1.settings.s_val.default; }],
  ['a setting of an undeclared fact', (c) => { c.features['f.one'].policies.p1.settings.s_val.valueOf = 'whole.ghost'; }],
  ['a setting default of the wrong type', (c) => { c.features['f.one'].policies.p1.settings.s_val.default = 'c'; }],
  ['a listOf default that repeats an element', (c) => { c.features['f.one'].policies.p1.settings.s_list.default = ['x', 'x']; }],
  ['a listOf default out of order', (c) => { c.features['f.one'].policies.p1.settings.s_list.default = ['y', 'x']; }],
  ['a listOf default that is not a list', (c) => { c.features['f.one'].policies.p1.settings.s_list.default = 'x'; }],
  ['requiredFacts with a fact the rules do not use', (c) => { c.features['f.one'].policies.p1.requiredFacts = ['parent.flag', 'part.qty', 'part.tag', 'whole.kind']; }],
  ['requiredFacts that omits a fact the rules use', (c) => { c.features['f.one'].policies.p1.requiredFacts = ['parent.flag', 'part.tag']; }],
  ['requiredFacts out of order', (c) => { c.features['f.one'].policies.p1.requiredFacts = ['part.tag', 'parent.flag', 'whole.kind']; }],
  ['requiredFacts that repeats a fact', (c) => { c.features['f.one'].policies.p1.requiredFacts = ['parent.flag', 'parent.flag', 'part.tag', 'whole.kind']; }],
  ['requiredFacts naming an undeclared fact', (c) => { c.features['f.one'].policies.p2.requiredFacts = ['ghost.x']; }],
  ['a requiredFacts for a policy with no rules', (c) => { c.features['f.one'].policies.p2.requiredFacts = ['parent.flag']; }],
  ['two rules with one id', (c) => { c.features['f.one'].policies.p1.rules[1].id = 'r1'; }],
  ['a rule without a name', (c) => { delete c.features['f.one'].policies.p1.rules[0].name; }],
  ['a rule with an extra member', (c) => { c.features['f.one'].policies.p1.rules[0].extra = 1; }],
  ['conditions out of order', (c) => { c.features['f.one'].policies.p1.rules[0].when.reverse(); }],
  ['two conditions on one fact', (c) => { c.features['f.one'].policies.p1.rules[0].when.push({ fact: 'whole.kind', op: 'eq', value: 'a' }); }],
  ['a condition on an undeclared fact', (c) => { c.features['f.one'].policies.p1.rules[0].when[0].fact = 'ghost.x'; }],
  ['an unknown operator', (c) => { c.features['f.one'].policies.p1.rules[0].when[0].op = 'gt'; }],
  ['a condition with an extra member', (c) => { c.features['f.one'].policies.p1.rules[0].when[0].extra = 1; }],
  ['an eq literal of the wrong type', (c) => { c.features['f.one'].policies.p1.rules[0].when[0].value = 'true'; }],
  ['a reference to an undeclared setting', (c) => { c.features['f.one'].policies.p1.rules[0].when[1].value = '$ghost'; }],
  ['a reference to a list setting from eq', (c) => { c.features['f.one'].policies.p1.rules[0].when[1].value = '$s_list'; }],
  ['a reference to a value setting from all_in', (c) => { c.features['f.one'].policies.p1.rules[1].when[1].value = '$s_val'; }],
  ['a reference to a setting of another fact', (c) => {
    c.features['f.one'].policies.p1.settings.s_other = { valueOf: 'parent.flag', default: true };
    c.features['f.one'].policies.p1.rules[0].when[1].value = '$s_other';
  }],
  ['a reference inside a literal list', (c) => { c.features['f.one'].policies.p1.rules[1].when[1].value = ['$s_list']; }],
  ['an all_in operand that is not a list', (c) => { c.features['f.one'].policies.p1.rules[1].when[1].value = 'x'; }],
  ['an all_in operand of the wrong element type', (c) => { c.features['f.one'].policies.p1.rules[1].when[1].value = [1]; }],
  ['an all_in operand out of order', (c) => { c.features['f.one'].policies.p1.rules[1].when[1].value = ['y', 'x']; }],
  ['a then outside the enum', (c) => { c.features['f.one'].policies.p1.rules[0].then = 'MAYBE'; }],
  ['a bindings entry for an axis', (c) => { c.features['f.one'].bindings.lane = { l1: { policy: 'p1', settings: {} } }; }],
  ['a bindings entry for an undeclared level', (c) => { c.features['f.one'].bindings.ghost = { x: { policy: 'p1', settings: {} } }; }],
  ['a bindings entry for a level the feature may not bind', (c) => { c.features['f.one'].bindings.low = { n: { policy: 'p1', settings: {} } }; }],
  ['a binding for a node outside the catalog', (c) => { c.features['f.one'].bindings.mid.m9 = { policy: 'p1', settings: {} }; }],
  ['a binding at the root with a node key', (c) => { c.features['f.one'].bindings.top.n = { policy: 'p1', settings: {} }; }],
  ['a binding that names an undeclared policy', (c) => { c.features['f.one'].bindings.mid.m1.policy = 'p9'; }],
  ['a binding with a setting the policy does not declare', (c) => { c.features['f.one'].bindings.mid.m1.settings.ghost = 1; }],
  ['a binding setting of the wrong type', (c) => { c.features['f.one'].bindings.mid.m1.settings.s_val = 'c'; }],
  ['a binding list setting that repeats an element', (c) => { c.features['f.one'].bindings.mid.m1.settings.s_list = ['x', 'x']; }],
  ['a binding without settings', (c) => { delete c.features['f.one'].bindings.mid.m1.settings; }],
  ['a binding level with no node', (c) => { c.features['f.one'].bindings.mid = {}; }],
  ['no root binding', (c) => { delete c.features['f.one'].bindings.top; }],
  ['a root level entry with no node', (c) => { c.features['f.one'].bindings.top = {}; }],
];

for (const [name, change] of REJECTED) {
  test(`rejects ${name}`, () => {
    assert.equal(loadCode(changed(change)), 'E_BUNDLE_INTEGRITY');
  });
}

const ACCEPTED = [
  ['a minimal hierarchy with a named level', (c) => {
    c.hierarchy = { levels: ['top', 'x'], axes: [], scopes: { x: { nodes: 'open', values: 'files' } } };
    c.keys = {}; c.values = {}; c.features = {}; c.facts = {}; c.entities = {};
  }],
  ['a repeated element in an in, all_in or any_in list (ADR 0018)', (c) => { c.features['f.one'].policies.p1.rules[1].when[1].value = ['x', 'x']; }],
  ['an empty in list', (c) => { c.features['f.one'].policies.p1.rules[1].when[1].value = []; }],
  ['an entity that is both a parent and a child of another (ENTITY-13)', (c) => { c.entities.parent.hasMany = ['whole']; }],
  ['a closed catalog with no node (SCOPE-21)', (c) => { c.hierarchy.scopes.mid.nodes = []; c.values.mid = {}; delete c.values.mid; c.features['f.one'].bindings.mid = {}; delete c.features['f.one'].bindings.mid; }],
  ['a key with a kind', (c) => { c.keys['k.hier'].kind = 'other'; }],
  ['a hierarchy with no axis', (c) => {
    c.hierarchy.axes = [];
    delete c.hierarchy.scopes.lane;
    delete c.values.lane;
    for (const key of Object.values(c.keys)) {
      key.settableAt = key.settableAt.filter((scope) => scope !== 'lane');
      if (!key.settableAt.includes('low')) {
        delete key.onSourceFailure;
      }
      if (['k.over', 'k.hier', 'k.deep'].includes(Object.keys(c.keys).find((name) => c.keys[name] === key))) {
        delete key.combineAcrossAxes;
      }
    }
  }],
  ['a value that equals a strategy identity', (c) => { c.values.mid.m2 = { 'k.any': false, 'k.inter': ['u3', 'u1', 'u2'] }; }],
  ['non-NFC strings, which are compared as received (BUNDLE-138)', (c) => { c.hierarchy.scopes.low.nodes = 'open'; c.keys['k.text'].default = 'é'; }],
];

for (const [name, change] of ACCEPTED) {
  test(`accepts ${name}`, () => {
    assert.equal(loadCode(changed(change)), null);
  });
}
