package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.model.ScopeDef;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.PScalar;
import io.github.tierwise.core.internal.yaml.PSeq;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads one scope entry of {@code decision_hierarchy.scopes} into a {@link ScopeDef} (SCOPE-17 to SCOPE-26).
 *
 * <p>The entry is built only when it has no fault. The caller checks the whole declaration, so a fault in one
 * entry leaves the whole hierarchy unbuilt (FIND-35).
 */
final class HierarchyScope {

  /** What the scope is in the hierarchy, which decides whether {@code values: service} is allowed (SCOPE-26). */
  enum Kind {
    /** A non-root level. */
    LEVEL,
    /** An axis. */
    AXIS,
    /** Not known, because the level or axis lists are faulty. */
    UNKNOWN
  }

  /** The form of the node catalog that the {@code nodes} member gives. */
  private enum Form {
    /** An inline list or a generated catalog, which admits only the listed nodes. */
    CLOSED,
    /** The string {@code open}, which admits any valid node ID. */
    OPEN,
    /** The member is faulty, so nothing about the catalog is known. */
    UNKNOWN
  }

  /**
   * The catalog of a scope.
   *
   * @param form the form that the member gives
   * @param nodes the node IDs of a closed catalog, in code point order
   */
  private record NodeSpec(Form form, List<String> nodes) {
  }

  private static final List<String> MEMBERS = List.of("nodes", "on_unknown_node", "values");

  private final ScopeReport report;
  private final HierarchyCatalog catalog;

  /**
   * Creates a reader.
   *
   * @param report the report that receives the faults
   * @param catalog the catalog locator, or {@code null} when the project directory is not known
   */
  HierarchyScope(ScopeReport report, HierarchyCatalog catalog) {
    this.report = report;
    this.catalog = catalog;
  }

  /**
   * Reads one scope entry.
   *
   * @param entryNode the entry, a map from member name to node
   * @param kind what the scope is in the hierarchy
   * @return the definition, or empty when the entry has a fault
   */
  Optional<ScopeDef> read(PNode entryNode, Kind kind) {
    int before = report.faults();
    if (!(entryNode instanceof PMap entry)) {
      report.at(Code.E_SCOPE_DECLARATION, entryNode, "a scope entry is a map (SCOPE-17)");
      return Optional.empty();
    }
    for (Map.Entry<String, PNode> member : entry.entries().entrySet()) {
      if (!MEMBERS.contains(member.getKey())) {
        report.at(Code.E_SCOPE_DECLARATION, member.getValue(),
            "a scope entry has the member " + member.getKey() + " (SCOPE-18)");
      }
    }
    Optional<ScopeDef.ValuesSource> source = values(entry, kind);
    NodeSpec spec = nodes(entry, entryNode);
    Optional<ScopeDef.OnUnknownNode> policy = onUnknownNode(entry, entryNode, spec.form());
    if (report.faults() != before || source.isEmpty() || spec.form() == Form.UNKNOWN) {
      return Optional.empty();
    }
    if (spec.form() == Form.OPEN) {
      return Optional.of(ScopeDef.openCatalog(source.get()));
    }
    return policy.map(p -> ScopeDef.closedCatalog(source.get(), spec.nodes(), p));
  }

  /** The {@code values} member: absent means file-sourced (SCOPE-25); service is for a level (SCOPE-26). */
  private Optional<ScopeDef.ValuesSource> values(PMap entry, Kind kind) {
    Optional<PNode> node = entry.get("values");
    if (node.isEmpty()) {
      return Optional.of(ScopeDef.ValuesSource.FILES);
    }
    Optional<String> text = Names.text(node.get());
    if (text.filter("files"::equals).isPresent()) {
      return Optional.of(ScopeDef.ValuesSource.FILES);
    }
    if (text.filter("service"::equals).isPresent()) {
      if (kind == Kind.AXIS) {
        report.at(Code.E_SCOPE_DECLARATION, node.get(), "an axis cannot be service-sourced (SCOPE-26)");
        return Optional.empty();
      }
      return Optional.of(ScopeDef.ValuesSource.SERVICE);
    }
    report.at(Code.E_SCOPE_DECLARATION, node.get(), "values is files or service (SCOPE-25)");
    return Optional.empty();
  }

  /** The {@code nodes} member: an inline list, a {@code from} object, or the string open (SCOPE-19). */
  private NodeSpec nodes(PMap entry, PNode entryNode) {
    Optional<PNode> member = entry.get("nodes");
    if (member.isEmpty()) {
      report.at(Code.E_SCOPE_DECLARATION, entryNode, "a scope entry has no nodes member (SCOPE-17)");
      return new NodeSpec(Form.UNKNOWN, List.of());
    }
    PNode node = member.get();
    if (node instanceof PSeq list) {
      return new NodeSpec(Form.CLOSED, HierarchyNodes.validate(report, list.items()));
    }
    if (node instanceof PMap object) {
      return fromCatalog(object, node);
    }
    if (node instanceof PScalar scalar && scalar.kind() == PScalar.Kind.STRING && "open".equals(scalar.value())) {
      return new NodeSpec(Form.OPEN, List.of());
    }
    report.at(Code.E_SCOPE_DECLARATION, node, "nodes is an inline list, a from object or open (SCOPE-19)");
    return new NodeSpec(Form.UNKNOWN, List.of());
  }

  /** A {@code from} object: its only property is {@code from}, with a non-empty string path (SCOPE-19). */
  private NodeSpec fromCatalog(PMap object, PNode member) {
    Optional<PNode> from = object.get("from");
    Optional<String> path = from.flatMap(Names::text).filter(text -> !text.isEmpty());
    if (object.entries().size() != 1 || path.isEmpty()) {
      report.at(Code.E_SCOPE_DECLARATION, member,
          "a from object has only the property from, with a non-empty path (SCOPE-19)");
      return new NodeSpec(Form.UNKNOWN, List.of());
    }
    if (catalog == null) {
      report.markIncomplete();
      return new NodeSpec(Form.CLOSED, List.of());
    }
    Optional<HierarchyCatalog.Catalog> read = catalog.read(path.get(), from.get(), report);
    List<String> nodes = read.isPresent() ? HierarchyNodes.validate(report, read.get().items()) : List.of();
    return new NodeSpec(Form.CLOSED, nodes);
  }

  /** The {@code on_unknown_node} member: required for a closed catalog, absent for an open one (SCOPE-22 to 24). */
  private Optional<ScopeDef.OnUnknownNode> onUnknownNode(PMap entry, PNode entryNode, Form form) {
    Optional<PNode> node = entry.get("on_unknown_node");
    Optional<ScopeDef.OnUnknownNode> policy = Optional.empty();
    if (node.isPresent()) {
      Optional<String> text = Names.text(node.get());
      if (text.filter("error"::equals).isPresent()) {
        policy = Optional.of(ScopeDef.OnUnknownNode.ERROR);
      } else if (text.filter("ignore"::equals).isPresent()) {
        policy = Optional.of(ScopeDef.OnUnknownNode.IGNORE);
      } else {
        report.at(Code.E_SCOPE_DECLARATION, node.get(), "on_unknown_node is error or ignore (SCOPE-23)");
      }
      if (form == Form.OPEN) {
        report.at(Code.E_SCOPE_DECLARATION, node.get(), "an open catalog has no on_unknown_node (SCOPE-24)");
      }
    } else if (form == Form.CLOSED) {
      report.at(Code.E_SCOPE_DECLARATION, entryNode, "a closed catalog needs on_unknown_node (SCOPE-22)");
    }
    return policy;
  }
}
