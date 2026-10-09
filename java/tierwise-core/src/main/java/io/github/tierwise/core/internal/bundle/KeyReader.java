package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.KeyDef;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads {@code keys} (BUNDLE-51 to BUNDLE-63). It applies the presence rules of the optional members, the
 * strategy names (MERGE-1), the type-to-strategy table (MERGE-3), the default rules (MERGE-4) and the
 * settable-scope order (BUNDLE-54, BUNDLE-127).
 */
final class KeyReader {

  /** The order-dependent strategies (MERGE-1). */
  static final Set<String> ORDER_DEPENDENT = Set.of("override", "deep_merge");

  /** The commutative strategies (MERGE-1). */
  static final Set<String> COMMUTATIVE = Set.of("all", "any", "intersect", "union", "min", "max");

  private static final Set<String> ACROSS_AXES = Set.of("axis_wins", "hierarchy_wins", "max", "min");
  private static final Set<String> SOURCE_FAILURE = Set.of("skip", "deny");

  private KeyReader() {
  }

  /**
   * Reads every key declaration.
   *
   * @param value the {@code keys} member
   * @param hierarchy the hierarchy, which names the scopes that a key may be settable at
   * @return the keys by name
   */
  static Map<String, KeyDef> read(Object value, Hierarchy hierarchy) {
    Map<String, Object> raw = Members.object(value, "content.keys");
    Map<String, KeyDef> keys = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      keys.put(entry.getKey(), readKey(entry.getKey(), entry.getValue(), hierarchy));
    }
    return keys;
  }

  private static KeyDef readKey(String name, Object value, Hierarchy hierarchy) {
    String at = "content.keys." + name;
    Map<String, Object> members = Members.object(value, at);
    Members.members(members, at, Set.of("type", "combine", "settableAt"),
        Set.of("default", "combineAcrossAxes", "onSourceFailure", "kind"));
    TypeDecl type = TypeReader.read(members.get("type"), at + ".type", false);
    String combine = Members.string(members.get("combine"), at + ".combine");
    if (!ORDER_DEPENDENT.contains(combine) && !COMMUTATIVE.contains(combine)) {
      throw BundleFault.integrity(at + ".combine is not a merge strategy (MERGE-1)");
    }
    requireStrategyType(combine, type, at);
    List<String> settableAt = settableAt(members.get("settableAt"), at + ".settableAt", hierarchy);
    boolean orderDependent = ORDER_DEPENDENT.contains(combine);
    boolean settableAtAxis = settableAt.stream().anyMatch(hierarchy::isAxis);
    boolean settableAtService = settableAt.stream().anyMatch(scope -> isService(hierarchy, scope));

    Optional<Object> defaultValue = Optional.empty();
    if (members.containsKey("default")) {
      if (!orderDependent) {
        throw BundleFault.integrity(at + " declares a default, which a commutative key may not have (MERGE-4)");
      }
      Object declared = members.get("default");
      ValueChecker.check(type, declared, ValueChecker.Mode.COMPLETE, at + ".default");
      defaultValue = Optional.of(declared);
    }
    Optional<String> acrossAxes = readAcrossAxes(members, at, orderDependent && settableAtAxis);
    Optional<String> onSourceFailure = readSourceFailure(members, at, settableAtService);
    Optional<String> kind = Optional.empty();
    if (members.containsKey("kind")) {
      kind = Optional.of(Members.string(members.get("kind"), at + ".kind"));
    }
    return new KeyDef(type, combine, defaultValue, settableAt, acrossAxes, onSourceFailure, kind);
  }

  /**
   * Requires that the type of a key is allowed for its strategy (MERGE-3).
   */
  private static void requireStrategyType(String combine, TypeDecl type, String at) {
    boolean allowed = switch (combine) {
      case "override" -> true;
      case "deep_merge" -> "object".equals(type.type());
      case "all", "any" -> "boolean".equals(type.type());
      case "intersect", "union" -> "set".equals(type.type());
      default -> "integer".equals(type.type());
    };
    if (!allowed) {
      throw BundleFault.integrity(at + ": the strategy " + combine + " does not allow the type " + type.type()
          + " (MERGE-3)");
    }
  }

  private static List<String> settableAt(Object value, String at, Hierarchy hierarchy) {
    List<String> names = Members.stringArray(value, at);
    if (names.isEmpty()) {
      throw BundleFault.integrity(at + " is empty (KEY-7)");
    }
    List<String> order = hierarchy.scopeOrder();
    int previous = -1;
    for (String name : names) {
      int position = order.indexOf(name);
      if (position < 0) {
        throw BundleFault.integrity(at + " names the undeclared scope " + name + " (BUNDLE-127)");
      }
      if (position <= previous) {
        throw BundleFault.integrity(at + " is not in scope order without repeats (BUNDLE-54)");
      }
      previous = position;
    }
    return new ArrayList<>(names);
  }

  private static Optional<String> readAcrossAxes(Map<String, Object> members, String at, boolean required) {
    if (!members.containsKey("combineAcrossAxes")) {
      if (required) {
        throw BundleFault.integrity(at + " lacks combineAcrossAxes, which an order-dependent key settable at an axis has (BUNDLE-57)");
      }
      return Optional.empty();
    }
    if (!required) {
      throw BundleFault.integrity(at + " has combineAcrossAxes, which it may not have (BUNDLE-57)");
    }
    return Optional.of(Members.oneOf(members.get("combineAcrossAxes"), ACROSS_AXES, at + ".combineAcrossAxes"));
  }

  private static Optional<String> readSourceFailure(Map<String, Object> members, String at, boolean required) {
    if (!members.containsKey("onSourceFailure")) {
      if (required) {
        throw BundleFault.integrity(at + " lacks onSourceFailure, which a key settable at a service-sourced level has (BUNDLE-59)");
      }
      return Optional.empty();
    }
    if (!required) {
      throw BundleFault.integrity(at + " has onSourceFailure, which it may not have (BUNDLE-59)");
    }
    return Optional.of(Members.oneOf(members.get("onSourceFailure"), SOURCE_FAILURE, at + ".onSourceFailure"));
  }

  private static boolean isService(Hierarchy hierarchy, String name) {
    return hierarchy.scope(name) != null && hierarchy.scope(name).isServiceSourced();
  }
}
