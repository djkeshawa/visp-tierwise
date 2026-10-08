// Checks the Phase 1 spec suite and writes spec/conformance/COVERAGE.md.
// Stages: (a) schemas compile, (b) presets and samples validate, (c) bundles are canonical and digested,
// (d) vectors validate (provenance cases also run), (e) cited clauses exist and are not OPEN,
// (f) coverage of normative clauses and engine codes, (g) code enums match the catalogue, (h) COVERAGE.md.
// Usage: node spec-tools/check.mjs [--strict] [--no-write]
// --no-write skips writing COVERAGE.md (the tests use it, so that they leave the repository unchanged).
// Exit 0: no problem. Exit 1: a problem, or with --strict a coverage gap. Exit 2: bad arguments.
import { existsSync, readFileSync, readdirSync, realpathSync, statSync, writeFileSync } from 'node:fs';
import { basename, dirname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { bundleBytes, bundleVersionOf, parseBundleJson, parseJsonStrict } from './lib/jcs.mjs';
import { loadYamlFile } from './lib/load.mjs';
import { createSchemas } from './lib/schemas.mjs';
import { readCatalogue, readClauses } from './lib/spec-index.mjs';

const ROOT = fileURLToPath(new URL('../../', import.meta.url));
const SPEC = join(ROOT, 'spec');
const CONFORMANCE = join(SPEC, 'conformance');
const BUNDLES = join(CONFORMANCE, 'bundles');
const INVALID = join(BUNDLES, 'invalid');
// A sidecar kept beside a bundle: <stem>.provenance.json or <stem>.provenance.<label>.json (BUNDLE-149).
// BUNDLE-149 says only "a lowercase label that the tool chooses", so any dot-free label without upper-case letters is accepted.
const SIDECAR_NAME = /\.provenance(?:\.[^./\\\sA-Z]+)?\.json$/;
// The codes that a bundle or evaluation may report for a bad bundle file (VEC-70).
const LOAD_CODES = ['E_BUNDLE_INTEGRITY', 'E_BUNDLE_UNSUPPORTED'];

const problems = [];
const gaps = [];
const lines = [];
const suiteGaps = []; // missing suite directories and empty families (VEC-1, VEC-71, VEC-131); also in `gaps`
const stats = { presets: 0, samples: 0, sourceFiles: 0, bundles: 0, invalid: 0, sidecars: 0, evaluate: 0, canonical: 0, compile: 0, provenance: 0 };

// Records one failure. Every failure makes the check exit 1, whichever stage found it.
function problem(message) {
  problems.push(message);
}

// Returns a path relative to the project root, with / as the separator.
function rel(path) {
  return relative(ROOT, path).split(sep).join('/');
}

// Returns a path relative to a base directory, with / as the separator.
function relUnder(base, path) {
  return relative(base, path).split(sep).join('/');
}

// True when target is base itself or lies under it, judged by resolved paths.
function isInside(base, target) {
  return target === base || target.startsWith(base + sep);
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// Lists the files under dir as sorted absolute paths. Hidden files are included (SRC-18 (d)), symbolic links are followed (SRC-18 (e)).
// `trail` holds the real paths of the directories being walked, so a link back to one of them (a cycle) is not entered again.
// A dangling link is skipped.
function listFilesUnder(dir, trail = new Set()) {
  if (!existsSync(dir)) return [];
  const real = realpathSync(dir);
  if (trail.has(real)) return [];
  trail.add(real);
  const files = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    let stat;
    try {
      stat = statSync(full);
    } catch {
      continue;
    }
    if (stat.isDirectory()) files.push(...listFilesUnder(full, trail));
    else if (stat.isFile()) files.push(full);
  }
  trail.delete(real);
  return files.sort();
}

// Lists the directories directly inside dir as sorted absolute paths. A dangling link is skipped, as in listFilesUnder.
function listDirs(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir)
    .map((entry) => join(dir, entry))
    .filter((full) => existsSync(full) && statSync(full).isDirectory())
    .sort();
}

// Lists the files directly inside dir whose names end with ext.
function listFiles(dir, ext) {
  return listFilesUnder(dir).filter((file) => dirname(file) === dir && file.endsWith(ext));
}

// Loads one YAML file under the source rules (options: see loadYaml). A failure is recorded as a problem, and the result is undefined.
function loadSource(file, options = {}) {
  try {
    return loadYamlFile(file, options);
  } catch (error) {
    problem(`${rel(file)}: ${error.code ?? 'load error'}: ${error.message}`);
    return undefined;
  }
}

// Returns true when value is a plain object (not an array, not null).
function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

// Returns the string items of a value that should be a list. Anything else gives an empty list.
function stringItems(value) {
  return Array.isArray(value) ? value.filter((item) => typeof item === 'string') : [];
}

