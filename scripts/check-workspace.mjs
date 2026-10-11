import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const root = new URL('../js/', import.meta.url);
const readJson = (path) => JSON.parse(readFileSync(new URL(path, root), 'utf8'));
const workspace = readJson('package.json');
assert.equal(workspace.private, true, 'The foundation workspace must be private.');
assert.match(workspace.packageManager, /^pnpm@\d+\.\d+\.\d+$/, 'Pin pnpm exactly.');
assert.equal(readJson('tsconfig.base.json').compilerOptions.strict, true);
for (const [name, version] of Object.entries(workspace.devDependencies ?? {})) {
  assert.match(version, /^\d+\.\d+\.\d+$/, `Pin ${name} to an exact version.`);
}

for (const name of ['evaluator', 'node', 'openfeature']) {
  const manifest = readJson(`${name}/package.json`);
  assert.equal(manifest.name, `@visplabs/${name}`);
  assert.equal(manifest.version, workspace.version);
  // Published packages: not private, public access, provenance (docs/release-process.md, step 9).
  assert.equal(manifest.private, undefined, `${manifest.name} must not be private: it is published.`);
  assert.equal(manifest.publishConfig?.access, 'public', `${manifest.name} must publish with public access.`);
  assert.equal(manifest.license, 'Apache-2.0');
  // The evaluator has zero runtime dependencies; the other packages depend only on it (requirements §3.2).
  const allowed = name === 'evaluator' ? {} : { '@visplabs/evaluator': 'workspace:*' };
  assert.deepEqual(manifest.dependencies ?? {}, allowed, `${manifest.name} has unapproved runtime dependencies.`);
  // Packages declare the spec and bundle-format versions they implement; placeholders declare none.
  for (const list of [manifest.tierwise.supportedSpecVersions, manifest.tierwise.supportedBundleFormatVersions]) {
    assert.ok(Array.isArray(list) && list.every((v) => typeof v === 'string'), 'Supported versions must be string arrays.');
  }
  assert.equal(readJson(`${name}/tsconfig.json`).extends, '../tsconfig.base.json');
  assert.equal(readJson(`${name}/tsconfig.cjs.json`).extends, './tsconfig.json');
  assert.deepEqual(manifest.devDependencies ?? {}, {}, 'Declare development tools once, at the workspace root.');
  console.log(`Checked ${manifest.name}: publishable, strict TypeScript, no runtime dependencies.`);
}
console.log(`Workspace metadata verified at ${fileURLToPath(root)}. Run pnpm build and pnpm test to compile and load the packages.`);
