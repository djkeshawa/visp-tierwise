package io.github.tierwise.core.internal.lint;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.model.KeyDef;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.project.SourceDocument;
import io.github.tierwise.core.internal.project.ValueGroup;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * {@code values/no-op} (LINT-23 to LINT-25): an authored value that equals the identity of its key's
 * commutative strategy (MERGE-5). The value is compared after normalisation, so the model's value is used.
 */
final class ValuesNoOpRule implements LintRule {

  @Override
  public String id() {
    return Code.LINT_VALUES_NO_OP;
  }

  @Override
  public List<LintHit> check(LintContext context) {
    Model model = context.model();
    List<LintHit> hits = new ArrayList<>();
    for (SourceDocument document : context.sources().values()) {
      for (ValueGroup group : document.groups()) {
        String nodeKey = group.node().orElse("");
        for (Map.Entry<String, PNode> entry : group.entries().entries().entrySet()) {
          String key = Names.nfc(entry.getKey());
          KeyDef definition = model.keys().get(key);
          if (definition == null) {
            continue;
          }
          Optional<Object> identity = identity(definition);
          if (identity.isPresent() && Objects.equals(identity.get(), model.values().get(group.scope(), nodeKey, key))) {
            hits.add(new LintHit(entry.getValue(), "the value of " + key + " is the identity of its strategy"));
          }
        }
      }
    }
    return hits;
  }

  /** The identity of a commutative strategy (MERGE-5); empty for an order-dependent strategy or min and max. */
  static Optional<Object> identity(KeyDef key) {
    return switch (key.combine()) {
      case "all" -> Optional.of(Boolean.TRUE);
      case "any" -> Optional.of(Boolean.FALSE);
      case "intersect" -> Optional.of(List.copyOf(key.type().universe()));
      case "union" -> Optional.of(List.of());
      default -> Optional.empty();
    };
  }
}
