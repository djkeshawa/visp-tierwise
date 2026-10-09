package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.project.SourceDocument;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads and checks the {@code naming} declarations of the shared schema files and presets (LINT-14,
 * LINT-15, LINT-17).
 *
 * <p>A fault is {@code E_PRESET} in a preset and {@code E_INVALID_DECLARATION} in a shared schema file.
 * It is located at the member that has it, and a missing member at the entry. Namespaced files hold no
 * {@code naming} (NS-4), which the loader reports. A later layer replaces an earlier pattern of the same
 * name entirely (MANIFEST-32, MANIFEST-33), and among the project's own sources the first declaration
 * stays, because a second one is {@code E_DUPLICATE_DEFINITION} (SRC-43).
 */
final class NamingDeclarations {

  static final Set<String> CATEGORIES = Set.of("key", "enum_value", "node_id", "feature");

  private final Findings findings;

  private NamingDeclarations(Findings findings) {
    this.findings = findings;
  }

  /**
   * Reads every naming declaration and reports the faulty ones.
   *
   * @param context the compilation
   * @return the patterns, faulty ones included but not usable
   */
  static NamingTable read(CheckContext context) {
    NamingDeclarations reader = new NamingDeclarations(context.findings());
    List<NamingPattern> all = new ArrayList<>();
    Map<String, NamingPattern> effective = new LinkedHashMap<>();
    for (SourceDocument document : context.sources().schemas()) {
      if (document.isNamespaced() || !(document.root().get("naming").orElse(null) instanceof PMap naming)) {
        continue;
      }
      String code = document.isPreset() ? Code.E_PRESET : Code.E_INVALID_DECLARATION;
      for (Map.Entry<String, PNode> entry : naming.entries().entrySet()) {
        NamingPattern pattern = reader.pattern(entry.getKey(), entry.getValue(), document, code);
        all.add(pattern);
        NamingPattern earlier = effective.get(pattern.name());
        if (earlier == null || earlier.layer() < pattern.layer()) {
          effective.put(pattern.name(), pattern);
        }
      }
    }
    return new NamingTable(effective, all);
  }

  private NamingPattern pattern(String name, PNode node, SourceDocument document, String code) {
    Optional<String> category = Optional.empty();
    Optional<NamingRegex> regex = Optional.empty();
    Optional<String> description = Optional.empty();
    if (badName(name)) {
      findings.error(code, node, "a pattern name is non-empty and has no slash, asterisk or white space (LINT-14)");
    }
    if (node instanceof PMap map) {
      category = category(map, code);
      regex = regex(map, code);
      description = map.get("description").flatMap(Names::text);
      for (Map.Entry<String, PNode> member : map.entries().entrySet()) {
        if (!Set.of("category", "pattern", "description").contains(member.getKey())) {
          findings.error(code, member.getValue(),
              "a naming entry has no member " + member.getKey() + " (LINT-14)");
        }
      }
    } else {
      findings.error(code, node, "a naming entry is a mapping with category and pattern (LINT-14)");
    }
    return new NamingPattern(name, category, regex, description, node, document.layer(), document.isPreset());
  }

  private Optional<String> category(PMap map, String code) {
    Optional<PNode> node = map.get("category");
    if (node.isEmpty()) {
      findings.error(code, map, "a naming entry has no category (LINT-14)");
      return Optional.empty();
    }
    Optional<String> text = Names.text(node.get()).filter(CATEGORIES::contains);
    if (text.isEmpty()) {
      findings.error(code, node.get(), "the category is key, enum_value, node_id or feature (LINT-15)");
    }
    return text;
  }

  private Optional<NamingRegex> regex(PMap map, String code) {
    Optional<PNode> node = map.get("pattern");
    if (node.isEmpty()) {
      findings.error(code, map, "a naming entry has no pattern (LINT-14)");
      return Optional.empty();
    }
    Optional<String> text = Names.text(node.get());
    if (text.isEmpty()) {
      findings.error(code, node.get(), "the pattern is a string (LINT-17)");
      return Optional.empty();
    }
    NamingRegex.Outcome outcome = NamingRegex.compile(text.get());
    if (!outcome.valid()) {
      findings.error(code, node.get(), "the pattern is invalid: " + outcome.problem() + " (LINT-17)");
    }
    return outcome.regex();
  }

  private static boolean badName(String name) {
    if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('*') >= 0) {
      return true;
    }
    return name.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c));
  }
}
