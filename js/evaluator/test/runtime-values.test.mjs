// Runtime values and the codes of section 4: an evaluator checks an iana_tz value by its syntax only and never
// consults time-zone data (TYPE-37, TYPE-38), and a value received at runtime is never reported with a code of
// section 4 (TYPE-73). A fact value is handled as FACT-35 defines, a provider value as SVC-31 and SVC-34 define.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { api, codeOf, distDir, listFiles, loadContent } from './support.mjs';
import { changed } from './fixtures.mjs';

const { createEvaluator, TierwiseError } = api;

/** The codes of section 4 (types). They apply to source values only. */
const SECTION_4_CODES = [
  'E_TYPE_REQUIRED',
  'E_INVALID_DECLARATION',
  'E_TYPE_MISMATCH',
  'E_INTEGER_RANGE',
  'E_OUT_OF_RANGE',
  'E_NOT_IN_UNIVERSE',
  'E_DUPLICATE_VALUE',
];

// The base content with a time-zone fact and a time-zone key. The zone at m2 is well formed and listed in no tz
// database, which an evaluator cannot tell, because it consults none.
const loaded = loadContent(
  changed((content) => {
    content.facts['whole.zone'] = { entity: 'whole', type: { type: 'string', format: 'iana_tz' } };
    content.keys['k.zone'] = {
      type: { type: 'string', format: 'iana_tz' },
      combine: 'override',
      default: 'Etc/UTC',
      settableAt: ['top', 'mid', 'low'],
      onSourceFailure: 'skip',
    };
    content.values.mid.m2['k.zone'] = 'Mars/Olympus_Mons';
  }),
);

function evaluator(reply) {
  const events = [];
  const tw = createEvaluator(loaded, {
    providers: { low: { fn: async () => reply, timeoutMs: 200 } },
    listener: (event) => events.push(event),
  });
  return { tw, events };
}

/** Replaces the time-zone facilities of the platform with ones that fail, runs `body`, then restores them. */
async function withoutTimeZoneData(body) {
  const replaced = [
    [Intl, 'DateTimeFormat'],
    [Intl, 'supportedValuesOf'],
    [Date.prototype, 'getTimezoneOffset'],
    [Date.prototype, 'toLocaleString'],
    [Date.prototype, 'toLocaleDateString'],
    [Date.prototype, 'toLocaleTimeString'],
    [Date.prototype, 'toString'],
    [Date.prototype, 'getHours'],
  ].map(([owner, name]) => [owner, name, Object.getOwnPropertyDescriptor(owner, name)]);
  for (const [owner, name] of replaced) {
    Object.defineProperty(owner, name, {
      configurable: true,
      writable: true,
      value: () => {
        throw new Error(`time-zone data consulted through ${name} (TYPE-38)`);
      },
    });
  }
  try {
    await body();
  } finally {
    for (const [owner, name, descriptor] of replaced) Object.defineProperty(owner, name, descriptor);
  }
}

test('TYPE-38: an iana_tz value is checked by its syntax alone, and no time-zone data is consulted', async () => {
  await withoutTimeZoneData(async () => {
    const base = { 'parent.flag': true, 'whole.kind': 'a' };
    const children = { part: [{ 'part.tag': 'x' }] };
    const facts = (zone) => ({ facts: { ...base, 'whole.zone': zone }, children });
    const { tw } = evaluator({ 'k.zone': 'Atlantis/Lost_City' });
    const without = tw.decideSync({}, 'f.one', { facts: base, children });
    assert.equal(without.effect, 'ALLOW');
    for (const zone of ['Mars/Olympus_Mons', 'Europe/London', 'Etc/GMT+99', 'A', 'x_y/z-1/W+2']) {
      assert.deepEqual(tw.decideSync({}, 'f.one', facts(zone)), without, zone);
    }
    for (const zone of ['', 'Europe London', '/Europe', 'Europe/', 'Europe//London', 'Zürich', 'UTC+01:00']) {
      assert.equal(codeOf(() => tw.decideSync({}, 'f.one', facts(zone))), 'E_FACTS_INVALID', zone);
    }
    assert.equal(tw.resolveSync({ levels: { mid: 'm2' } }, 'k.zone').value, 'Mars/Olympus_Mons');
    const fromProvider = await tw.resolve({ levels: { mid: 'm2', low: 'n1' } }, 'k.zone');
    assert.equal(fromProvider.value, 'Atlantis/Lost_City', 'a well-formed zone from a provider is a valid value');
    const { tw: invalid } = evaluator({ 'k.zone': 'Europe London' });
    const failed = await invalid.resolve({ levels: { mid: 'm2', low: 'n1' } }, 'k.zone');
    assert.equal(failed.value, 'Mars/Olympus_Mons');
    assert.equal(failed.contributions.at(-1).effect, 'INVALID');
  });
});

