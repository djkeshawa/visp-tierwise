// Shared helpers for the tests: the built package, the conformance suite on disk, a YAML loader that applies the
// source rules of the vectors (VEC-6 to VEC-8, VEC-34, VEC-35), and a builder of canonical bundles.
//
// The tests run over the built output in dist/, never over src/, so they check what ships.
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isMap, isScalar, isSeq, parseDocument, visit } from 'yaml';

export const distUrl = new URL('../dist/esm/', import.meta.url);
export const distDir = fileURLToPath(distUrl);
export const cjsEntry = fileURLToPath(new URL('../dist/cjs/index.js', import.meta.url));
export const conformanceDir = fileURLToPath(new URL('../../../spec/conformance/', import.meta.url));

/** The public API, as ESM. */
export const api = await import(new URL('index.js', distUrl).href);
/** The internal primitives, loaded from the build output so that the tests can reach them. */
export const json = await import(new URL('json.js', distUrl).href);
export const sha = await import(new URL('sha256.js', distUrl).href);

/** A vector that the runner cannot execute (VEC-111), as opposed to a vector that fails. */
export class RunnerFailure extends Error {
  constructor(message) {
    super(`runner failure: ${message}`);
    this.name = 'RunnerFailure';
  }
}

/** Lists the files below a directory at any depth, sorted by path. */
export function listFiles(dir, extension) {
  const found = [];
  const walk = (current) => {
    for (const entry of readdirSync(current, { withFileTypes: true })) {
      const path = join(current, entry.name);
      if (entry.isDirectory()) walk(path);
      else if (entry.isFile() && path.endsWith(extension)) found.push(path);
    }
  };
  walk(dir);
  return found.sort();
}

/** The path of a file relative to a directory, with "/" separators. */
export function relativePath(dir, file) {
  return relative(dir, file).split(sep).join('/');
}

const INTEGER_TOKEN = /^(?:[-+]?[0-9]+|0x[0-9a-fA-F]+|0o[0-7]+)$/;
const MAX_VECTOR_INTEGER = 9007199254740992n; // 2^53 (VEC-34)

function scalarOf(node) {
  const value = node.value;
  if (typeof value === 'number') {
    const source = node.source ?? '';
    const isInteger = INTEGER_TOKEN.test(source);
    if (!Number.isFinite(value) || (!isInteger && Number.isInteger(value))) {
      throw new RunnerFailure(`the float ${source} cannot be held as plain data (VEC-35)`);
    }
    if (isInteger) {
      const magnitude = BigInt(source.replace(/^\+/, ''));
      if ((magnitude < 0n ? -magnitude : magnitude) > MAX_VECTOR_INTEGER) {
        throw new RunnerFailure(`the integer ${source} is larger than 2^53 in magnitude (VEC-34)`);
      }
    }
  }
  return value;
}

function plainOf(node) {
  if (node === null || node === undefined) return null;
  if (isMap(node)) {
    const out = {};
    for (const pair of node.items) {
      const key = pair.key;
      if (!isScalar(key) || typeof key.value !== 'string') {
        throw new RunnerFailure('a mapping key is not a string scalar (VEC-36)');
      }
      Object.defineProperty(out, key.value, {
        value: plainOf(pair.value),
        writable: true,
        enumerable: true,
        configurable: true,
      });
    }
    return out;
  }
  if (isSeq(node)) return node.items.map(plainOf);
  if (isScalar(node)) return scalarOf(node);
  throw new RunnerFailure('an alias is not allowed (VEC-7)');
}

