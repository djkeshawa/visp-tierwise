package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.project.Names;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.Map;

/**
 * The static check of MERGE-26: for a {@code deep_merge} key, a value that sets an optional object field
 * which the starting value does not set must give a complete object for that field (MERGE-9), at any
 * depth.
 *
 * <p>The check runs on a value that already passed validation as a partial object (MERGE-11, MERGE-12).
 * Where the starting value sets the field, the value may stay partial for it and the check goes one level
 * deeper. Where it does not, the field is validated again as a complete object, which can fail only for a
 * missing required field, and the finding is {@code E_TYPE_MISMATCH} at the object that lacks it
 * (TYPE-63).
 */
final class ValueMerge {

  private final ValueValidator validator;

  ValueMerge(ValueValidator validator) {
    this.validator = validator;
  }

  /**
   * Checks one authored value.
   *
   * @param type the object declaration that the value follows
   * @param node the authored value
   * @param value the validated, normalised value
   * @param start the starting value at the same depth, or {@code null} when the starting value does not
   *     set the object
   * @return {@code true} when the value is acceptable; a fault is reported
   */
  boolean check(TypeDecl type, PNode node, Map<?, ?> value, Map<?, ?> start) {
    if (!(node instanceof PMap map)) {
      return true;
    }
    boolean ok = true;
    for (Map.Entry<String, PNode> entry : map.entries().entrySet()) {
      String field = Names.nfc(entry.getKey());
      TypeDecl declared = type.fields().get(field);
      if (declared == null || !declared.type().equals("object") || !(value.get(field) instanceof Map<?, ?> given)) {
        continue;
      }
      if (start != null && start.get(field) instanceof Map<?, ?> inner) {
        ok &= check(declared, entry.getValue(), given, inner);
      } else {
        ok &= validator.validate(declared, entry.getValue(), ValueRules.DEFAULT).isPresent();
      }
    }
    return ok;
  }
}
