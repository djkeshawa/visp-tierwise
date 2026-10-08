// Genericity check (requirements §1.1, implementation plan 1.17).
//
// The engine must work for any domain, so no name declared in a sample project may
// appear in production code. This script reads every sample under samples/, collects
// the names the samples declare, and fails when one of them appears as a whole word
// in an identifier or string literal under java/**/src/main or js/{evaluator,node,
// openfeature}/src.
//
// Collected: levels, axes, node IDs (closed catalogs, the generated site catalog and
// the nodes used in values, bindings and examples), entity names, fact names and
// their parts, key names and their segments, feature names and their segments,
// policy and setting names, rule IDs, kind names, namespace names, enum members,
// set universe members and effects. Fact source names, object field names, naming
// pattern names (generic words such as key and feature) and free text are not
// collected. Names listed in genericity-allowlist.txt are skipped.
//
// The scan is lexical. Comments are blanked, unless SCAN_COMMENTS is true, and the
// rest of each file is matched as text. Only Node built-ins and yaml are used.
//
// Scope: requirements §1.1 and the brief limit the scan to identifiers and string
// literals, and AGENTS.md says "js/**/src", so every js package source folder is
// scanned. Decisions that need an ADR are listed in the review notes (OPEN).

import { readFileSync, readdirSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parse } from 'yaml';

const JS_ROOT = fileURLToPath(new URL('..', import.meta.url));
const REPO_ROOT = fileURLToPath(new URL('../..', import.meta.url));
const SAMPLES_DIR = join(REPO_ROOT, 'samples');
const ALLOWLIST = join(JS_ROOT, 'scripts', 'genericity-allowlist.txt');
// Directories walked to find production files, then filtered by PRODUCTION_FILES.
// AGENTS.md and requirements §1.1 say js/**/src, so every js package is covered.
const SCAN_ROOTS = ['java', 'js'];
const PRODUCTION_FILES = [/^java\/.*\/src\/main\//, /^js\/(.*\/)?src\//];
const SKIP_DIRS = new Set(['node_modules', 'target', 'dist']);
// OPEN: AGENTS.md bans domain names anywhere in java/**/src/main and js/**/src, while
// requirements §1.1 and the brief scan only identifiers and string literals. Set this
// to true to scan comments as well (stricter; the allow-list would then need more entries).
const SCAN_COMMENTS = false;

// Every name the samples declare. Filled by harvestFile, read by main.
const names = new Set();
// Scope names and root level names of the sample being read. Multi-node values files
// are read after the schema, because SRC-30 identifies a root section by its level name.
const scopeNames = new Set();
const rootLevels = new Set();
const laterReads = [];

// ---------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------

const isMap = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);
const asMap = (value) => (isMap(value) ? value : {});
const asArray = (value) => (Array.isArray(value) ? value : []);
const toPosix = (path) => path.split(sep).join('/');
const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

// A declared name is a string (SRC-11). The compiler reports a non-string list entry
// in a name position; this check fails closed instead of skipping it. Mapping keys
// reach this script already as text (yaml stringifies them), so they are collected as
// they are and the compiler's SRC-11 handling remains the place that rejects them.
function addName(value) {
  if (value === undefined || value === null) return;
  if (typeof value !== 'string') {
    throw new Error(`non-string name ${JSON.stringify(value)} in a name position; quote the value (SRC-11)`);
  }
  names.add(value);
}

// Each member of a list in a name position.
function addEach(list) {
  for (const value of asArray(list)) addName(value);
}

// A key, fact or feature name, and each of its dot-separated segments.
function addDotted(value) {
  addName(value);
  if (typeof value === 'string') value.split('.').forEach((part) => names.add(part));
}

// Literal members in a condition, a default or an example: enum members and effects.
// A "$name" is a reference to a setting, not a literal, so it is skipped.
function addLiterals(value) {
  if (typeof value === 'string') {
    if (!value.startsWith('$')) names.add(value);
  } else if (Array.isArray(value)) {
    value.forEach(addLiterals);
  } else if (isMap(value)) {
    Object.values(value).forEach(addLiterals);
  }
}

// ---------------------------------------------------------------------------
// Name collection: one harvester per kind of sample file
// ---------------------------------------------------------------------------

// Enum members and set universe members of a type declaration (TYPE-41, TYPE-42),
// also those of its items declaration.
function addTypeMembers(declaration) {
  const body = asMap(declaration);
  addEach(body.values);
  addEach(body.universe);
  if (isMap(body.items)) addTypeMembers(body.items);
}

