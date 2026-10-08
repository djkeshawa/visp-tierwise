// YAML 1.2 loading under the source rules of SRC-1 to SRC-11, returning plain data.
// It uses the yaml package in its core schema mode, and rejects aliases, anchors, explicit tags,
// merge keys, extra documents and duplicate keys. Codes follow spec/error-codes.md.
// It also refuses float scalars that plain data cannot keep distinct from integers or from JSON (see scalarOf).
import { readFileSync } from 'node:fs';
import { isMap, isScalar, isSeq, parseAllDocuments, visit } from 'yaml';

// A load failure with the code that the spec names for it.
export class SpecLoadError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'SpecLoadError';
    this.code = code;
  }
}

// Decimal, hexadecimal and octal integer tokens of the core schema. Any other number token is a float.
const INTEGER_TOKEN = /^(?:[-+]?[0-9]+|0x[0-9a-fA-F]+|0o[0-7]+)$/;

// Decodes the bytes as UTF-8 and refuses a byte-order mark (SRC-10).
function decodeUtf8(bytes, code) {
  if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    throw new SpecLoadError(code, 'the source begins with a byte-order mark (SRC-10)');
  }
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    throw new SpecLoadError(code, 'the source is not valid UTF-8 (SRC-10)');
  }
}

// Turns one scalar into its plain value. `opts.typeCode` is the code for a scalar that plain data cannot keep:
// a float token with an integral value (`1.0`, `1e3`) or a non-finite float (`.inf`, `.nan`). Plain data cannot
// tell the first from an integer or the second from JSON data, and the example after TYPE-17 treats an integral float as the wrong
// type in a value, so the loader refuses them instead of changing their type silently. In the manifest the
// code is E_MANIFEST, because MANIFEST-4 makes a non-string `specVersion` an E_MANIFEST fault.
// `opts.maxInteger` (a BigInt) bounds the magnitude of an integer token (VEC-34).
function scalarOf(node, opts) {
  const value = node.value;
  if (typeof value === 'number') {
    const source = node.source ?? '';
    const isInteger = INTEGER_TOKEN.test(source);
    if (!Number.isFinite(value) || (!isInteger && Number.isInteger(value))) {
      throw new SpecLoadError(opts.typeCode, `the float ${source} cannot be held as plain data (an integral or non-finite float; see the example after TYPE-17, and VEC-35)`);
    }
    if (isInteger && opts.maxInteger !== undefined) {
      const magnitude = BigInt(source);
      if ((magnitude < 0n ? -magnitude : magnitude) > opts.maxInteger) {
        throw new SpecLoadError('VEC-34', `the integer ${source} is larger than 2^53 in magnitude`);
      }
    }
  }
  return value;
}

// Turns one mapping into a plain object. Keys must be strings, and two keys that are equal after NFC are duplicates (SRC-3, SRC-4, SRC-11).
function mapOf(map, opts) {
  const seen = new Set();
  const entries = [];
  for (const pair of map.items) {
    const key = pair.key;
    if (!isScalar(key) || typeof key.value !== 'string') {
      const shown = isScalar(key) ? key.source : '(empty key)';
      throw new SpecLoadError(opts.typeCode, `the key ${shown} is not a string; quote it (SRC-11)`);
    }
    if (key.type === 'PLAIN' && key.value === '<<') {
      throw new SpecLoadError('E_YAML_FEATURE', 'a merge key is not allowed (SRC-2)');
    }
    const normal = key.value.normalize('NFC');
    if (seen.has(normal)) {
      throw new SpecLoadError('E_DUPLICATE_KEY', `the key ${JSON.stringify(key.value)} is repeated (SRC-3)`);
    }
    seen.add(normal);
    entries.push([key.value, plainOf(pair.value, opts)]);
  }
  // Object.fromEntries defines each name as an own property, so a key such as "__proto__" stays data.
  return Object.fromEntries(entries);
}

// Converts a node of a checked document into plain data.
function plainOf(node, opts) {
  if (node === null || node === undefined) return null;
  if (isMap(node)) return mapOf(node, opts);
  if (isSeq(node)) return node.items.map((item) => plainOf(item, opts));
  if (isScalar(node)) return scalarOf(node, opts);
  throw new SpecLoadError('E_YAML_FEATURE', 'an alias is not allowed (SRC-2)');
}

// Rejects aliases, anchors and explicit tags anywhere in the document (SRC-2, SRC-7).
function assertPlainFeatures(doc) {
  visit(doc, {
    Alias() {
      throw new SpecLoadError('E_YAML_FEATURE', 'an alias is not allowed (SRC-2)');
    },
    Node(_key, node) {
      if (node.tag !== undefined) throw new SpecLoadError('E_YAML_FEATURE', `the explicit tag ${node.tag} is not allowed (SRC-7)`);
      if (node.anchor !== undefined) throw new SpecLoadError('E_YAML_FEATURE', 'an anchor is not allowed (SRC-7)');
    },
  });
}

// Loads one YAML source from its bytes. `manifest` selects the code for syntax and root-shape faults:
// E_MANIFEST for the manifest (SRC-5, MANIFEST-2), and E_PARSE for other sources (SRC-5, SRC-6). It also selects
// the code for a scalar or key that plain data cannot hold: E_MANIFEST (MANIFEST-3, MANIFEST-4), else E_TYPE_MISMATCH (SRC-11).
// `maxInteger` (a BigInt) refuses an integer token above that magnitude (VEC-34); the check sets it for evaluate vectors.
export function loadYaml(bytes, { manifest = false, maxInteger } = {}) {
  const shapeCode = manifest ? 'E_MANIFEST' : 'E_PARSE';
  const opts = { typeCode: manifest ? 'E_MANIFEST' : 'E_TYPE_MISMATCH', maxInteger };
  const text = decodeUtf8(bytes, shapeCode);
  // uniqueKeys is off so that the NFC rule of SRC-4 is applied below, not the library's exact-match rule.
  const docs = parseAllDocuments(text, { version: '1.2', schema: 'core', uniqueKeys: false, merge: false });
  if (docs.length !== 1) throw new SpecLoadError(shapeCode, `expected one YAML document, found ${docs.length} (SRC-6)`);
  const doc = docs[0];
  if (doc.errors.length > 0) throw new SpecLoadError(shapeCode, `YAML syntax error: ${doc.errors[0].message.split('\n')[0]} (SRC-5)`);
  if (doc.directives?.yaml?.version !== '1.2') throw new SpecLoadError(shapeCode, 'the YAML version must be 1.2 (SRC-1)');
  if (!isMap(doc.contents)) throw new SpecLoadError(shapeCode, 'the root node must be a mapping (SRC-6)');
  assertPlainFeatures(doc);
  return plainOf(doc.contents, opts);
}

// Reads a file and loads it with loadYaml.
export function loadYamlFile(path, options) {
  return loadYaml(readFileSync(path), options);
}
