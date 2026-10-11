package io.github.visplabs.tierwise.core.internal.project;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Severity;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks the {@code lint} member of the manifest or of a preset and reads its settings (MANIFEST-37 to
 * MANIFEST-39, LINT-3, LINT-8, LINT-10).
 *
 * <p>A fault in the manifest is {@code E_MANIFEST}, and a fault in a preset is {@code E_PRESET}. A setting
 * that names an engine error code is {@code E_PRESET} in both, whatever its value, and has no effect
 * (LINT-8, LINT-9).
 */
final class LintSettingsReader {

  private static final Set<String> SEVERITIES = Set.of("error", "warn", "info", "off");

  private final Reporter reporter;
  private final String faultCode;
  private final Set<String> namingRules;

  /**
   * Creates a reader.
   *
   * @param reporter the findings of the loader
   * @param preset {@code true} for a preset, whose faults are {@code E_PRESET}
   * @param namingPatterns the names of the naming patterns that the project declares or inherits, which
   *     make {@code naming/<name>} a rule of the project (LINT-10 (b))
   */
  LintSettingsReader(Reporter reporter, boolean preset, Set<String> namingPatterns) {
    this.reporter = reporter;
    this.faultCode = preset ? Code.E_PRESET : Code.E_MANIFEST;
    Set<String> rules = new TreeSet<>();
    for (String name : namingPatterns) {
      rules.add(Code.NAMING_PREFIX + name);
    }
    this.namingRules = rules;
  }

  /**
   * Reads the lint settings.
   *
   * @param source the file name of the preset, or {@code tierwise.yaml}
   * @param lint the {@code lint} node, or {@code null} when there is none
   * @return the valid settings of the layer
   */
  LintSettings.Layer read(String source, PNode lint) {
    Map<String, Optional<Severity>> settings = new LinkedHashMap<>();
    if (lint != null) {
      readLint(lint, settings);
    }
    return new LintSettings.Layer(source, settings);
  }

  private void readLint(PNode lint, Map<String, Optional<Severity>> settings) {
    if (!(lint instanceof PMap mapping)) {
      reporter.at(faultCode, lint, "lint is not a mapping (MANIFEST-37)");
      return;
    }
    for (Map.Entry<String, PNode> entry : mapping.entries().entrySet()) {
      if (!entry.getKey().equals("rules")) {
        reporter.at(faultCode, entry.getValue(), "lint has the key " + entry.getKey() + ", not allowed (MANIFEST-37)");
      }
    }
    Optional<PNode> rules = mapping.get("rules");
    if (rules.isEmpty()) {
      return;
    }
    if (!(rules.get() instanceof PMap table)) {
      reporter.at(faultCode, rules.get(), "lint.rules is not a mapping (MANIFEST-38)");
      return;
    }
    for (Map.Entry<String, PNode> setting : table.entries().entrySet()) {
      readSetting(setting.getKey(), setting.getValue(), settings);
    }
  }

  private void readSetting(String name, PNode node, Map<String, Optional<Severity>> settings) {
    if (Code.engine().contains(name)) {
      reporter.at(Code.E_PRESET, node,
          "a lint setting cannot name the engine error " + name + " (LINT-8)");
      return;
    }
    if (!isRule(name)) {
      List<String> candidates = new ArrayList<>(ruleIdentifiers());
      reporter.at(faultCode, node, name + " is not a lint rule of this project (LINT-10)",
          NameSuggester.closest(name, candidates));
      return;
    }
    Optional<String> severity = Names.text(node);
    if (severity.isEmpty() || !SEVERITIES.contains(severity.get())) {
      reporter.at(faultCode, node, "the severity of " + name + " is error, warn, info or off (LINT-3)");
      return;
    }
    settings.put(name, severity.get().equals("off") ? Optional.empty() : Severity.parse(severity.get()));
  }

  private boolean isRule(String name) {
    return name.equals(LintSettings.NAMING_WILDCARD) || fixedRules().contains(name) || namingRules.contains(name);
  }

  private Set<String> ruleIdentifiers() {
    Set<String> all = new TreeSet<>(fixedRules());
    all.add(LintSettings.NAMING_WILDCARD);
    all.addAll(namingRules);
    return all;
  }

  private static Set<String> fixedRules() {
    return Set.of(Code.LINT_VALUES_NO_OP, Code.LINT_RULES_DEAD_RULE, Code.LINT_EXAMPLES_REQUIRED_PER_BINDING,
        Code.LINT_EXAMPLES_MIXED_COLLECTION, Code.LINT_SCHEMA_UNUSED, Code.LINT_SCHEMA_DESCRIPTION_REQUIRED,
        Code.LINT_SCHEMA_OWNER_REQUIRED, Code.LINT_PRESETS_OVERRIDE);
  }
}
