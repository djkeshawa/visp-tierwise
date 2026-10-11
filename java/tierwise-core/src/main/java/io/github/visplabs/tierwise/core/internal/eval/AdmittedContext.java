package io.github.visplabs.tierwise.core.internal.eval;

import io.github.visplabs.tierwise.core.SkippedScope;
import java.util.List;

/**
 * A context that passed all four validation steps of CTX-9.
 *
 * @param path the evaluation path in order: the root level, the admitted levels from least to most
 *     specific, then the admitted axes in declared order (MERGE-13)
 * @param skippedScopes the scopes that admission skipped, ordered levels first, then axes (PROV-58)
 */
record AdmittedContext(List<PathScope> path, List<SkippedScope> skippedScopes) {

  AdmittedContext {
    path = List.copyOf(path);
    skippedScopes = List.copyOf(skippedScopes);
  }
}