// Shared schema members, also inline under a manifest's schema (MANIFEST-13).
function harvestSchema(doc) {
  const hierarchy = asMap(doc.decision_hierarchy);
  addEach(hierarchy.levels);
  addEach(hierarchy.axes);
  // The root level is the first level (requirements §1.1, SCOPE-13).
  if (typeof asArray(hierarchy.levels)[0] === 'string') rootLevels.add(hierarchy.levels[0]);
  for (const scope of [...asArray(hierarchy.levels), ...asArray(hierarchy.axes)]) {
    if (typeof scope === 'string') scopeNames.add(scope);
  }
  for (const [scope, value] of Object.entries(asMap(hierarchy.scopes))) {
    addName(scope);
    // Closed catalogs list their nodes. "open" and generated catalogs add none here;
    // the generated catalog is read as a JSON file (harvestFile).
    addEach(asMap(value).nodes);
  }
  for (const [entity, value] of Object.entries(asMap(doc.entity_hierarchy))) {
    addName(entity);
    addName(asMap(value).belongs_to);
    addName(asMap(value).has_many);
  }
  for (const [fact, value] of Object.entries(asMap(doc.facts))) {
    addDotted(fact);
    addTypeMembers(value);
  }
  for (const kind of Object.keys(asMap(doc.kinds))) addName(kind);
  for (const [key, value] of Object.entries(asMap(doc.keys))) {
    addDotted(key);
    addName(asMap(value).kind);
    addEach(asMap(value).settable_at);
    addTypeMembers(value);
  }
  for (const [feature, value] of Object.entries(asMap(doc.features))) {
    addDotted(feature);
    addEach(asMap(asMap(value).returns).enum);
    addEach(asMap(value).binding_settable_at);
  }
  addName(doc.namespace);
}

// A multi-node values map. A section named like the root level holds entries with no
// node ID (SRC-30). Every other declared scope maps node IDs to entries (SRC-29), and a
// node ID may be any string (SCOPE-33, SCOPE-34), so its keys are all collected. Only
// the node IDs are names here: values are authored data. Run after the schema is read.
function harvestValues(doc) {
  for (const [scope, value] of Object.entries(asMap(doc))) {
    if (rootLevels.has(scope) || !scopeNames.has(scope) || !isMap(value)) continue;
    for (const node of Object.keys(value)) addName(node);
  }
}

// A path-mapped values file is values/<scope>/<node>.yaml (SRC-22). Its scope and node
// come from the path, and the entries inside it are flat (SRC-25). A root values file
// is flat too (SRC-26). A flat file has no node IDs inside, so it adds nothing more.
// Any other file under values/ is a multi-node file (SRC-29) and is read later.
function harvestValuesFile(parts, doc) {
  if (parts.length === 3) {
    addName(parts[1]);
    addName(parts[2].replace(/\.ya?ml$/, ''));
  } else {
    laterReads.push(() => harvestValues(doc));
  }
}

// A manifest or a schema file. Namespaces are listed in the manifest. Inline schema
// and values are harvested too, for single-file projects.
function harvestDocument(doc) {
  harvestSchema(doc);
  for (const namespace of Object.keys(asMap(doc.namespaces))) addName(namespace);
  harvestSchema(asMap(doc.schema));
  laterReads.push(() => harvestValues(doc.values)); // Inline values are multi-node (MANIFEST-15).
}

// A binding's policy and its setting overrides. Setting overrides are literals.
function collectBinding(value) {
  const binding = asMap(value);
  addName(binding.policy);
  for (const [key, setting] of Object.entries(binding)) {
    if (key !== 'policy') addLiterals(setting);
  }
}

// A rules file: feature, policies, bindings and examples.
function harvestRules(doc) {
  addDotted(doc.feature);
  for (const [policy, value] of Object.entries(asMap(doc.policies))) {
    const body = asMap(value);
    addName(policy);
    addLiterals(body.default);
    for (const [setting, spec] of Object.entries(asMap(body.settings))) {
      addName(setting);
      addDotted(asMap(spec).list_of);
      addDotted(asMap(spec).value_of);
      addLiterals(asMap(spec).default);
    }
    for (const rule of asArray(body.rules)) {
      addName(asMap(rule).id);
      addLiterals(asMap(rule).then);
      for (const [fact, condition] of Object.entries(asMap(asMap(rule).when))) {
        addDotted(fact);
        addLiterals(condition);
      }
    }
  }
  for (const [scope, value] of Object.entries(asMap(doc.bindings))) {
    addName(scope);
    const level = asMap(value);
    if ('policy' in level) {
      collectBinding(level); // The root binding has no node ID (BIND-2).
    } else {
      for (const [node, binding] of Object.entries(level)) {
        addName(node);
        collectBinding(binding);
      }
    }
  }
  for (const example of asArray(doc.examples)) {
    const body = asMap(example);
    const context = asMap(body.context);
    for (const field of ['levels', 'axes']) {
      for (const [scope, node] of Object.entries(asMap(context[field]))) {
        addName(scope);
        addName(node);
      }
    }
    for (const [fact, value] of Object.entries(asMap(body.facts))) {
      addDotted(fact);
      addLiterals(value);
    }
    addLiterals(body.expect);
  }
}

// Sends one sample file to the harvester for its role. Roles come from the path for
// values files and from the content for rules files; anything else is a document.
function harvestFile(file, sampleDir) {
  const parts = toPosix(relative(sampleDir, file)).split('/');
  const text = readFileSync(file, 'utf8');
  if (file.endsWith('.json')) {
    addEach(asMap(JSON.parse(text)).nodes); // Generated node catalog (SCOPE-29).
    return;
  }
  const doc = asMap(parse(text));
  if (parts[0] === 'values') harvestValuesFile(parts, doc);
  else if ('policies' in doc || 'feature' in doc) harvestRules(doc);
  else harvestDocument(doc);
}

