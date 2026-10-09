// The time-zone name file spec/data/tz-names.txt (TYPE-34, TYPE-36, TYPE-80).
//
// TYPE-36 fixes the format of the file and requires a constant in spec/spec.md that names the pinned release. These
// tests always run.
//
// TYPE-80 requires the file to list exactly the Zone and Link names of the pinned release's main data files and
// `backward`, excluding Factory and `backzone`. The release files are not part of the repository and the tests open
// no network connection, so the comparison runs only when TIERWISE_TZDATA_DIR names a directory that holds the
// extracted release (for example tzdata2026e.tar.gz from https://data.iana.org/time-zones/releases/). The parser
// that the comparison uses is tested on a small fixture every time.
import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const SPEC = fileURLToPath(new URL('../../../spec/', import.meta.url));
const NAMES_FILE = join(SPEC, 'data', 'tz-names.txt');

/** The data files whose Zone and Link names TYPE-80 lists: the main files (the Makefile's YDATA) and backward. */
const RELEASE_FILES = ['africa', 'antarctica', 'asia', 'australasia', 'europe', 'northamerica', 'southamerica',
  'etcetera', 'backward'];

/** Compares two strings by code point, as TYPE-36 orders the names. */
function compareCodePoints(a, b) {
  const x = Array.from(a, (c) => c.codePointAt(0));
  const y = Array.from(b, (c) => c.codePointAt(0));
  for (let i = 0; i < Math.min(x.length, y.length); i++) {
    if (x[i] !== y[i]) return x[i] - y[i];
  }
  return x.length - y.length;
}

/** The names of the file, as the lines between its LF line endings. */
function readNames() {
  const text = new TextDecoder('utf-8', { fatal: true }).decode(readFileSync(NAMES_FILE));
  assert.ok(text.endsWith('\n'), 'every line ends with LF');
  return text.slice(0, -1).split('\n');
}

/** The release that a constant of spec/spec.md and the text of TYPE-80 name. */
function pinnedReleases() {
  const spec = readFileSync(join(SPEC, 'spec.md'), 'utf8');
  const constant = spec.match(/^- \*\*IANA tz database release:\*\* (\S+) \(\[TYPE-80\]\)$/m);
  const types = readFileSync(join(SPEC, 'sections', '04-types.md'), 'utf8');
  const clause = types.match(/^\*\*\[TYPE-80\]\*\* Spec version [0-9.]+ pins IANA tz database release `([^`]+)`/m);
  return { constant: constant?.[1], clause: clause?.[1] };
}

/**
 * Lists the Zone and Link names that tz source text declares: the second field of a Zone line and the third field
 * of a Link line, after comments are removed.
 */
function declaredNames(sourceText) {
  const names = [];
  for (const raw of sourceText.split('\n')) {
    const fields = raw.replace(/#.*/, '').trim().split(/\s+/);
    if (fields[0] === 'Zone' && fields.length > 1) names.push(fields[1]);
    else if (fields[0] === 'Link' && fields.length > 2) names.push(fields[2]);
  }
  return names;
}

/** The sorted, distinct Zone and Link names of an extracted release, without Factory. */
function releaseNames(dir) {
  const names = new Set();
  for (const file of RELEASE_FILES) {
    for (const name of declaredNames(readFileSync(join(dir, file), 'utf8'))) names.add(name);
  }
  names.delete('Factory');
  return [...names].sort(compareCodePoints);
}

test('TYPE-36: tz-names.txt is UTF-8 with LF line endings and no byte-order mark', () => {
  const bytes = readFileSync(NAMES_FILE);
  assert.ok(!(bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf), 'no byte-order mark');
  assert.ok(!bytes.includes(0x0d), 'no carriage return');
  assert.doesNotThrow(() => new TextDecoder('utf-8', { fatal: true }).decode(bytes));
});

test('TYPE-36: tz-names.txt has one name per line, with no comment and no blank line', () => {
  const names = readNames();
  assert.ok(names.length > 0);
  for (const [index, name] of names.entries()) {
    assert.notEqual(name, '', `line ${index + 1} is blank`);
    assert.ok(!name.startsWith('#'), `line ${index + 1} is a comment`);
    assert.match(name, /^[^\s#]+$/, `line ${index + 1} holds more than one name: ${JSON.stringify(name)}`);
  }
});

test('TYPE-36: the names of tz-names.txt are sorted by code point and distinct', () => {
  const names = readNames();
  for (let i = 1; i < names.length; i++) {
    assert.ok(compareCodePoints(names[i - 1], names[i]) < 0, `line ${i + 1}: ${names[i - 1]} then ${names[i]}`);
  }
});

test('TYPE-36: a constant in spec/spec.md names the pinned release, and TYPE-80 names the same one', () => {
  const { constant, clause } = pinnedReleases();
  assert.match(constant ?? '', /^[0-9]{4}[a-z]$/, 'spec/spec.md names the release as a constant');
  assert.equal(clause, constant);
});

test('TYPE-80: the parser lists Zone and Link names and ignores rules, comments and continuation lines', () => {
  const text = [
    '# Zone  Commented/Out  0:00 - UTC',
    'Rule\tX\t1970\tonly\t-\tJan\t1\t0:00\t0\t-',
    'Zone\tArea/First\t0:01 -\tLMT\t1900',
    '\t\t\t1:00\tX\tCE%sT',
    'Link\tArea/First\tArea/Alias\t# an alias',
    'Zone Etc/UTC 0 - UTC',
    'Zone\tFactory\t0\t-\t-00',
  ].join('\n');
  assert.deepEqual(declaredNames(text), ['Area/First', 'Area/Alias', 'Etc/UTC', 'Factory']);
  const dir = mkdtempSync(join(tmpdir(), 'tz-release-'));
  try {
    for (const file of RELEASE_FILES) writeFileSync(join(dir, file), file === 'etcetera' ? text : '');
    writeFileSync(join(dir, 'backzone'), 'Zone\tOnly/InBackzone\t0\t-\tUTC\n');
    assert.deepEqual(releaseNames(dir), ['Area/Alias', 'Area/First', 'Etc/UTC']);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

const releaseDir = process.env.TIERWISE_TZDATA_DIR;
test('TYPE-80: tz-names.txt lists exactly the Zone and Link names of the pinned release', {
  skip: releaseDir ? false : 'set TIERWISE_TZDATA_DIR to an extracted tzdata release to run this comparison',
}, () => {
  assert.ok(existsSync(join(releaseDir, 'version')), `${releaseDir} holds no tz release`);
  const version = readFileSync(join(releaseDir, 'version'), 'utf8').trim();
  assert.equal(version, pinnedReleases().constant, 'the release in TIERWISE_TZDATA_DIR is the pinned one');
  assert.deepEqual(readNames(), releaseNames(releaseDir));
});
