package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import io.github.visplabs.tierwise.core.internal.project.Names;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import io.github.visplabs.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the {@code decision_hierarchy} declaration into a {@link Hierarchy} (SCOPE-1 to SCOPE-16 and SCOPE-18).
 *
 * <p>The levels and the axes are read in document order, so that a repeated name is reported at the later
 * occurrence (FIND-32, SCOPE-3, SCOPE-4). The scopes are checked against the levels and axes only when those
 * lists are trustworthy. A faulty list gives no reliable set of non-root scopes, so no scope is reported as
 * extra or missing because of it (SCOPE-14); the scope entries that are present are still read.
 */
final class HierarchyDeclaration {

  private static final Set<String> MEMBERS = Set.of("levels", "axes", "scopes");

  /**
   * The names of a level or axis list.
   *
   * @param list {@code true} when the node is a sequence
   * @param clean {@code true} when every item is a valid name
   * @param names the valid names in document order, a repeated name included
   * @param first the first name, when the first item is valid
   */
  private record NameList(boolean list, boolean clean, List<String> names, Optional<String> first) {
  }

  private final ScopeReport report;
  private final HierarchyScope scopeReader;

  /**
   * Creates a reader.
   *
   * @param report the report that receives the faults
   * @param catalog the catalog locator, or {@code null} when the project directory is not known
   */
  HierarchyDeclaration(ScopeReport report, HierarchyCatalog catalog) {
    this.report = report;
    this.scopeReader = new HierarchyScope(report, catalog);
  }

  /**
   * Reads the declaration.
   *
   * @param declaration the value of the {@code decision_hierarchy} member
   * @return the hierarchy, or empty when the declaration has a fault or cannot be read
   */
  Optional<Hierarchy> read(PNode declaration) {
    int before = report.faults();
    if (!(declaration instanceof PMap decision)) {
      report.at(Code.E_SCOPE_DECLARATION, declaration, "decision_hierarchy is a map (SCOPE-9)");
      return Optional.empty();
    }
    for (Map.Entry<String, PNode> member : decision.entries().entrySet()) {
      if (!MEMBERS.contains(member.getKey())) {
        report.at(Code.E_SCOPE_DECLARATION, member.getValue(),
            "decision_hierarchy has the member " + member.getKey() + " (SCOPE-9)");
      }
    }
    Set<String> seen = new HashSet<>();
    NameList levels = null;
    NameList axes = null;
    for (Map.Entry<String, PNode> member : decision.entries().entrySet()) {
      if (member.getKey().equals("levels")) {
        levels = nameList(member.getValue(), true, seen);
      } else if (member.getKey().equals("axes")) {
        axes = nameList(member.getValue(), false, seen);
      }
    }
    if (levels == null) {
      report.at(Code.E_SCOPE_DECLARATION, decision, "decision_hierarchy has no levels member (SCOPE-9)");
    }
    boolean levelsClean = levels != null && levels.list() && levels.clean() && !levels.names().isEmpty();
    boolean axesClean = axes == null || (axes.list() && axes.clean());
    boolean crossCheck = levelsClean && axesClean;
    Optional<String> root = levels != null && levels.list() ? levels.first() : Optional.empty();
    List<String> levelNames = levels == null ? List.of() : levels.names();
    List<String> axisNames = axes == null ? List.of() : axes.names();
    Set<String> nonRoot = new LinkedHashSet<>();
    if (crossCheck) {
      // The root level is never a non-root scope, even when it is also repeated in levels or listed in axes (SCOPE-11).
      String rootName = levelNames.get(0);
      levelNames.stream().skip(1).filter(name -> !name.equals(rootName)).forEach(nonRoot::add);
      axisNames.stream().filter(name -> !name.equals(rootName)).forEach(nonRoot::add);
    }
    Map<String, ScopeDef> built = scopes(decision, root, crossCheck, nonRoot, axisNames, axesClean);
    if (report.faults() != before || !report.clean() || !crossCheck) {
      return Optional.empty();
    }
    Map<String, ScopeDef> definitions = new LinkedHashMap<>();
    for (String name : nonRoot) {
      if (!built.containsKey(name)) {
        return Optional.empty();
      }
      definitions.put(name, built.get(name));
    }
    return Optional.of(new Hierarchy(levelNames, axisNames, definitions));
  }

