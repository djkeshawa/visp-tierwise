package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.SettingDef;
import io.github.tierwise.core.internal.project.NameSuggester;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses the {@code settings} of a policy (POL-9, POL-11 to POL-20).
 *
 * <p>A setting with both forms or neither gets no check of its default (FIND-35). Every other fault is
 * reported, each at its own node.
 */
final class SettingParser {

  private static final Set<String> MEMBERS = Set.of("default", "value_of", "list_of");

  private final Findings findings;
  private final FeatureInputs inputs;
  private final Optional<FeatureScope> scope;
  private final SettingValues values;

  /**
   * Creates the parser.
   *
   * @param findings the accumulator that receives the findings
   * @param inputs what the earlier stages built
   * @param scope the facts the decided entity may read, when the decided entity is known
   */
  SettingParser(Findings findings, FeatureInputs inputs, Optional<FeatureScope> scope) {
    this.findings = findings;
    this.inputs = inputs;
    this.scope = scope;
    this.values = new SettingValues(findings);
  }

  /**
   * Parses a {@code settings} mapping.
   *
   * @param node the node, a mapping from setting name to declaration
   * @return the settings by NFC-normalised name, or empty when the node is not a mapping (which is
   *     reported)
   */
  Optional<Map<String, SettingEntry>> parse(PNode node) {
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "settings is a map from setting name to declaration (POL-9)");
      return Optional.empty();
    }
    Map<String, SettingEntry> settings = new LinkedHashMap<>();
    for (Map.Entry<String, PNode> entry : map.entries().entrySet()) {
      String name = Names.nfc(entry.getKey());
      // A name repeated after NFC normalisation is a repeated key, which the loader reports (POL-10, SRC-4).
      settings.putIfAbsent(name, parseOne(name, entry.getValue()));
    }
    return Optional.of(settings);
  }

  private SettingEntry parseOne(String name, PNode node) {
    boolean ok = nameIsValid(name, node);
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "a setting declaration is a map (POL-9, FEAT-2)");
      return new SettingEntry(name, Optional.empty());
    }
    for (Map.Entry<String, PNode> member : map.entries().entrySet()) {
      if (!MEMBERS.contains(member.getKey())) {
        findings.error(Code.E_INVALID_SETTING, member.getValue(),
            "a setting declaration has no entry " + member.getKey() + " (POL-13)");
        ok = false;
      }
    }
    if (!map.has("default")) {
      findings.error(Code.E_INVALID_SETTING, map, "a setting declaration contains default (POL-11)");
      ok = false;
    }
    boolean valueOf = map.has("value_of");
    boolean listOf = map.has("list_of");
    if (valueOf == listOf) {
      findings.error(Code.E_INVALID_SETTING, map, "a setting declaration contains exactly one of value_of and list_of (POL-12)");
      return new SettingEntry(name, Optional.empty());
    }
    SettingDef.Kind kind = valueOf ? SettingDef.Kind.VALUE_OF : SettingDef.Kind.LIST_OF;
    PNode factNode = map.get(valueOf ? "value_of" : "list_of").orElseThrow();
    Optional<String> fact = fact(factNode);
    ok &= fact.isPresent();
    Optional<Object> defaultValue = Optional.empty();
    if (fact.isPresent() && map.has("default")) {
      FactDef definition = inputs.facts().orElseThrow().get(fact.get());
      defaultValue = values.read(kind, definition.type(), map.get("default").orElseThrow());
      ok &= defaultValue.isPresent();
    }
    return new SettingEntry(name, ok && defaultValue.isPresent()
        ? Optional.of(new SettingDef(kind, fact.get(), defaultValue.get())) : Optional.empty());
  }

  /** Checks a setting name (POL-20). */
  private boolean nameIsValid(String name, PNode node) {
    if (name.isEmpty() || name.contains("$") || name.contains(".") || name.equals("policy")) {
      findings.error(Code.E_INVALID_SETTING, node,
          "a setting name is non-empty, has no $ or ., and is not the reserved name policy (POL-20)");
      return false;
    }
    return true;
  }

  /** Reads the fact of {@code value_of} or {@code list_of}: declared and eligible (POL-14, POL-16). */
  private Optional<String> fact(PNode node) {
    Optional<String> name = FeatureNodes.name(node);
    if (name.isEmpty()) {
      findings.error(Code.E_INVALID_SETTING, node, "value_of and list_of name a fact (POL-12)");
      return Optional.empty();
    }
    if (inputs.facts().isEmpty()) {
      return Optional.empty();
    }
    Map<String, FactDef> facts = inputs.facts().get();
    FactDef definition = facts.get(name.get());
    if (definition == null) {
      findings.error(Code.E_UNKNOWN_FACT, node, name.get() + " is not a declared fact (POL-14)",
          NameSuggester.closest(name.get(), facts.keySet()));
      return Optional.empty();
    }
    if (scope.isPresent()) {
      FeatureScope.Position position = scope.get().position(definition.entity());
      if (position == FeatureScope.Position.DEEP) {
        findings.error(Code.E_DEPTH_UNSUPPORTED, node, name.get() + " lies two or more has_many edges below the decided entity (POL-16, RULE-11)");
        return Optional.empty();
      }
      if (position == FeatureScope.Position.OUT) {
        findings.error(Code.E_FACT_NOT_IN_SCOPE, node, name.get() + " is not eligible for the decided entity (POL-16)");
        return Optional.empty();
      }
    }
    return name;
  }
}
