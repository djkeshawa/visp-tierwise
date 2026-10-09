// load bundle: the byte, parse and envelope faults of BUNDLE-3, BUNDLE-13 and BUNDLE-116, and the check order of
// BUNDLE-117 and EVAL-14. Conformance vectors cannot hold these faults (VEC-71, VEC-72, VEC-80), so these tests are
// their enforcers (spec/conformance/enforcers.d/E7.yaml). Where a case changes only the form of the bytes, the parsed
// content is unchanged, so the digest still matches and the form is the only fault.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { api, bundleBytes, codeOf, json, sha } from './support.mjs';
import { changed, content } from './fixtures.mjs';

const { loadBundle, TierwiseError } = api;
const decoder = new TextDecoder();
const encoder = new TextEncoder();

/** The canonical text of the base bundle. */
const baseText = () => decoder.decode(bundleBytes(content()));

/** Replaces one occurrence and fails the test if the text does not contain it. */
function replaceOnce(text, from, to) {
  assert.ok(text.includes(from), `the base bundle contains ${from}`);
  return text.replace(from, to);
}

/** Loads the input and returns the TierwiseError it throws. */
function errorOf(input) {
  try {
    loadBundle(input);
  } catch (error) {
    if (error instanceof TierwiseError) return error;
    throw error;
  }
  assert.fail('the bundle loaded');
}

test('the base bundle loads, so each case below has one fault', () => {
  assert.equal(codeOf(() => loadBundle(baseText())), null);
});

test('BUNDLE-3: a bundle that begins with a byte-order mark raises E_BUNDLE_INTEGRITY', () => {
  const bytes = bundleBytes(content());
  assert.equal(codeOf(() => loadBundle(new Uint8Array([0xef, 0xbb, 0xbf, ...bytes]))), 'E_BUNDLE_INTEGRITY');
  assert.equal(codeOf(() => loadBundle(`﻿${decoder.decode(bytes)}`)), 'E_BUNDLE_INTEGRITY', 'as a string');
  // A byte-order mark after the first byte is not well-formed JSON either.
  assert.equal(codeOf(() => loadBundle(`${decoder.decode(bytes)}﻿`)), 'E_BUNDLE_INTEGRITY');
});

test('BUNDLE-13: an escaped unpaired surrogate raises E_BUNDLE_INTEGRITY', () => {
  const text = baseText();
  // In a value, in a member name, high and low halves, and a high half followed by a non-surrogate escape.
  for (const escape of ['\\ud800', '\\udbff', '\\udc00', '\\udfff', '\\ud800x', '\\ud800\\u0041', 'a\\udc00\\ud800']) {
    assert.equal(codeOf(() => loadBundle(replaceOnce(text, '"first rule"', `"${escape}"`))), 'E_BUNDLE_INTEGRITY', escape);
  }
  assert.equal(codeOf(() => loadBundle(replaceOnce(text, '"m1":{', '"\\ud800":{'))), 'E_BUNDLE_INTEGRITY', 'in a name');
});

test('BUNDLE-116: input that is not UTF-8 raises E_BUNDLE_INTEGRITY', () => {
  const bytes = [...bundleBytes(content())];
  const at = bytes.indexOf('f'.charCodeAt(0)); // a byte inside a string
  for (const bad of [[0xff], [0xc0, 0xaf], [0xe0, 0x80, 0xaf], [0xed, 0xa0, 0x80], [0xf4, 0x90, 0x80, 0x80], [0x80], [0xc3]]) {
    const broken = new Uint8Array([...bytes.slice(0, at), ...bad, ...bytes.slice(at + 1)]);
    assert.equal(codeOf(() => loadBundle(broken)), 'E_BUNDLE_INTEGRITY', bad.map((b) => b.toString(16)).join(' '));
  }
  // A string with an unpaired surrogate has no UTF-8 form.
  assert.equal(codeOf(() => loadBundle(replaceOnce(baseText(), 'first rule', 'first \ud800rule'))), 'E_BUNDLE_INTEGRITY');
});

