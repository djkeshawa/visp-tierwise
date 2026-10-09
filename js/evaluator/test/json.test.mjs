// The strict JSON reader and the canonical writer (BUNDLE-7 to BUNDLE-13, BUNDLE-111, BUNDLE-116, DET-4, DET-5).
import assert from 'node:assert/strict';
import { test } from 'node:test';
import fc from 'fast-check';
import { distUrl, json } from './support.mjs';

const text = await import(new URL('text.js', distUrl).href);
const { parseStrictJson, canonicalJson, JsonFormatError } = json;

const rejects = (source) => assert.throws(() => parseStrictJson(source), JsonFormatError, source);

test('the reader accepts the JSON subset of a bundle', () => {
  const plain = (source) => JSON.parse(JSON.stringify(parseStrictJson(source)));
  assert.deepEqual(plain('{"a":[1,-2,0,true,false,null,"x"],"b":{}}'), { a: [1, -2, 0, true, false, null, 'x'], b: {} });
  assert.deepEqual(plain(' \n\t{ "a" : 1 }\r\n'), { a: 1 });
  assert.equal(parseStrictJson('"\\u0041\\n\\"\\\\\\/"'), 'A\n"\\/');
  assert.equal(parseStrictJson('"\\ud83d\\ude00"'), '\u{1F600}');
  assert.equal(parseStrictJson('9007199254740991'), 9007199254740991);
  assert.equal(parseStrictJson('-9007199254740991'), -9007199254740991);
});

test('the reader rejects what the bundle format does not accept', () => {
  for (const source of [
    '', ' ', '{', '}', '[', ']', '{"a"}', '{"a":}', '{"a":1,}', '[1,]', '[,1]', '{,}', '{"a" 1}', '{a:1}', "{'a':1}",
    'nul', 'truex', 'True', 'NaN', 'Infinity', '-Infinity', '1 2', '{} {}', '{}x', '[]]', '"abc', '"a\nb"', '"a\tb"', '"\\x41"',
    '"\\u12"', '"\\u12G4"', '"\\', '// c\n{}', '/* c */{}',
    // numbers
    '1.5', '1.0', '-1.5', '1e2', '1E2', '1e+2', '1e-2', '-0', '-0.0', '01', '-01', '00', '+1', '.5', '1.', '-', '--1',
    '9007199254740992', '-9007199254740992', '12345678901234567890', '1' + '0'.repeat(400),
    // surrogates
    '"\\ud800"', '"\\udc00"', '"\\udbff\\u0041"', '"\\ud800\\ud800"', '"\\udc00\\ud800"', '"\ud800"', '"x\udc00y"',
    // duplicates
    '{"a":1,"a":2}', '{"a":{"b":1,"b":2}}', '[{"a":1,"a":1}]', '{"a":1,"\\u0061":2}', '{"":1,"":2}', '{"__proto__":1,"__proto__":2}',
  ]) {
    rejects(source);
  }
});

test('a deep document is refused without exhausting the stack', () => {
  rejects('['.repeat(100000) + ']'.repeat(100000));
  rejects('{"a":'.repeat(5000) + '1' + '}'.repeat(5000));
  const fine = '['.repeat(900) + ']'.repeat(900);
  assert.doesNotThrow(() => parseStrictJson(fine));
});

test('a member named __proto__ is data, in the reader and in the writer', () => {
  const parsed = parseStrictJson('{"__proto__":{"x":1},"constructor":2}');
  assert.deepEqual(Object.keys(parsed), ['__proto__', 'constructor']);
  assert.equal(Object.getPrototypeOf(parsed), null);
  assert.equal(canonicalJson(parsed), '{"__proto__":{"x":1},"constructor":2}');
  assert.equal(({}).polluted, undefined);
});

test('the writer sorts member names by UTF-16 code units (BUNDLE-9)', () => {
  assert.equal(canonicalJson({ '': 1, '\u{1F600}': 2 }), '{"\u{1F600}":2,"":1}');
  assert.equal(canonicalJson({ b: 1, a: 2, B: 3, '': 4, aa: 5, '10': 6, '9': 7 }), '{"":4,"10":6,"9":7,"B":3,"a":2,"aa":5,"b":1}');
});

test('the writer uses the escapes of BUNDLE-10', () => {
  assert.equal(canonicalJson('"\\\b\t\n\f\r'), '"\\"\\\\\\b\\t\\n\\f\\r"');
  assert.equal(canonicalJson('\u0000\u0001\u001f\u007f\u0085/é \u{1F600}'), '"\\u0000\\u0001\\u001f\u007f\u0085/é \u{1F600}"');
  assert.equal(canonicalJson({ '\n': 1 }), '{"\\n":1}');
});

