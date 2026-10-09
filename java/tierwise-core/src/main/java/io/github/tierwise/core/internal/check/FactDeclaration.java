package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.TypeDecl;
import io.github.tierwise.core.internal.project.NameSuggester;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.project.SourceDocument;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.PScalar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the {@code facts} declarations into {@link FactDef}s (FACT-1 to FACT-8, FACT-11, FACT-45, FACT-3).
 *
 * <p>Every declaration in every shared or preset schema document is checked, so that each fault is reported
 * (FIND-3). A repeated fact name is reported by the project loader (SRC-40), and the first valid declaration is
 * the one that the model keeps. A name whose entity part is not declared is {@code E_UNKNOWN_ENTITY}; the
 * declared names are {@code null} when the entity declarations could not be read, and then that check is skipped.
 */
final class FactDeclaration {

  private static final Set<String> MEMBERS = Set.of(
      "type", "source", "description", "min", "max", "format", "values", "universe", "items", "fields");
  private static final Set<String> SCALAR_TYPES = Set.of("boolean", "string", "integer", "decimal", "enum");

  private final EntityReport report;
  private final Set<String> declaredEntities;
  private final TypeDeclParser types;

  /**
   * Creates a reader.
   *
   * @param findings the accumulator, which the type parser reports to
   * @param report the report that counts the faults of the facts
   * @param declaredEntities the declared entity names, or {@code null} when they are not known
   */
  FactDeclaration(Findings findings, EntityReport report, Set<String> declaredEntities) {
    this.report = report;
    this.declaredEntities = declaredEntities;
    this.types = new TypeDeclParser(findings);
  }

  /**
   * Reads the facts of every non-namespaced schema document, in definition order.
   *
   * @param documents the schema documents, presets first
   * @return the valid declarations by fact name; the first valid one wins
   */
  Map<String, FactDef> read(List<SourceDocument> documents) {
    Map<String, FactDef> facts = new LinkedHashMap<>();
    for (SourceDocument document : documents) {
      if (!(document.root().get("facts").orElse(null) instanceof PMap map)) {
        continue;
      }
      for (Map.Entry<String, PNode> entry : map.entries().entrySet()) {
        fact(entry.getKey(), entry.getValue()).ifPresent(def -> facts.putIfAbsent(Names.nfc(entry.getKey()), def));
      }
    }
    return facts;
  }

  /** Checks one declaration (FACT-1 to FACT-3, FACT-6 to FACT-8, FACT-11, FACT-45). */
  private Optional<FactDef> fact(String written, PNode node) {
    int before = report.faults();
    String name = Names.nfc(written);
    int dot = name.indexOf('.');
    String entity = dot < 0 ? "" : name.substring(0, dot);
    if (dot < 0) {
      report.at(Code.E_UNKNOWN_ENTITY, node, "a fact name is <entity>.<attribute>, so this names no entity (FACT-3)");
    } else {
      checkEntityPart(entity, name.substring(dot + 1), node);
    }
    Optional<TypeDecl> type = types.parse(node, TypeDeclParser.Position.FACT);
    if (node instanceof PMap declaration) {
      checkMembers(declaration);
      if (type.isPresent() && !SCALAR_TYPES.contains(type.get().type())) {
        report.at(Code.E_INVALID_DECLARATION, declaration.get("type").orElse(node),
            "a fact has a type of boolean, string, integer, decimal or enum, not " + type.get().type()
            + " (FACT-7, FACT-8)");
      }
    }
    if (report.faults() != before || type.isEmpty() || dot < 0) {
      return Optional.empty();
    }
    return Optional.of(new FactDef(entity, type.get()));
  }

  /** The entity part is declared, and the attribute part is not empty (FACT-2, FACT-3). */
  private void checkEntityPart(String entity, String attribute, PNode node) {
    if (attribute.isEmpty()) {
      report.at(Code.E_INVALID_DECLARATION, node, "a fact name has an attribute after its dot (FACT-3)");
    }
    if (declaredEntities != null && !declaredEntities.contains(entity)) {
      report.at(Code.E_UNKNOWN_ENTITY, node, "the entity " + entity + " is not declared (FACT-2)",
          NameSuggester.closest(entity, declaredEntities));
    }
  }

  /** The members of a fact: the type, source and description, and the type members (FACT-9, FACT-11, FACT-45). */
  private void checkMembers(PMap declaration) {
    for (Map.Entry<String, PNode> member : declaration.entries().entrySet()) {
      if (!MEMBERS.contains(member.getKey())) {
        report.at(Code.E_INVALID_DECLARATION, member.getValue(),
            "a fact declaration has the member " + member.getKey() + " (FACT-45)");
      }
    }
    checkText(declaration, "source", "a source is a string (FACT-11)");
    checkText(declaration, "description", "a description is a string (FACT-45)");
  }

  private void checkText(PMap declaration, String member, String message) {
    Optional<PNode> node = declaration.get(member);
    if (node.isPresent() && !(node.get() instanceof PScalar scalar && scalar.kind() == PScalar.Kind.STRING)) {
      report.at(Code.E_INVALID_DECLARATION, node.get(), message);
    }
  }
}