  /** Reads the {@code scopes} member and checks it against the root and non-root names (SCOPE-12, SCOPE-14 to 16). */
  private Map<String, ScopeDef> scopes(PMap decision, Optional<String> root, boolean crossCheck, Set<String> nonRoot,
      List<String> axisNames, boolean axesClean) {
    Map<String, ScopeDef> built = new LinkedHashMap<>();
    Optional<PNode> member = decision.get("scopes");
    if (member.isEmpty()) {
      if (crossCheck && !nonRoot.isEmpty()) {
        report.at(Code.E_SCOPE_DECLARATION, decision,
            "scopes is absent, but the hierarchy has non-root scopes (SCOPE-14, SCOPE-16)");
      }
      return built;
    }
    PNode node = member.get();
    if (node instanceof PScalar scalar && scalar.kind() == PScalar.Kind.NULL) {
      if (crossCheck && !nonRoot.isEmpty()) {
        report.at(Code.E_SCOPE_DECLARATION, node,
            "scopes is null, but the hierarchy has non-root scopes (SCOPE-14, SCOPE-16)");
      }
      return built;
    }
    if (!(node instanceof PMap scopeMap)) {
      report.at(Code.E_SCOPE_DECLARATION, node, "scopes is a map (SCOPE-15)");
      return built;
    }
    Set<String> present = new HashSet<>();
    for (Map.Entry<String, PNode> entry : scopeMap.entries().entrySet()) {
      String name = Names.nfc(entry.getKey());
      if (root.filter(name::equals).isPresent()) {
        report.at(Code.E_SCOPE_DECLARATION, entry.getValue(), "the root level has no scope entry (SCOPE-12)");
        continue;
      }
      if (crossCheck && !nonRoot.contains(name)) {
        report.at(Code.E_SCOPE_DECLARATION, entry.getValue(),
            "the scope " + name + " is not a non-root level or axis (SCOPE-14)");
        continue;
      }
      present.add(name);
      HierarchyScope.Kind kind;
      if (axesClean && axisNames.contains(name)) {
        kind = HierarchyScope.Kind.AXIS;
      } else {
        kind = crossCheck ? HierarchyScope.Kind.LEVEL : HierarchyScope.Kind.UNKNOWN;
      }
      scopeReader.read(entry.getValue(), kind).ifPresent(definition -> built.put(name, definition));
    }
    if (crossCheck) {
      for (String name : nonRoot) {
        if (!present.contains(name)) {
          report.at(Code.E_SCOPE_DECLARATION, scopeMap,
              "the non-root scope " + name + " has no entry (SCOPE-14)");
        }
      }
    }
    return built;
  }

  /** Reads a level or axis list (SCOPE-1, SCOPE-2, SCOPE-5, SCOPE-8, SCOPE-9, SCOPE-3 and SCOPE-4). */
  private NameList nameList(PNode node, boolean required, Set<String> seen) {
    if (!(node instanceof PSeq list)) {
      report.at(Code.E_SCOPE_DECLARATION, node, "levels and axes are lists (SCOPE-9)");
      return new NameList(false, false, List.of(), Optional.empty());
    }
    if (required && list.items().isEmpty()) {
      report.at(Code.E_SCOPE_DECLARATION, node, "levels is not an empty list (SCOPE-1, SCOPE-9)");
      return new NameList(true, false, List.of(), Optional.empty());
    }
    boolean clean = true;
    List<String> names = new ArrayList<>();
    Optional<String> first = Optional.empty();
    for (int index = 0; index < list.items().size(); index++) {
      PNode item = list.items().get(index);
      Optional<String> name = Names.text(item).map(Names::nfc);
      if (name.isEmpty()) {
        report.at(Code.E_SCOPE_DECLARATION, item, "a level or axis name is a string (SCOPE-2, SCOPE-9)");
        clean = false;
        continue;
      }
      if (name.get().isEmpty() || HierarchyNodeId.hasUnpairedSurrogate(name.get())) {
        report.at(Code.E_SCOPE_DECLARATION, item,
            "a level or axis name is not empty and has no unpaired surrogate (SCOPE-8)");
        clean = false;
        continue;
      }
      if (!seen.add(name.get())) {
        report.at(Code.E_SCOPE_DECLARATION, item,
            "the name " + name.get() + " is repeated across levels and axes (SCOPE-3, SCOPE-4)");
      }
      if (index == 0) {
        first = name;
      }
      names.add(name.get());
    }
    return new NameList(true, clean, names, first);
  }
}