test('BUNDLE-116: input that is not well-formed JSON raises E_BUNDLE_INTEGRITY', () => {
  const text = baseText();
  for (const broken of [
    '',
    text.slice(0, -1),
    text.slice(1),
    `${text}}`,
    replaceOnce(text, '"m1","m2"', '"m1","m2",'),
    replaceOnce(text, '"first rule"', "'first rule'"),
    replaceOnce(text, '"first rule"', '"first\u0001rule"'),
    replaceOnce(text, '"first rule"', '"first rule'),
    replaceOnce(text, '"max":1000', '"max":NaN'),
    replaceOnce(text, '"max":1000', '"max":/* c */1000'),
    replaceOnce(text, '"first rule"', '"first \\x41 rule"'),
    `${text}${text}`,
  ]) {
    assert.equal(codeOf(() => loadBundle(broken)), 'E_BUNDLE_INTEGRITY', broken.slice(0, 40));
  }
});

test('BUNDLE-116: an envelope that lacks bundleVersion or content raises E_BUNDLE_INTEGRITY', () => {
  const c = content();
  const version = `sha256:${sha.sha256Hex(json.canonicalBytes(c))}`;
  assert.equal(codeOf(() => loadBundle(json.canonicalBytes({ content: c }))), 'E_BUNDLE_INTEGRITY');
  assert.equal(codeOf(() => loadBundle(json.canonicalBytes({ bundleVersion: version }))), 'E_BUNDLE_INTEGRITY');
  assert.equal(codeOf(() => loadBundle('{}')), 'E_BUNDLE_INTEGRITY');
});

test('BUNDLE-116, BUNDLE-11: -0, a fraction or an exponent raises E_BUNDLE_INTEGRITY, even for an integral value', () => {
  const text = baseText();
  // Each form has the value of the canonical number, so the form is the only fault.
  for (const [from, to] of [
    ['"min":0', '"min":-0'],
    ['"max":1000', '"max":1000.0'],
    ['"max":1000', '"max":1e3'],
    ['"max":1000', '"max":1E3'],
    ['"max":1000', '"max":1e+3'],
    ['"max":1000', '"max":10000e-1'],
    ['"max":1000', '"max":01000'],
    ['"max":1000', '"max":+1000'],
  ]) {
    assert.equal(codeOf(() => loadBundle(replaceOnce(text, from, to))), 'E_BUNDLE_INTEGRITY', to);
  }
});

test('BUNDLE-116, BUNDLE-12: an integer outside +/-(2^53 - 1) raises E_BUNDLE_INTEGRITY', () => {
  const tooLarge = changed((c) => {
    c.facts['part.qty'].type.max = 9007199254740991;
  });
  const text = decoder.decode(bundleBytes(tooLarge));
  for (const number of ['9007199254740992', '-9007199254740992', '18446744073709551616']) {
    assert.equal(codeOf(() => loadBundle(replaceOnce(text, '9007199254740991', number))), 'E_BUNDLE_INTEGRITY', number);
  }
  assert.equal(codeOf(() => loadBundle(text)), null, 'the limit itself loads');
});

test('BUNDLE-116, BUNDLE-2: bytes that are not exactly the JCS serialisation raise E_BUNDLE_INTEGRITY', () => {
  const text = baseText();
  const c = content();
  const version = `sha256:${sha.sha256Hex(json.canonicalBytes(c))}`;
  for (const [name, broken] of [
    ['a trailing newline', `${text}\n`],
    ['leading whitespace', ` ${text}`],
    ['a space after a colon', replaceOnce(text, '"content":', '"content": ')],
    ['a space after a comma', replaceOnce(text, '"m1","m2"', '"m1", "m2"')],
    ['envelope members out of order', `{"content":${json.canonicalJson(c)},"bundleVersion":"${version}"}`],
    ['content members out of order', replaceOnce(text, '{"axes":["lane"],"levels":["top","mid","low"],', '{"levels":["top","mid","low"],"axes":["lane"],')],
    ['a needless escape of a letter', replaceOnce(text, '"first rule"', '"\\u0066irst rule"')],
    ['an escaped slash', replaceOnce(text, '"first rule"', '"first\\/rule"')],
    ['an escaped non-ASCII code point', replaceOnce(text, '"first rule"', '"first \\u00e9"')],
    ['an escaped surrogate pair', replaceOnce(text, '"first rule"', '"\\ud83d\\ude00"')],
  ]) {
    assert.equal(codeOf(() => loadBundle(broken)), 'E_BUNDLE_INTEGRITY', name);
  }
  // The same values written canonically load.
  const accented = changed((x) => {
    x.features['f.one'].policies.p1.rules[0].name = 'first é/\u{1f600}';
  });
  assert.equal(codeOf(() => loadBundle(bundleBytes(accented))), null);
  assert.ok(decoder.decode(bundleBytes(accented)).includes('"first é/\u{1f600}"'));
});

