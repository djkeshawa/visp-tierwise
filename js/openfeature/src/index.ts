// @visporg/openfeature: OpenFeature providers over a Tierwise evaluator (docs/openfeature-mapping.md).
//
// The package imports only types and constants from @openfeature/core, which is a peer dependency, so one
// build serves both SDKs: install @openfeature/server-sdk for the server provider or @openfeature/web-sdk for
// the web provider.

export { TierwiseServerProvider } from './server.js';
export { TierwiseWebProvider } from './browser.js';
export type { EvaluatorSource } from './source.js';
export { AXES_ATTRIBUTE, FACTS_ATTRIBUTE, LEVELS_ATTRIBUTE, PROVIDER_NAME, TierwiseMetadata } from './metadata.js';
