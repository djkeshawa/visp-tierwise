// Property tests of requirements section 12, over generated schemas with arbitrary names:
//   - narrowing strategies never widen when a value is added;
//   - commutative results, and their contribution effects, do not depend on input order;
//   - the most specific override value wins;
//   - all_in over an empty collection is false;
//   - a missing fact gives the same reason whatever the rule order.
// Each property builds bundles from generated names, loads them through loadBundle, and compares the evaluator
// with a small reference model that is written from the spec, not from the implementation.
import assert from 'node:assert/strict';
import { test } from 'node:test';
import fc from 'fast-check';
import { api, distUrl, loadContent } from './support.mjs';

const text = await import(new URL('text.js', distUrl).href);
const { createEvaluator } = api;

const RUNS = 300;
const byCodePoint = (a, b) => text.compareCodePoints(a, b);

// ---------------------------------------------------------------------------
// Generators
// ---------------------------------------------------------------------------

const word = fc.string({ unit: 'grapheme', minLength: 1, maxLength: 6 });
const nodeId = word.filter((candidate) => text.isNodeId(candidate));
const entityName = word.filter((candidate) => !candidate.includes('.'));
/** A value that can appear as a condition literal: a string that begins with `$` is a setting reference (RULE-5). */
const literalWord = word.filter((candidate) => !candidate.startsWith('$'));

/** A hierarchy: a root, some levels, some axes, one node per non-root scope, and which scopes the context supplies. */
const hierarchyArb = fc
  .record({
    names: fc.uniqueArray(word, { minLength: 2, maxLength: 6 }),
    levelCount: fc.nat(5),
    nodes: fc.array(nodeId, { minLength: 6, maxLength: 6 }),
    supplied: fc.array(fc.boolean(), { minLength: 6, maxLength: 6 }),
  })
  .map(({ names, levelCount, nodes, supplied }) => {
    const levelEnd = 1 + (levelCount % names.length);
    const scopes = names.map((name, index) => ({
      name,
      kind: index < levelEnd ? 'level' : 'axis',
      root: index === 0,
      node: index === 0 ? '' : nodes[index],
      supplied: index === 0 ? true : supplied[index],
    }));
    return { scopes, levels: scopes.filter((s) => s.kind === 'level'), axes: scopes.filter((s) => s.kind === 'axis') };
  });

/** The content of a bundle with one key, set at every scope, and the values given for it. */
function keyContent(h, key, def, values) {
  const nonRoot = h.scopes.filter((s) => !s.root);
  const byScope = {};
  h.scopes.forEach((scope, index) => {
    if (values[index] !== undefined) {
      byScope[scope.name] = { [scope.node]: { [key]: values[index] } };
    }
  });
  return {
    bundleFormatVersion: '1',
    specVersion: '0.1',
    hierarchy: {
      levels: h.levels.map((s) => s.name),
      axes: h.axes.map((s) => s.name),
      scopes: Object.fromEntries(nonRoot.map((s) => [s.name, { nodes: 'open', values: 'files' }])),
    },
    entities: {},
    facts: {},
    keys: { [key]: { ...def, settableAt: h.scopes.map((s) => s.name) } },
    values: byScope,
    features: {},
  };
}

/** The context that supplies a node for each scope that the hierarchy marks as supplied. */
function contextOf(h) {
  const pick = (kind) =>
    Object.fromEntries(h.scopes.filter((s) => s.kind === kind && !s.root && s.supplied).map((s) => [s.name, s.node]));
  return { levels: pick('level'), axes: pick('axis') };
}

/** The scopes on the evaluation path that hold a value: the contributions, in path order. */
function contributions(h, values) {
  return h.scopes
    .map((scope, index) => ({ scope, value: values[index] }))
    .filter((entry) => entry.scope.supplied && entry.value !== undefined);
}

function resolveWith(h, key, def, values) {
  const tw = createEvaluator(loadContent(keyContent(h, key, def, values)));
  return tw.resolveSync(contextOf(h), key);
}

// ---------------------------------------------------------------------------
// The six commutative strategies, with a reference model
// ---------------------------------------------------------------------------

const keyName = word;
const universeArb = fc.uniqueArray(word, { minLength: 1, maxLength: 4 });
const intArb = fc.integer({ min: -50, max: 50 });