test('BUNDLE-116: an upper-case \\u escape of a control character is not the canonical form', () => {
  const lower = changed((x) => {
    x.features['f.one'].policies.p1.rules[0].name = 'first\u001frule';
  });
  const text = decoder.decode(bundleBytes(lower));
  assert.equal(codeOf(() => loadBundle(text)), null, 'the canonical lower-case escape loads');
  assert.equal(codeOf(() => loadBundle(replaceOnce(text, '\\u001f', '\\u001F'))), 'E_BUNDLE_INTEGRITY');
  assert.equal(codeOf(() => loadBundle(encoder.encode(replaceOnce(text, '\\u001f', '\\u001F')))), 'E_BUNDLE_INTEGRITY');
});

// ---------------------------------------------------------------------------
// BUNDLE-117: a bundle that fails several checks raises the code of the first, in the order of EVAL-14
// ---------------------------------------------------------------------------

const WRONG_DIGEST = `sha256:${'0'.repeat(64)}`;
const unsupported = () => changed((c) => {
  c.specVersion = '0.2';
});
const structural = (c) => {
  c.features['f.one'].policies.p1.rules[1].id = 'r1'; // BUNDLE-132
};

test('BUNDLE-117: a parse or envelope fault (step 1) wins over an unsupported version (step 2)', () => {
  const text = decoder.decode(bundleBytes(unsupported()));
  assert.equal(codeOf(() => loadBundle(text)), 'E_BUNDLE_UNSUPPORTED', 'the version alone');
  assert.equal(codeOf(() => loadBundle(`${text}\n`)), 'E_BUNDLE_INTEGRITY');
  assert.equal(codeOf(() => loadBundle(new Uint8Array([0xef, 0xbb, 0xbf, ...encoder.encode(text)]))), 'E_BUNDLE_INTEGRITY');
  assert.equal(codeOf(() => loadBundle(replaceOnce(text, '"max":1000', '"max":1e3'))), 'E_BUNDLE_INTEGRITY');
  const c = unsupported();
  assert.equal(codeOf(() => loadBundle(json.canonicalBytes({ bundleVersion: WRONG_DIGEST, content: c, extra: 1 }))), 'E_BUNDLE_INTEGRITY');
});

test('BUNDLE-117: an unsupported version (step 2) wins over a digest mismatch (step 3) and a structural fault (step 4)', () => {
  const both = changed((c) => {
    c.bundleFormatVersion = '2';
    structural(c);
  });
  assert.equal(codeOf(() => loadBundle(bundleBytes(unsupported(), WRONG_DIGEST))), 'E_BUNDLE_UNSUPPORTED', 'steps 2 and 3');
  assert.equal(codeOf(() => loadBundle(bundleBytes(both))), 'E_BUNDLE_UNSUPPORTED', 'steps 2 and 4');
  assert.equal(codeOf(() => loadBundle(bundleBytes(both, WRONG_DIGEST))), 'E_BUNDLE_UNSUPPORTED', 'steps 2, 3 and 4');
});

test('BUNDLE-117: a digest mismatch (step 3) is reported before a structural fault (step 4)', () => {
  const broken = changed(structural);
  const structureOnly = errorOf(bundleBytes(broken));
  assert.equal(structureOnly.code, 'E_BUNDLE_INTEGRITY');
  assert.ok(!structureOnly.message.includes('BUNDLE-113'), structureOnly.message);
  const both = errorOf(bundleBytes(broken, WRONG_DIGEST));
  assert.equal(both.code, 'E_BUNDLE_INTEGRITY');
  assert.ok(both.message.includes('BUNDLE-113'), `the digest check ran first: ${both.message}`);
});

test('BUNDLE-117: a fault of every step at once raises the code of step 1', () => {
  const all = changed((c) => {
    c.specVersion = '0.2';
    structural(c);
  });
  const text = decoder.decode(bundleBytes(all, WRONG_DIGEST));
  assert.equal(codeOf(() => loadBundle(`${text} `)), 'E_BUNDLE_INTEGRITY');
});
