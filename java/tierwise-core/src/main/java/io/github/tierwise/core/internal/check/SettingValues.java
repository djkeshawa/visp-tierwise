package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.model.SettingDef;
import io.github.tierwise.core.internal.model.TypeDecl;
import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the value of a setting, which is the {@code default} of a setting declaration or a value that a
 * binding gives (POL-15, POL-17 to POL-19, BIND-9).
 *
 * <p>An invalid value is {@code E_INVALID_SETTING}, which replaces every code of section 4 (TYPE-71). A
 * {@code list_of} value that repeats an element is {@code E_DUPLICATE_VALUE} at the repeated element
 * instead (POL-19). {@code null} is never a value (POL-18).
 */
final class SettingValues {

  private static final ValueRules RULES = ValueRules.DEFAULT.withOwnerCode(Code.E_INVALID_SETTING);

  private final Findings findings;

  /**
   * Creates the checker.
   *
   * @param findings the accumulator that receives the findings
   */
  SettingValues(Findings findings) {
    this.findings = findings;
  }

  /**
   * Reads a setting value.
   *
   * @param kind the form of the setting
   * @param type the declared type of the setting's fact
   * @param node the value node
   * @return the normalised plain value (a list for {@code list_of}), or empty when it has a fault (which
   *     is reported)
   */
  Optional<Object> read(SettingDef.Kind kind, TypeDecl type, PNode node) {
    ValueValidator validator = new ValueValidator(findings);
    if (kind == SettingDef.Kind.VALUE_OF) {
      return validator.validate(type, node, RULES);
    }
    if (!(node instanceof PSeq list)) {
      findings.error(Code.E_INVALID_SETTING, node, "a list_of value is a list (POL-15, POL-17)");
      return Optional.empty();
    }
    boolean ok = true;
    List<Object> values = new ArrayList<>();
    Set<Object> seen = new HashSet<>();
    for (PNode item : list.items()) {
      Optional<Object> element = validator.validate(type, item, RULES);
      if (element.isEmpty()) {
        ok = false;
      } else if (!seen.add(element.get())) {
        findings.error(Code.E_DUPLICATE_VALUE, item, "a list_of value repeats an element (POL-15, POL-19)");
        ok = false;
      } else {
        values.add(element.get());
      }
    }
    return ok ? Optional.of(values) : Optional.empty();
  }
}
