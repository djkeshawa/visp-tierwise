// JSON Canonicalization Scheme (RFC 8785) for the bundle subset, a strict JSON reader and SHA-256.
// The bundle subset (BUNDLE-7 to BUNDLE-13, DET-4): strings without lone surrogates, booleans,
// null, arrays, objects, and integers within ±(2^53 − 1) written as `0|-?[1-9][0-9]*`.
import { createHash } from 'node:crypto';

// Short escapes that RFC 8785 section 3.2.2.2 requires (BUNDLE-10).
const SHORT_ESCAPES = new Map([
  ['\b', '\\b'], ['\t', '\\t'], ['\n', '\\n'], ['\f', '\\f'], ['\r', '\\r'],
  ['"', '\\"'], ['\\', '\\\\'],
]);

// A lone surrogate is a UTF-16 code unit without its partner (BUNDLE-13).
const LONE_SURROGATE = /[\uD800-\uDBFF](?![\uDC00-\uDFFF])|(?<![\uD800-\uDBFF])[\uDC00-\uDFFF]/;

// Integer text that the bundle form allows (BUNDLE-11).
const BUNDLE_INTEGER = /^(?:0|-?[1-9][0-9]*)$/;

// Writes one string as a JSON string literal in canonical form.
function quote(text) {
  if (LONE_SURROGATE.test(text)) throw new RangeError('a string has an unpaired surrogate (BUNDLE-13)');
  let out = '"';
  // for...of walks code points, so non-BMP characters are written as their UTF-8 bytes.
  for (const ch of text) {
    const code = ch.codePointAt(0);
    if (SHORT_ESCAPES.has(ch)) out += SHORT_ESCAPES.get(ch);
    else if (code < 0x20) out += '\\u' + code.toString(16).padStart(4, '0');
    else out += ch;
  }
  return out + '"';
}

// Writes one number. Only safe integers are allowed, and -0 is excluded because bundles never write it (BUNDLE-11).
function integerText(value) {
  if (!Number.isSafeInteger(value)) throw new RangeError(`not an integer within ±(2^53 − 1): ${value} (DET-4)`);
  if (Object.is(value, -0)) throw new RangeError('-0 is not allowed in a bundle (BUNDLE-11)');
  return String(value);
}

function isPlainObject(value) {
  const proto = Object.getPrototypeOf(value);
  return proto === Object.prototype || proto === null;
}

// Serialises a value of the bundle subset as RFC 8785 JSON text with no insignificant whitespace.
export function canonicalJson(value) {
  if (value === null) return 'null';
  if (value === true) return 'true';
  if (value === false) return 'false';
  if (typeof value === 'string') return quote(value);
  if (typeof value === 'number') return integerText(value);
  if (Array.isArray(value)) {
    const items = [];
    for (let i = 0; i < value.length; i++) items.push(canonicalJson(value[i]));
    return '[' + items.join(',') + ']';
  }
  if (typeof value === 'object' && isPlainObject(value)) {
    // Array.prototype.sort on strings compares UTF-16 code units, which is the order RFC 8785 requires (BUNDLE-9).
    const names = Object.keys(value).sort();
    return '{' + names.map((name) => quote(name) + ':' + canonicalJson(value[name])).join(',') + '}';
  }
  throw new TypeError(`a value of type ${typeof value} is not in the bundle subset`);
}

// Returns the bytes of the canonical envelope {bundleVersion, content} (BUNDLE-1, BUNDLE-2, BUNDLE-4).
export function bundleBytes(bundleVersion, content) {
  return Buffer.from(canonicalJson({ bundleVersion, content }), 'utf8');
}

// Returns the digest that bundleVersion names: sha256 of the canonical bytes of content alone (BUNDLE-5, BUNDLE-6).
export function bundleVersionOf(content) {
  return 'sha256:' + sha256Hex(Buffer.from(canonicalJson(content), 'utf8'));
}

