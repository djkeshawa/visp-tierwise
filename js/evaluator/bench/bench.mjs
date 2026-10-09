// In-process benchmark of resolve and decide (requirements section 12, implementation plan 4.5), with no dependency beyond
// Node itself.
//
// The bundle has the shape of the Java benchmark (java/tierwise-core, BenchModel): 1,000 keys and 50 features, three
// levels and one axis, all files-sourced, and no service-sourced level, so no provider is ever called. The content is
// built here and canonicalised with the test helpers, so the bytes are the bytes of a bundle; the two benchmarks use
// the same shape but are not the same bytes.
//
// Each operation is timed on its own with process.hrtime.bigint() after a warm-up of the same operation, and the
// percentiles are nearest-rank values of every sample. Run it after `pnpm --dir js run build`:
//
//   node js/evaluator/bench/bench.mjs [output-file]
//
// The report is printed, and written to the output file when one is given.
import { writeFileSync } from 'node:fs';
import { arch, cpus, platform } from 'node:os';
import { api, bundleBytes } from '../test/support.mjs';

const KEYS = 1000;
const FEATURES = 50;
const ZONES = 40;
const UNITS = 20;
const LANES = 4;
const WARMUP = 50_000;
const MEASURED = 200_000;
const CONTEXTS = 64;
const STATUSES = ['closed', 'hold', 'open', 'shipped'];
const KINDS = ['a', 'b', 'c'];
const SETS = ['s0', 's1', 's2', 's3', 's4', 's5'];

const pad = (value, width) => String(value).padStart(width, '0');
const nodes = (prefix, count, width) => Array.from({ length: count }, (_, i) => `${prefix}${pad(i, width)}`);
const zoneNodes = nodes('z', ZONES, 2);
const unitNodes = nodes('u', UNITS, 2);
const laneNodes = nodes('l', LANES, 1);
const keyName = (index) => `k.${pad(index, 4)}`;
const featureName = (index) => `f.${pad(index, 2)}`;

function keyDefinition(shape) {
  switch (shape) {
    case 0:
      return { type: { type: 'boolean' }, combine: 'all', settableAt: ['base', 'zone'] };
    case 1:
      return {
        type: { type: 'integer', min: 0, max: 1000 },
        combine: 'override',
        default: 0,
        settableAt: ['base', 'zone', 'lane'],
        combineAcrossAxes: 'max',
      };
    case 2:
      return { type: { type: 'integer', min: 0, max: 1000 }, combine: 'min', settableAt: ['base', 'zone', 'unit'] };
    case 3:
      return {
        type: { type: 'enum', values: ['a', 'b', 'c', 'd'] },
        combine: 'override',
        default: 'a',
        settableAt: ['base', 'unit'],
      };
    default:
      return { type: { type: 'set', universe: SETS }, combine: 'union', settableAt: ['base', 'zone', 'lane'] };
  }
}

function rootValue(shape, index) {
  switch (shape) {
    case 0:
      return index % 3 !== 0;
    case 1:
      return index % 1000;
    case 2:
      return (index * 37) % 1000;
    case 3:
      return index % 2 === 0 ? 'b' : 'd';
    default:
      return ['s1', 's3'];
  }
}

/** The values of the run, by scope and node, as the Java benchmark makes them (BUNDLE-64 to BUNDLE-67). */
function values(keys) {
  const root = {};
  const zones = {};
  const units = {};
  const lanes = {};
  const put = (scope, node, name, value) => {
    scope[node] ??= {};
    scope[node][name] = value;
  };
  keys.forEach((key, index) => {
    const shape = index % 5;
    root[key.name] = rootValue(shape, index);
    for (let z = 0; z < ZONES; z += 1) {
      if ((index + z) % 4 === 0 && key.settableAt.includes('zone')) {
        put(zones, zoneNodes[z], key.name, rootValue(shape, index + z));
      }
    }
    for (let u = 0; u < UNITS; u += 1) {
      if ((index + u) % 4 === 0 && key.settableAt.includes('unit')) {
        put(units, unitNodes[u], key.name, rootValue(shape, index + u));
      }
    }
    for (let l = 0; l < LANES; l += 1) {
      if (index % 2 === l % 2 && key.settableAt.includes('lane')) {
        put(lanes, laneNodes[l], key.name, rootValue(shape, index + l));
      }
    }
  });
  return { base: { '': root }, zone: zones, unit: units, lane: lanes };
}

