package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.PScalar;
import io.github.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the {@code entity_hierarchy} declarations into entries (ENTITY-1 to ENTITY-9, ENTITY-18, ENTITY-19).
 *
 * <p>An entity name is declared when it is non-empty and has no dot (ENTITY-18). A declared name whose body is
 * faulty stays declared, so that a reference to it does not add a second finding; its relations are then the
 * ones that could be read. A name that breaks ENTITY-18 is reported and left out, so a reference to it is an
 * unknown entity (ENTITY-6).
 */
final class EntityDeclaration {

  /**
   * A name in a relation and the node that writes it.
   *
   * @param name the NFC-normalised name
   * @param location the node that locates a finding about the name
   */
  record Ref(String name, PNode location) {
  }

  /**
   * A declared entity with the names of its relations.
   *
   * @param name the entity name
   * @param valueNode the entry's value, where a finding about the entity is located (FIND-22)
   * @param belongsTo the names of {@code belongs_to}, in document order
   * @param hasMany the names of {@code has_many}, in document order
   */
  record Entry(String name, PNode valueNode, List<Ref> belongsTo, List<Ref> hasMany) {
  }

  private static final Set<String> MEMBERS = Set.of("belongs_to", "has_many");

  private final EntityReport report;

  /**
   * Creates a reader.
   *
   * @param report the report that receives the faults
   */
  EntityDeclaration(EntityReport report) {
    this.report = report;
  }

  /**
   * Reads every entity of an {@code entity_hierarchy} mapping, in document order.
   *
   * @param map the mapping
   * @return the declared entities
   */
  List<Entry> read(PMap map) {
    List<Entry> entries = new ArrayList<>();
    for (Map.Entry<String, PNode> entry : map.entries().entrySet()) {
      String name = entry.getKey();
      PNode value = entry.getValue();
      if (name.isEmpty() || name.contains(".")) {
        report.at(Code.E_INVALID_DECLARATION, value, "an entity name is non-empty and has no dot (ENTITY-18)");
        continue;
      }
      if (!(value instanceof PMap body)) {
        report.at(Code.E_INVALID_DECLARATION, value, "an entity declaration is a map (ENTITY-7)");
        entries.add(new Entry(name, value, List.of(), List.of()));
        continue;
      }
      for (Map.Entry<String, PNode> member : body.entries().entrySet()) {
        if (!MEMBERS.contains(member.getKey())) {
          report.at(Code.E_INVALID_DECLARATION, member.getValue(),
              "an entity declaration has the member " + member.getKey() + " (ENTITY-4, ENTITY-7)");
        }
      }
      List<Ref> belongsTo = relation(body, "belongs_to");
      List<Ref> hasMany = relation(body, "has_many");
      entries.add(new Entry(name, value, belongsTo, hasMany));
    }
    return entries;
  }

  /** Reads one relation: one name, or a list of names (ENTITY-5, ENTITY-7, ENTITY-8, ENTITY-19). */
  private List<Ref> relation(PMap body, String member) {
    List<Ref> refs = new ArrayList<>();
    Optional<PNode> node = body.get(member);
    if (node.isEmpty()) {
      return refs;
    }
    PNode relation = node.get();
    if (relation instanceof PScalar scalar) {
      nameOf(scalar, relation).ifPresent(name -> refs.add(new Ref(name, relation)));
      return refs;
    }
    if (relation instanceof PSeq list) {
      Set<String> seen = new HashSet<>();
      for (PNode item : list.items()) {
        if (item instanceof PScalar scalar) {
          addItem(scalar, item, seen, refs);
        } else {
          report.at(Code.E_INVALID_DECLARATION, item, "a relation list holds names, not maps or lists (ENTITY-7)");
        }
      }
      return refs;
    }
    report.at(Code.E_INVALID_DECLARATION, relation, "a relation is one name or a list of names (ENTITY-5, ENTITY-7)");
    return refs;
  }

  /** Adds one list item: a repeat is E_DUPLICATE_VALUE at the later item (ENTITY-8, FIND-32). */
  private void addItem(PScalar scalar, PNode item, Set<String> seen, List<Ref> refs) {
    Optional<String> name = nameOf(scalar, item);
    if (name.isEmpty()) {
      return;
    }
    if (!seen.add(name.get())) {
      report.at(Code.E_DUPLICATE_VALUE, item, "the entity " + name.get() + " is named twice in one list (ENTITY-8)");
      return;
    }
    refs.add(new Ref(name.get(), item));
  }

  /** A string scalar is a name; any other scalar is a type mismatch and is never converted (ENTITY-19). */
  private Optional<String> nameOf(PScalar scalar, PNode node) {
    if (scalar.kind() == PScalar.Kind.STRING) {
      return Optional.of(Names.nfc((String) scalar.value()));
    }
    String hint = scalar.kind() == PScalar.Kind.NULL
        ? "null is not an entity name (ENTITY-19)"
        : "an entity name is a string, so quote the value (ENTITY-19)";
    report.at(Code.E_TYPE_MISMATCH, node, hint);
    return Optional.empty();
  }
}
