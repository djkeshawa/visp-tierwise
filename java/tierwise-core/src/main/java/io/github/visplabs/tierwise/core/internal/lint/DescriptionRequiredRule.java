package io.github.visplabs.tierwise.core.internal.lint;

import io.github.visplabs.tierwise.core.Code;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code schema/description-required} (LINT-44, LINT-45): a key, feature or fact declaration with no
 * description, or with one that holds only ASCII white space.
 */
final class DescriptionRequiredRule implements LintRule {

  @Override
  public String id() {
    return Code.LINT_SCHEMA_DESCRIPTION_REQUIRED;
  }

  @Override
  public List<LintHit> check(LintContext context) {
    List<LintHit> hits = new ArrayList<>();
    for (List<SourceIndex.Entry> section : List.of(context.index().keys(), context.index().features(),
        context.index().facts())) {
      for (SourceIndex.Entry declaration : section) {
        if (missingDescription(declaration)) {
          hits.add(new LintHit(declaration.node(), "the declaration of " + declaration.name() + " has no description"));
        }
      }
    }
    return hits;
  }

  private static boolean missingDescription(SourceIndex.Entry declaration) {
    var description = declaration.node().get("description");
    if (description.isEmpty()) {
      return true;
    }
    return SourceIndex.text(declaration.node(), "description").map(DescriptionRequiredRule::blank).orElse(false);
  }

  /** Tells whether a text consists only of ASCII space, tab, carriage return and line feed (LINT-45). */
  static boolean blank(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
        return false;
      }
    }
    return true;
  }
}
