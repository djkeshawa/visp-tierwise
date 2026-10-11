package io.github.visplabs.tierwise.core.internal.lint;

import io.github.visplabs.tierwise.core.internal.check.NamingRegex;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The lint rules of section 13.6 for one project: the fixed rules and one naming rule for each pattern. */
public final class LintRules {

  private LintRules() {
  }

  /**
   * Returns the rules that apply to a project. A naming pattern whose text is outside the subset gets no
   * rule: the engine reports that pattern, so the lint stage does not run. The patterns are compiled by the
   * same reader that the engine uses for declarations (LINT-17), so the two cannot disagree on validity.
   *
   * @param context the lint context
   * @return the rules, in a fixed order
   */
  public static List<LintRule> of(LintContext context) {
    List<LintRule> rules = new ArrayList<>(List.of(new ValuesNoOpRule(), new DeadRuleRule(),
        new RequiredPerBindingRule(), new MixedCollectionRule(), new UnusedRule(), new DescriptionRequiredRule(),
        new OwnerRequiredRule(), new PresetsOverrideRule()));
    for (Map.Entry<String, SourceIndex.Definition> pattern : context.index().naming().entrySet()) {
      PMap node = pattern.getValue().node();
      String category = SourceIndex.text(node, "category").orElse("");
      Optional<NamingRegex> compiled = SourceIndex.text(node, "pattern")
          .flatMap(text -> NamingRegex.compile(text).regex());
      compiled.ifPresent(regex -> rules.add(new NamingRule(pattern.getKey(), category, regex)));
    }
    return rules;
  }
}