function feature(index) {
  const settings = {
    limit: { valueOf: 'order.total', default: 500 },
    allowed: { listOf: 'order.status', default: ['open'] },
  };
  const rules = [
    { id: 'r1', name: 'Flagged region', when: [{ fact: 'order.region_ok', op: 'eq', value: true }], then: 'ALLOW' },
    { id: 'r2', name: 'Allowed status', when: [{ fact: 'order.status', op: 'in', value: '$allowed' }], then: 'ALLOW' },
    { id: 'r3', name: 'Limit reached', when: [{ fact: 'order.total', op: 'eq', value: '$limit' }], then: 'REVIEW' },
    { id: 'r4', name: 'Kinds', when: [{ fact: 'line.kind', op: 'all_in', value: ['a', 'b'] }], then: 'ALLOW' },
    { id: 'r5', name: 'Quantities', when: [{ fact: 'line.qty', op: 'any_in', value: [1, 2, 3] }], then: 'REVIEW' },
    { id: 'r6', name: 'Default catch-all', when: [], then: 'DENY' },
  ];
  const zone = {};
  for (let z = 0; z < 10; z += 1) {
    zone[zoneNodes[z]] = { policy: 'p', settings: { limit: 100 + index + z } };
  }
  return {
    decidedPer: 'order',
    returns: { enum: ['ALLOW', 'DENY', 'REVIEW'] },
    hitPolicy: 'FIRST',
    ifFactMissing: 'DENY',
    bindingSettableAt: ['base', 'zone'],
    policies: {
      p: {
        settings,
        default: 'DENY',
        requiredFacts: ['line.kind', 'line.qty', 'order.region_ok', 'order.status', 'order.total'],
        rules,
      },
    },
    bindings: { base: { '': { policy: 'p', settings: {} } }, zone },
  };
}

/** The content of the benchmark bundle. */
export function benchContent() {
  const keys = [];
  const keyDefinitions = {};
  for (let i = 0; i < KEYS; i += 1) {
    const definition = keyDefinition(i % 5);
    keys.push({ name: keyName(i), settableAt: definition.settableAt });
    keyDefinitions[keyName(i)] = definition;
  }
  const features = {};
  for (let i = 0; i < FEATURES; i += 1) features[featureName(i)] = feature(i);
  return {
    bundleFormatVersion: '1',
    specVersion: '0.1',
    hierarchy: {
      levels: ['base', 'zone', 'unit'],
      axes: ['lane'],
      scopes: {
        zone: { values: 'files', nodes: zoneNodes, onUnknownNode: 'error' },
        unit: { values: 'files', nodes: unitNodes, onUnknownNode: 'ignore' },
        lane: { values: 'files', nodes: laneNodes, onUnknownNode: 'ignore' },
      },
    },
    entities: {
      line: { belongsTo: ['order'], hasMany: [] },
      order: { belongsTo: [], hasMany: ['line'] },
    },
    facts: {
      'order.total': { entity: 'order', type: { type: 'integer', min: 0, max: 10000 } },
      'order.region_ok': { entity: 'order', type: { type: 'boolean' } },
      'order.status': { entity: 'order', type: { type: 'enum', values: STATUSES } },
      'line.kind': { entity: 'line', type: { type: 'enum', values: KINDS } },
      'line.qty': { entity: 'line', type: { type: 'integer', min: 0, max: 100 } },
    },
    keys: keyDefinitions,
    values: values(keys),
    features,
  };
}

