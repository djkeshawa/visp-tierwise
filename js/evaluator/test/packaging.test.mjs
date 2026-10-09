// Packaging and portability of the built package: ESM and CommonJS both load and agree, the built ESM imports
// nothing from outside the package (zero runtime dependencies), no Node-only API is used, and the package runs in
// a context that has only the globals of a browser.
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { test } from 'node:test';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import { api, bundleBytes, cjsEntry, distDir } from './support.mjs';
import { content } from './fixtures.mjs';

const srcDir = fileURLToPath(new URL('../src/', import.meta.url));
const cjsDir = dirname(cjsEntry);
const manifest = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8'));

const files = (dir, extension) => readdirSync(dir).filter((name) => name.endsWith(extension)).sort();

/** Removes comments and string-free noise so that a scan sees code only. */
function stripComments(source) {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:'"`\\])\/\/.*$/gm, '$1');
}

/** The module specifiers that a built file imports: static imports, re-exports, dynamic imports and requires. */
function specifiers(source) {
  const found = [];
  const code = stripComments(source);
  for (const match of code.matchAll(/(?:^|[;\s}])(?:import|export)\s[^'"`;]*?\sfrom\s*['"]([^'"]+)['"]/g)) found.push(match[1]);
  for (const match of code.matchAll(/(?:^|[;\s}])import\s*['"]([^'"]+)['"]/g)) found.push(match[1]);
  for (const match of code.matchAll(/\bimport\s*\(\s*([^)]*)\)/g)) found.push(`dynamic import(${match[1]})`);
  for (const match of code.matchAll(/\brequire\s*\(\s*([^)]*)\)/g)) found.push(match[1].replace(/^['"]|['"]$/g, ''));
  return found;
}

test('the built ESM imports no module from outside the package', () => {
  const names = files(join(distDir), '.js');
  assert.ok(names.length > 5, 'the build output is present');
  for (const name of names) {
    for (const specifier of specifiers(readFileSync(join(distDir, name), 'utf8'))) {
      assert.match(specifier, /^\.\/[a-z0-9]+\.js$/, `${name} imports ${specifier}`);
      assert.ok(existsSync(resolve(distDir, specifier)), `${name} imports ${specifier}, which is not in the package`);
    }
  }
});

test('the built ESM has no dynamic import and no require', () => {
  for (const name of files(distDir, '.js')) {
    const code = stripComments(readFileSync(join(distDir, name), 'utf8'));
    assert.ok(!/\bimport\s*\(/.test(code), name);
    assert.ok(!/\brequire\s*\(/.test(code), name);
  }
});

test('the built CommonJS requires only files of the package', () => {
  for (const name of files(cjsDir, '.js')) {
    for (const specifier of specifiers(readFileSync(join(cjsDir, name), 'utf8'))) {
      assert.match(specifier, /^\.\/[a-z0-9]+\.js$/, `${name} requires ${specifier}`);
      assert.ok(existsSync(resolve(cjsDir, specifier)), `${name} requires ${specifier}`);
    }
  }
});

test('the declarations import nothing from outside the package', () => {
  const typesDir = join(distDir, '..', 'types');
  for (const name of files(typesDir, '.d.ts')) {
    for (const match of readFileSync(join(typesDir, name), 'utf8').matchAll(/\bfrom\s*['"]([^'"]+)['"]/g)) {
      assert.match(match[1], /^\.\/[a-z0-9]+\.js$/, `${name} imports ${match[1]}`);
    }
  }
});

test('the source uses no Node-only API (the package must run in a browser)', () => {
  for (const name of files(srcDir, '.ts')) {
    const code = stripComments(readFileSync(join(srcDir, name), 'utf8'));
    for (const forbidden of [/\bprocess\b/, /\bBuffer\b/, /\brequire\s*\(/, /\b__dirname\b/, /\b__filename\b/, /['"`]node:/, /\bfs\b/, /\bcrypto\b/, /\bglobal\b/, /\bfetch\b/]) {
      assert.ok(!forbidden.test(code), `${name} uses ${forbidden}`);
    }
    assert.ok(!/^\s*import[^;]*from\s*['"](?!\.\/)/m.test(code), `${name} imports a module outside src/`);
  }
});

test('the package declares no runtime dependency, and a side-effect-free ESM entry', () => {
  assert.deepEqual(manifest.dependencies ?? {}, {});
  assert.deepEqual(manifest.peerDependencies ?? {}, {});
  assert.deepEqual(manifest.optionalDependencies ?? {}, {});
  assert.equal(manifest.sideEffects, false);
  assert.equal(manifest.type, 'module');
});

test('importing the package does not change the globals', async () => {
  const before = Object.getOwnPropertyNames(globalThis).sort();
  await import(new URL('index.js?again', new URL('../dist/esm/', import.meta.url)).href);
  assert.deepEqual(Object.getOwnPropertyNames(globalThis).sort(), before);
});

test('CommonJS loads through the exports map and gives the same results as ESM', async () => {
  const require = createRequire(import.meta.url);
  const cjs = require(cjsEntry);
  assert.deepEqual(Object.keys(cjs).sort(), Object.keys(api).sort());
  const bytes = bundleBytes(content());
  const run = async (module) => {
    const tw = module.createEvaluator(module.loadBundle(bytes), { providers: { low: { fn: async () => ({ 'k.min': 4 }), timeoutMs: 50 } } });
    const context = { levels: { mid: 'm1', low: 'n' }, axes: { lane: 'l1' } };
    return JSON.stringify([await tw.resolve(context, 'k.over'), await tw.resolve(context, 'k.min'), tw.decideSync(context, 'f.one', {})]);
  };
  assert.equal(await run(cjs), await run(api));
  assert.ok(new cjs.TierwiseError('E_UNKNOWN_KEY', 'x') instanceof Error);
  assert.equal(new cjs.TierwiseError('E_UNKNOWN_KEY', 'x').code, 'E_UNKNOWN_KEY');
});

test('the package runs in a context that has only the globals of a browser', async () => {
  // A context with no process, Buffer, require or module. The CommonJS build is run in it with a loader that
  // resolves only the files of the package.
  const sandbox = vm.createContext({ TextEncoder, TextDecoder, setTimeout, clearTimeout });
  const modules = new Map();
  const load = (path) => {
    if (!modules.has(path)) {
      const module = { exports: {} };
      modules.set(path, module);
      const require = (specifier) => {
        assert.match(specifier, /^\.\/[a-z0-9]+\.js$/);
        return load(resolve(dirname(path), specifier));
      };
      const wrapper = vm.runInContext(`(function (exports, require, module) {${readFileSync(path, 'utf8')}\n})`, sandbox, { filename: path });
      wrapper(module.exports, require, module);
    }
    return modules.get(path).exports;
  };
  assert.equal(vm.runInContext('typeof process + typeof Buffer + typeof require + typeof module', sandbox), 'undefinedundefinedundefinedundefined');
  const browser = load(cjsEntry);
  const bytes = bundleBytes(content());
  const tw = browser.createEvaluator(browser.loadBundle(bytes), { providers: { low: { fn: async () => ({ 'k.min': 4 }), timeoutMs: 50 } } });
  const context = { levels: { mid: 'm1', low: 'n' } };
  assert.equal((await tw.resolve(context, 'k.min')).value, 4);
  assert.equal(tw.decideSync({}, 'f.one', { facts: { 'parent.flag': true, 'whole.kind': 'a' }, children: { part: [{ 'part.tag': 'x' }] } }).effect, 'ALLOW');
  assert.equal(browser.loadBundle(new TextDecoder().decode(bytes)).bundleVersion, api.loadBundle(bytes).bundleVersion);
  assert.throws(() => browser.loadBundle(new Uint8Array(3)), (error) => error.code === 'E_BUNDLE_INTEGRITY');
});
