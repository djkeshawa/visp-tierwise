// Determinism rules that concern the environment, not the bytes (DET-7, DET-8). A conformance vector cannot run
// under another locale, time zone or clock, so these tests are their enforcers (spec/conformance/enforcers.d/E7.yaml).
// Each one loads and evaluates the same bundle bytes through determinism-probe.mjs and compares the canonical text of
// every result and error code.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { probe } from './determinism-probe.mjs';

const probeFile = fileURLToPath(new URL('./determinism-probe.mjs', import.meta.url));

/** Runs the probe in a new Node process with exactly the given environment. */
function probeIn(env, args = [], cwd = undefined) {
  return execFileSync(process.execPath, [probeFile, ...args], { env, cwd, encoding: 'utf8' });
}

/** Reports the default locale and time zone that a new Node process sees under the given environment. */
function intlIn(env) {
  const script = 'const o = new Intl.DateTimeFormat().resolvedOptions(); process.stdout.write(JSON.stringify([o.locale, o.timeZone]));';
  return JSON.parse(execFileSync(process.execPath, ['-e', script], { env, encoding: 'utf8' }));
}

const BASE_ENV = { PATH: process.env.PATH ?? '' };

test('DET-7: loading and evaluating under a non-English locale and a non-UTC time zone gives the same results', async () => {
  const reference = probeIn({ ...BASE_ENV, LANG: 'C', LC_ALL: 'C', TZ: 'UTC' });
  assert.equal(await probe(), reference, 'the process under test agrees with the reference');
  for (const [locale, zone] of [
    ['tr_TR.UTF-8', 'Pacific/Kiritimati'],
    ['ar_EG.UTF-8', 'America/St_Johns'],
    ['de_DE.UTF-8', 'Asia/Tokyo'],
  ]) {
    const env = { ...BASE_ENV, LANG: locale, LC_ALL: locale, TZ: zone };
    const [childLocale, childZone] = intlIn(env);
    assert.equal(childZone, zone, 'the child process runs in the requested time zone');
    assert.equal(childLocale.slice(0, 2), locale.slice(0, 2), 'the child process runs in the requested locale');
    assert.equal(probeIn(env), reference, `${locale} ${zone}`);
  }
});

test('DET-8: the results do not depend on the wall-clock time', async () => {
  const reference = await probe();
  const RealDate = Date;
  for (const offset of [-1e12, 4e12]) {
    class ShiftedDate extends RealDate {
      constructor(...args) {
        super(...(args.length === 0 ? [RealDate.now() + offset] : args));
      }

      static now() {
        return RealDate.now() + offset;
      }
    }
    globalThis.Date = ShiftedDate;
    try {
      assert.ok(Math.abs(Date.now() - RealDate.now() - offset) < 60000, 'the clock is shifted');
      assert.equal(await probe(), reference, `clock shifted by ${offset} ms`);
    } finally {
      globalThis.Date = RealDate;
    }
  }
  assert.equal(await probe(), reference, 'a later run');
});

test('DET-8: the results do not depend on the host environment', () => {
  const reference = probeIn(BASE_ENV);
  const noisy = {
    ...BASE_ENV,
    HOME: '/nonexistent',
    LANG: 'ja_JP.UTF-8',
    TZ: 'Etc/GMT+12',
    NODE_ENV: 'production',
    TIERWISE_SPEC_VERSION: '0.2',
    TIERWISE_BUNDLE_FORMAT_VERSION: '2',
    DEBUG: '*',
  };
  assert.equal(probeIn(noisy), reference, 'another environment');
  assert.equal(probeIn(BASE_ENV, [], tmpdir()), reference, 'another working directory');
});

test('DET-8: the results do not depend on the iteration order of the input maps', async () => {
  assert.equal(await probe(true), await probe(false));
  assert.equal(probeIn(BASE_ENV, ['reverse']), probeIn(BASE_ENV));
});
