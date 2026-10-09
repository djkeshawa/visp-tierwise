// The Node service of the end-to-end test. The Java test spawns it with
//
//   node node-client.mjs <path of js/node/dist/esm/index.js> <bundle URL> <refresh interval in ms>
//
// and talks to it over standard input and output: one JSON command per input line, one JSON reply per output line.
// Nothing else is written to standard output. Diagnostics go to standard error.
//
// Commands:
//   {"op":"status"}                                   -> {"ok":true,"bundleVersion":...,"refreshFailures":...}
//   {"op":"await","version":V,"timeoutMs":N}         -> {"ok":true,"bundleVersion":V,"waitedMs":...} or {"ok":false,...}
//   {"op":"evaluate","cases":[...]}                   -> {"ok":true,"results":[{"id":...,"result":...}|{"id":...,"error":{"code":...}}]}
//   {"op":"stop"}                                     -> {"ok":true}, then the process ends
//
// The loader polls on its own timer (intervalMs). The "await" command only watches its status, so the test measures
// how long the refresh interval takes to deliver a new version. The providers must answer exactly as the providers
// of the Java client and the server do (TestProviders.java).
import { createInterface } from 'node:readline';
import { pathToFileURL } from 'node:url';

const [distIndex, url, intervalArg] = process.argv.slice(2);
if (!distIndex || !url || !intervalArg) {
  process.stderr.write('usage: node-client.mjs <dist/esm/index.js> <bundle url> <interval ms>\n');
  process.exit(2);
}

const { createBundleLoader } = await import(pathToFileURL(distIndex).href);

// Service-sourced levels of the sample: account has values for three nodes, user has none.
const providers = {
  account: {
    timeoutMs: 200,
    fn: async (nodeId) => {
      switch (nodeId) {
        case 'acct-1':
          return { 'checkout.shipping.allowed_methods': ['PICKUP'] };
        case 'acct-2':
          throw new Error('the account service is down');
        case 'acct-3':
          return { 'checkout.container_order.allowed': false };
        default:
          return {};
      }
    },
  },
  user: { timeoutMs: 200, fn: async () => ({}) },
};

const loader = createBundleLoader({
  url,
  intervalMs: Number(intervalArg),
  evaluatorOptions: { providers },
  onError: (error) => process.stderr.write(`bundle refresh failed: ${error}\n`),
});

await loader.start();

function reply(object) {
  process.stdout.write(JSON.stringify(object) + '\n');
}

function status() {
  const s = loader.status();
  return { ok: true, bundleVersion: s.bundleVersion, refreshFailures: s.refreshFailures };
}

async function awaitVersion(version, timeoutMs) {
  const started = Date.now();
  while (loader.status().bundleVersion !== version) {
    if (Date.now() - started > timeoutMs) {
      return { ok: false, error: 'timeout', bundleVersion: loader.status().bundleVersion };
    }
    await new Promise((resolve) => setTimeout(resolve, 5));
  }
  return { ok: true, bundleVersion: version, waitedMs: Date.now() - started };
}

async function evaluate(cases) {
  const evaluator = loader.current(); // one bundle for the whole batch
  const results = [];
  for (const c of cases) {
    try {
      const result = c.call === 'resolve'
        ? await evaluator.resolve(c.context, c.key)
        : await evaluator.decide(c.context, c.feature, c.input);
      results.push({ id: c.id, result });
    } catch (error) {
      results.push({ id: c.id, error: { code: error.code ?? 'UNKNOWN' } });
    }
  }
  return { ok: true, results };
}

reply({ ok: true, ready: true, ...status() });

const lines = createInterface({ input: process.stdin });
// Commands run one after the other: the test sends the next one only after the reply.
for await (const line of lines) {
  if (line.trim() === '') continue;
  let command;
  try {
    command = JSON.parse(line);
    switch (command.op) {
      case 'status':
        reply(status());
        break;
      case 'await':
        reply(await awaitVersion(command.version, command.timeoutMs));
        break;
      case 'evaluate':
        reply(await evaluate(command.cases));
        break;
      case 'stop':
        loader.stop();
        reply({ ok: true });
        process.exit(0);
        break;
      default:
        reply({ ok: false, error: `unknown op ${command.op}` });
    }
  } catch (error) {
    reply({ ok: false, error: String(error) });
  }
}
loader.stop();