// Compiles schemas for the checks. Returns null when they cannot be loaded, and the stages then skip.
function loadSchemas() {
  try {
    const schemas = createSchemas(join(SPEC, 'schemas'));
    for (const file of schemas.files) {
      try {
        schemas.validator(file);
      } catch (error) {
        problem(`schema ${file} does not compile: ${error.message}`);
      }
    }
    lines.push(`(a) schemas: ${schemas.files.length} compiled; ${schemas.notes.length} strict-mode notes (counted, not failures)`);
    return schemas;
  } catch (error) {
    problem(`schemas could not be loaded: ${error.message}`);
    return null;
  }
}

// Returns a compiled validator, or null when the schema is unavailable (stage (a) reports why).
function getValidator(schemas, file, pointer = '') {
  if (!schemas) return null;
  try {
    return schemas.validator(file, pointer);
  } catch {
    return null;
  }
}

// Returns the problems of data against one schema, or of one part of it.
function problemsOf(schemas, file, data, pointer = '') {
  const validate = getValidator(schemas, file, pointer);
  return validate ? schemas.problems(validate, data) : [];
}

// Records each validation problem of a file under the file's path.
function reportProblems(prefix, list) {
  for (const item of list) problem(`${prefix}: ${item}`);
}

// Matches path segments against a glob pattern: * within one segment, ** as whole directory segments (SRC-18 (b)).
// The last segment of a path is a file name, so ** never consumes it.
function matchSegments(pattern, segments) {
  if (pattern.length === 0) return segments.length === 0;
  const [head, ...rest] = pattern;
  if (head === '**') {
    return matchSegments(rest, segments) || (segments.length > 1 && matchSegments(pattern, segments.slice(1)));
  }
  if (segments.length === 0) return false;
  const segmentRegex = new RegExp('^' + head.split('*').map(escapeRegex).join('[^/]*') + '$');
  return segmentRegex.test(segments[0]) && matchSegments(rest, segments.slice(1));
}

// Returns the files under base that a glob pattern selects (SRC-18).
function globFiles(base, pattern) {
  const segments = pattern.split('/');
  return listFilesUnder(base).filter((file) => matchSegments(segments, relUnder(base, file).split('/')));
}

// Returns the files that a path pattern with {scope} and {node} selects (SRC-22, SRC-23).
// A malformed pattern is reported as E_SOURCE_MAPPING and selects nothing.
function pathMappedFiles(base, pattern) {
  if (typeof pattern !== 'string') return [];
  const placeholders = pattern.match(/\{[^}]*\}/g) ?? [];
  const wellFormed =
    placeholders.length === 2 &&
    placeholders.includes('{scope}') &&
    placeholders.includes('{node}') &&
    !/[*?[]/.test(pattern);
  const segments = pattern.split('/');
  const sameSegment = segments.some((segment) => segment.includes('{scope}') && segment.includes('{node}'));
  if (!wellFormed || sameSegment) {
    problem(`${rel(base)}: path pattern ${pattern} is malformed: E_SOURCE_MAPPING (SRC-23)`);
    return [];
  }
  const source = segments
    .map((segment) => segment.split(/(\{scope\}|\{node\})/).map((part) => (part.startsWith('{') ? '[^/]*' : escapeRegex(part))).join(''))
    .join('/');
  const regex = new RegExp('^' + source + '$');
  return listFilesUnder(base).filter((file) => regex.test(relUnder(base, file)));
}

// Resolves a file that the manifest names directly. It must exist and lie inside the project root (MANIFEST-12, SRC-17).
function directFile(base, path, what) {
  if (typeof path !== 'string') return null;
  const full = resolve(base, path);
  if (!existsSync(full) || !statSync(full).isFile()) {
    problem(`${rel(base)}: ${what} ${path} does not name a file: E_MANIFEST (SRC-17)`);
    return null;
  }
  if (!isInside(realpathSync(base), realpathSync(full))) {
    problem(`${rel(base)}: ${what} ${path} resolves outside the project root: E_MANIFEST (MANIFEST-12)`);
    return null;
  }
  return full;
}

// True when a file that a glob or path pattern selected resolves inside the project root once links are resolved.
// Otherwise it records E_MANIFEST (MANIFEST-12, SRC-18 (e)).
function insideRoot(base, file, what) {
  if (isInside(realpathSync(base), realpathSync(file))) return true;
  problem(`${rel(file)}: a file selected by ${what} resolves outside the project root: E_MANIFEST (MANIFEST-12)`);
  return false;
}

// Reads one catalog file named by `nodes: { from: ... }` and records its defects (SCOPE-27 to SCOPE-30).
function checkCatalog(base, from, owner) {
  const name = `${rel(owner)}: catalog ${from}`;
  const full = resolve(base, from);
  if (isAbsolute(from) || !existsSync(full) || !statSync(full).isFile() || !isInside(realpathSync(base), realpathSync(full))) {
    problem(`${name} is absolute, outside the project, missing or a directory: E_SCOPE_DECLARATION (SCOPE-28)`);
    return;
  }
  const bytes = readFileSync(full);
  let data;
  try {
    if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) throw new SyntaxError('begins with a byte-order mark');
    data = parseJsonStrict(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
  } catch (error) {
    const code = /duplicate member name/.test(error.message) ? 'E_DUPLICATE_KEY' : 'E_PARSE';
    problem(`${name}: ${code}: ${error.message} (SCOPE-30)`);
    return;
  }
  const shapeOk =
    isObject(data) && Object.keys(data).length === 1 && Array.isArray(data.nodes) && data.nodes.every((node) => typeof node === 'string');
  if (!shapeOk) problem(`${name} must be an object with one property, nodes, an array of strings: E_SCOPE_DECLARATION (SCOPE-29, SCOPE-30)`);
}

