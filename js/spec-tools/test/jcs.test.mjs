// Tests for the JCS encoder, the strict JSON reader and the canonicalize tool.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { bundleVersionOf, canonicalJson, parseBundleJson, parseJsonStrict, sha256Hex } from '../lib/jcs.mjs';

const CANONICALIZE = fileURLToPath(new URL('../canonicalize.mjs', import.meta.url));
const SECTION_10 = fileURLToPath(new URL('../../../spec/sections/10-bundle-format-and-determinism.md', import.meta.url));

// The content of the non-normative example in section 10.6, which is a complete content object.
function specExampleContent() {
  const text = readFileSync(SECTION_10, 'utf8');
  const open = text.indexOf('```json', text.indexOf('**Example (non-normative).**'));
  const close = text.indexOf('```', open + '```json'.length);
  return JSON.parse(text.slice(open + '```json'.length, close)).content;
}

test('member names sort by UTF-16 code units, so U+1F600 precedes U+E000', () => {
  // U+1F600 is the surrogate pair D83D DE00, which sorts before E000 although its code point is larger.
  assert.equal(canonicalJson({ '': 1, '\u{1F600}': 2 }), '{"\u{1F600}":2,"":1}');
});

test('empty and non-ASCII member names sort by code unit', () => {
  assert.equal(canonicalJson({ z: 1, é: 2, '': 3, 'a/b': 4 }), '{"":3,"a/b":4,"z":1,"é":2}');
});

test('strings use the short escapes, \\u for other controls, and leave the rest raw', () => {
  assert.equal(canonicalJson('\b\t\n\f\r"\\'), '"\\b\\t\\n\\f\\r\\"\\\\"');
  assert.equal(canonicalJson('\u0000\u001f'), '"\\u0000\\u001f"');
  assert.equal(canonicalJson('/\u007fé\u{1F600}'), '"/\u007fé\u{1F600}"');
});

test('a string with an unpaired surrogate is refused, and a surrogate pair is written as UTF-8', () => {
  assert.throws(() => canonicalJson('\uD800'), RangeError);
  assert.equal(canonicalJson('😀'), '"\u{1F600}"');
});

test('integers are accepted up to ±(2^53 − 1), and nothing else is', () => {
  assert.equal(canonicalJson([9007199254740991, -9007199254740991]), '[9007199254740991,-9007199254740991]');
  assert.throws(() => canonicalJson(9007199254740992), RangeError);
  assert.throws(() => canonicalJson(-9007199254740992), RangeError);
  assert.throws(() => canonicalJson(1.5), RangeError);
  assert.throws(() => canonicalJson(NaN), RangeError);
  assert.throws(() => canonicalJson(-0), RangeError);
});

test('the strict reader rejects a duplicate member name at any depth, after escape decoding', () => {
  assert.throws(() => parseJsonStrict('{"a":1,"a":2}'), /duplicate member name "a"/);
  assert.throws(() => parseJsonStrict('{"\\u0061":1,"a":2}'), /duplicate member name/);
  assert.throws(() => parseJsonStrict('{"x":[{"b":1,"b":2}]}'), /duplicate member name "b"/);
  assert.deepEqual(parseJsonStrict('[{"a":1},{"a":2}]'), [{ a: 1 }, { a: 2 }]);
});

test('bundle numbers must be integers in bundle form and within the safe range', () => {
  assert.throws(() => parseBundleJson('{"a":1.0}'), /not an integer in bundle form/);
  assert.throws(() => parseBundleJson('{"a":-0}'), /not an integer in bundle form/);
  assert.throws(() => parseBundleJson('{"a":9007199254740993}'), /outside/);
  assert.deepEqual(parseBundleJson('{"a":-12,"b":[0,9007199254740991]}'), { a: -12, b: [0, 9007199254740991] });
});

test('sha256 matches the published digests of the empty input and of "abc"', () => {
  assert.equal(sha256Hex(''), 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
  assert.equal(sha256Hex('abc'), 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
});

test('canonicalize writes the canonical envelope with no trailing newline and prints its bundleVersion', () => {
  const content = specExampleContent();
  const dir = mkdtempSync(join(tmpdir(), 'spec-tools-'));
  try {
    const input = join(dir, 'content.json');
    const output = join(dir, 'bundle.json');
    writeFileSync(input, JSON.stringify(content));
    const result = spawnSync(process.execPath, [CANONICALIZE, input, output], { encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    const version = bundleVersionOf(content);
    assert.equal(result.stdout, version + '\n');
    assert.equal(readFileSync(output, 'utf8'), canonicalJson({ bundleVersion: version, content }));
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('canonicalize refuses content that does not match bundle.schema.json, and writes nothing', () => {
  const dir = mkdtempSync(join(tmpdir(), 'spec-tools-'));
  try {
    const input = join(dir, 'content.json');
    const output = join(dir, 'bundle.json');
    writeFileSync(input, '{"bundleFormatVersion":"1"}');
    const result = spawnSync(process.execPath, [CANONICALIZE, input, output], { encoding: 'utf8' });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /nothing was written/);
    assert.equal(existsSync(output), false);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
