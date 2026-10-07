import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = new URL('../js/', import.meta.url);
const readJson = (path) => JSON.parse(readFileSync(new URL(path, root), 'utf8'));
const workspace = readJson('package.json');
assert.equal(workspace.private, true, 'The foundation workspace must be private.');
assert.match(workspace.packageManager, /^pnpm@\d+\.\d+\.\d+$/, 'Pin pnpm exactly.');
assert.equal(readJson('tsconfig.base.json').compilerOptions.strict, true);

for (const name of ['evaluator', 'node', 'openfeature']) {
  const manifest = readJson(`${name}/package.json`);
  assert.equal(manifest.name, `@tierwise/${name}`);
  assert.equal(manifest.version, workspace.version);
  assert.equal(manifest.private, true, 'Phase 0 packages must not be publishable.');
  assert.equal(manifest.license, 'Apache-2.0');
  assert.deepEqual(manifest.dependencies ?? {}, {}, 'Phase 0 adds no runtime dependencies.');
  assert.deepEqual(manifest.tierwise.supportedSpecVersions, []);
  assert.deepEqual(manifest.tierwise.supportedBundleFormatVersions, []);
  assert.equal(readJson(`${name}/tsconfig.json`).extends, '../tsconfig.base.json');
  console.log(`Checked ${manifest.name}: private, strict TypeScript, no runtime dependencies.`);
}
console.log(`Workspace metadata verified at ${fileURLToPath(root)}. This is not a compiler check.`);
