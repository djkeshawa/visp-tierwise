// Context validation and node admission (CTX-1 to CTX-23). The steps run in the order of CTX-9 and finish
// before any lookup or provider call (CTX-10). Node IDs are compared as received, with no normalisation
// (CTX-7, CTX-8, NORM-3).

import { TierwiseError } from './errors.js';
import type { Hierarchy, Model, ScopeDef } from './model.js';
import { has, isNodeId, isPlainObject } from './text.js';
import type { ScopeKind, SkippedScope } from './types.js';

/** One scope on the evaluation path (CTX-19). The root level has no node. */
export interface PathScope {
  readonly name: string;
  readonly kind: ScopeKind;
  readonly node: string | null;
}

/** A context that has passed all four steps of CTX-9. */
export interface AdmittedContext {
  /** The root level, then each admitted non-root level and axis in declared order (MERGE-13). */
  readonly path: readonly PathScope[];
  /** The scopes that admission skipped, in declared level order then axis order (PROV-58). */
  readonly skippedScopes: readonly SkippedScope[];
}

function invalid(message: string): TierwiseError {
  return new TierwiseError('E_CONTEXT_INVALID', message);
}

/** Reads one of the `levels` and `axes` members of a context: a map from names to node IDs (CTX-1). */
function readNodes(value: unknown, member: string): Map<string, string> {
  const nodes = new Map<string, string>();
  if (value === undefined) {
    return nodes; // An absent member is an empty map (CTX-2).
  }
  if (!isPlainObject(value)) {
    throw invalid(`the context member ${member} is not a map`);
  }
  for (const name of Object.keys(value)) {
    const node = value[name];
    if (typeof node !== 'string') {
      throw invalid(`the node ID for ${name} in ${member} is not a string`);
    }
    nodes.set(name, node);
  }
  return nodes;
}

/** Validates and admits the contexts of one bundle. */
export class ContextValidator {
  private readonly hierarchy: Hierarchy;
  private readonly root: string;
  private readonly catalogs = new Map<string, ReadonlySet<string>>();

  constructor(model: Model) {
    this.hierarchy = model.hierarchy;
    this.root = model.hierarchy.levels[0] as string;
    for (const name of Object.keys(model.hierarchy.scopes)) {
      const scope = model.hierarchy.scopes[name] as ScopeDef;
      if (!scope.open) {
        this.catalogs.set(name, new Set(scope.nodes));
      }
    }
  }

  /**
   * Runs the steps of CTX-9: shape, scope names, node ID structure, admission.
   *
   * @param context a context; `undefined` is the empty context (EVAL-54)
   * @throws TierwiseError E_CONTEXT_INVALID, E_UNKNOWN_SCOPE or E_UNKNOWN_NODE
   */
  validate(context: unknown): AdmittedContext {
    const { levels, axes } = this.checkShape(context);
    this.checkNames(levels, axes);
    for (const [name, node] of [...levels, ...axes]) {
      if (!isNodeId(node)) {
        throw invalid(`the node ID for ${name} is not a valid node ID`);
      }
    }
    return this.admit(levels, axes);
  }

  /** Step 1: CTX-1 and CTX-2. A context that is present but is not a map is malformed (EVAL-31). */
  private checkShape(context: unknown): { levels: Map<string, string>; axes: Map<string, string> } {
    if (context === undefined) {
      return { levels: new Map(), axes: new Map() };
    }
    if (!isPlainObject(context)) {
      throw invalid('the context is not a map');
    }
    for (const member of Object.keys(context)) {
      if (member !== 'levels' && member !== 'axes') {
        throw invalid(`the context has an unknown member: ${member}`);
      }
    }
    return { levels: readNodes(context.levels, 'levels'), axes: readNodes(context.axes, 'axes') };
  }

  /** Step 2: CTX-3, CTX-4 and CTX-5. Naming the root level wins over an unknown name (CTX-11, EVAL-19). */
  private checkNames(levels: ReadonlyMap<string, string>, axes: ReadonlyMap<string, string>): void {
    if (levels.has(this.root) || axes.has(this.root)) {
      throw invalid('a context must not name the root level');
    }
    for (const name of levels.keys()) {
      if (!this.hierarchy.levels.includes(name)) {
        throw new TierwiseError('E_UNKNOWN_SCOPE', `not a declared level: ${name}`);
      }
    }
    for (const name of axes.keys()) {
      if (!this.hierarchy.axes.includes(name)) {
        throw new TierwiseError('E_UNKNOWN_SCOPE', `not a declared axis: ${name}`);
      }
    }
  }

  /** Step 4: CTX-12 to CTX-14, and the evaluation path of CTX-19 and CTX-20. */
  private admit(levels: ReadonlyMap<string, string>, axes: ReadonlyMap<string, string>): AdmittedContext {
    const path: PathScope[] = [{ name: this.root, kind: 'level', node: null }];
    const skippedScopes: SkippedScope[] = [];
    const admitScope = (name: string, kind: ScopeKind, node: string | undefined): void => {
      if (node === undefined) {
        return; // An omitted scope contributes nothing and is not skipped (CTX-21).
      }
      const catalog = this.catalogs.get(name);
      if (catalog === undefined || catalog.has(node)) {
        path.push({ name, kind, node });
      } else if ((this.hierarchy.scopes[name] as ScopeDef).onUnknownNode === 'ignore') {
        skippedScopes.push({ scope: kind, name, node, reason: 'UNKNOWN_NODE' });
      } else {
        throw new TierwiseError('E_UNKNOWN_NODE', `the node ${node} is not listed for ${name}`);
      }
    };
    for (const level of this.hierarchy.levels.slice(1)) {
      admitScope(level, 'level', levels.get(level));
    }
    for (const axis of this.hierarchy.axes) {
      admitScope(axis, 'axis', axes.get(axis));
    }
    return { path, skippedScopes };
  }
}

/** Tells whether a scope is service-sourced; the root level never is (SCOPE-12). */
export function isServiceSourced(model: Model, scope: string): boolean {
  return has(model.hierarchy.scopes, scope) && (model.hierarchy.scopes[scope] as ScopeDef).values === 'service';
}