/** For each strategy: the key declaration, a generator of one value, the reference fold, and the label. */
const STRATEGIES = {
  all: {
    narrowing: true,
    decl: () => ({ type: { type: 'boolean' }, combine: 'all' }),
    value: () => fc.boolean(),
    identity: () => true,
    fold: (values) => values.every((v) => v),
    label: (v) => (v ? 'IGNORED' : 'VETO'),
    leq: (extended, base) => !extended || base, // adding a value never turns false into true
  },
  any: {
    decl: () => ({ type: { type: 'boolean' }, combine: 'any' }),
    value: () => fc.boolean(),
    identity: () => false,
    fold: (values) => values.some((v) => v),
    label: (v) => (v ? 'WIDENED' : 'IGNORED'),
  },
  intersect: {
    narrowing: true,
    decl: (u) => ({ type: { type: 'set', universe: u }, combine: 'intersect' }),
    value: (u) => fc.subarray(u).map((members) => u.filter((m) => members.includes(m))),
    identity: (u) => u,
    fold: (values, u) => u.filter((m) => values.every((v) => v.includes(m))),
    label: (v, u) => (v.length >= u.length ? 'IGNORED' : 'NARROWED'),
    leq: (extended, base) => extended.every((m) => base.includes(m)),
  },
  union: {
    decl: (u) => ({ type: { type: 'set', universe: u }, combine: 'union' }),
    value: (u) => fc.subarray(u).map((members) => u.filter((m) => members.includes(m))),
    identity: () => [],
    fold: (values, u) => u.filter((m) => values.some((v) => v.includes(m))),
    label: (v) => (v.length === 0 ? 'IGNORED' : 'WIDENED'),
  },
  min: {
    narrowing: true,
    decl: () => ({ type: { type: 'integer' }, combine: 'min' }),
    value: () => intArb,
    identity: () => null,
    fold: (values) => (values.length === 0 ? null : Math.min(...values)),
    label: () => 'NARROWED',
    leq: (extended, base) => base === null || (extended !== null && extended <= base),
  },
  max: {
    decl: () => ({ type: { type: 'integer' }, combine: 'max' }),
    value: () => intArb,
    identity: () => null,
    fold: (values) => (values.length === 0 ? null : Math.max(...values)),
    label: () => 'WIDENED',
  },
};

/** Generates a scenario: a hierarchy, a key, a universe and an optional value for each scope. */
function scenario(strategyName) {
  const strategy = STRATEGIES[strategyName];
  return fc
    .record({ h: hierarchyArb, key: keyName, universe: universeArb })
    .chain(({ h, key, universe }) =>
      fc
        .array(fc.option(strategy.value(universe), { nil: undefined }), { minLength: h.scopes.length, maxLength: h.scopes.length })
        .map((values) => ({ h, key, universe, values })),
    );
}

for (const [name, strategy] of Object.entries(STRATEGIES)) {
  test(`the ${name} strategy matches the reference model, with labels from each value alone`, () => {
    fc.assert(
      fc.property(scenario(name), ({ h, key, universe, values }) => {
        const result = resolveWith(h, key, strategy.decl(universe), values);
        const held = contributions(h, values);
        const expected = held.length === 0 ? strategy.identity(universe) : strategy.fold(held.map((e) => e.value), universe);
        assert.deepEqual(result.value, expected);
        assert.deepEqual(
          result.contributions.map((c) => [c.name, c.value, c.effect]),
          held.map((e) => [e.scope.name, e.value, strategy.label(e.value, universe)]),
          'one entry for each contributing scope, in path order (PROV-20, PROV-34, PROV-37)',
        );
        assert.ok(!('source' in result));
      }),
      { numRuns: RUNS },
    );
  });
}

for (const name of Object.keys(STRATEGIES).filter((n) => STRATEGIES[n].narrowing)) {
  test(`property: the narrowing strategy ${name} never widens when a value is added`, () => {
    const strategy = STRATEGIES[name];
    fc.assert(
      fc.property(
        scenario(name),
        fc.nat(),
        fc.context(),
        ({ h, key, universe, values }, pickIndex, ctx) => {
          const free = values.map((v, index) => (v === undefined ? index : -1)).filter((index) => index >= 0);
          fc.pre(free.length > 0);
          const slot = free[pickIndex % free.length];
          const extra = fc.sample(strategy.value(universe), { numRuns: 1, seed: pickIndex })[0];
          const added = values.map((v, index) => (index === slot ? extra : v));
          const before = resolveWith(h, key, strategy.decl(universe), values).value;
          const after = resolveWith(h, key, strategy.decl(universe), added).value;
          ctx.log(JSON.stringify({ before, after, slot, extra }));
          assert.ok(strategy.leq(after, before), 'the result after adding a value is no wider than before');
        },
      ),
      { numRuns: RUNS },
    );
  });
}

