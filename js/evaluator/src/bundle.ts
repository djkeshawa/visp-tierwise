// load bundle (EVAL-2, EVAL-3, EVAL-14): synchronous, with the checks in the order of EVAL-14 and the version
// order of ADR 0018. The result is a deeply frozen model (EVAL-46) that only this package can read.

import { TierwiseError, integrity } from './errors.js';
import { JsonFormatError, canonicalBytes, canonicalJson, parseStrictJson, utf8Encode } from './json.js';
import { type Model } from './model.js';
import { sha256Hex } from './sha256.js';
import { readModel } from './structure.js';
import { has, hasUnpairedSurrogate, isPlainObject } from './text.js';
import type { LoadedBundle } from './types.js';

/** The bundle format version that this loader supports (BUNDLE-137). */
export const SUPPORTED_BUNDLE_FORMAT_VERSION = '1';

/** The spec version that this loader supports (BUNDLE-137). */
export const SUPPORTED_SPEC_VERSION = '0.1';

const BUNDLE_VERSION = /^sha256:[0-9a-f]{64}$/;

/** The models of loaded bundles. A bundle is known to the evaluator only through this table (EVAL-55). */
const models = new WeakMap<object, Model>();

/**
 * Returns the model of a loaded bundle.
 *
 * @throws TypeError when the value was not returned by loadBundle (EVAL-55 defines no code for it)
 */
export function modelOf(bundle: LoadedBundle): Model {
  const model = typeof bundle === 'object' && bundle !== null ? models.get(bundle) : undefined;
  if (model === undefined) {
    throw new TypeError('the bundle argument must be a value returned by loadBundle');
  }
  return model;
}

/** Decodes the bytes of a bundle as UTF-8 without a byte-order mark (BUNDLE-3, BUNDLE-116). */
function decode(bytes: Uint8Array): string {
  if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    throw integrity('the document begins with a BOM (BUNDLE-3)');
  }
  try {
    return new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(bytes);
  } catch {
    throw integrity('the document is not valid UTF-8 (BUNDLE-116)');
  }
}

/** Step 1 of EVAL-14: strict parsing and the envelope (BUNDLE-1, BUNDLE-2, BUNDLE-111, BUNDLE-112, BUNDLE-116). */
function readEnvelope(text: string): { readonly bundleVersion: unknown; readonly content: unknown } {
  let root: unknown;
  try {
    root = parseStrictJson(text);
  } catch (error) {
    if (error instanceof JsonFormatError) throw integrity(error.message);
    throw error;
  }
  if (!isPlainObject(root)) throw integrity('a bundle is a JSON object (BUNDLE-1)');
  for (const name of Object.keys(root)) {
    if (name !== 'bundleVersion' && name !== 'content') {
      throw integrity(`the envelope has the member ${name} (BUNDLE-112)`);
    }
  }
  if (!has(root, 'bundleVersion') || !has(root, 'content')) {
    throw integrity('the envelope lacks bundleVersion or content (BUNDLE-1, BUNDLE-116)');
  }
  if (!isPlainObject(root.content)) throw integrity('content is not an object (BUNDLE-14)');
  if (canonicalJson(root) !== text) {
    throw integrity('the bytes are not the JCS serialisation of the envelope (BUNDLE-2, BUNDLE-116)');
  }
  return { bundleVersion: root.bundleVersion, content: root.content };
}

/**
 * Step 2 of EVAL-14: the versions (BUNDLE-115, BUNDLE-137, EVAL-56). `bundleFormatVersion` is checked
 * completely first, then `specVersion` (ADR 0018), so a bundle in a future format reports
 * E_BUNDLE_UNSUPPORTED even when `specVersion` is missing.
 */
function checkVersions(content: { readonly [name: string]: unknown }): void {
  if (!has(content, 'bundleFormatVersion')) throw integrity('content lacks bundleFormatVersion (EVAL-56)');
  if (content.bundleFormatVersion !== SUPPORTED_BUNDLE_FORMAT_VERSION) {
    throw new TierwiseError('E_BUNDLE_UNSUPPORTED', 'bundleFormatVersion is not the supported value (BUNDLE-115, BUNDLE-137)');
  }
  if (!has(content, 'specVersion')) throw integrity('content lacks specVersion (EVAL-56)');
  if (content.specVersion !== SUPPORTED_SPEC_VERSION) {
    throw new TierwiseError('E_BUNDLE_UNSUPPORTED', 'specVersion is not the supported value (BUNDLE-115, BUNDLE-137)');
  }
}

/** Step 3 of EVAL-14: the digest (BUNDLE-5, BUNDLE-6, BUNDLE-113). */
function checkDigest(bundleVersion: unknown, content: unknown): string {
  if (typeof bundleVersion !== 'string' || !BUNDLE_VERSION.test(bundleVersion)) {
    throw integrity('bundleVersion is not sha256: followed by 64 lower-case hexadecimal digits (BUNDLE-5)');
  }
  if (`sha256:${sha256Hex(canonicalBytes(content))}` !== bundleVersion) {
    throw integrity('bundleVersion is not the digest of content (BUNDLE-113)');
  }
  return bundleVersion;
}

/**
 * Loads the bytes of one bundle (EVAL-2). It applies the checks of EVAL-14 in order: strict parse and
 * envelope, versions, digest, structure. The first failing check decides the code.
 *
 * A string is encoded as UTF-8 before any check (EVAL-3), so the checks apply to the encoded bytes. A string
 * with an unpaired surrogate has no UTF-8 form and is rejected as input that is not UTF-8 (BUNDLE-116).
 *
 * @param input the exact bytes of the bundle, or the same text as a string
 * @throws TierwiseError E_BUNDLE_INTEGRITY or E_BUNDLE_UNSUPPORTED
 */
export function loadBundle(input: Uint8Array | ArrayBuffer | string): LoadedBundle {
  let text: string;
  if (typeof input === 'string') {
    if (hasUnpairedSurrogate(input)) throw integrity('the text is not valid UTF-8 (BUNDLE-116)');
    // Encoding and decoding again makes the byte-order-mark check the same as for bytes.
    text = decode(utf8Encode(input));
  } else if (ArrayBuffer.isView(input)) {
    text = decode(new Uint8Array(input.buffer, input.byteOffset, input.byteLength));
  } else if (input instanceof ArrayBuffer) {
    text = decode(new Uint8Array(input));
  } else {
    throw new TypeError('loadBundle takes bytes (a Uint8Array or an ArrayBuffer) or a string');
  }
  const envelope = readEnvelope(text);
  const content = envelope.content as { readonly [name: string]: unknown };
  checkVersions(content);
  const bundleVersion = checkDigest(envelope.bundleVersion, content);
  const model = readModel(content);
  const bundle: LoadedBundle = Object.freeze({
    bundleVersion,
    bundleFormatVersion: SUPPORTED_BUNDLE_FORMAT_VERSION,
    specVersion: SUPPORTED_SPEC_VERSION,
  } as const);
  models.set(bundle, model);
  return bundle;
}
