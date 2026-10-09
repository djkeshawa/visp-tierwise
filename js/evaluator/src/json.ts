// The strict JSON reader and the canonical writer for the subset that a bundle uses (BUNDLE-7 to
// BUNDLE-13, BUNDLE-111, BUNDLE-116, DET-4). ADR 0012 section 2 describes the choice of in-house code: a
// bundle holds only integers, so number formatting is integer formatting.
//
// The reader is a small recursive-descent parser instead of JSON.parse with a pre-scan. It sees every
// member name, so it rejects a repeated name at any depth directly, and it never builds a floating-point
// number that it must then distrust.

import { hasUnpairedSurrogate, setOwn } from './text.js';

/** A JSON value of the bundle subset. Integers only; objects carry string names. */
export type JsonValue =
  | null
  | boolean
  | number
  | string
  | readonly JsonValue[]
  | { readonly [name: string]: JsonValue };

/** The largest magnitude of an integer in a bundle, 2^53 - 1 (BUNDLE-11, BUNDLE-12, DET-4). */
export const MAX_SAFE_INTEGER = 9007199254740991;

/** Raised by the strict reader for input that the bundle format does not accept. */
export class JsonFormatError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'JsonFormatError';
  }
}

/** The deepest nesting that the reader accepts. Deeper input is refused instead of risking the stack. */
const MAX_DEPTH = 1000;

const NUMBER_TOKEN = /-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?/y;
const INTEGER_TOKEN = /^(?:0|-?[1-9][0-9]*)$/;

class StrictParser {
  private cursor = 0;

  constructor(private readonly text: string) {}

  parseDocument(): JsonValue {
    this.skipWhitespace();
    if (this.cursor >= this.text.length) {
      throw new JsonFormatError('the document is empty');
    }
    const value = this.parseValue(0);
    this.skipWhitespace();
    if (this.cursor < this.text.length) {
      throw new JsonFormatError('data follows the document (BUNDLE-4, BUNDLE-116)');
    }
    return value;
  }

  private skipWhitespace(): void {
    while (this.cursor < this.text.length) {
      const c = this.text.charCodeAt(this.cursor);
      if (c === 0x20 || c === 0x0a || c === 0x0d || c === 0x09) {
        this.cursor++;
      } else {
        return;
      }
    }
  }

  private parseValue(depth: number): JsonValue {
    if (depth > MAX_DEPTH) {
      throw new JsonFormatError('the document is nested too deeply');
    }
    if (this.cursor >= this.text.length) {
      throw new JsonFormatError('unexpected end of the document');
    }
    const c = this.text.charAt(this.cursor);
    switch (c) {
      case '{':
        return this.parseObject(depth);
      case '[':
        return this.parseArray(depth);
      case '"':
        return this.parseString();
      case 't':
        return this.literal('true', true);
      case 'f':
        return this.literal('false', false);
      case 'n':
        return this.literal('null', null);
      default:
        if (c === '-' || (c >= '0' && c <= '9')) {
          return this.parseNumber();
        }
        throw new JsonFormatError(`unexpected character at offset ${this.cursor}`);
    }
  }

  private literal(word: string, value: boolean | null): boolean | null {
    if (!this.text.startsWith(word, this.cursor)) {
      throw new JsonFormatError(`unexpected token at offset ${this.cursor}`);
    }
    this.cursor += word.length;
    return value;
  }

  private parseNumber(): number {
    NUMBER_TOKEN.lastIndex = this.cursor;
    const match = NUMBER_TOKEN.exec(this.text);
    if (match === null) {
      throw new JsonFormatError(`malformed number at offset ${this.cursor}`);
    }
    const token = match[0];
    this.cursor += token.length;
    if (!INTEGER_TOKEN.test(token)) {
      throw new JsonFormatError('a number that is not an integer, or that is -0 (BUNDLE-11)');
    }
    // Sixteen digits always fit in a double well enough to be compared with the bound: every value above
    // 2^53 - 1 rounds to at least 2^53.
    const digits = token.startsWith('-') ? token.length - 1 : token.length;
    const value = Number(token);
    if (digits > 16 || Math.abs(value) > MAX_SAFE_INTEGER) {
      throw new JsonFormatError('an integer outside +/-(2^53 - 1) (BUNDLE-12)');
    }
    return value;
  }

  private parseString(): string {
    // The opening quote is at this.cursor.
    let out = '';
    let start = ++this.cursor;
    for (;;) {
      if (this.cursor >= this.text.length) {
        throw new JsonFormatError('unterminated string');
      }
      const c = this.text.charCodeAt(this.cursor);
      if (c === 0x22) {
        out += this.text.slice(start, this.cursor);
        this.cursor++;
        break;
      }
      if (c < 0x20) {
        throw new JsonFormatError('a control character inside a string');
      }
      if (c === 0x5c) {
        out += this.text.slice(start, this.cursor);
        out += this.parseEscape();
        start = this.cursor;
      } else {
        this.cursor++;
      }
    }
    if (hasUnpairedSurrogate(out)) {
      throw new JsonFormatError('an unpaired surrogate (BUNDLE-13)');
    }
    return out;
  }

  private parseEscape(): string {
    // this.cursor is at the backslash.
    const code = this.text.charAt(this.cursor + 1);
    this.cursor += 2;
    switch (code) {
      case '"':
        return '"';
      case '\\':
        return '\\';
      case '/':
        return '/';
      case 'b':
        return '\b';
      case 'f':
        return '\f';
      case 'n':
        return '\n';
      case 'r':
        return '\r';
      case 't':
        return '\t';
      case 'u': {
        const hex = this.text.slice(this.cursor, this.cursor + 4);
        if (!/^[0-9a-fA-F]{4}$/.test(hex)) {
          throw new JsonFormatError('a malformed \\u escape');
        }
        this.cursor += 4;
        return String.fromCharCode(parseInt(hex, 16));
      }
      default:
        throw new JsonFormatError('an unknown escape sequence');
    }
  }

