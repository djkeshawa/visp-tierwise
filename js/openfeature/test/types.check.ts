// A compile-time check, run by `tsc -p test/tsconfig.json` (the "typecheck" script): the providers satisfy the
// Provider interfaces of the two SDKs, for the SDK versions that are installed.
import type { Provider as ServerProvider } from '@openfeature/server-sdk';
import type { Provider as WebProvider } from '@openfeature/web-sdk';
import type { Evaluator } from '@tierwise/evaluator';
import { TierwiseServerProvider, TierwiseWebProvider } from '../src/index.js';
import type { EvaluatorSource } from '../src/index.js';

declare const evaluator: Evaluator;
declare const source: EvaluatorSource;

export const server: ServerProvider = new TierwiseServerProvider(evaluator);
export const web: WebProvider = new TierwiseWebProvider(evaluator);
export const serverFromFunction: ServerProvider = new TierwiseServerProvider(() => evaluator);
export const webFromSource: WebProvider = new TierwiseWebProvider(source);
