// Reads the bytes of a startup snapshot. A path is read from disk; bytes are returned as they were given.

import { readFile } from 'node:fs/promises';
import type { SnapshotSource } from './types.js';

/**
 * The bytes of a snapshot. The result is passed to loadBundle, which checks it.
 *
 * @throws the file system error when the file cannot be read
 */
export async function readSnapshot(source: SnapshotSource): Promise<Uint8Array | ArrayBuffer | string> {
  if ('path' in source) return readFile(source.path);
  return source.bytes;
}