// Returns the `from` paths of the catalogs that a schema file names: `nodes: { from: <path> }` at any depth.
function catalogPaths(value, found = []) {
  if (Array.isArray(value)) value.forEach((item) => catalogPaths(item, found));
  else if (isObject(value)) {
    if (isObject(value.nodes) && typeof value.nodes.from === 'string') found.push(value.nodes.from);
    Object.values(value).forEach((item) => catalogPaths(item, found));
  }
  return found;
}

// Validates the presets and every sample project (stage (b)).
function checkPresetsAndSamples(schemas) {
  for (const file of listFiles(join(ROOT, 'presets'), '.yaml')) {
    stats.presets++;
    const data = loadSource(file);
    if (data !== undefined) reportProblems(rel(file), problemsOf(schemas, 'preset.schema.json', data));
  }
  for (const dir of listDirs(join(ROOT, 'samples'))) checkSample(dir, schemas);
}

// Validates one sample project: its manifest, and the schema, rules and values files that the manifest maps.
function checkSample(dir, schemas) {
  stats.samples++;
  const manifestPath = join(dir, 'tierwise.yaml');
  if (!existsSync(manifestPath)) {
    problem(`${rel(dir)}: missing tierwise.yaml: E_MANIFEST (MANIFEST-2)`);
    return;
  }
  const manifest = loadSource(manifestPath, { manifest: true });
  if (manifest === undefined) return;
  reportProblems(rel(manifestPath), problemsOf(schemas, 'manifest.schema.json', manifest));
  if (!isObject(manifest)) return;

  const sources = isObject(manifest.sources) ? manifest.sources : {};
  const schemaFiles = new Set();
  const ruleFiles = new Set();
  const valuesFiles = new Map(); // file -> flat: flat is true for the path-mapped and root shapes (SRC-25, SRC-26)

  // A file that a pattern selects must resolve inside the project root (MANIFEST-12, SRC-18 (e)).
  const selectGlob = (patterns, list, what) => {
    for (const pattern of stringItems(patterns)) {
      for (const file of globFiles(dir, pattern)) if (insideRoot(dir, file, what)) list.add(file);
    }
  };
  selectGlob(sources.schema, schemaFiles, 'sources.schema');
  selectGlob(sources.rules, ruleFiles, 'sources.rules');
  const selectValues = (file, flat) => {
    // A file selected by more than one sources.values item is E_SOURCE_MAPPING (SRC-34).
    if (valuesFiles.has(file)) problem(`${rel(file)}: selected by more than one sources.values item: E_SOURCE_MAPPING (SRC-34)`);
    valuesFiles.set(file, flat);
  };
  for (const entry of Array.isArray(sources.values) ? sources.values : []) {
    if (!isObject(entry)) continue;
    if ('pattern' in entry) {
      for (const file of pathMappedFiles(dir, entry.pattern)) if (insideRoot(dir, file, 'a path-mapped values pattern')) selectValues(file, true);
    } else if ('file' in entry) {
      const file = directFile(dir, entry.file, 'values file');
      if (file) selectValues(file, 'scope' in entry);
    }
  }
  if (isObject(manifest.namespaces)) {
    for (const entry of Object.values(manifest.namespaces)) {
      const file = isObject(entry) ? directFile(dir, entry.schema, 'namespace schema') : null;
      if (file) schemaFiles.add(file);
    }
  }
  // A file that entries of two different roles select is E_SOURCE_MAPPING (SRC-12, SRC-20 (a)).
  const roleOf = (file) => [schemaFiles.has(file) && 'schema', ruleFiles.has(file) && 'rules', valuesFiles.has(file) && 'values'].filter(Boolean);
  for (const file of new Set([...schemaFiles, ...ruleFiles, ...valuesFiles.keys()])) {
    const roles = roleOf(file);
    if (roles.length > 1) problem(`${rel(file)}: selected with more than one role (${roles.join(', ')}): E_SOURCE_MAPPING (SRC-20)`);
  }

  for (const file of schemaFiles) {
    const data = validateSourceFile(file, 'schema-file.schema.json', schemas);
    for (const from of catalogPaths(data)) checkCatalog(dir, from, file);
  }
  for (const file of ruleFiles) validateSourceFile(file, 'rules-file.schema.json', schemas);
  for (const [file, flat] of valuesFiles) {
    const pointer = flat ? '#/$defs/entries' : '#/$defs/multiNodeFile';
    validateSourceFile(file, 'values-file.schema.json', schemas, pointer);
  }
}

// Loads one source file of a sample and validates it against a schema (or one part of it). Returns the loaded data.
function validateSourceFile(file, schemaFile, schemas, pointer = '') {
  stats.sourceFiles++;
  const data = loadSource(file);
  if (data !== undefined) reportProblems(rel(file), problemsOf(schemas, schemaFile, data, pointer));
  return data;
}