/** The contexts of the run: a zone, a unit and a lane each. */
export function benchContexts(count) {
  return Array.from({ length: count }, (_, i) => ({
    levels: { zone: zoneNodes[i % ZONES], unit: unitNodes[i % UNITS] },
    axes: { lane: `l${i % LANES}` },
  }));
}

/** The fact sets of the run: an order with a total, a flag and a status, and three lines. */
export function benchFactSets(count) {
  return Array.from({ length: count }, (_, i) => ({
    facts: {
      'order.total': (i * 7919) % 10000,
      'order.region_ok': i % 2 === 0,
      'order.status': STATUSES[i % STATUSES.length],
    },
    children: {
      line: [0, 1, 2].map((j) => ({
        'line.kind': KINDS[(i + j) % KINDS.length],
        'line.qty': ((i + j) * 13) % 101,
      })),
    },
  }));
}

/** Returns the nearest-rank percentiles of the samples, in nanoseconds. */
export function summarise(samples) {
  const sorted = Float64Array.from(samples).sort();
  const rank = (p) => sorted[Math.max(0, Math.ceil(p * sorted.length) - 1)];
  const mean = sorted.reduce((sum, value) => sum + value, 0) / sorted.length;
  return {
    count: sorted.length,
    p50: rank(0.5),
    p90: rank(0.9),
    p99: rank(0.99),
    p999: rank(0.999),
    max: sorted[sorted.length - 1],
    mean,
  };
}

// Counts the results, so that the work of each call is used and the engine cannot drop it.
let sink = 0;
const consume = (value) => {
  if (value !== undefined && value !== null) sink = (sink + 1) % 1000003;
};

async function measure(operation, asynchronous) {
  for (let i = 0; i < WARMUP; i += 1) consume(asynchronous ? await operation(i) : operation(i));
  const samples = new Array(MEASURED);
  for (let i = 0; i < MEASURED; i += 1) {
    const start = process.hrtime.bigint();
    const result = asynchronous ? await operation(i) : operation(i);
    samples[i] = Number(process.hrtime.bigint() - start);
    consume(result);
  }
  return summarise(samples);
}

function report(rows) {
  const lines = [
    `JavaScript in-process benchmark: ${KEYS} keys, ${FEATURES} features, ${WARMUP} warm-up operations, ` +
      `${MEASURED} measured operations per call`,
    `node=${process.version} platform=${platform()} arch=${arch()} cpu=${cpus()[0]?.model ?? 'unknown'} ` +
      `processors=${cpus().length}`,
    'operation,count,p50_ns,p90_ns,p99_ns,p999_ns,max_ns,mean_ns',
  ];
  for (const [name, summary] of rows) {
    const fields = [summary.count, summary.p50, summary.p90, summary.p99, summary.p999, summary.max, Math.round(summary.mean)];
    lines.push(`${name},${fields.join(',')}`);
  }
  lines.push(`checksum=${sink}`);
  return `${lines.join('\n')}\n`;
}

async function main() {
  const bundle = api.loadBundle(bundleBytes(benchContent()));
  const evaluator = api.createEvaluator(bundle, {});
  const contexts = benchContexts(CONTEXTS);
  const facts = benchFactSets(CONTEXTS);
  const keys = Array.from({ length: KEYS }, (_, i) => keyName(i));
  const features = Array.from({ length: FEATURES }, (_, i) => featureName(i));
  const rows = [];
  rows.push(['resolve (sync)', await measure(
    (i) => evaluator.resolveSync(contexts[i % CONTEXTS], keys[i % KEYS]), false)]);
  rows.push(['resolve (async API)', await measure(
    (i) => evaluator.resolve(contexts[i % CONTEXTS], keys[i % KEYS]), true)]);
  rows.push(['decide (sync)', await measure(
    (i) => evaluator.decideSync(contexts[i % CONTEXTS], features[i % FEATURES], facts[i % CONTEXTS]), false)]);
  const text = report(rows);
  process.stdout.write(text);
  if (process.argv[2]) writeFileSync(process.argv[2], text, 'utf8');
}

await main();
