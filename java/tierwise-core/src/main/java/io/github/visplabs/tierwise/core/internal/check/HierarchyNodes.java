package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.project.CodePointOrder;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the items of a closed catalog, inline or generated (SCOPE-20, SCOPE-38, SCOPE-42).
 *
 * <p>An item that is not a string is reported as {@code E_SCOPE_DECLARATION} and is never converted (SCOPE-20).
 * A string that breaks the form rules is {@code E_INVALID_NODE_ID} (SCOPE-38) and is never checked for a
 * duplicate (SCOPE-39). A repeat after NFC normalisation is {@code E_DUPLICATE_NODE}, reported at the later
 * item (SCOPE-42, FIND-32).
 */
final class HierarchyNodes {

  private HierarchyNodes() {
  }

  /**
   * Validates the items of one catalog and returns its node IDs.
   *
   * @param report the report that receives the faults
   * @param items the items in document order
   * @return the valid node IDs in NFC, in code point order (SCOPE-31, BUNDLE-39)
   */
  static List<String> validate(ScopeReport report, List<PNode> items) {
    Set<String> seen = new HashSet<>();
    List<String> nodes = new ArrayList<>();
    for (PNode item : items) {
      if (!(item instanceof PScalar scalar) || scalar.kind() != PScalar.Kind.STRING) {
        report.at(Code.E_SCOPE_DECLARATION, item, "a node ID is a string, and it is never converted (SCOPE-20)");
        continue;
      }
      Optional<String> id = HierarchyNodeId.admissible((String) scalar.value());
      if (id.isEmpty()) {
        report.at(Code.E_INVALID_NODE_ID, item, "a node ID is 1 to 128 code points with no control character "
            + "and no unpaired surrogate (SCOPE-33, SCOPE-34, SCOPE-37, SCOPE-38)");
        continue;
      }
      if (!seen.add(id.get())) {
        report.at(Code.E_DUPLICATE_NODE, item, "the node ID repeats an earlier one after NFC normalisation (SCOPE-42)");
        continue;
      }
      nodes.add(id.get());
    }
    nodes.sort(CodePointOrder.ORDER);
    return nodes;
  }
}