// Reads a bundle file, checks it as a canonical bundle (stage (c)), and returns its envelope or undefined.
function checkBundle(file, schemas) {
  const name = rel(file);
  const bytes = readFileSync(file);
  if (bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf) {
    problem(`${name}: begins with a byte-order mark (BUNDLE-3)`);
    return undefined;
  }
  let text;
  let envelope;
  try {
    text = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
    envelope = parseBundleJson(text);
  } catch (error) {
    problem(`${name}: E_BUNDLE_INTEGRITY: ${error.message}`);
    return undefined;
  }
  reportProblems(name, problemsOf(schemas, 'bundle.schema.json', envelope));
  try {
    if (!bytes.equals(bundleBytes(envelope.bundleVersion, envelope.content))) {
      problem(`${name}: the bytes are not the canonical serialisation (BUNDLE-2, BUNDLE-4)`);
    }
    if (bundleVersionOf(envelope.content) !== envelope.bundleVersion) {
      problem(`${name}: bundleVersion is not the digest of content (BUNDLE-6)`);
    }
  } catch (error) {
    problem(`${name}: ${error.message}`);
  }
  return envelope;
}

// Returns the bundleVersion of a bundle file without reporting anything, or undefined when it is not a readable bundle.
function bundleVersionQuietly(file) {
  try {
    return parseBundleJson(new TextDecoder('utf-8', { fatal: true }).decode(readFileSync(file))).bundleVersion;
  } catch {
    return undefined;
  }
}