for (const name of Object.keys(STRATEGIES)) {
  test(`property: the commutative ${name} result and its contribution effects do not depend on input order`, () => {
    const strategy = STRATEGIES[name];
    fc.assert(
      fc.property(scenario(name), fc.infiniteStream(fc.nat()), ({ h, key, universe, values }, random) => {
        // Move the same values to other scopes (all scopes are supplied), in a different order.
        const supplied = { ...h, scopes: h.scopes.map((s) => ({ ...s, supplied: true })) };
        const held = values.map((v, index) => [v, index]).filter(([v]) => v !== undefined);
        const slots = held.map(([, index]) => index);
        const shuffled = [...slots];
        const draw = random[Symbol.iterator]();
        for (let i = shuffled.length - 1; i > 0; i--) {
          const j = draw.next().value % (i + 1);
          [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
        }
        const moved = values.map(() => undefined);
        held.forEach(([v], position) => {
          moved[shuffled[position]] = v;
        });
        const first = resolveWith(supplied, key, strategy.decl(universe), values);
        const second = resolveWith(supplied, key, strategy.decl(universe), moved);
        assert.deepEqual(second.value, first.value, 'the result');
        const effects = (result) => result.contributions.map((c) => [JSON.stringify(c.value), c.effect]).sort();
        assert.deepEqual(effects(second), effects(first), 'the contribution effects, as a multiset');
      }),
      { numRuns: RUNS },
    );
  });
}

test('property: the order of the entries of the context does not change the result (CTX-11, EVAL-51)', () => {
  fc.assert(
    fc.property(scenario('max'), fc.boolean(), ({ h, key, universe, values }, reverse) => {
      const tw = createEvaluator(loadContent(keyContent(h, key, STRATEGIES.max.decl(universe), values)));
      const context = contextOf(h);
      const reordered = {
        axes: Object.fromEntries(Object.entries(context.axes).reverse()),
        levels: Object.fromEntries(Object.entries(context.levels).reverse()),
      };
      assert.deepEqual(tw.resolveSync(reverse ? reordered : context, key), tw.resolveSync(reverse ? context : reordered, key));
    }),
    { numRuns: RUNS },
  );
});

// ---------------------------------------------------------------------------
// Order-dependent keys: the most specific override value wins
// ---------------------------------------------------------------------------

const ACROSS = ['axis_wins', 'hierarchy_wins', 'max', 'min'];

test('property: for an override key the most specific level wins, and the axis rules of AXIS-7 hold', () => {
  fc.assert(
    fc.property(
      hierarchyArb,
      keyName,
      intArb,
      fc.constantFrom(...ACROSS),
      fc.array(fc.option(intArb, { nil: undefined }), { minLength: 6, maxLength: 6 }),
      (h, key, fallback, across, all) => {
        const values = all.slice(0, h.scopes.length);
        const decl = { type: { type: 'integer' }, combine: 'override', default: fallback };
        if (h.axes.length > 0) decl.combineAcrossAxes = across;
        const result = resolveWith(h, key, decl, values);
        const held = contributions(h, values);
        const levels = held.filter((e) => e.scope.kind === 'level');
        const axes = held.filter((e) => e.scope.kind === 'axis');
        const H = levels.at(-1);
        const A = axes.at(-1);
        let winner;
        if (H && A) {
          if (across === 'hierarchy_wins') winner = H;
          else if (across === 'max') winner = A.value > H.value ? A : H;
          else if (across === 'min') winner = A.value < H.value ? A : H;
          else winner = A;
        } else {
          winner = H ?? A;
        }
        if (winner === undefined) {
          assert.equal(result.value, fallback);
          assert.equal(result.source, 'DEFAULT');
        } else {
          assert.equal(result.value, winner.value);
          assert.equal(result.source.name, winner.scope.name);
          assert.equal(result.source.scope, winner.scope.kind);
          assert.equal('node' in result.source, !winner.scope.root);
        }
        assert.ok(result.contributions.every((c) => c.effect === 'SET'));
        assert.equal(result.contributions.length, held.length);
      },
    ),
    { numRuns: RUNS },
  );
});

test('property: with only levels, the most specific level that sets a value wins over every less specific one', () => {
  fc.assert(
    fc.property(
      fc.uniqueArray(word, { minLength: 2, maxLength: 6 }),
      keyName,
      fc.array(fc.option(intArb, { nil: undefined }), { minLength: 6, maxLength: 6 }),
      fc.array(nodeId, { minLength: 6, maxLength: 6 }),
      (names, key, all, nodes) => {
        const scopes = names.map((name, index) => ({ name, kind: 'level', root: index === 0, node: index === 0 ? '' : nodes[index], supplied: true }));
        const h = { scopes, levels: scopes, axes: [] };
        const values = all.slice(0, scopes.length);
        const decl = { type: { type: 'integer' }, combine: 'override', default: 999 };
        const result = resolveWith(h, key, decl, values);
        const last = values.map((v, i) => [v, i]).filter(([v]) => v !== undefined).at(-1);
        assert.equal(result.value, last === undefined ? 999 : last[0]);
      },
    ),
    { numRuns: RUNS },
  );
});

// ---------------------------------------------------------------------------
// Decisions: quantifiers over child collections, and the precheck
// ---------------------------------------------------------------------------

/** A bundle with one feature decided per a parent entity, whose rule reads one child fact. */
function quantifierContent({ parent, child, attr, values, op, operand, viaSetting }) {
  const fact = `${child}.${attr}`;
  const sorted = [...values].sort(byCodePoint);
  const setting = viaSetting ? { s: { listOf: fact, default: [...operand].sort(byCodePoint) } } : {};
  return {
    bundleFormatVersion: '1',
    specVersion: '0.1',
    hierarchy: { levels: ['root'], axes: [], scopes: {} },
    entities: {
      [parent]: { belongsTo: [], hasMany: [child] },
      [child]: { belongsTo: [], hasMany: [] },
    },
    facts: { [fact]: { entity: child, type: { type: 'enum', values: sorted } } },
    keys: {},
    values: {},
    features: {
      feat: {
        decidedPer: parent,
        returns: { enum: ['ALLOW', 'DENY'] },
        hitPolicy: 'FIRST',
        ifFactMissing: 'DENY',
        bindingSettableAt: ['root'],
        policies: {
          pol: {
            settings: setting,
            default: 'DENY',
            requiredFacts: [fact],
            rules: [
              {
                id: 'rule',
                name: 'rule',
                when: [{ fact, op, value: viaSetting ? '$s' : [...operand].sort(byCodePoint) }],
                then: 'ALLOW',
              },
            ],
          },
        },
        bindings: { root: { '': { policy: 'pol', settings: {} } } },
      },
    },
  };
}

const quantifierArb = fc
  .record({
    parent: entityName,
    child: entityName,
    attr: word,
    values: fc.uniqueArray(literalWord, { minLength: 1, maxLength: 5 }),
    op: fc.constantFrom('all_in', 'any_in'),
    viaSetting: fc.boolean(),
  })
  .filter(({ parent, child }) => parent !== child)
  .chain((base) =>
    fc.record({
      operand: fc.subarray(base.values),
      children: fc.array(fc.constantFrom(...base.values), { maxLength: 5 }),
    }).map((rest) => ({ ...base, ...rest })),
  );

function decideQuantifier(spec, children) {
  const tw = createEvaluator(loadContent(quantifierContent(spec)));
  const fact = `${spec.child}.${spec.attr}`;
  return tw.decideSync({}, 'feat', { children: { [spec.child]: children.map((value) => ({ [fact]: value })) } });
}

test('property: all_in and any_in over an empty collection are false, whatever the operand (RULE-33)', () => {
  fc.assert(
    fc.property(quantifierArb, (spec) => {
      const result = decideQuantifier(spec, []);
      assert.equal(result.effect, 'DENY');
      assert.deepEqual(result.reason, { type: 'DEFAULT' });
      assert.equal(result.trace[0].failed.op, spec.op);
    }),
    { numRuns: RUNS },
  );
});

test('property: an empty operand list never holds, as a literal or as a setting (RULE-34)', () => {
  fc.assert(
    fc.property(quantifierArb, (spec) => {
      const result = decideQuantifier({ ...spec, operand: [] }, spec.children);
      assert.equal(result.effect, 'DENY');
    }),
    { numRuns: RUNS },
  );
});

test('property: all_in and any_in over a non-empty collection follow RULE-31 and RULE-32', () => {
  fc.assert(
    fc.property(quantifierArb, (spec) => {
      fc.pre(spec.children.length > 0);
      const inList = (value) => spec.operand.includes(value);
      const holds = spec.op === 'all_in' ? spec.children.every(inList) : spec.children.some(inList);
      const result = decideQuantifier(spec, spec.children);
      assert.equal(result.effect, holds ? 'ALLOW' : 'DENY');
      assert.deepEqual(result.reason, holds ? { type: 'RULE', id: 'rule', name: 'rule' } : { type: 'DEFAULT' });
    }),
    { numRuns: RUNS },
  );
});

/** A bundle whose policy has several rules over single-value boolean facts. */
function precheckContent(parent, attrs, rules) {
  const facts = Object.fromEntries(attrs.map((attr) => [`${parent}.${attr}`, { entity: parent, type: { type: 'boolean' } }]));
  const required = [...new Set(rules.flatMap((rule) => rule.when.map((c) => c.fact)))].sort(byCodePoint);
  return {
    bundleFormatVersion: '1',
    specVersion: '0.1',
    hierarchy: { levels: ['root'], axes: [], scopes: {} },
    entities: { [parent]: { belongsTo: [], hasMany: [] } },
    facts,
    keys: {},
    values: {},
    features: {
      feat: {
        decidedPer: parent,
        returns: { enum: ['ALLOW', 'DENY', 'MISSING'] },
        hitPolicy: 'FIRST',
        ifFactMissing: 'MISSING',
        bindingSettableAt: ['root'],
        policies: {
          pol: {
            settings: {},
            default: 'DENY',
            requiredFacts: required,
            rules: rules.map((rule) => ({
              ...rule,
              when: [...rule.when].sort((a, b) => byCodePoint(a.fact, b.fact)),
            })),
          },
        },
        bindings: { root: { '': { policy: 'pol', settings: {} } } },
      },
    },
  };
}

test('property: a missing fact gives if_fact_missing with the same reason whatever the rule order (RULE-40 to RULE-44)', () => {
  const arb = fc.record({
    parent: entityName,
    attrs: fc.uniqueArray(word, { minLength: 2, maxLength: 4 }),
  }).chain(({ parent, attrs }) =>
    fc.record({
      parent: fc.constant(parent),
      attrs: fc.constant(attrs),
      rules: fc.array(
        fc.record({
          picks: fc.subarray(attrs, { minLength: 1 }),
          wants: fc.boolean(),
          effect: fc.constantFrom('ALLOW', 'DENY'),
        }),
        { minLength: 1, maxLength: 4 },
      ),
      given: fc.array(fc.option(fc.boolean(), { nil: undefined }), { minLength: attrs.length, maxLength: attrs.length }),
      seed: fc.infiniteStream(fc.nat()),
    }),
  );
  fc.assert(
    fc.property(arb, ({ parent, attrs, rules, given, seed }) => {
      const make = (order) =>
        order.map((rule, index) => ({
          id: `r${rules.indexOf(rule)}`,
          name: `rule ${index}`,
          when: rule.picks.map((attr) => ({ fact: `${parent}.${attr}`, op: 'eq', value: rule.wants })),
          then: rule.effect,
        }));
      const facts = {};
      attrs.forEach((attr, index) => {
        if (given[index] !== undefined) facts[`${parent}.${attr}`] = given[index];
      });
      const shuffled = [...rules];
      const draw = seed[Symbol.iterator]();
      for (let i = shuffled.length - 1; i > 0; i--) {
        const j = draw.next().value % (i + 1);
        [shuffled[i], shuffled[j]] = [shuffled[j], shuffled[i]];
      }
      const run = (order) => createEvaluator(loadContent(precheckContent(parent, attrs, make(order)))).decideSync({}, 'feat', { facts });
      const a = run(rules);
      const b = run(shuffled);
      const needed = [...new Set(rules.flatMap((rule) => rule.picks.map((attr) => `${parent}.${attr}`)))].sort(byCodePoint);
      const missing = needed.filter((fact) => facts[fact] === undefined);
      if (missing.length > 0) {
        for (const result of [a, b]) {
          assert.equal(result.effect, 'MISSING');
          assert.deepEqual(result.reason, { type: 'MISSING_FACT', facts: missing });
          assert.deepEqual(result.trace, []);
        }
      } else {
        // The first matching rule in each order wins.
        for (const [order, result] of [[rules, a], [shuffled, b]]) {
          const winner = order.find((rule) => rule.picks.every((attr) => facts[`${parent}.${attr}`] === rule.wants));
          assert.equal(result.effect, winner === undefined ? 'DENY' : winner.effect);
          assert.equal(result.reason.type, winner === undefined ? 'DEFAULT' : 'RULE');
        }
      }
    }),
    { numRuns: RUNS },
  );
});
