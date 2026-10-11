package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Checks the {@code context} of an example (POL-31, POL-43, POL-44). The context must be one that section 2
 * accepts: no root level (CTX-3), only declared levels under {@code levels} and declared axes under
 * {@code axes} (CTX-4, CTX-5), node IDs of valid form (CTX-6, SCOPE-33, SCOPE-34, SCOPE-37), and, for a
 * closed catalog, a listed node under either value of {@code on_unknown_node} (POL-44). Node IDs are compared
 * as written, not normalised (CTX-8).
 */
final class ExampleContext {

  /**
   * The parsed context.
   *
   * @param levels the non-root levels that the context names, with their node IDs
   * @param axes the axes that the context names, with their node IDs
   * @param valid {@code false} when a finding was reported
   */
  record Parsed(Map<String, String> levels, Map<String, String> axes, boolean valid) {
  }

  private ExampleContext() {
  }

  /**
   * Parses and checks a context.
   *
   * @param node the {@code context} node
   * @param hierarchy the decision hierarchy
   * @param findings the findings to report to
   * @return the parsed context
   */
  static Parsed parse(PNode node, Hierarchy hierarchy, Findings findings) {
    if (!(node instanceof PMap context)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "the context of an example is not a mapping (POL-43)");
      return new Parsed(Map.of(), Map.of(), false);
    }
    boolean valid = true;
    for (Entry<String, PNode> member : context.entries().entrySet()) {
      if (!member.getKey().equals("levels") && !member.getKey().equals("axes")) {
        findings.error(Code.E_INVALID_DECLARATION, member.getValue(),
            "the context has the member " + member.getKey() + ", which is not allowed (CTX-2)");
        valid = false;
      }
    }
    Map<String, String> levels = new LinkedHashMap<>();
    Map<String, String> axes = new LinkedHashMap<>();
    valid &= readNodes(context, "levels", hierarchy, findings, levels);
    valid &= readNodes(context, "axes", hierarchy, findings, axes);
    return new Parsed(levels, axes, valid);
  }

  private static boolean readNodes(PMap context, String member, Hierarchy hierarchy, Findings findings,
      Map<String, String> into) {
    PNode map = context.get(member).orElse(null);
    if (map == null) {
      return true;
    }
    if (!(map instanceof PMap names)) {
      findings.error(Code.E_INVALID_DECLARATION, map, "the context member " + member + " is not a mapping (POL-43)");
      return false;
    }
    boolean valid = true;
    for (Entry<String, PNode> entry : names.entries().entrySet()) {
      String name = entry.getKey();
      PNode value = entry.getValue();
      if (name.equals(hierarchy.rootLevel())) {
        findings.error(Code.E_INVALID_DECLARATION, value, "the context names the root level (CTX-3)");
        valid = false;
        continue;
      }
      boolean declared = member.equals("levels") ? hierarchy.isLevel(name) : hierarchy.isAxis(name);
      if (!declared) {
        findings.error(Code.E_UNKNOWN_SCOPE, value, "the context names " + name + ", which is not declared (CTX-4)");
        valid = false;
        continue;
      }
      if (!(value instanceof PScalar scalar) || scalar.kind() != PScalar.Kind.STRING) {
        findings.error(Code.E_INVALID_DECLARATION, value, "the node ID of " + name + " is not a string (POL-43)");
        valid = false;
        continue;
      }
      String node = (String) scalar.value();
      if (!validNodeId(node)) {
        findings.error(Code.E_INVALID_NODE_ID, value, "the node ID of " + name + " is malformed (SCOPE-38)");
        valid = false;
        continue;
      }
      ScopeDef definition = hierarchy.scope(name);
      if (definition != null && !definition.open() && !definition.nodes().contains(node)) {
        findings.error(Code.E_UNKNOWN_NODE, value, "the node " + node + " is not in the closed catalog of " + name
            + " (POL-44)");
        valid = false;
        continue;
      }
      into.put(name, node);
    }
    return valid;
  }

  /** A node ID of 1 to 128 code points, with no control character and no unpaired surrogate (SCOPE-33 to 37). */
  static boolean validNodeId(String id) {
    int length = id.codePointCount(0, id.length());
    if (length < 1 || length > 128 || ValueText.hasUnpairedSurrogate(id)) {
      return false;
    }
    return id.codePoints().allMatch(cp -> cp > 0x1F && (cp < 0x7F || cp > 0x9F));
  }
}
