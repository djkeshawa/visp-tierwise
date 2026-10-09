package io.github.tierwise.core.internal.lint;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.check.NamingRegex;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.project.SourceDocument;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rule {@code naming/<name>} of one naming pattern (LINT-12, LINT-18, LINT-19). It checks the names of
 * one category: {@code key}, {@code enum_value}, {@code node_id} or {@code feature}.
 */
final class NamingRule implements LintRule {

  private final String name;
  private final String category;
  private final NamingRegex regex;

  /**
   * Creates the rule of a pattern.
   *
   * @param name the pattern name
   * @param category the category of names that the pattern checks
   * @param regex the compiled pattern
   */
  NamingRule(String name, String category, NamingRegex regex) {
    this.name = name;
    this.category = category;
    this.regex = regex;
  }

  @Override
  public String id() {
    return Code.NAMING_PREFIX + name;
  }

  @Override
  public List<LintHit> check(LintContext context) {
    return switch (category) {
      case "key" -> keys(context);
      case "enum_value" -> enumValues(context);
      case "node_id" -> nodeIds(context);
      case "feature" -> features(context);
      default -> List.of();
    };
  }

  /** A key of a kind that names this pattern is checked only when its kind is one of them (LINT-16). */
  private List<LintHit> keys(LintContext context) {
    Set<String> kindsOfPattern = new TreeSet<>();
    context.index().kinds().forEach((kind, definition) -> {
      if (name.equals(SourceIndex.text(definition.node(), "naming").orElse(null))) {
        kindsOfPattern.add(kind);
      }
    });
    boolean scoped = !kindsOfPattern.isEmpty();
    List<LintHit> hits = new ArrayList<>();
    for (SourceIndex.Entry key : context.index().keys()) {
      Optional<String> kind = SourceIndex.text(key.node(), "kind");
      boolean applies = !scoped || kind.isPresent() && kindsOfPattern.contains(kind.get());
      if (applies && !regex.matches(key.name())) {
        hits.add(new LintHit(key.node(), "the key " + key.name() + " breaks the naming pattern " + name));
      }
    }
    return hits;
  }

  private List<LintHit> enumValues(LintContext context) {
    List<PNode> values = new ArrayList<>();
    for (SourceIndex.Entry key : context.index().keys()) {
      collectEnumValues(key.node(), values);
    }
    for (SourceIndex.Entry fact : context.index().facts()) {
      collectEnumValues(fact.node(), values);
    }
    for (SourceIndex.Entry feature : context.index().features()) {
      Optional<PNode> returns = feature.node().get("returns");
      if (returns.isPresent() && returns.get() instanceof PMap effect) {
        effect.get("enum").filter(PSeq.class::isInstance).map(PSeq.class::cast)
            .ifPresent(seq -> values.addAll(seq.items()));
      }
    }
    List<LintHit> hits = new ArrayList<>();
    for (PNode value : values) {
      Optional<String> text = Names.text(value);
      if (text.isPresent() && !regex.matches(text.get())) {
        hits.add(new LintHit(value, "the enum value " + text.get() + " breaks the naming pattern " + name));
      }
    }
    return hits;
  }

  /**
   * Collects the entries of every enum type in a type declaration, through object fields and list items
   * (LINT-20). A set's universe is not an enum (LINT-20).
   */
  static void collectEnumValues(PNode declaration, List<PNode> into) {
    if (!(declaration instanceof PMap decl)) {
      return;
    }
    String type = SourceIndex.text(decl, "type").orElse("");
    switch (type) {
      case "enum" -> decl.get("values").filter(PSeq.class::isInstance).map(PSeq.class::cast)
          .ifPresent(seq -> into.addAll(seq.items()));
      case "object" -> decl.get("fields").filter(PMap.class::isInstance).map(PMap.class::cast)
          .ifPresent(fields -> fields.entries().values().forEach(field -> collectEnumValues(field, into)));
      case "list" -> decl.get("items").ifPresent(item -> collectEnumValues(item, into));
      default -> {
      }
    }
  }

  /**
   * The node IDs of the inline lists of the closed scopes, then those of the generated catalogs (LINT-19). An
   * open scope declares none.
   */
  private List<LintHit> nodeIds(LintContext context) {
    List<LintHit> hits = new ArrayList<>();
    for (SourceDocument document : context.index().schemas()) {
      Optional<PNode> hierarchy = document.root().get("decision_hierarchy");
      if (hierarchy.isEmpty() || !(hierarchy.get() instanceof PMap decision)) {
        continue;
      }
      Optional<PNode> scopes = decision.get("scopes");
      if (scopes.isEmpty() || !(scopes.get() instanceof PMap scopeMap)) {
        continue;
      }
      for (Map.Entry<String, PNode> scope : scopeMap.entries().entrySet()) {
        if (scope.getValue() instanceof PMap body && body.get("nodes").orElse(null) instanceof PSeq nodes) {
          for (PNode node : nodes.items()) {
            Optional<String> text = Names.text(node);
            if (text.isPresent() && !regex.matches(text.get())) {
              hits.add(new LintHit(node, "the node ID " + text.get() + " breaks the naming pattern " + name));
            }
          }
        }
      }
    }
    for (PNode node : context.catalogNodes()) {
      Optional<String> text = Names.text(node);
      if (text.isPresent() && !regex.matches(text.get())) {
        hits.add(new LintHit(node, "the node ID " + text.get() + " breaks the naming pattern " + name));
      }
    }
    return hits;
  }

  private List<LintHit> features(LintContext context) {
    List<LintHit> hits = new ArrayList<>();
    for (SourceIndex.Entry feature : context.index().features()) {
      if (!regex.matches(feature.name())) {
        hits.add(new LintHit(feature.node(), "the feature " + feature.name() + " breaks the naming pattern " + name));
      }
    }
    return hits;
  }
}
