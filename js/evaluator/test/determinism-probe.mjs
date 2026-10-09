// A probe for the determinism tests (DET-7, DET-8): it loads the base bundle and runs resolve and decide over a
// fixed set of inputs, and returns every result and error code as canonical JSON text. Run as a script, it prints
// that text, so a test can compare runs under another locale, time zone, clock or environment.
// With the argument "reverse", every input map is built with its entries in the reverse order.
import { argv, stdout } from 'node:process';
import { fileURLToPath } from 'node:url';
import { api, bundleBytes, json } from './support.mjs';
import { content } from './fixtures.mjs';

/** Builds a plain object from entries, in the given order or the reverse. */
function map(entries, reverse) {
  return Object.fromEntries(reverse ? [...entries].reverse() : entries);
}

/** Runs the fixed inputs and returns the results and error codes as canonical JSON text. */
export async function probe(reverse = false) {
  const loaded = api.loadBundle(bundleBytes(content()));
  const provider = async () => map([['k.all', true], ['k.inter', ['u3', 'u1']], ['k.text', 'svc'], ['k.union', ['u2']], ['k.min', 7]], reverse);
  const tw = api.createEvaluator(loaded, { providers: { low: { fn: provider, timeoutMs: 1000 } } });
  const contexts = [
    {},
    { levels: map([['mid', 'm1'], ['low', 'n1']], reverse), axes: { lane: 'l1' } },
    map([['levels', { mid: 'm2' }], ['axes', { lane: 'l2' }]], reverse),
    { levels: { mid: 'm9' } },
  ];
  const facts = [
    { facts: map([['parent.flag', true], ['whole.kind', 'a'], ['whole.price', '01.50']], reverse) },
    map([['facts', map([['parent.flag', false], ['whole.kind', 'b']], reverse)], ['children', { part: [{ 'part.tag': 'x' }, { 'part.tag': 'y' }] }]], reverse),
    { facts: { 'parent.flag': 'yes' } },
    {},
  ];
  const out = { bundleVersion: loaded.bundleVersion, results: [] };
  const record = async (run) => {
    try {
      out.results.push({ ok: await run() });
    } catch (error) {
      if (!(error instanceof api.TierwiseError)) throw error;
      out.results.push({ error: error.code });
    }
  };
  for (const context of contexts) {
    for (const key of Object.keys(content().keys).sort()) {
      await record(() => tw.resolve(context, key));
    }
    for (const feature of ['f.obj', 'f.one', 'f.null']) {
      for (const factSet of facts) {
        await record(() => tw.decideSync(context, feature, factSet));
      }
    }
  }
  return json.canonicalJson(out);
}

if (argv[1] !== undefined && fileURLToPath(import.meta.url) === argv[1]) {
  stdout.write(await probe(argv[2] === 'reverse'));
}
