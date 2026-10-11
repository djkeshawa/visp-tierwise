package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import io.github.visplabs.tierwise.core.internal.model.Returns;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.project.NameSuggester;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import io.github.visplabs.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses a feature declaration of a schema document (section 7.1: FEAT-1 to FEAT-21).
 *
 * <p>Every fault of the declaration is reported, and a declaration with any fault is not clean. A missing
 * member is located at the declaration, any other fault at the member or entry that has it (FIND-23,
 * FIND-31).
 */
final class FeatureDeclParser {

  private static final Set<String> REQUIRED =
      Set.of("decided_per", "returns", "hit_policy", "binding_settable_at", "if_fact_missing");

  private final Findings findings;
  private final FeatureInputs inputs;
  private final TypeDeclParser types;
  private final FeatureEffects effects;

  /**
   * Creates the parser.
   *
   * @param findings the accumulator that receives the findings
   * @param inputs what the earlier stages built
   */
  FeatureDeclParser(Findings findings, FeatureInputs inputs) {
    this.findings = findings;
    this.inputs = inputs;
    this.types = new TypeDeclParser(findings);
    this.effects = new FeatureEffects(findings);
  }

  /**
   * Parses one declaration.
   *
   * @param name the feature name
   * @param node the declaration node
   * @return the parsed declaration
   */
  FeatureDecl parse(String name, PNode node) {
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "a feature declaration is a map (FEAT-1, FEAT-2)");
      return new FeatureDecl(name, node, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
          Optional.empty(), false);
    }
    boolean clean = members(map);
    Optional<String> decided = decidedPer(map);
    Optional<Returns> returns = map.get("returns").flatMap(this::returns);
    Optional<FeatureEffect> missing = Optional.empty();
    if (map.has("if_fact_missing") && returns.isPresent()) {
      missing = effects.read(returns.get(), map.get("if_fact_missing").orElseThrow());
    }
    boolean hit = map.get("hit_policy").map(this::hitPolicy).orElse(false);
    Settable scopes = map.get("binding_settable_at").map(this::settable).orElse(null);
    clean &= decided.isPresent() && returns.isPresent() && missing.isPresent() && hit && scopes != null
        && scopes.clean();
    return new FeatureDecl(name, node, decided, returns, missing,
        Optional.ofNullable(scopes).flatMap(found -> Optional.ofNullable(found.listed())),
        Optional.ofNullable(scopes).filter(Settable::clean).map(Settable::levels), clean);
  }

  /** Checks the member names and the presence of the required ones (FEAT-1, FEAT-2, FEAT-3). */
  private boolean members(PMap map) {
    boolean ok = true;
    for (Map.Entry<String, PNode> member : map.entries().entrySet()) {
      String key = member.getKey();
      if (key.equals("description")) {
        if (FeatureNodes.name(member.getValue()).isEmpty()) {
          findings.error(Code.E_INVALID_DECLARATION, member.getValue(), "description is a string (FEAT-1, FEAT-2)");
          ok = false;
        }
      } else if (!REQUIRED.contains(key)) {
        findings.error(Code.E_INVALID_DECLARATION, member.getValue(),
            "a feature declaration has no member " + key + " (FEAT-1, FEAT-2)");
        ok = false;
      }
    }
    for (String required : REQUIRED) {
      if (!map.has(required)) {
        findings.error(Code.E_INVALID_DECLARATION, map,
            "a feature declaration lacks " + required + " (FEAT-1, FEAT-2, FEAT-3)");
        ok = false;
      }
    }
    return ok;
  }

  // ---- decided_per ------------------------------------------------------------------------------

  /** Reads {@code decided_per}: one declared entity name (FEAT-4, FEAT-5). */
  private Optional<String> decidedPer(PMap map) {
    Optional<PNode> node = map.get("decided_per");
    if (node.isEmpty()) {
      return Optional.empty();
    }
    Optional<String> entity = FeatureNodes.name(node.get());
    if (entity.isEmpty() || entity.get().isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, node.get(), "decided_per is one entity name (FEAT-4, FEAT-2)");
      return Optional.empty();
    }
    if (inputs.entities().isEmpty()) {
      return Optional.empty();
    }
    if (!inputs.entities().get().containsKey(entity.get())) {
      findings.error(Code.E_UNKNOWN_ENTITY, node.get(), "decided_per names the undeclared entity " + entity.get() + " (FEAT-5)",
          NameSuggester.closest(entity.get(), inputs.entities().get().keySet()));
      return Optional.empty();
    }
    return entity;
  }

  // ---- returns ----------------------------------------------------------------------------------

  /** Reads {@code returns}: the enum form or the object form (FEAT-6 to FEAT-12). */
  private Optional<Returns> returns(PNode node) {
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "returns is a map with an enum or an object (FEAT-6)");
      return Optional.empty();
    }
    boolean ok = true;
    for (Map.Entry<String, PNode> member : map.entries().entrySet()) {
      if (!Set.of("enum", "object", "nullable").contains(member.getKey())) {
        findings.error(Code.E_INVALID_DECLARATION, member.getValue(),
            "returns has no member " + member.getKey() + " (FEAT-6, FEAT-2)");
        ok = false;
      }
    }
    boolean enumForm = map.has("enum");
    boolean objectForm = map.has("object");
    if (enumForm == objectForm) {
      findings.error(Code.E_INVALID_DECLARATION, map, "returns takes exactly one of enum and object (FEAT-6)");
      return Optional.empty();
    }
    if (objectForm) {
      if (map.has("nullable")) {
        findings.error(Code.E_INVALID_DECLARATION, map.get("nullable").orElseThrow(),
            "the object form does not declare nullable (FEAT-12)");
        ok = false;
      }
      Optional<Map<String, TypeDecl>> fields =
          types.parseFields(map.get("object").orElseThrow());
      return ok ? fields.map(Returns::object) : Optional.empty();
    }
    boolean nullable = false;
    Optional<PNode> nullableNode = map.get("nullable");
    if (nullableNode.isPresent()) {
      if (nullableNode.get() instanceof PScalar scalar && scalar.kind() == PScalar.Kind.BOOLEAN) {
        nullable = (Boolean) scalar.value();
      } else {
        findings.error(Code.E_INVALID_DECLARATION, nullableNode.get(), "nullable is a boolean (FEAT-7)");
        ok = false;
      }
    }
    Optional<List<String>> values = types.parseStrings(map.get("enum").orElseThrow());
    if (!ok || values.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(Returns.enumeration(values.get(), nullable));
  }

  // ---- hit_policy -------------------------------------------------------------------------------

  /** Checks {@code hit_policy}, which is {@code FIRST} (FEAT-15, FEAT-16). */
  private boolean hitPolicy(PNode node) {
    if (FeatureNodes.name(node).filter("FIRST"::equals).isPresent()) {
      return true;
    }
    findings.error(Code.E_INVALID_DECLARATION, node, "hit_policy is FIRST (FEAT-15, FEAT-16)");
    return false;
  }

  // ---- binding_settable_at ----------------------------------------------------------------------

  /** The outcome of reading {@code binding_settable_at}. */
  private record Settable(Set<String> listed, List<String> levels, boolean clean) {
  }

  private Settable settable(PNode node) {
    if (!(node instanceof PSeq list)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "binding_settable_at is a list of level names (FEAT-17)");
      return new Settable(null, List.of(), false);
    }
    boolean ok = true;
    Set<String> listed = new LinkedHashSet<>();
    Set<String> seen = new HashSet<>();
    List<String> levels = new ArrayList<>();
    for (PNode item : list.items()) {
      Optional<String> name = FeatureNodes.name(item);
      if (name.isEmpty()) {
        findings.error(Code.E_INVALID_DECLARATION, item, "an entry of binding_settable_at is a level name (FEAT-17)");
        ok = false;
        continue;
      }
      listed.add(name.get());
      if (!seen.add(name.get())) {
        findings.error(Code.E_DUPLICATE_VALUE, item, "binding_settable_at names " + name.get() + " twice (FEAT-21)");
        ok = false;
      } else if (inputs.scopes().isPresent() && !scopeEntry(inputs.scopes().get(), name.get(), item)) {
        ok = false;
      } else {
        levels.add(name.get());
      }
    }
    if (inputs.hierarchy().isPresent() && !listed.contains(inputs.hierarchy().get().rootLevel())) {
      findings.error(Code.E_ROOT_BINDING_NOT_ALLOWED, node, "binding_settable_at includes the root level (FEAT-18)");
      ok = false;
    }
    return new Settable(listed, levels, ok && inputs.hierarchy().isPresent());
  }

  /** Checks one entry: a declared level that is not service-sourced (FEAT-17, FEAT-19, FEAT-20). */
  private boolean scopeEntry(ScopeIndex scopes, String name, PNode item) {
    Optional<String> scope = scopes.resolveScope(findings, item, name);
    if (scope.isEmpty()) {
      return false;
    }
    if (scopes.hierarchy().isAxis(scope.get())) {
      findings.error(Code.E_BINDING_NOT_ALLOWED, item, "only levels carry bindings, and " + name + " is an axis (FEAT-20)");
      return false;
    }
    if (scopes.definition(scope.get()).filter(ScopeDef::isServiceSourced).isPresent()) {
      findings.error(Code.E_BINDING_NOT_ALLOWED, item, "the service-sourced level " + name + " carries no binding (FEAT-19)");
      return false;
    }
    return true;
  }
}