test('TYPE-38: the evaluator code names no time-zone facility (code review, over the shipped output)', () => {
  const forbidden = /\bIntl\b|getTimezoneOffset|toLocale\w*String|timeZone|tz-names|supportedValuesOf/;
  for (const file of listFiles(distDir, '.js')) {
    const source = readFileSync(file, 'utf8');
    assert.ok(!forbidden.test(source), `${file} names a time-zone facility: ${source.match(forbidden)?.[0]}`);
  }
  assert.ok(listFiles(distDir, '.js').length > 5, 'the build output was found');
});

test('TYPE-73: a fact value that fails validation raises E_FACTS_INVALID and never a code of section 4', () => {
  const { tw } = evaluator({});
  const facts = [
    { 'whole.count': 2 ** 53 },
    { 'whole.count': -(2 ** 53) },
    { 'whole.count': 1.5 },
    { 'whole.count': '7' },
    { 'whole.kind': 'c' },
    { 'whole.price': 1.5 },
    { 'whole.price': 'abc' },
    { 'whole.note': 7 },
    { 'whole.note': 'unpaired \uD800' },
    { 'parent.flag': 'true' },
    { 'whole.zone': 'not a zone' },
  ];
  for (const set of facts) {
    const code = codeOf(() => tw.decideSync({}, 'f.one', { facts: set }));
    assert.equal(code, 'E_FACTS_INVALID', JSON.stringify(set));
  }
  for (const part of [{ 'part.qty': 1001 }, { 'part.qty': -1 }, { 'part.tag': 'w' }, { 'part.tag': ['x'] }]) {
    const code = codeOf(() => tw.decideSync({}, 'f.one', { children: { part: [part] } }));
    assert.equal(code, 'E_FACTS_INVALID', JSON.stringify(part));
  }
});

test('TYPE-73: a provider value that fails validation is an INVALID entry and is never reported with a code of section 4', async () => {
  const replies = [
    { 'k.min': 1000 }, // above the declared max
    { 'k.min': -1 }, // below the declared min
    { 'k.min': 2 ** 53 }, // outside the integer range
    { 'k.min': 1.5 },
    { 'k.min': '7' },
    { 'k.inter': ['u9'] }, // not in the universe
    { 'k.inter': ['u1', 'u1'] }, // a repeated member
    { 'k.inter': 'u1' },
    { 'k.deep': { a: 'x' } },
    { 'k.deep': { z: 1 } }, // an unknown field
    { 'k.all': null },
    { 'k.text': 5 },
    { 'k.zone': 'not a zone' },
  ];
  for (const reply of replies) {
    const [key] = Object.keys(reply);
    const { tw, events } = evaluator(reply);
    let result;
    try {
      result = await tw.resolve({ levels: { mid: 'm1', low: 'n1' } }, key);
    } catch (error) {
      assert.ok(!(error instanceof TierwiseError) || !SECTION_4_CODES.includes(error.code), `${key} raised ${error.code}`);
      throw error;
    }
    const entry = result.contributions.find((contribution) => contribution.name === 'low');
    assert.equal(entry?.effect, 'INVALID', JSON.stringify(reply));
    assert.ok(events.some((event) => event.type === 'SOURCE_INVALID'), JSON.stringify(reply));
    const reported = JSON.stringify({ result, events });
    for (const code of SECTION_4_CODES) assert.ok(!reported.includes(code), `${code} reported for ${JSON.stringify(reply)}`);
  }
});

test('TYPE-73: the evaluator has no code of section 4 at all', () => {
  for (const code of SECTION_4_CODES) assert.ok(!api.ERROR_CODES.includes(code), code);
  for (const file of listFiles(distDir, '.js')) {
    const source = readFileSync(file, 'utf8');
    for (const code of SECTION_4_CODES) assert.ok(!source.includes(code), `${file} names ${code}`);
  }
});
