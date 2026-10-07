// Marks a CommonJS output directory so Node loads it as CommonJS inside a "type": "module" package.
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const directory = process.argv[2];
if (!directory) throw new Error('usage: mark-cjs.mjs <output-directory>');
mkdirSync(directory, { recursive: true });
writeFileSync(join(directory, 'package.json'), '{"type":"commonjs"}\n');
