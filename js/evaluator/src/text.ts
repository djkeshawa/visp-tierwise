// String helpers shared by the loader and the evaluation code: code point order (DET-5), unpaired
// surrogates (NORM-8) and the structure of a node ID (SCOPE-33, SCOPE-34). No Unicode normalisation is
// ever applied (NORM-3).

/** The largest number of code points in a node ID (SCOPE-33). */
export const MAX_NODE_ID_CODE_POINTS = 128;

/** Compares two strings code point by code point; a proper prefix sorts first (DET-5). */
export function compareCodePoints(a: string, b: string): number {
  let i = 0;
  let j = 0;
  while (i < a.length && j < b.length) {
    const x = a.codePointAt(i) as number;
    const y = b.codePointAt(j) as number;
    if (x !== y) {
      return x < y ? -1 : 1;
    }
    i += x > 0xffff ? 2 : 1;
    j += y > 0xffff ? 2 : 1;
  }
  if (i < a.length) return 1;
  if (j < b.length) return -1;
  return 0;
}

/** Tells whether the strings are in strictly increasing code point order, which also means distinct. */
export function strictlyIncreasing(names: readonly string[]): boolean {
  for (let i = 1; i < names.length; i++) {
    if (compareCodePoints(names[i - 1] as string, names[i] as string) >= 0) {
      return false;
    }
  }
  return true;
}

/** Tells whether a string contains a surrogate code unit that is not part of a pair (NORM-8). */
export function hasUnpairedSurrogate(text: string): boolean {
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    if (c >= 0xd800 && c <= 0xdbff) {
      const next = i + 1 < text.length ? text.charCodeAt(i + 1) : 0;
      if (next >= 0xdc00 && next <= 0xdfff) {
        i++;
      } else {
        return true;
      }
    } else if (c >= 0xdc00 && c <= 0xdfff) {
      return true;
    }
  }
  return false;
}

/** Counts the code points of a string. An unpaired surrogate counts as one. */
export function codePointCount(text: string): number {
  let count = 0;
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    if (c >= 0xd800 && c <= 0xdbff && i + 1 < text.length) {
      const next = text.charCodeAt(i + 1);
      if (next >= 0xdc00 && next <= 0xdfff) {
        i++;
      }
    }
    count++;
  }
  return count;
}

/** Tells whether a string contains a control character U+0000 to U+001F or U+007F to U+009F (SCOPE-34). */
export function hasControlCharacter(text: string): boolean {
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    if (c <= 0x1f || (c >= 0x7f && c <= 0x9f)) {
      return true;
    }
  }
  return false;
}

/**
 * Tells whether a string is a well-formed node ID: one to 128 code points, no control character and no
 * unpaired surrogate (SCOPE-33, SCOPE-34, SCOPE-37). It is not normalised or checked for NFC (CTX-7).
 */
export function isNodeId(text: string): boolean {
  if (text.length === 0) return false;
  if (hasUnpairedSurrogate(text) || hasControlCharacter(text)) return false;
  return codePointCount(text) <= MAX_NODE_ID_CODE_POINTS;
}

const hasOwn = Object.prototype.hasOwnProperty;

/** Tells whether an object has an own property, whatever the name (a name such as "constructor" is data). */
export function has(object: object, name: string): boolean {
  return hasOwn.call(object, name);
}

/** Defines an own data property, so that a name such as "__proto__" stays data. */
export function setOwn(target: object, name: string, value: unknown): void {
  Object.defineProperty(target, name, { value, writable: true, enumerable: true, configurable: true });
}

/**
 * Tells whether a value is a plain mapping: a non-null object that is neither an array nor an instance of
 * a class. A null-prototype object counts.
 */
export function isPlainObject(value: unknown): value is { readonly [name: string]: unknown } {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return false;
  }
  const proto: unknown = Object.getPrototypeOf(value);
  return proto === null || Object.getPrototypeOf(proto) === null;
}