test('the writer writes integers, and refuses what a bundle cannot hold', () => {
  assert.equal(canonicalJson([0, -0, 5, -5, 9007199254740991, -9007199254740991]), '[0,0,5,-5,9007199254740991,-9007199254740991]');
  for (const value of [1.5, NaN, Infinity, 2 ** 53, -(2 ** 53), undefined, () => 1, Symbol('x'), 5n, '\ud800', { a: undefined }, { '\udc00': 1 }]) {
    assert.throws(() => canonicalJson(value), TypeError);
  }
});

const jsonValue = fc.letrec((tie) => ({
  value: fc.oneof(
    { depthSize: 'small', maxDepth: 4 },
    fc.constant(null),
    fc.boolean(),
    fc.integer({ min: -9007199254740991, max: 9007199254740991 }),
    fc.string({ unit: 'binary', maxLength: 12 }),
    fc.array(tie('value'), { maxLength: 4 }),
    fc.dictionary(fc.string({ unit: 'binary', maxLength: 6 }), tie('value'), { maxKeys: 4 }),
  ),
})).value;

test('property: parse(canonical(v)) equals v, and the canonical form is a fixed point', () => {
  fc.assert(
    fc.property(jsonValue, (value) => {
      const canonical = canonicalJson(value);
      const parsed = parseStrictJson(canonical);
      assert.equal(canonicalJson(parsed), canonical);
      assert.deepEqual(JSON.parse(canonical), JSON.parse(JSON.stringify(value)));
    }),
    { numRuns: 500 },
  );
});

test('property: the canonical text is the sorted JSON.stringify text for values without special characters', () => {
  fc.assert(
    fc.property(fc.dictionary(fc.string({ unit: 'grapheme-ascii', maxLength: 6 }), fc.array(fc.integer({ min: -1000, max: 1000 }), { maxLength: 4 })), (value) => {
      const sorted = Object.fromEntries(Object.keys(value).sort().map((name) => [name, value[name]]));
      // JSON.stringify moves integer-like names first, so compare through the parsed text instead.
      assert.deepEqual(JSON.parse(canonicalJson(value)), JSON.parse(JSON.stringify(sorted)));
      const names = [...canonicalJson(value).matchAll(/"((?:[^"\\]|\\.)*)":/g)].map((match) => JSON.parse(`"${match[1]}"`));
      const top = names.filter((name) => Object.prototype.hasOwnProperty.call(value, name));
      assert.deepEqual(top, [...top].sort());
    }),
    { numRuns: 300 },
  );
});

// ---------------------------------------------------------------------------
// text.ts
// ---------------------------------------------------------------------------

const referenceCompare = (a, b) => {
  const x = Array.from(a, (c) => c.codePointAt(0));
  const y = Array.from(b, (c) => c.codePointAt(0));
  for (let i = 0; i < Math.min(x.length, y.length); i++) {
    if (x[i] !== y[i]) return x[i] < y[i] ? -1 : 1;
  }
  return x.length === y.length ? 0 : x.length < y.length ? -1 : 1;
};

test('code point order differs from UTF-16 order only above the BMP (DET-5)', () => {
  assert.equal(text.compareCodePoints('', '\u{1F600}'), -1);
  assert.equal('' < '\u{1F600}', false, 'UTF-16 order puts the surrogate first');
  assert.equal(text.compareCodePoints('a', 'ab'), -1);
  assert.equal(text.compareCodePoints('', ''), 0);
  assert.equal(text.compareCodePoints('b', 'a'), 1);
});

test('property: compareCodePoints agrees with a reference over generated strings', () => {
  fc.assert(
    fc.property(fc.string({ unit: 'binary', maxLength: 8 }), fc.string({ unit: 'binary', maxLength: 8 }), (a, b) => {
      // Binary units can produce unpaired surrogates; the order is still defined code unit by code unit there.
      fc.pre(!text.hasUnpairedSurrogate(a) && !text.hasUnpairedSurrogate(b));
      assert.equal(text.compareCodePoints(a, b), referenceCompare(a, b));
    }),
    { numRuns: 1000 },
  );
});

test('node ID structure (SCOPE-33, SCOPE-34, SCOPE-37)', () => {
  assert.equal(text.isNodeId('a'), true);
  assert.equal(text.isNodeId('x'.repeat(128)), true);
  assert.equal(text.isNodeId('\u{1F600}'.repeat(128)), true, '128 code points of two code units each');
  assert.equal(text.isNodeId('\u{1F600}'.repeat(129)), false);
  assert.equal(text.isNodeId(''), false);
  assert.equal(text.isNodeId('a\u001fb'), false);
  assert.equal(text.isNodeId('a\u009fb'), false);
  assert.equal(text.isNodeId('a b'), true);
  assert.equal(text.isNodeId('a\ud800'), false);
  assert.equal(text.isNodeId('\udc00a'), false);
});
