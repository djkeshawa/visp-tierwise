package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.project.SourceDocument;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.PScalar;
import io.github.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads and checks the kind definitions of the schema documents and presets (KIND-1, KIND-3 to KIND-5,
 * KIND-10 to KIND-12, SVC-4).
 *
 * <p>Every fault is {@code E_KIND_DEFINITION}. A fault is located at the member that has it, and a fault of
 * one entry of {@code strategies} at that entry. A member that is faulty imposes no constraint on the keys
 * that use the kind, so one fault gives one finding. A kind that a later layer defines replaces an earlier
 * one entirely (MANIFEST-32, MANIFEST-33).
 */
final class KindDeclarations {

  private static final Set<String> MEMBERS =
      Set.of("strategies", "narrowing_only", "require_default", "on_source_failure", "naming");

  private final Findings findings;
  private final NamingTable naming;

  private KindDeclarations(Findings findings, NamingTable naming) {
    this.findings = findings;
    this.naming = naming;
  }

  /**
   * Reads every kind definition and reports the faulty ones.
   *
   * @param context the compilation
   * @param naming the naming patterns, which kinds name (KIND-12)
   * @return the kinds
   */
  static KindTable read(CheckContext context, NamingTable naming) {
    KindDeclarations reader = new KindDeclarations(context.findings(), naming);
    List<KindDef> all = new ArrayList<>();
    Map<String, KindDef> effective = new LinkedHashMap<>();
    for (SourceDocument document : context.sources().schemas()) {
      if (document.isNamespaced() || !(document.root().get("kinds").orElse(null) instanceof PMap kinds)) {
        continue;
      }
      for (Map.Entry<String, PNode> entry : kinds.entries().entrySet()) {
        KindDef kind = reader.kind(Names.nfc(entry.getKey()), entry.getValue(), document);
        all.add(kind);
        KindDef earlier = effective.get(kind.name());
        if (earlier == null || earlier.layer() < kind.layer()) {
          effective.put(kind.name(), kind);
        }
      }
    }
    return new KindTable(effective, all);
  }