// ---------------------------------------------------------------------------
// Scanning production code
// ---------------------------------------------------------------------------

// Lists every file below dir, skipping build output and dependency folders.
function listFiles(dir) {
  const files = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name)) continue;
    const path = join(dir, entry.name);
    if (entry.isDirectory()) files.push(...listFiles(path));
    else if (entry.isFile()) files.push(path);
  }
  return files;
}

function productionFiles() {
  return SCAN_ROOTS.flatMap((root) => listFiles(join(REPO_ROOT, root)))
    .filter((file) => PRODUCTION_FILES.some((pattern) => pattern.test(toPosix(relative(REPO_ROOT, file)))));
}

// Index of the line end at or after start, or the text length.
function lineEnd(text, start) {
  const end = text.indexOf('\n', start);
  return end < 0 ? text.length : end;
}

// End index of the string literal that starts at start. A Java text block (""")
// runs to its closing delimiter. A single-line quote ends at the line end when it is
// unterminated, so one bad quote cannot hide the rest of the file.
function literalEnd(text, start) {
  const quote = text[start];
  const block = text.startsWith('"""', start);
  const delimiter = block ? '"""' : quote;
  const multiline = block || quote === '`';
  let j = start + delimiter.length;
  while (j < text.length) {
    if (text[j] === '\\') {
      j += 2;
    } else if (text.startsWith(delimiter, j)) {
      return j + delimiter.length;
    } else if (text[j] === '\n' && !multiline) {
      return j;
    } else {
      j += 1;
    }
  }
  return text.length;
}

// Blanks comments and keeps every newline, so line numbers stay correct. String
// literals are copied as they are. This lexer does not know regex literals, so a
// regex that contains a quote can mislead it; review such a file by hand.
function stripComments(text) {
  let out = '';
  let i = 0;
  while (i < text.length) {
    let end;
    let keep = true;
    if (text.startsWith('//', i)) {
      end = lineEnd(text, i);
      keep = false;
    } else if (text.startsWith('/*', i)) {
      const close = text.indexOf('*/', i + 2);
      end = close < 0 ? text.length : close + 2;
      keep = false;
    } else if (text[i] === '"' || text[i] === "'" || text[i] === '`') {
      end = literalEnd(text, i);
    } else {
      end = i + 1;
    }
    const chunk = text.slice(i, end);
    out += keep ? chunk : chunk.replace(/[^\n]/g, ' ');
    i = end;
  }
  return out;
}

// Names listed one per line. "#" starts a comment, and blank lines are ignored.
function readAllowlist(file) {
  return new Set(
    readFileSync(file, 'utf8')
      .split('\n')
      .map((line) => line.split('#')[0].trim())
      .filter(Boolean),
  );
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

function main() {
  const allow = readAllowlist(ALLOWLIST);

  // Each sample must declare names, so that a path mistake cannot pass vacuously.
  const samples = readdirSync(SAMPLES_DIR, { withFileTypes: true }).filter((entry) => entry.isDirectory());
  for (const sample of samples) {
    const sampleDir = join(SAMPLES_DIR, sample.name);
    const before = names.size;
    scopeNames.clear();
    rootLevels.clear();
    for (const file of listFiles(sampleDir).filter((path) => /\.(ya?ml|json)$/.test(path))) {
      harvestFile(file, sampleDir);
    }
    laterReads.splice(0).forEach((read) => read()); // Values need the levels of all schema files.
    if (names.size === before) throw new Error(`sample ${sample.name} declares no names`);
  }

  for (const name of allow) {
    if (!names.has(name)) console.warn(`genericity: allow-list entry is not a sample name: ${name}`);
  }
  const patterns = [...names]
    .filter((name) => !allow.has(name))
    .sort()
    .map((name) => ({ name, regex: new RegExp(`\\b${escapeRegExp(name)}\\b`) }));

  const files = productionFiles();
  if (files.length === 0) throw new Error('no production files found under java/**/src/main or js/*/src');

  const hits = [];
  for (const file of files) {
    const path = toPosix(relative(REPO_ROOT, file));
    const text = readFileSync(file, 'utf8');
    const code = SCAN_COMMENTS ? text : stripComments(text);
    code.split('\n').forEach((line, index) => {
      for (const { name, regex } of patterns) {
        if (regex.test(line)) hits.push(`${path}:${index + 1}: ${name}`);
      }
    });
  }

  if (hits.length > 0) {
    for (const hit of hits) console.log(hit);
    console.error(`Genericity check failed: ${hits.length} hit(s). Remove the name or allow-list it with a reason.`);
    process.exitCode = 1;
    return;
  }
  console.log(
    `Genericity check passed: ${patterns.length} names from ${samples.length} samples, ` +
      `${files.length} production files scanned, ${allow.size} allow-listed.`,
  );
}

try {
  main();
} catch (error) {
  console.error(`Genericity check failed: ${error.message}`);
  process.exitCode = 1;
}
