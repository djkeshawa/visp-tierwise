package io.github.visplabs.tierwise.core.internal.lint;

import io.github.visplabs.tierwise.core.Code;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code presets/override} (LINT-49, LINT-50): a definition of a kind or a naming pattern that replaces an
 * earlier definition from another layer (MANIFEST-32). Each replacing definition gets one finding, at its
 * entry, whose message names the replaced file.
 */
final class PresetsOverrideRule implements LintRule {

  @Override
  public String id() {
    return Code.LINT_PRESETS_OVERRIDE;
  }

  @Override
  public List<LintHit> check(LintContext context) {
    List<LintHit> hits = new ArrayList<>();
    replacements(context.index().kindDefinitions(), "kind", hits);
    replacements(context.index().namingDefinitions(), "naming pattern", hits);
    return hits;
  }

  private static void replacements(List<SourceIndex.Definition> definitions, String what, List<LintHit> hits) {
    Map<String, SourceIndex.Definition> previous = new HashMap<>();
    for (SourceIndex.Definition definition : definitions) {
      SourceIndex.Definition replaced = previous.get(definition.name());
      if (replaced != null && replaced.layer() != definition.layer()) {
        hits.add(new LintHit(definition.node(), "the " + what + " " + definition.name() + " replaces the one in "
            + replaced.file()));
      }
      previous.put(definition.name(), definition);
    }
  }
}
