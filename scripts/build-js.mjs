import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const require = createRequire(join(root, 'js', 'package.json'));
const packages = ['evaluator', 'node', 'openfeature'];

function compiler() {
  let ts;
  try {
    ts = require(process.env.STRATA_TYPESCRIPT_PATH || 'typescript');
  } catch {
    throw new Error('TypeScript compiler unavailable. Use an existing TypeScript installation via STRATA_TYPESCRIPT_PATH, or approve adding a development dependency.');
  }
  const major = Number.parseInt(ts.version, 10);
  if (typeof ts.createProgram !== 'function' || !Number.isInteger(major) || major < 5) {
    throw new Error('A TypeScript 5+ compiler is required.');
  }
  return ts;
}

function checkDiagnostics(ts, diagnostics) {
  if (diagnostics.length === 0) return;
  const host = {
    getCanonicalFileName: (name) => name,
    getCurrentDirectory: () => root,
    getNewLine: () => '\n',
  };
  console.error(ts.formatDiagnosticsWithColorAndContext(diagnostics, host));
  throw new Error('TypeScript compilation failed.');
}

function configuration(ts, directory) {
  const path = join(directory, 'tsconfig.json');
  const config = ts.readConfigFile(path, ts.sys.readFile);
  checkDiagnostics(ts, config.error ? [config.error] : []);
  const parsed = ts.parseJsonConfigFileContent(config.config, ts.sys, directory);
  checkDiagnostics(ts, parsed.errors);
  if (parsed.options.strict !== true) throw new Error(`${path} must enable strict type checking.`);
  return parsed;
}

function emit(ts, config, options) {
  const program = ts.createProgram(config.fileNames, { ...config.options, ...options, noEmit: false });
  checkDiagnostics(ts, ts.getPreEmitDiagnostics(program));
  const result = program.emit();
  checkDiagnostics(ts, result.diagnostics);
  if (result.emitSkipped) throw new Error('TypeScript skipped build output.');
}

async function build(ts, name) {
  const directory = join(root, 'js', name);
  const config = configuration(ts, directory);
  const common = {
    rootDir: join(directory, 'src'),
    emitDeclarationOnly: false,
    noEmitOnError: true,
    sourceMap: false,
    declarationMap: false,
    incremental: false,
    composite: false,
  };
  emit(ts, config, {
    ...common,
    module: ts.ModuleKind.ES2020,
    moduleResolution: ts.ModuleResolutionKind.Bundler,
    outDir: join(directory, 'dist', 'esm'),
    declaration: true,
    declarationDir: join(directory, 'dist', 'types'),
  });
  emit(ts, config, {
    ...common,
    module: ts.ModuleKind.CommonJS,
    moduleResolution: ts.ModuleResolutionKind.Node10,
    ignoreDeprecations: Number(ts.version.split('.')[0]) >= 6 ? '6.0' : '5.0',
    outDir: join(directory, 'dist', 'cjs'),
    declaration: false,
    declarationDir: undefined,
  });
  const commonjs = join(directory, 'dist', 'cjs');
  await mkdir(commonjs, { recursive: true });
  await writeFile(join(commonjs, 'package.json'), '{"type":"commonjs"}\n');
  console.log(`${name}: strict ESM, CommonJS and declaration build passed.`);
}

try {
  const ts = compiler();
  console.log(`Using TypeScript ${ts.version}.`);
  for (const name of packages) await build(ts, name);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
