// A self-contained SHA-256 (FIPS 180-4), so that loadBundle stays synchronous and runs in a browser
// (ADR 0012 section 3). Web Crypto's digest is asynchronous only. The tests compare this code with
// node:crypto over generated inputs.

const K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
]);

const INITIAL = [
  0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a, 0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19,
] as const;

function rotr(x: number, n: number): number {
  return (x >>> n) | (x << (32 - n));
}

/** Mixes one 64-byte block, starting at `offset` in `block`, into the state `h`. */
function compress(h: Uint32Array, w: Uint32Array, block: Uint8Array, offset: number): void {
  for (let i = 0; i < 16; i++) {
    const j = offset + i * 4;
    w[i] =
      ((block[j] as number) << 24) |
      ((block[j + 1] as number) << 16) |
      ((block[j + 2] as number) << 8) |
      (block[j + 3] as number);
  }
  for (let i = 16; i < 64; i++) {
    const w15 = w[i - 15] as number;
    const w2 = w[i - 2] as number;
    const s0 = rotr(w15, 7) ^ rotr(w15, 18) ^ (w15 >>> 3);
    const s1 = rotr(w2, 17) ^ rotr(w2, 19) ^ (w2 >>> 10);
    w[i] = ((w[i - 16] as number) + s0 + (w[i - 7] as number) + s1) | 0;
  }
  let a = h[0] as number;
  let b = h[1] as number;
  let c = h[2] as number;
  let d = h[3] as number;
  let e = h[4] as number;
  let f = h[5] as number;
  let g = h[6] as number;
  let hh = h[7] as number;
  for (let i = 0; i < 64; i++) {
    const s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
    const choose = (e & f) ^ (~e & g);
    const t1 = (hh + s1 + choose + (K[i] as number) + (w[i] as number)) | 0;
    const s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
    const majority = (a & b) ^ (a & c) ^ (b & c);
    const t2 = (s0 + majority) | 0;
    hh = g;
    g = f;
    f = e;
    e = (d + t1) | 0;
    d = c;
    c = b;
    b = a;
    a = (t1 + t2) | 0;
  }
  h[0] = (h[0] as number) + a;
  h[1] = (h[1] as number) + b;
  h[2] = (h[2] as number) + c;
  h[3] = (h[3] as number) + d;
  h[4] = (h[4] as number) + e;
  h[5] = (h[5] as number) + f;
  h[6] = (h[6] as number) + g;
  h[7] = (h[7] as number) + hh;
}

/** Computes the SHA-256 digest of a byte array and returns the 32 digest bytes. */
export function sha256(input: Uint8Array): Uint8Array {
  const h = new Uint32Array(INITIAL);
  const w = new Uint32Array(64);
  const length = input.length;
  const whole = Math.floor(length / 64);
  for (let i = 0; i < whole; i++) {
    compress(h, w, input, i * 64);
  }
  // The tail: the remaining bytes, one 0x80 byte, zeros, and the message length in bits as a 64-bit
  // big-endian number. It fills one block when at most 55 bytes remain, and two blocks otherwise.
  const rest = length - whole * 64;
  const tail = new Uint8Array(rest < 56 ? 64 : 128);
  tail.set(input.subarray(whole * 64));
  tail[rest] = 0x80;
  const high = Math.floor(length / 0x20000000);
  const low = (length << 3) >>> 0;
  const end = tail.length;
  tail[end - 8] = (high >>> 24) & 0xff;
  tail[end - 7] = (high >>> 16) & 0xff;
  tail[end - 6] = (high >>> 8) & 0xff;
  tail[end - 5] = high & 0xff;
  tail[end - 4] = (low >>> 24) & 0xff;
  tail[end - 3] = (low >>> 16) & 0xff;
  tail[end - 2] = (low >>> 8) & 0xff;
  tail[end - 1] = low & 0xff;
  for (let offset = 0; offset < end; offset += 64) {
    compress(h, w, tail, offset);
  }
  const digest = new Uint8Array(32);
  for (let i = 0; i < 8; i++) {
    const word = h[i] as number;
    digest[i * 4] = (word >>> 24) & 0xff;
    digest[i * 4 + 1] = (word >>> 16) & 0xff;
    digest[i * 4 + 2] = (word >>> 8) & 0xff;
    digest[i * 4 + 3] = word & 0xff;
  }
  return digest;
}

/** Renders bytes as lower-case hexadecimal, two digits per byte. */
export function toHex(bytes: Uint8Array): string {
  let out = '';
  for (let i = 0; i < bytes.length; i++) {
    out += (bytes[i] as number).toString(16).padStart(2, '0');
  }
  return out;
}

/** Computes the SHA-256 digest of a byte array and renders it as 64 lower-case hexadecimal digits. */
export function sha256Hex(input: Uint8Array): string {
  return toHex(sha256(input));
}