  private KindDef kind(String name, PNode node, SourceDocument document) {
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_KIND_DEFINITION, node, "a kind definition is a mapping (KIND-3)");
      return new KindDef(name, Optional.empty(), false, false, Optional.empty(), Optional.empty(), node,
          document.layer(), document.isPreset());
    }
    for (Map.Entry<String, PNode> member : map.entries().entrySet()) {
      if (!MEMBERS.contains(member.getKey())) {
        findings.error(Code.E_KIND_DEFINITION, member.getValue(),
            "a kind definition has no member " + member.getKey() + " (KIND-3)");
      }
    }
    Optional<List<String>> strategies = strategies(map);
    boolean narrowingOnly = flag(map, "narrowing_only");
    boolean requireDefault = flag(map, "require_default");
    Optional<String> onSourceFailure = onSourceFailure(map);
    Optional<String> namingName = naming(map);
    if (narrowingOnly) {
      allowOnly(map, "narrowing_only", "allows only narrowing strategies (KIND-5)", StrategyRules::isNarrowing);
    }
    if (requireDefault) {
      allowOnly(map, "require_default", "allows no commutative strategy (KIND-10)",
          strategy -> !StrategyRules.isCommutative(strategy));
    }
    return new KindDef(name, strategies, narrowingOnly, requireDefault, onSourceFailure, namingName, node,
        document.layer(), document.isPreset());
  }

  /** Reads {@code strategies}: a non-empty list of strategies (KIND-3, KEY-5). */
  private Optional<List<String>> strategies(PMap map) {
    Optional<PNode> node = map.get("strategies");
    if (node.isEmpty()) {
      return Optional.empty();
    }
    if (!(node.get() instanceof PSeq list)) {
      findings.error(Code.E_KIND_DEFINITION, node.get(), "strategies is a list of merge strategies (KEY-5)");
      return Optional.empty();
    }
    if (list.items().isEmpty()) {
      findings.error(Code.E_KIND_DEFINITION, list, "strategies is not empty (KIND-3)");
      return Optional.empty();
    }
    List<String> names = new ArrayList<>();
    boolean ok = true;
    for (PNode item : list.items()) {
      Optional<String> text = Names.text(item).filter(StrategyRules::isStrategy);
      if (text.isEmpty()) {
        findings.error(Code.E_KIND_DEFINITION, item, "an entry of strategies is a merge strategy (KEY-5, MERGE-1)");
        ok = false;
      } else {
        names.add(text.get());
      }
    }
    return ok ? Optional.of(names) : Optional.empty();
  }

  /** Reads a boolean member; a member that is not a boolean is a fault and imposes nothing. */
  private boolean flag(PMap map, String member) {
    Optional<PNode> node = map.get(member);
    if (node.isEmpty()) {
      return false;
    }
    if (node.get() instanceof PScalar scalar && scalar.kind() == PScalar.Kind.BOOLEAN) {
      return Boolean.TRUE.equals(scalar.value());
    }
    findings.error(Code.E_KIND_DEFINITION, node.get(), member + " is a boolean (KIND-3)");
    return false;
  }

  /** Reads {@code on_source_failure}: skip or deny (KIND-11, SVC-3, SVC-4). */
  private Optional<String> onSourceFailure(PMap map) {
    Optional<PNode> node = map.get("on_source_failure");
    if (node.isEmpty()) {
      return Optional.empty();
    }
    Optional<String> text = Names.text(node.get()).filter(value -> value.equals("skip") || value.equals("deny"));
    if (text.isEmpty()) {
      findings.error(Code.E_KIND_DEFINITION, node.get(), "on_source_failure is skip or deny (SVC-3, SVC-4)");
    }
    return text;
  }

  /** Reads {@code naming}: the name of a declared pattern of category key (KIND-12, LINT-16). */
  private Optional<String> naming(PMap map) {
    Optional<PNode> node = map.get("naming");
    if (node.isEmpty()) {
      return Optional.empty();
    }
    Optional<String> text = Names.text(node.get());
    if (text.isEmpty()) {
      findings.error(Code.E_KIND_DEFINITION, node.get(), "naming is the name of a pattern (KIND-12, LINT-16)");
      return Optional.empty();
    }
    NamingPattern pattern = naming.effective().get(text.get());
    if (pattern == null) {
      findings.error(Code.E_KIND_DEFINITION, node.get(),
          "naming names no declared pattern (KIND-12, LINT-16)");
    } else if (pattern.category().filter(category -> !category.equals("key")).isPresent()) {
      findings.error(Code.E_KIND_DEFINITION, node.get(),
          "naming names a pattern whose category is not key (KIND-12, LINT-16)");
    }
    return text;
  }

  /**
   * Checks that a definition with a flag allows only strategies of a kind (KIND-5, KIND-10). A definition
   * without a {@code strategies} member allows every strategy and is reported at the flag. A strategy
   * that is not allowed is reported at its entry.
   */
  private void allowOnly(PMap map, String flag, String requirement,
      java.util.function.Predicate<String> allowed) {
    Optional<PNode> node = map.get("strategies");
    if (node.isEmpty()) {
      findings.error(Code.E_KIND_DEFINITION, map.get(flag).orElseThrow(),
          "a definition with " + flag + " lists strategies, because it " + requirement);
      return;
    }
    if (!(node.get() instanceof PSeq list)) {
      return;
    }
    for (PNode item : list.items()) {
      Optional<String> text = Names.text(item).filter(StrategyRules::isStrategy);
      if (text.isPresent() && !allowed.test(text.get())) {
        findings.error(Code.E_KIND_DEFINITION, item,
            "the strategy " + text.get() + " is not allowed by a definition that " + requirement);
      }
    }
  }
}
