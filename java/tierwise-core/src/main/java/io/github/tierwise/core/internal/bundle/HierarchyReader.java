package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.ScopeDef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code hierarchy} (BUNDLE-29 to BUNDLE-43): the levels, the axes and one scope definition for each
 * non-root level and each axis.
 */
final class HierarchyReader {

  private HierarchyReader() {
  }

  /**
   * Reads the hierarchy.
   *
   * @param value the {@code hierarchy} member
   * @return the hierarchy
   */
  static Hierarchy read(Object value) {
    String at = "content.hierarchy";
    Map<String, Object> members = Members.object(value, at);
    Members.members(members, at, Set.of("levels", "axes", "scopes"), Set.of());
    List<String> levels = Members.stringArray(members.get("levels"), at + ".levels");
    List<String> axes = Members.stringArray(members.get("axes"), at + ".axes");
    if (levels.isEmpty()) {
      throw BundleFault.integrity(at + ".levels lacks the root level (BUNDLE-30)");
    }
    requireDistinctNames(levels, at + ".levels");
    requireDistinctNames(axes, at + ".axes");
    for (String axis : axes) {
      if (levels.contains(axis)) {
        throw BundleFault.integrity(at + ": " + axis + " is both a level and an axis (SCOPE-3)");
      }
    }
    Map<String, Object> scopes = Members.object(members.get("scopes"), at + ".scopes");
    Set<String> expected = new HashSet<>(levels.subList(1, levels.size()));
    expected.addAll(axes);
    if (!expected.equals(scopes.keySet())) {
      throw BundleFault.integrity(at + ".scopes must have exactly one member for each non-root level and each axis (BUNDLE-32)");
    }
    Map<String, ScopeDef> definitions = new LinkedHashMap<>();
    for (String name : levels.subList(1, levels.size())) {
      definitions.put(name, readScope(scopes.get(name), at + ".scopes." + name, false));
    }
    for (String axis : axes) {
      definitions.put(axis, readScope(scopes.get(axis), at + ".scopes." + axis, true));
    }
    return new Hierarchy(levels, axes, definitions);
  }

  /**
   * Requires that the names are distinct and not empty. A level or axis name is never empty (SCOPE-8). The root level
   * is checked too, because the empty name is a defect whichever scope it names.
   *
   * @param names the level or axis names
   * @param at the location of the list
   */
  private static void requireDistinctNames(List<String> names, String at) {
    if (new HashSet<>(names).size() != names.size()) {
      throw BundleFault.integrity(at + " repeats a name (SCOPE-3)");
    }
    if (names.contains("")) {
      throw BundleFault.integrity(at + " has an empty name, which a level or axis may not have (SCOPE-8)");
    }
  }

  private static ScopeDef readScope(Object value, String at, boolean axis) {
    Map<String, Object> members = Members.object(value, at);
    Members.members(members, at, Set.of("values", "nodes"), Set.of("onUnknownNode"));
    String values = Members.oneOf(members.get("values"), Set.of("files", "service"), at + ".values");
    if (axis && "service".equals(values)) {
      throw BundleFault.integrity(at + ".values is service, which only a level may be (BUNDLE-36)");
    }
    ScopeDef.ValuesSource source = ScopeDef.ValuesSource.parse(values).orElseThrow();
    Object nodes = members.get("nodes");
    if ("open".equals(nodes)) {
      if (members.containsKey("onUnknownNode")) {
        throw BundleFault.integrity(at + " has onUnknownNode, which only a closed catalog has (BUNDLE-42)");
      }
      return ScopeDef.openCatalog(source);
    }
    if (!(nodes instanceof List<?>)) {
      throw BundleFault.integrity(at + ".nodes is neither open nor an array (BUNDLE-37)");
    }
    List<String> catalog = readCatalog(nodes, at + ".nodes");
    if (!members.containsKey("onUnknownNode")) {
      throw BundleFault.integrity(at + " lacks onUnknownNode, which a closed catalog has (BUNDLE-42)");
    }
    String policy = Members.oneOf(members.get("onUnknownNode"), Set.of("error", "ignore"), at + ".onUnknownNode");
    ScopeDef.OnUnknownNode onUnknown = ScopeDef.OnUnknownNode.parse(policy).orElseThrow();
    return ScopeDef.closedCatalog(source, catalog, onUnknown);
  }

  private static List<String> readCatalog(Object value, String at) {
    List<String> nodes = new ArrayList<>();
    for (String id : Members.stringArray(value, at)) {
      NodeIds.requireNodeId(id, at);
      nodes.add(id);
    }
    if (!Order.strictlyIncreasing(nodes)) {
      throw BundleFault.integrity(at + " is not in code point order without repeats (BUNDLE-38, BUNDLE-39, BUNDLE-133)");
    }
    return nodes;
  }
}