  private parseArray(depth: number): JsonValue[] {
    this.cursor++;
    const items: JsonValue[] = [];
    this.skipWhitespace();
    if (this.text.charAt(this.cursor) === ']') {
      this.cursor++;
      return items;
    }
    for (;;) {
      this.skipWhitespace();
      items.push(this.parseValue(depth + 1));
      this.skipWhitespace();
      const c = this.text.charAt(this.cursor);
      this.cursor++;
      if (c === ']') {
        return items;
      }
      if (c !== ',') {
        throw new JsonFormatError('a comma or a closing bracket was expected');
      }
    }
  }

  private parseObject(depth: number): JsonValue {
    this.cursor++;
    // A null-prototype object: a member named "__proto__" is an ordinary member.
    const members: { [name: string]: JsonValue } = Object.create(null) as { [name: string]: JsonValue };
    const seen = new Set<string>();
    this.skipWhitespace();
    if (this.text.charAt(this.cursor) === '}') {
      this.cursor++;
      return members;
    }
    for (;;) {
      this.skipWhitespace();
      if (this.text.charAt(this.cursor) !== '"') {
        throw new JsonFormatError('a member name was expected');
      }
      const name = this.parseString();
      if (seen.has(name)) {
        throw new JsonFormatError(`duplicate property name (BUNDLE-111): ${name}`);
      }
      seen.add(name);
      this.skipWhitespace();
      if (this.text.charAt(this.cursor) !== ':') {
        throw new JsonFormatError('a colon was expected');
      }
      this.cursor++;
      this.skipWhitespace();
      setOwn(members, name, this.parseValue(depth + 1));
      this.skipWhitespace();
      const c = this.text.charAt(this.cursor);
      this.cursor++;
      if (c === '}') {
        return members;
      }
      if (c !== ',') {
        throw new JsonFormatError('a comma or a closing brace was expected');
      }
    }
  }
}

/**
 * Parses one strict JSON document (BUNDLE-111, BUNDLE-116). It rejects a member name that repeats in one object
 * at any depth, a number that is not an integer of the form `0|-?[1-9][0-9]*` within +/-(2^53 - 1), a string
 * with an unpaired surrogate (also one written as an escape), malformed JSON and data after the document. It
 * does not check the exact byte form: the caller compares the text with {@link canonicalJson} for that.
 *
 * @throws JsonFormatError when the text is not an acceptable document
 */
export function parseStrictJson(text: string): JsonValue {
  return new StrictParser(text).parseDocument();
}

const HEX = '0123456789abcdef';

function writeString(text: string, out: string[]): void {
  if (hasUnpairedSurrogate(text)) {
    throw new TypeError('unpaired surrogate in a string (BUNDLE-13)');
  }
  let piece = '"';
  let start = 0;
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    let escape: string | null = null;
    if (c === 0x22) escape = '\\"';
    else if (c === 0x5c) escape = '\\\\';
    else if (c === 0x08) escape = '\\b';
    else if (c === 0x09) escape = '\\t';
    else if (c === 0x0a) escape = '\\n';
    else if (c === 0x0c) escape = '\\f';
    else if (c === 0x0d) escape = '\\r';
    else if (c < 0x20) escape = `\\u00${HEX.charAt(c >> 4)}${HEX.charAt(c & 0xf)}`;
    if (escape !== null) {
      piece += text.slice(start, i) + escape;
      start = i + 1;
    }
  }
  piece += text.slice(start) + '"';
  out.push(piece);
}

function writeValue(value: unknown, out: string[]): void {
  if (value === null) {
    out.push('null');
  } else if (typeof value === 'boolean') {
    out.push(value ? 'true' : 'false');
  } else if (typeof value === 'string') {
    writeString(value, out);
  } else if (typeof value === 'number') {
    if (!Number.isSafeInteger(value)) {
      throw new TypeError(`not an integer within +/-(2^53 - 1): ${String(value)}`);
    }
    out.push(String(value));
  } else if (Array.isArray(value)) {
    out.push('[');
    for (let i = 0; i < value.length; i++) {
      if (i > 0) out.push(',');
      writeValue(value[i], out);
    }
    out.push(']');
  } else if (typeof value === 'object') {
    const record = value as { readonly [name: string]: unknown };
    // The default sort compares UTF-16 code units, which is the member order of RFC 8785 (BUNDLE-9).
    const names = Object.keys(record).sort();
    out.push('{');
    names.forEach((name, index) => {
      if (index > 0) out.push(',');
      writeString(name, out);
      out.push(':');
      writeValue(record[name], out);
    });
    out.push('}');
  } else {
    throw new TypeError(`not a value of the bundle subset: ${typeof value}`);
  }
}

/**
 * Serialises a value to its canonical JSON text (RFC 8785 for the bundle subset): no insignificant whitespace,
 * object members sorted by UTF-16 code units, the escapes of BUNDLE-10, and integers only.
 *
 * @throws TypeError when the value is outside the bundle subset
 */
export function canonicalJson(value: unknown): string {
  const out: string[] = [];
  writeValue(value, out);
  return out.join('');
}

const encoder = new TextEncoder();

/** Encodes a string as UTF-8. The string must not contain an unpaired surrogate. */
export function utf8Encode(text: string): Uint8Array {
  return encoder.encode(text);
}

/** The canonical JSON of a value as UTF-8 bytes (BUNDLE-2). */
export function canonicalBytes(value: unknown): Uint8Array {
  return utf8Encode(canonicalJson(value));
}