// Returns the lowercase hexadecimal SHA-256 digest of bytes (a string is read as UTF-8).
export function sha256Hex(bytes) {
  return createHash('sha256').update(bytes).digest('hex');
}

// Scans valid JSON text and returns the first duplicate member name at any depth, and every number token.
// JSON.parse keeps only the last of two equal names, so this pre-scan is what catches them (BUNDLE-111).
// It assumes the text is already well-formed JSON, which is checked by JSON.parse first.
function scanJson(text) {
  const frames = []; // one per open container: a Set of member names for an object, null for an array
  const numbers = [];
  let i = 0;
  while (i < text.length) {
    const c = text[i];
    if (c === '"') {
      const end = stringEnd(text, i);
      const top = frames[frames.length - 1];
      if (top && top.names && nextSignificant(text, end) === ':') {
        const name = JSON.parse(text.slice(i, end)); // decodes escapes, so "a" and "a" are equal
        if (top.names.has(name)) return { duplicate: name, numbers };
        top.names.add(name);
      }
      i = end;
    } else if (c === '{') {
      frames.push({ names: new Set() });
      i++;
    } else if (c === '[') {
      frames.push({ names: null });
      i++;
    } else if (c === '}' || c === ']') {
      frames.pop();
      i++;
    } else if (c === '-' || (c >= '0' && c <= '9')) {
      let j = i + 1;
      while (j < text.length && /[0-9eE+.-]/.test(text[j])) j++;
      numbers.push(text.slice(i, j));
      i = j;
    } else {
      i++;
    }
  }
  return { duplicate: null, numbers };
}

// Returns the index just after the string token that starts at `start` (a quote character).
function stringEnd(text, start) {
  let i = start + 1;
  while (i < text.length) {
    if (text[i] === '\\') i += 2;
    else if (text[i] === '"') return i + 1;
    else i++;
  }
  return text.length;
}

// Returns the first character after `from` that is not JSON whitespace.
function nextSignificant(text, from) {
  let i = from;
  while (i < text.length && ' \t\r\n'.includes(text[i])) i++;
  return text[i];
}

// Parses JSON text and rejects a duplicate member name at any depth. Returns the value and the number tokens.
function readStrictJson(text) {
  const value = JSON.parse(text); // rejects syntax errors first
  const { duplicate, numbers } = scanJson(text);
  if (duplicate !== null) throw new SyntaxError(`duplicate member name ${JSON.stringify(duplicate)} (BUNDLE-111)`);
  return { value, numbers };
}

// Strict JSON parser: rejects duplicate member names, and returns plain data.
export function parseJsonStrict(text) {
  return readStrictJson(text).value;
}

// Strict parser for bundle files and content files: also requires every number in the bundle form (BUNDLE-11, BUNDLE-12)
// and no lone surrogate in a string (BUNDLE-13). The result is the value that a loader may use.
export function parseBundleJson(text) {
  const { value, numbers } = readStrictJson(text);
  for (const token of numbers) {
    if (!BUNDLE_INTEGER.test(token)) throw new RangeError(`number ${token} is not an integer in bundle form (BUNDLE-11)`);
    if (!Number.isSafeInteger(Number(token))) throw new RangeError(`number ${token} is outside ±(2^53 − 1) (DET-4)`);
  }
  assertNoLoneSurrogates(value);
  return value;
}

// Walks a parsed value and rejects any string that holds an unpaired surrogate (BUNDLE-13).
function assertNoLoneSurrogates(value) {
  if (typeof value === 'string') {
    if (LONE_SURROGATE.test(value)) throw new RangeError('a string has an unpaired surrogate (BUNDLE-13)');
  } else if (Array.isArray(value)) {
    value.forEach(assertNoLoneSurrogates);
  } else if (value !== null && typeof value === 'object') {
    Object.keys(value).forEach((name) => {
      assertNoLoneSurrogates(name);
      assertNoLoneSurrogates(value[name]);
    });
  }
}
