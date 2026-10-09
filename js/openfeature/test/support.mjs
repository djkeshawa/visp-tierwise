// Shared helpers for the provider tests: the built package, a small canonical bundle, and evaluators over it.
//
// The tests run over the built output in dist/, never over src/, so they check what ships. The bundle content is
// a copy of the fixture that the Java tests build (TestBundles.java in tierwise-openfeature), with arbitrary names: the
// providers know no domain.
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

export const distUrl = new URL('../dist/esm/', import.meta.url);
export const api = await import(new URL('index.js', distUrl).href);
export const mapping = await import(new URL('mapping.js', distUrl).href);

// The evaluator's own build: its public API, and its internal primitives for sealing a bundle (BUNDLE-1 to BUNDLE-6).
const evaluatorDist = new URL('../../evaluator/dist/esm/', import.meta.url);
export const evaluatorApi = await import(new URL('index.js', evaluatorDist).href);
const json = await import(new URL('json.js', evaluatorDist).href);
const sha = await import(new URL('sha256.js', evaluatorDist).href);

/** A fresh copy of the fixture content. */
export function content() {
  return JSON.parse(readFileSync(fileURLToPath(new URL('./fixture-content.json', import.meta.url)), 'utf8'));
}

/** The canonical bytes of a bundle built from a content object. */
export function bundleBytes(body) {
  const version = `sha256:${sha.sha256Hex(json.canonicalBytes(body))}`;
  return json.canonicalBytes({ bundleVersion: version, content: body });
}

/** The loaded fixture bundle. */
export function bundle() {
  return evaluatorApi.loadBundle(bundleBytes(content()));
}

/**
 * An evaluator over the fixture bundle. `low` is the function that serves the service-sourced level of the
 * bundle; by default it returns nothing for every node.
 */
export function evaluator(low = async () => ({})) {
  return evaluatorApi.createEvaluator(bundle(), { providers: { low: { fn: low, timeoutMs: 500 } } });
}

/** A context with the Tierwise attributes. */
export function context({ levels, axes, facts, ...others } = {}) {
  return {
    ...others,
    ...(levels !== undefined ? { 'tierwise.levels': levels } : {}),
    ...(axes !== undefined ? { 'tierwise.axes': axes } : {}),
    ...(facts !== undefined ? { 'tierwise.facts': facts } : {}),
  };
}

/** The contribution summary of one entry, as the providers write it. */
export const SUMMARY = {
  top: (effect) => `{"scope":"level","name":"top","effect":"${effect}"}`,
  mid: (node, effect) => `{"scope":"level","name":"mid","node":"${node}","effect":"${effect}"}`,
};