/** Loads one vector file under the YAML rules of VEC-6 to VEC-8: UTF-8, YAML 1.2 core schema, one document. */
export function loadVector(path) {
  const bytes = readFileSync(path);
  if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    throw new RunnerFailure('the file begins with a byte-order mark (VEC-6)');
  }
  let text;
  try {
    text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch {
    throw new RunnerFailure('the file is not valid UTF-8 (VEC-6)');
  }
  const doc = parseDocument(text, { version: '1.2', schema: 'core', uniqueKeys: true, merge: false });
  if (doc.errors.length > 0) {
    throw new RunnerFailure(`YAML error: ${doc.errors[0].message.split('\n')[0]} (VEC-7)`);
  }
  visit(doc, {
    Alias() {
      throw new RunnerFailure('an alias is not allowed (VEC-7)');
    },
    Node(_key, node) {
      if (node.tag !== undefined) throw new RunnerFailure(`the explicit tag ${node.tag} is not allowed (VEC-8)`);
      if (node.anchor !== undefined) throw new RunnerFailure('an anchor is not allowed (VEC-8)');
    },
  });
  if (!isMap(doc.contents)) throw new RunnerFailure('the root of a vector is a mapping');
  return plainOf(doc.contents);
}

/** Builds the canonical bytes of a bundle from its content, computing the digest (BUNDLE-1 to BUNDLE-6). */
export function bundleBytes(content, bundleVersion) {
  const version = bundleVersion !== undefined ? bundleVersion : `sha256:${sha.sha256Hex(json.canonicalBytes(content))}`;
  return json.canonicalBytes({ bundleVersion: version, content });
}

/** Loads a bundle built from a content object. */
export function loadContent(content) {
  return api.loadBundle(bundleBytes(content));
}

/** Runs a function and returns the code of the TierwiseError it throws, or null when it returns. */
export function codeOf(run) {
  try {
    run();
    return null;
  } catch (error) {
    if (error instanceof api.TierwiseError) return error.code;
    throw error;
  }
}

/** Awaits a promise and returns the code of the TierwiseError it rejects with, or null when it resolves. */
export async function codeOfAsync(promise) {
  try {
    await promise;
    return null;
  } catch (error) {
    if (error instanceof api.TierwiseError) return error.code;
    throw error;
  }
}

// Matching of an actual result against an expectation (VEC-54 to VEC-64).

const isObject = (value) => typeof value === 'object' && value !== null && !Array.isArray(value);
const own = (object, name) => Object.prototype.hasOwnProperty.call(object, name);

/** Compares an expected value with an actual one and returns the mismatches. */
export function mismatches(expected, actual, exact, at = '$', out = []) {
  if (expected === null) {
    if (actual !== null) out.push(`${at}: expected null, got ${show(actual)}`); // VEC-56
  } else if (Array.isArray(expected)) {
    if (!Array.isArray(actual)) {
      out.push(`${at}: expected a sequence, got ${show(actual)}`);
    } else if (expected.length !== actual.length) {
      out.push(`${at}: expected ${expected.length} element(s), got ${actual.length}`); // VEC-61
    } else {
      expected.forEach((element, index) => mismatches(element, actual[index], exact, `${at}[${index}]`, out));
    }
  } else if (isObject(expected)) {
    if (!isObject(actual) || Array.isArray(actual)) {
      out.push(`${at}: expected a mapping, got ${show(actual)}`);
    } else {
      for (const name of Object.keys(expected)) {
        if (!own(actual, name)) {
          out.push(`${at}.${name}: absent from the result`); // VEC-55
        } else {
          mismatches(expected[name], actual[name], exact, `${at}.${name}`, out);
        }
      }
      if (exact) {
        for (const name of Object.keys(actual)) {
          if (!own(expected, name)) out.push(`${at}.${name}: present in the result but not named by exact expect`);
        }
      }
    }
  } else if (typeof expected === 'boolean' || typeof expected === 'string' || typeof expected === 'number') {
    // A scalar matches only when the actual value has the same JSON type and an equal value (VEC-57, VEC-58).
    if (typeof actual !== typeof expected || actual !== expected) {
      out.push(`${at}: expected ${show(expected)}, got ${show(actual)}`);
    }
  } else {
    out.push(`${at}: unsupported expected value`);
  }
  return out;
}

function show(value) {
  const text = JSON.stringify(value);
  return text === undefined ? String(value) : text.length > 120 ? `${text.slice(0, 117)}...` : text;
}

