package io.github.tierwise.core.internal.eval;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.Model;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * FactSet validation for a decision (FACT-22 to FACT-44), run at step 3 of EVAL-17.
 *
 * <p>Every failure raises {@code E_FACTS_INVALID} and no other code (FACT-42). A malformed FactSet; an
 * entry that is not a declared fact, or is not eligible for the decided entity, whatever its value;
 * a child entity that is not a child of the decided entity; and a non-{@code null} value that is not a
 * valid value of its fact's declared type, all fail validation (FACT-43). A {@code null} value is a
 * missing fact, not an invalid one (FACT-16, FACT-21), and is not checked against the type (FACT-35).
 * Entry names and string values are compared as received (FACT-36).
 */
final class FactSetValidator {

  private final Model model;

  /**
   * Creates a validator.
   *
   * @param model the model of the bundle
   */
  FactSetValidator(Model model) {
    this.model = model;
  }

  /**
   * Validates a FactSet against the decided entity.
   *
   * @param scope the eligible facts of the decided entity
   * @param facts the FactSet; {@code null} counts as empty (EVAL-54)
   * @return the facts in normal form
   * @throws TierwiseException with {@code E_FACTS_INVALID}
   */
  ValidatedFacts validate(EntityScope scope, FactSet facts) {
    FactSet supplied = facts == null ? FactSet.empty() : facts;
    supplied.problem().ifPresent(problem -> {
      throw invalid(problem);
    });
    Map<String, Object> single = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : supplied.facts().entrySet()) {
      if (!scope.isSingleValue(entry.getKey())) {
        throw invalid("not a single-value fact of " + scope.entity() + ": " + entry.getKey());
      }
      single.put(entry.getKey(), checkValue(entry.getKey(), entry.getValue()));
    }
    Map<String, List<Map<String, Object>>> children = new LinkedHashMap<>();
    for (Map.Entry<String, List<Map<String, Object>>> entry : supplied.children().entrySet()) {
      if (!scope.isChild(entry.getKey())) {
        throw invalid("not a child of " + scope.entity() + ": " + entry.getKey());
      }
      children.put(entry.getKey(), checkChildMaps(scope, entry.getKey(), entry.getValue()));
    }
    return new ValidatedFacts(Collections.unmodifiableMap(single), Collections.unmodifiableMap(children));
  }

  private List<Map<String, Object>> checkChildMaps(EntityScope scope, String child, List<Map<String, Object>> maps) {
    List<Map<String, Object>> checked = new ArrayList<>(maps.size());
    for (Map<String, Object> map : maps) {
      Map<String, Object> values = new LinkedHashMap<>();
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        if (!scope.factsOfChild(child).contains(entry.getKey())) {
          throw invalid("not a fact of the child entity " + child + ": " + entry.getKey());
        }
        values.put(entry.getKey(), checkValue(entry.getKey(), entry.getValue()));
      }
      checked.add(Collections.unmodifiableMap(values));
    }
    return Collections.unmodifiableList(checked);
  }

  /** Returns the value in normal form; {@code null} stays {@code null} (FACT-35). */
  private Object checkValue(String fact, Object value) {
    if (value == null) {
      return null;
    }
    FactDef declaration = model.facts().get(fact);
    Optional<Object> valid = RuntimeValues.validate(declaration.type(), value);
    if (valid.isEmpty()) {
      throw invalid("not a valid value of the fact " + fact);
    }
    return valid.get();
  }

  private static TierwiseException invalid(String message) {
    return new TierwiseException(Code.E_FACTS_INVALID, message);
  }
}
