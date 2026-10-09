// The one Node built-in that this package uses. The workspace has no @types/node, and this package may not add a
// dependency, so the declaration is written here. It is not emitted into dist/types, because no public type uses it.
declare module 'node:fs/promises' {
  /** Reads the whole file at the path. */
  export function readFile(path: string): Promise<Uint8Array>;
}
