package io.github.visplabs.tierwise.core.internal.eval;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.ScopeKind;
import io.github.visplabs.tierwise.core.SkippedScope;
import io.github.visplabs.tierwise.core.TierwiseException;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Context validation: the four steps of CTX-9, in order, followed by the construction of the evaluation
 * path.
 *
 * <ol>
 *   <li>Shape ([CTX-1], [CTX-2]): a malformed context raises {@code E_CONTEXT_INVALID}.
 *   <li>Scope names ([CTX-3] to [CTX-5]): a root-level name raises {@code E_CONTEXT_INVALID}, whatever
 *       else is wrong ([CTX-11]); an undeclared name, or a name in the wrong map, raises
 *       {@code E_UNKNOWN_SCOPE}.
 *   <li>Node ID structure ([CTX-6]): {@code E_CONTEXT_INVALID}.
 *   <li>Admission ([CTX-12] to [CTX-14]): {@code E_UNKNOWN_NODE} for an unlisted node in a closed
 *       catalog under {@code on_unknown_node: error}; the scope is skipped under {@code ignore}.
 * </ol>
 *
 * <p>Names and node IDs are compared exactly, as received ([CTX-4], [CTX-8]); nothing is normalised
 * (NORM-3). An instance is immutable and may be shared between threads.
 */
final class ContextValidator {

  /** The longest node ID, in code points (SCOPE-33). */
  static final int MAX_NODE_ID_CODE_POINTS = 128;

  private final Hierarchy hierarchy;
  private final Map<String, ScopeDef> scopes;
  private final Map<String, Set<String>> catalogs;

  /**
   * Creates a validator for the hierarchy of a model.
   *
   * @param model the model of a loaded bundle
   */
  ContextValidator(Model model) {
    this.hierarchy = model.hierarchy();
    this.scopes = model.hierarchy().scopes();
    Map<String, Set<String>> index = new HashMap<>();
    for (Map.Entry<String, ScopeDef> scope : scopes.entrySet()) {
      if (!scope.getValue().open()) {
        index.put(scope.getKey(), new HashSet<>(scope.getValue().nodes()));
      }
    }
    this.catalogs = Map.copyOf(index);
  }

  /**
   * Validates a context and builds the evaluation path.
   *
   * @param context the context; {@code null} counts as the empty context (EVAL-54)
   * @return the admitted context
   * @throws io.github.visplabs.tierwise.core.TierwiseException with {@code E_CONTEXT_INVALID},
   *     {@code E_UNKNOWN_SCOPE} or {@code E_UNKNOWN_NODE}, for the first failing step
   */
  AdmittedContext validate(Context context) {
    Context supplied = context == null ? Context.empty() : context;
    checkShape(supplied);
    checkNames(supplied);
    checkNodeIds(supplied);
    return admit(supplied);
  }

  private static void checkShape(Context context) {
    context.problem().ifPresent(problem -> {
      throw new TierwiseException(Code.E_CONTEXT_INVALID, problem);
    });
  }

  private void checkNames(Context context) {
    String root = hierarchy.rootLevel();
    if (context.levels().containsKey(root) || context.axes().containsKey(root)) {
      throw new TierwiseException(Code.E_CONTEXT_INVALID, "a context must not name the root level");
    }
    for (String name : context.levels().keySet()) {
      if (!hierarchy.isLevel(name)) {
        throw new TierwiseException(Code.E_UNKNOWN_SCOPE, "not a declared level: " + name);
      }
    }
    for (String name : context.axes().keySet()) {
      if (!hierarchy.isAxis(name)) {
        throw new TierwiseException(Code.E_UNKNOWN_SCOPE, "not a declared axis: " + name);
      }
    }
  }

  private static void checkNodeIds(Context context) {
    for (Map.Entry<String, String> level : context.levels().entrySet()) {
      checkNodeId(level.getKey(), level.getValue());
    }
    for (Map.Entry<String, String> axis : context.axes().entrySet()) {
      checkNodeId(axis.getKey(), axis.getValue());
    }
  }

  /** CTX-6: SCOPE-33, SCOPE-34 and SCOPE-37, checked on the node ID as received. */
  private static void checkNodeId(String scope, String node) {
    int length = CodePoints.count(node);
    boolean valid = length >= 1 && length <= MAX_NODE_ID_CODE_POINTS && !CodePoints.hasUnpairedSurrogate(node);
    for (int i = 0; valid && i < node.length(); i++) {
      char c = node.charAt(i);
      valid = !(c <= 0x1F || (c >= 0x7F && c <= 0x9F));
    }
    if (!valid) {
      throw new TierwiseException(Code.E_CONTEXT_INVALID, "the node ID for " + scope + " is not a valid node ID");
    }
  }

  private AdmittedContext admit(Context context) {
    List<PathScope> path = new ArrayList<>();
    List<SkippedScope> skipped = new ArrayList<>();
    path.add(new PathScope(hierarchy.rootLevel(), ScopeKind.LEVEL, null));
    List<String> levels = hierarchy.levels();
    for (String level : levels.subList(1, levels.size())) {
      admitScope(level, ScopeKind.LEVEL, context.levels().get(level), path, skipped);
    }
    for (String axis : hierarchy.axes()) {
      admitScope(axis, ScopeKind.AXIS, context.axes().get(axis), path, skipped);
    }
    return new AdmittedContext(path, skipped);
  }

  private void admitScope(
      String name, ScopeKind kind, String node, List<PathScope> path, List<SkippedScope> skipped) {
    if (node == null) {
      return;
    }
    ScopeDef definition = scopes.get(name);
    Set<String> catalog = catalogs.get(name);
    if (definition == null || definition.open() || catalog == null || catalog.contains(node)) {
      path.add(new PathScope(name, kind, node));
    } else if (definition.onUnknownNode().orElse(ScopeDef.OnUnknownNode.ERROR) == ScopeDef.OnUnknownNode.IGNORE) {
      skipped.add(new SkippedScope(kind, name, node));
    } else {
      throw new TierwiseException(Code.E_UNKNOWN_NODE, "the node " + node + " is not listed for " + name);
    }
  }
}