// True when a date-time string names a real calendar date (BUNDLE-141). The schema checks only the form.
function isRealDate(text) {
  const match = typeof text === 'string' ? text.match(/^(\d{4})-(\d{2})-(\d{2})T/) : null;
  if (!match) return false;
  const [year, month, day] = match.slice(1).map(Number);
  const date = new Date(0);
  date.setUTCFullYear(year, month - 1, day);
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

// Returns the reasons that a sidecar is rejected for a bundle with the given version, or [] when it is accepted (BUNDLE-151).
function sidecarReasons(file, bundleVersion, schemas) {
  let data;
  try {
    data = parseJsonStrict(readFileSync(file, 'utf8'));
  } catch (error) {
    return [`not strict JSON: ${error.message}`];
  }
  const reasons = problemsOf(schemas, 'provenance.schema.json', data);
  if (isObject(data) && data.bundleVersion !== bundleVersion) reasons.push('bundleVersion differs from the bundle (BUNDLE-144)');
  if (isObject(data) && !isRealDate(data.createdAt)) reasons.push('createdAt is not a real calendar date (BUNDLE-141)');
  return reasons;
}

// Checks every bundle under bundles/ (stage (c)), and every sidecar kept beside one.
function checkBundles(schemas) {
  stats.invalid = listFilesUnder(INVALID).filter((file) => file.endsWith('.json')).length;
  const files = listFilesUnder(BUNDLES).filter((file) => file.endsWith('.json') && !isInside(INVALID, file));
  const sidecars = [];
  for (const file of files) {
    if (basename(file).includes('.provenance')) {
      sidecars.push(file);
      continue;
    }
    stats.bundles++;
    checkBundle(file, schemas);
  }
  for (const file of sidecars) {
    stats.sidecars++;
    checkSidecarBeside(file, schemas);
  }
}

// Checks a sidecar that sits beside a bundle: its name (BUNDLE-149), the bundle it names, and its contents (BUNDLE-151).
function checkSidecarBeside(file, schemas) {
  const name = basename(file);
  const stem = name.slice(0, name.indexOf('.provenance'));
  const bundle = join(dirname(file), stem + '.json');
  if (!SIDECAR_NAME.test(name)) {
    problem(`${rel(file)}: the sidecar name does not follow BUNDLE-149: E_PROVENANCE_INVALID`);
    return;
  }
  const version = bundleVersionQuietly(bundle);
  if (version === undefined) {
    problem(`${rel(file)}: no readable bundle ${stem}.json beside it: E_PROVENANCE_INVALID (BUNDLE-149)`);
    return;
  }
  for (const reason of sidecarReasons(file, version, schemas)) problem(`${rel(file)}: ${reason}: E_PROVENANCE_INVALID`);
}

// Returns an error text when a bundle reference is not allowed, or undefined when it is.
// `invalid` tells the caller whether the reference names a file under bundles/invalid/.
function bundleReference(base, ref, allowInvalid) {
  if (typeof ref !== 'string' || ref === '') return { error: 'is not a path', invalid: false };
  const full = resolve(base, ref);
  if (!isInside(BUNDLES, full)) return { error: `${ref} must lie inside spec/conformance/bundles/ (VEC-23)`, invalid: false };
  const invalid = isInside(INVALID, full);
  if (invalid && !allowInvalid) return { error: `${ref} must not lie under bundles/invalid/ (VEC-93)`, invalid };
  if (!existsSync(full) || !statSync(full).isFile()) return { error: `${ref} does not name a file`, invalid };
  return { error: undefined, invalid };
}

// Returns the vector ID that a file at `path` must carry: its path under dir, without .yaml (VEC-12, VEC-127).
function expectedId(dir, file) {
  return relUnder(dir, file).replace(/\.yaml$/, '');
}

// Validates one vector file: its schema, its ID, and its ID's uniqueness (VEC-11, VEC-12). Returns its data or undefined.
function checkVectorFile(dir, file, schemaFile, schemas, seen, loadOptions = {}) {
  const data = loadSource(file, loadOptions);
  if (data === undefined) return undefined;
  reportProblems(rel(file), problemsOf(schemas, schemaFile, data));
  if (!isObject(data)) return undefined;
  if (data.id !== expectedId(dir, file)) problem(`${rel(file)}: id must equal the path without .yaml, found ${data.id} (VEC-12)`);
  if (seen.has(data.id)) problem(`${rel(file)}: id ${data.id} is repeated (VEC-11)`);
  seen.add(data.id);
  return data;
}

// Checks the evaluate, canonical, compile and provenance families (stage (d)). Each vector adds a record for stages (e) and (f).
function checkVectors(schemas, records) {
  const evaluateDir = join(CONFORMANCE, 'evaluate');
  const seenEvaluate = new Set();
  for (const file of listFilesUnder(evaluateDir).filter((f) => f.endsWith('.yaml'))) {
    stats.evaluate++;
    // An integer token in an evaluate vector's context, facts or provider values may not exceed 2^53 in magnitude (VEC-34).
    const data = checkVectorFile(evaluateDir, file, 'vector-evaluate.schema.json', schemas, seenEvaluate, { maxInteger: 2n ** 53n });
    if (!isObject(data)) continue;
    const ref = bundleReference(dirname(file), data.bundle, true);
    if (ref.error) problem(`${rel(file)}: bundle ${ref.error}`);
    if (ref.invalid && !LOAD_CODES.includes(data.expect?.error)) {
      problem(`${rel(file)}: uses an invalid bundle, so it must expect a load error, found ${data.expect?.error} (VEC-70)`);
    }
    records.push({ kind: 'evaluate', file: rel(file), clauses: data.clauses });
  }

  const canonicalDir = join(CONFORMANCE, 'canonical');
  const seenCanonical = new Set();
  for (const file of listFilesUnder(canonicalDir).filter((f) => f.endsWith('.yaml'))) {
    stats.canonical++;
    const data = checkVectorFile(canonicalDir, file, 'vector-canonical.schema.json', schemas, seenCanonical);
    if (isObject(data)) records.push({ kind: 'canonical', file: rel(file), clauses: data.clauses });
  }

  for (const dir of listDirs(join(CONFORMANCE, 'compile'))) checkCompileCase(dir, schemas, records);

  const provenanceDir = join(CONFORMANCE, 'provenance');
  const seenProvenance = new Set();
  for (const file of listFilesUnder(provenanceDir).filter((f) => f.endsWith('.yaml'))) {
    stats.provenance++;
    const data = checkVectorFile(provenanceDir, file, 'vector-provenance.schema.json', schemas, seenProvenance);
    if (!isObject(data)) continue;
    records.push({ kind: 'provenance', file: rel(file), clauses: data.clauses });
    checkProvenanceCase(file, data, schemas);
  }
}

// Checks one compile case directory (VEC-3 to VEC-5, VEC-13, VEC-90 to VEC-93) and adds its record.
function checkCompileCase(dir, schemas, records) {
  const expectedPath = join(dir, 'expected.yaml');
  stats.compile++;
  if (!existsSync(expectedPath)) {
    problem(`${rel(dir)}: missing expected.yaml (VEC-4)`);
    return;
  }
  const data = loadSource(expectedPath);
  if (!isObject(data)) return;
  reportProblems(rel(expectedPath), problemsOf(schemas, 'vector-compile.schema.json', data));
  if (data.id !== basename(dir)) problem(`${rel(expectedPath)}: id must equal the directory name, found ${data.id} (VEC-13)`);
  if (data.project !== undefined) {
    // The sample exception of VEC-91: the project is a directory inside samples/.
    const project = resolve(dir, String(data.project));
    if (!isInside(join(ROOT, 'samples'), project) || !existsSync(project) || !statSync(project).isDirectory()) {
      problem(`${rel(expectedPath)}: project ${data.project} must name a directory inside samples/ (VEC-91)`);
    }
    if (existsSync(join(dir, 'project'))) problem(`${rel(dir)}: a case that sets project must not contain a project/ directory (VEC-91)`);
  } else if (!existsSync(join(dir, 'project')) || !statSync(join(dir, 'project')).isDirectory()) {
    problem(`${rel(expectedPath)}: a case without project needs a project/ directory (VEC-5)`);
  }
  if (data.expect && data.expect.bundle !== undefined) {
    const ref = bundleReference(dir, data.expect.bundle, false);
    if (ref.error) problem(`${rel(expectedPath)}: expect.bundle ${ref.error}`);
  }
  const findings = Array.isArray(data.expect?.findings) ? data.expect.findings.map((finding) => finding.code) : [];
  records.push({ kind: 'compile', file: rel(expectedPath), clauses: data.clauses, findings });
}

// Runs one provenance case: the sidecar must be accepted or rejected as the case expects (VEC-129 to VEC-130).
// Sidecars under provenance/ are case fixtures, so the BUNDLE-149 name rule does not apply to them.
function checkProvenanceCase(file, data, schemas) {
  const ref = bundleReference(dirname(file), data.bundle, false);
  if (ref.error) {
    problem(`${rel(file)}: bundle ${ref.error}`);
    return;
  }
  const sidecarPath = resolve(dirname(file), String(data.sidecar));
  if (!isInside(join(CONFORMANCE, 'provenance'), sidecarPath) || !sidecarPath.endsWith('.json') || !existsSync(sidecarPath)) {
    problem(`${rel(file)}: sidecar ${data.sidecar} must be an existing .json file under provenance/ (VEC-128)`);
    return;
  }
  const version = bundleVersionQuietly(resolve(dirname(file), data.bundle));
  const reasons = version === undefined ? ['the bundle is not readable'] : sidecarReasons(sidecarPath, version, schemas);
  const accepted = reasons.length === 0;
  if (data.expect?.accept === true && !accepted) problem(`${rel(file)}: expected acceptance, but ${reasons.join('; ')} (VEC-129)`);
  if (data.expect?.error !== undefined && accepted) problem(`${rel(file)}: expected ${data.expect.error}, but the sidecar was accepted (VEC-129)`);
}

// Reads the enforcer map of spec/conformance/enforcers.yaml (VEC-125). Returns an empty map when it is missing.
function readEnforcers() {
  const file = join(CONFORMANCE, 'enforcers.yaml');
  const data = existsSync(file) ? loadSource(file) : {};
  if (!isObject(data)) {
    if (data !== undefined) problem(`${rel(file)}: must be a mapping from clause ID to enforcer`);
    return {};
  }
  for (const [id, text] of Object.entries(data)) {
    if (typeof text !== 'string' || text.trim() === '' || /[\n\r]/.test(text)) {
      problem(`${rel(file)}: the enforcer of ${id} must be a one-line, non-empty string (VEC-125)`);
    }
  }
  return data;
}

// Checks clause definitions and citations (stage (e)): every cited clause and enforcer key exists and is not OPEN.
function checkClauses(clauses, records, enforcers) {
  for (const clause of clauses.values()) {
    if (clause.duplicate) problem(`${clause.id} is defined twice (${clause.file})`);
  }
  for (const record of records) {
    for (const id of Array.isArray(record.clauses) ? record.clauses : []) {
      const clause = clauses.get(id);
      if (!clause) problem(`${record.file}: cites ${id}, which no section defines (VEC-16)`);
      else if (clause.open) problem(`${record.file}: cites ${id}, which is OPEN (VEC-16)`);
    }
  }
  for (const id of Object.keys(enforcers)) {
    const clause = clauses.get(id);
    if (!clause) problem(`spec/conformance/enforcers.yaml: ${id} is not a clause (VEC-125)`);
    else if (clause.open) problem(`spec/conformance/enforcers.yaml: ${id} is OPEN and is not normative (VEC-125)`);
  }
}

// Adds the catalogue's own problems to the failures (stage (g)).
function checkCatalogue(catalogue) {
  for (const message of catalogue.problems) problem(`spec/error-codes.md: ${message}`);
}

// Returns the enforcer text of a clause when it is a one-line, non-empty string, and null otherwise.
function validEnforcer(enforcers, id) {
  const text = Object.hasOwn(enforcers, id) ? enforcers[id] : undefined;
  return typeof text === 'string' && text.trim() !== '' && !/[\n\r]/.test(text) ? text : null;
}

// Computes coverage (stage (f)): which normative clauses vectors or enforcers cover, and which engine codes have a compile case.
function computeCoverage(clauses, records, enforcers, catalogue) {
  const citing = new Map();
  for (const record of records) {
    for (const id of Array.isArray(record.clauses) ? record.clauses : []) {
      if (!citing.has(id)) citing.set(id, []);
      citing.get(id).push(record.file);
    }
  }
  const normative = [...clauses.values()].filter((clause) => !clause.open);
  const rows = normative.map((clause) => ({
    id: clause.id,
    vectors: [...new Set(citing.get(clause.id) ?? [])].sort(),
    // An invalid entry is reported by readEnforcers, and here it counts as no enforcer.
    enforcer: validEnforcer(enforcers, clause.id),
  }));
  // Rows follow area order (first seen in the sections) and then clause number, because a section can define
  // a later clause before an earlier one (SRC-45 comes before SRC-7).
  const areaRank = new Map();
  for (const clause of clauses.values()) {
    const area = clause.id.replace(/-\d+$/, '');
    if (!areaRank.has(area)) areaRank.set(area, areaRank.size);
  }
  const numberOf = (id) => Number(id.replace(/^.*-/, ''));
  rows.sort((a, b) => {
    const areaA = areaRank.get(a.id.replace(/-\d+$/, ''));
    const areaB = areaRank.get(b.id.replace(/-\d+$/, ''));
    return areaA - areaB || numberOf(a.id) - numberOf(b.id);
  });
  for (const row of rows) {
    if (row.vectors.length === 0 && row.enforcer === null) gaps.push(`clause ${row.id} is uncovered`);
  }
  const compiled = new Set(records.filter((record) => record.kind === 'compile').flatMap((record) => record.findings));
  const codesWithoutCase = catalogue.engine.filter((code) => !compiled.has(code));
  for (const code of codesWithoutCase) gaps.push(`engine code ${code} has no compile case`);
  return {
    rows,
    normativeCount: normative.length,
    openCount: clauses.size - normative.length,
    citedCount: rows.filter((row) => row.vectors.length > 0).length,
    enforcedCount: rows.filter((row) => row.enforcer !== null).length,
    uncoveredCount: rows.filter((row) => row.vectors.length === 0 && row.enforcer === null).length,
    engineCodes: catalogue.engine,
    codesWithoutCase,
  };
}

// Records the suite directories and families that are absent or empty as gaps (stage (d)). They are fatal only with --strict,
// because other work items are still adding vectors. VEC-1 requires the six directories, VEC-71 the invalid bundle files,
// and VEC-131 the provenance family.
function checkLayout() {
  for (const dir of ['bundles', 'bundles/invalid', 'evaluate', 'canonical', 'compile', 'provenance']) {
    if (!existsSync(join(CONFORMANCE, dir))) suiteGaps.push(`the directory spec/conformance/${dir}/ is missing (VEC-1)`);
  }
  const families = [
    ['bundle files under bundles/', stats.bundles, 'VEC-9, VEC-105'],
    ['bundle files under bundles/invalid/', stats.invalid, 'VEC-71'],
    ['evaluate vectors', stats.evaluate, 'VEC-2'],
    ['canonical vectors', stats.canonical, 'VEC-2'],
    ['compile cases', stats.compile, 'VEC-3'],
    ['provenance cases', stats.provenance, 'VEC-131'],
  ];
  for (const [name, count, clause] of families) if (count === 0) suiteGaps.push(`no ${name} (${clause})`);
  gaps.push(...suiteGaps);
}

// Compares a schema enum with the catalogue group that it must equal (stage (g)).
function compareSet(name, actual, expected) {
  if (!Array.isArray(actual)) {
    problem(`${name}: the enum was not found (VEC-16, error-codes.md)`);
    return;
  }
  const seen = new Set();
  for (const code of actual) {
    if (seen.has(code)) problem(`${name}: ${code} is repeated`);
    seen.add(code);
  }
  for (const code of expected) if (!seen.has(code)) problem(`${name}: ${code} is in error-codes.md but not in the enum`);
  for (const code of seen) if (!expected.includes(code)) problem(`${name}: ${code} is in the enum but not in error-codes.md`);
}

// Checks that the code enums of the vector schemas equal the catalogue (stage (g)).
function checkEnums(catalogue) {
  const compileSchema = parseJsonStrict(readFileSync(join(SPEC, 'schemas', 'vector-compile.schema.json'), 'utf8'));
  const evaluateSchema = parseJsonStrict(readFileSync(join(SPEC, 'schemas', 'vector-evaluate.schema.json'), 'utf8'));
  const codeAnyOf = compileSchema.$defs?.finding?.properties?.code?.anyOf;
  compareSet('vector-compile.schema.json engine codes', codeAnyOf?.[0]?.enum, catalogue.engine);
  compareSet('vector-compile.schema.json lint identifiers', codeAnyOf?.[1]?.enum, catalogue.lint);
  compareSet('vector-evaluate.schema.json error codes', evaluateSchema.$defs?.errorCode?.enum, catalogue.evaluation);
}

// Renders COVERAGE.md (stage (h)). The output is deterministic, so a rerun with the same inputs changes nothing.
function renderCoverage(coverage) {
  const out = [];
  out.push('# Conformance coverage', '');
  out.push('Generated by `pnpm spec:check` (`js/spec-tools/check.mjs`). Do not edit by hand.', '');
  out.push(`- Normative clauses: ${coverage.normativeCount} (OPEN clauses excluded: ${coverage.openCount}).`);
  out.push(`- Cited by a conformance vector: ${coverage.citedCount}.`);
  out.push(`- Listed with an enforcer in enforcers.yaml: ${coverage.enforcedCount} (an entry that says "Planned" names a test that is not written yet).`);
  out.push(`- Uncovered: ${coverage.uncoveredCount}.`);
  out.push(`- Engine error codes: ${coverage.engineCodes.length}. Without a compile case: ${coverage.codesWithoutCase.length}.`, '');

  out.push('## Suite gaps', '');
  if (suiteGaps.length === 0) out.push('None.', '');
  for (const gap of suiteGaps) out.push(`- ${gap}`);
  if (suiteGaps.length > 0) out.push('');

  out.push('## Uncovered clauses', '');
  const uncovered = coverage.rows.filter((row) => row.vectors.length === 0 && row.enforcer === null);
  if (uncovered.length === 0) out.push('None.', '');
  for (const area of groupBy(uncovered)) out.push(`- ${area.area}: ${area.ids.join(', ')}`);
  if (uncovered.length > 0) out.push('');

  out.push('## Engine codes without a compile case', '');
  if (coverage.codesWithoutCase.length === 0) out.push('None.', '');
  for (const code of coverage.codesWithoutCase) out.push(`- ${code}`);
  if (coverage.codesWithoutCase.length > 0) out.push('');

  out.push('## Clauses', '');
  const areaOf = (id) => id.replace(/-\d+$/, '');
  coverage.rows.forEach((row, index) => {
    const area = areaOf(row.id);
    if (index === 0 || areaOf(coverage.rows[index - 1].id) !== area) {
      out.push(`### ${area}`, '', '| Clause | Vectors | Enforcer |', '| --- | --- | --- |');
    }
    const vectors = row.vectors.length > 0 ? row.vectors.map((file) => '`' + file.replace(/^spec\/conformance\//, '') + '`').join('<br>') : '-';
    const enforcer = row.enforcer === null ? '-' : row.enforcer.replace(/\|/g, '\\|');
    out.push(`| ${row.id} | ${vectors} | ${enforcer} |`);
    const nextArea = index + 1 < coverage.rows.length ? areaOf(coverage.rows[index + 1].id) : null;
    if (nextArea !== area) out.push('');
  });
  return out.join('\n') + '\n';
}

// Groups uncovered rows by clause area, in spec order.
function groupBy(rows) {
  const groups = [];
  for (const row of rows) {
    const area = row.id.replace(/-\d+$/, '');
    const last = groups[groups.length - 1];
    if (last && last.area === area) last.ids.push(row.id);
    else groups.push({ area, ids: [row.id] });
  }
  return groups;
}

// Runs every stage and returns the exit code.
function main(argv) {
  // pnpm can pass a bare "--" through to the script, so it is ignored.
  const args = argv.filter((arg) => arg !== '--');
  const strict = args.includes('--strict');
  const write = !args.includes('--no-write');
  if (args.some((arg) => arg !== '--strict' && arg !== '--no-write')) {
    console.error('usage: node spec-tools/check.mjs [--strict] [--no-write]');
    return 2;
  }

  const schemas = loadSchemas();
  if (schemas) {
    checkPresetsAndSamples(schemas);
    lines.push(`(b) presets: ${stats.presets}; samples: ${stats.samples} (${stats.sourceFiles} source files checked)`);
    checkBundles(schemas);
    lines.push(`(c) bundles: ${stats.bundles}; sidecars beside bundles: ${stats.sidecars}`);
  }

  const records = [];
  if (schemas) checkVectors(schemas, records);
  lines.push(
    `(d) vectors: evaluate ${stats.evaluate}, canonical ${stats.canonical}, compile ${stats.compile}, provenance ${stats.provenance}`,
  );

  checkLayout();
  const clauses = readClauses(join(SPEC, 'sections'));
  const enforcers = readEnforcers();
  checkClauses(clauses, records, enforcers);
  lines.push(`(e) clause citations checked: ${records.reduce((sum, record) => sum + (record.clauses?.length ?? 0), 0)}`);

  const catalogue = readCatalogue(readFileSync(join(SPEC, 'error-codes.md'), 'utf8'));
  checkCatalogue(catalogue);
  const coverage = computeCoverage(clauses, records, enforcers, catalogue);
  lines.push(
    `(f) coverage: ${coverage.normativeCount} normative clauses; ${coverage.citedCount} cited; ${coverage.enforcedCount} by enforcer; ` +
      `${coverage.uncoveredCount} uncovered; engine codes with a compile case: ${coverage.engineCodes.length - coverage.codesWithoutCase.length} of ${coverage.engineCodes.length}`,
  );

  checkEnums(catalogue);
  lines.push(`(g) code enums: checked against error-codes.md (${catalogue.engine.length} engine, ${catalogue.lint.length} lint, ${catalogue.evaluation.length} evaluation)`);

  const coveragePath = join(CONFORMANCE, 'COVERAGE.md');
  if (write) writeFileSync(coveragePath, renderCoverage(coverage));
  lines.push(write ? `(h) wrote ${rel(coveragePath)}` : '(h) COVERAGE.md not written (--no-write)');

  for (const line of lines) console.log(line);
  for (const message of problems) console.log(`FAIL ${message}`);
  for (const message of suiteGaps) console.log(`gap: ${message}`);
  if (gaps.length > 0) {
    console.log(`coverage gaps: ${gaps.length}${strict ? ' (fatal with --strict)' : ' (fatal only with --strict)'}; see ${rel(coveragePath)}`);
  }
  const failed = problems.length > 0 || (strict && gaps.length > 0);
  console.log(failed ? `check failed: ${problems.length} problem(s)${strict && gaps.length > 0 ? `, ${gaps.length} gap(s)` : ''}` : 'check passed');
  return failed ? 1 : 0;
}

process.exitCode = main(process.argv.slice(2));
