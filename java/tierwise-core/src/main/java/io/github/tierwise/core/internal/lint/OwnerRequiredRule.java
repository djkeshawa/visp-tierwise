package io.github.tierwise.core.internal.lint;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.yaml.PMap;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code schema/owner-required} (LINT-46 to LINT-48): with at least one namespace in the manifest, a key
 * declared in a shared file that has no {@code owner} member. Without namespaces the rule reports nothing.
 */
final class OwnerRequiredRule implements LintRule {

  @Override
  public String id() {
    return Code.LINT_SCHEMA_OWNER_REQUIRED;
  }

  @Override
  public List<LintHit> check(LintContext context) {
    List<LintHit> hits = new ArrayList<>();
    if (!hasNamespaces(context)) {
      return hits;
    }
    for (SourceIndex.Entry key : context.index().keys()) {
      if (!key.document().isNamespaced() && !key.node().has("owner")) {
        hits.add(new LintHit(key.node(), "the key " + key.name() + " has no owner"));
      }
    }
    return hits;
  }

  private static boolean hasNamespaces(LintContext context) {
    return context.sources().manifest()
        .flatMap(root -> root.get("namespaces"))
        .filter(PMap.class::isInstance)
        .map(namespaces -> !((PMap) namespaces).entries().isEmpty())
        .orElse(false);
  }
}
