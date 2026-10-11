package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.KeyDef;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.project.NameSuggester;
import io.github.visplabs.tierwise.core.internal.project.Names;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks one key declaration (KEY-1 to KEY-9, MERGE-3, MERGE-4, MERGE-7, MERGE-8, MERGE-10, AXIS-2 to
 * AXIS-6, KIND-4, KIND-8, KIND-9, KIND-11, KIND-13, SVC-1 to SVC-5, SVC-7, SVC-11, SVC-12).
 *
 * <p>The declaration is checked in two groups. The members that stand alone are always checked:
 * the member names (KEY-3), {@code type} through {@link TypeDeclParser}, {@code combine}, {@code
 * settable_at}, {@code on_source_failure}, {@code kind}, {@code description} and {@code owner}. The checks
 * that relate members to each other are made only when the type and the strategy are both valid (FIND-35):
 * the strategy and type fit, the default, the axis combination, the kind and the source-failure rules. The
 * checks that need the hierarchy, the kinds or the root values are skipped when that input is absent.
 */
final class KeyChecks {

  /** What the check of one declaration produced. */
  record Result(KeyInfo info, Optional<KeyDef> def, boolean fault) {
  }

  private static final Set<String> MEMBERS = Set.of("type", "combine", "settable_at", "default",
      "combine_across_axes", "kind", "on_source_failure", "description", "owner", "min", "max", "format",
      "values", "universe", "items", "fields", "optional");

  private final Findings findings;
  private final TypeDeclParser typeParser;
  private final ValueValidator validator;
  private final Optional<Hierarchy> hierarchy;
  private final Optional<KindTable> kinds;
  private final Optional<Set<String>> rootValueKeys;

  KeyChecks(Findings findings, Optional<Hierarchy> hierarchy, Optional<KindTable> kinds,
      Optional<Set<String>> rootValueKeys) {
    this.findings = findings;
    this.typeParser = new TypeDeclParser(findings);
    this.validator = new ValueValidator(findings);
    this.hierarchy = hierarchy;
    this.kinds = kinds;
    this.rootValueKeys = rootValueKeys;
  }

  /** The state of one declaration while it is checked. */
  private final class Declaration {
    final String name;
    final PMap map;
    boolean fault;

    Declaration(String name, PMap map) {
      this.name = name;
      this.map = map;
    }

    void report(String code, PNode node, String message) {
      findings.error(code, node, message);
      fault = true;
    }

    void report(String code, PNode node, String message, Optional<String> suggestion) {
      findings.error(code, node, message, suggestion);
      fault = true;
    }
  }

  /**
   * The {@code settable_at} member: the valid names, whether it is a usable non-empty list, and whether
   * every entry was a valid name. When an entry is dropped (an undeclared scope, for instance) it is not
   * known whether the key is settable on an axis, so the checks that depend on that stay silent.
   */
  private record Settable(List<String> names, boolean known, boolean complete) {
  }

  /**
   * Checks a declaration.
   *
   * @param name the key name
   * @param node the declaration node
   * @return the facts that later stages need, the key for the model when the declaration has no fault
   *     and the hierarchy is known, and whether any fault was reported
   */
  Result check(String name, PNode node) {
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "a key declaration is a mapping (KEY-2, SRC-45)");
      return new Result(new KeyInfo(name, node, Optional.empty(), Optional.empty(), List.of(), false,
          Optional.empty(), false, Optional.empty()), Optional.empty(), true);
    }
    Declaration d = new Declaration(name, map);
    for (Map.Entry<String, PNode> member : map.entries().entrySet()) {
      if (!MEMBERS.contains(member.getKey())) {
        d.report(Code.E_INVALID_DECLARATION, member.getValue(),
            "a key declaration has no member " + member.getKey() + " (KEY-3, KEY-5)");
      }
    }
    Optional<TypeDecl> type = typeParser.parse(map, TypeDeclParser.Position.KEY);
    d.fault |= type.isEmpty();
    Optional<String> combine = combine(d);
    Settable settable = settableAt(d);
    informational(d, "description");
    informational(d, "owner");
    Optional<String> ownSourceFailure = sourceFailure(d);
    Optional<String> kind = kindName(d);

    Optional<Object> defaultValue = Optional.empty();
    boolean declaresDefault = map.has("default");
    Optional<String> acrossAxes = Optional.empty();
    Optional<String> effectiveSourceFailure = Optional.empty();
    if (type.isPresent() && combine.isPresent()) {
      String strategy = combine.get();
      if (!StrategyRules.allowsType(strategy, type.get())) {
        d.report(Code.E_STRATEGY_TYPE, map, "the strategy " + strategy + " does not allow the type "
            + type.get().type() + " (MERGE-3)");
      }
      defaultValue = defaultValue(d, type.get(), strategy);
      missingDefault(d, strategy);
      acrossAxes = acrossAxes(d, type.get(), strategy, settable);
      effectiveSourceFailure = kindAndSourceFailure(d, strategy, type.get(), kind, ownSourceFailure, settable);
    }
    KeyInfo info = new KeyInfo(name, map, type, combine, settable.names(), settable.known(), defaultValue,
        declaresDefault, kind);
    Optional<KeyDef> def = Optional.empty();
    if (!d.fault && type.isPresent() && combine.isPresent() && hierarchy.isPresent()) {
      List<String> ordered = new ArrayList<>(settable.names());
      ordered.sort(java.util.Comparator.comparingInt(scope -> hierarchy.get().scopeOrder().indexOf(scope)));
      def = Optional.of(new KeyDef(type.get(), combine.get(), defaultValue, ordered, acrossAxes,
          effectiveSourceFailure, kind));
    }
    return new Result(info, def, d.fault);
  }

  // ---- stand-alone members -----------------------------------------------------------------------

  /** Reads {@code combine}: the name of a strategy (KEY-2, KEY-5, MERGE-1). */
  private Optional<String> combine(Declaration d) {
    Optional<PNode> node = d.map.get("combine");
    if (node.isEmpty()) {
      d.report(Code.E_INVALID_DECLARATION, d.map, "a key declaration omits combine (KEY-2, KEY-5)");
      return Optional.empty();
    }
    Optional<String> text = Names.text(node.get()).filter(StrategyRules::isStrategy);
    if (text.isEmpty()) {
      d.report(Code.E_INVALID_DECLARATION, node.get(), "combine names no merge strategy (KEY-5, MERGE-1)");
    }
    return text;
  }

  /** Reads {@code settable_at} (KEY-2, KEY-5 to KEY-7). */
  private Settable settableAt(Declaration d) {
    Optional<PNode> node = d.map.get("settable_at");
    if (node.isEmpty()) {
      d.report(Code.E_INVALID_DECLARATION, d.map, "a key declaration omits settable_at (KEY-2, KEY-5)");
      return new Settable(List.of(), false, false);
    }
    if (!(node.get() instanceof PSeq list)) {
      d.report(Code.E_INVALID_DECLARATION, node.get(), "settable_at is a list of scope names (KEY-5, KEY-6)");
      return new Settable(List.of(), false, false);
    }
    if (list.items().isEmpty()) {
      d.report(Code.E_INVALID_DECLARATION, list, "settable_at is not empty (KEY-7)");
      return new Settable(List.of(), false, false);
    }
    List<String> names = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    boolean complete = true;
    for (PNode item : list.items()) {
      Optional<String> text = Names.text(item).filter(value -> !ValueText.hasUnpairedSurrogate(value));
      if (text.isEmpty()) {
        d.report(Code.E_INVALID_DECLARATION, item, "an entry of settable_at is a scope name (KEY-5, KEY-6)");
        complete = false;
        continue;
      }
      String scope = Names.nfc(text.get());
      if (!seen.add(scope)) {
        d.report(Code.E_DUPLICATE_VALUE, item, "a scope name is repeated in settable_at (KEY-7)");
      } else if (hierarchy.isPresent() && !hierarchy.get().isLevel(scope) && !hierarchy.get().isAxis(scope)) {
        d.report(Code.E_UNKNOWN_SCOPE, item, scope + " is not a declared level or axis (KEY-6, SCOPE-46)",
            NameSuggester.closest(scope, hierarchy.get().scopeOrder()));
        complete = false;
      } else {
        names.add(scope);
      }
    }
    return new Settable(names, true, complete);
  }

  /** Checks that {@code description} or {@code owner} is a string when present (KEY-9). */
  private void informational(Declaration d, String member) {
    d.map.get(member).filter(node -> Names.text(node).isEmpty()).ifPresent(node ->
        d.report(Code.E_INVALID_DECLARATION, node, member + " is a string (KEY-9)"));
  }

  /** Reads the key's own {@code on_source_failure}: {@code skip} or {@code deny} (KEY-5, SVC-3, SVC-4). */
  private Optional<String> sourceFailure(Declaration d) {
    Optional<PNode> node = d.map.get("on_source_failure");
    if (node.isEmpty()) {
      return Optional.empty();
    }
    Optional<String> text = Names.text(node.get()).filter(v -> v.equals("skip") || v.equals("deny"));
    if (text.isEmpty()) {
      d.report(Code.E_INVALID_DECLARATION, node.get(), "on_source_failure is skip or deny (KEY-5, SVC-3, SVC-4)");
    }
    return text;
  }

  /** Reads {@code kind}: a name (KIND-2). Whether the kind is declared is checked later (KIND-13). */
  private Optional<String> kindName(Declaration d) {
    Optional<PNode> node = d.map.get("kind");
    if (node.isEmpty()) {
      return Optional.empty();
    }
    Optional<String> text = Names.text(node.get());
    if (text.isEmpty()) {
      d.report(Code.E_INVALID_DECLARATION, node.get(), "kind is the name of a kind (KIND-2)");
    }
    return text.map(Names::nfc);
  }

  // ---- checks that need type and strategy --------------------------------------------------------

  /**
   * Checks and validates the default (MERGE-4, MERGE-8, MERGE-10, KEY-13). A commutative key declares
   * none. A default is validated as a complete value, which for a {@code deep_merge} key is the complete
   * starting value (MERGE-10, TYPE-62).
   */
  private Optional<Object> defaultValue(Declaration d, TypeDecl type, String strategy) {
    Optional<PNode> node = d.map.get("default");
    if (node.isEmpty()) {
      return Optional.empty();
    }
    if (StrategyRules.isCommutative(strategy)) {
      d.report(Code.E_DEFAULT_NOT_ALLOWED, node.get(), "a commutative key declares no default (MERGE-4)");
      return Optional.empty();
    }
    Optional<Object> value = validator.validate(type, node.get());
    d.fault |= value.isEmpty();
    return value;
  }

  /** An order-dependent key declares a default or has a root value (MERGE-7). */
  private void missingDefault(Declaration d, String strategy) {
    if (StrategyRules.isCommutative(strategy) || d.map.has("default") || rootValueKeys.isEmpty()) {
      return;
    }
    if (!rootValueKeys.get().contains(d.name)) {
      d.report(Code.E_MISSING_DEFAULT, d.map, "an order-dependent key declares a default or has a root value (MERGE-7)");
    }
  }

  /** Checks {@code combine_across_axes} (AXIS-2 to AXIS-6) and returns the value that the model keeps. */
  private Optional<String> acrossAxes(Declaration d, TypeDecl type, String strategy, Settable settable) {
    boolean commutative = StrategyRules.isCommutative(strategy);
    boolean onAxis = hierarchy.isPresent() && settable.names().stream().anyMatch(hierarchy.get()::isAxis);
    Optional<PNode> node = d.map.get("combine_across_axes");
    if (node.isEmpty()) {
      if (!commutative && onAxis) {
        d.report(Code.E_MISSING_AXIS_COMBINE, d.map,
            "an order-dependent key settable on an axis declares combine_across_axes (AXIS-3)");
      }
      return Optional.empty();
    }
    Optional<String> text = Names.text(node.get()).filter(StrategyRules.ACROSS_AXES::contains);
    if (commutative) {
      d.report(Code.E_AXIS_COMBINE_INVALID, node.get(), "a commutative key declares no combine_across_axes (AXIS-2)");
    } else if (text.isEmpty()) {
      d.report(Code.E_AXIS_COMBINE_INVALID, node.get(),
          "combine_across_axes is axis_wins, hierarchy_wins, max or min (AXIS-4)");
    } else if ((text.get().equals("max") || text.get().equals("min")) && !type.type().equals("integer")) {
      d.report(Code.E_AXIS_COMBINE_INVALID, node.get(), "max and min need an integer key (AXIS-5)");
    } else if (hierarchy.isPresent() && settable.complete() && !onAxis) {
      d.report(Code.E_AXIS_COMBINE_INVALID, node.get(), "a key settable on no axis declares no combine_across_axes (AXIS-6)");
    }
    return !commutative && onAxis ? text : Optional.empty();
  }

  /**
   * Checks the kind (KIND-4, KIND-8, KIND-11, KIND-13) and the source-failure rules (SVC-1, SVC-2, SVC-5,
   * SVC-11, SVC-12), and returns the behaviour in effect for a key settable at a service-sourced level.
   */
  private Optional<String> kindAndSourceFailure(Declaration d, String strategy, TypeDecl type,
      Optional<String> kind, Optional<String> ownSourceFailure, Settable settable) {
    Optional<String> kindSourceFailure = Optional.empty();
    if (kind.isPresent() && kinds.isPresent()) {
      KindDef def = kinds.get().effective().get(kind.get());
      PNode kindNode = d.map.get("kind").orElseThrow();
      if (def == null) {
        d.report(Code.E_UNKNOWN_KIND, kindNode, "the kind " + kind.get() + " is not declared (KIND-13)",
            NameSuggester.closest(kind.get(), kinds.get().effective().keySet()));
      } else {
        kindSourceFailure = def.onSourceFailure();
        kindConstraints(d, def, strategy, ownSourceFailure);
      }
    }
    boolean ownInvalid = d.map.has("on_source_failure") && ownSourceFailure.isEmpty();
    boolean atService = hierarchy.isPresent() && settable.names().stream().anyMatch(
        scope -> hierarchy.get().scope(scope) != null && hierarchy.get().scope(scope).isServiceSourced());
    if (!atService || ownInvalid) {
      return Optional.empty();
    }
    Optional<String> effective = ownSourceFailure.isPresent() ? ownSourceFailure : kindSourceFailure;
    if (effective.isEmpty()) {
      d.report(Code.E_MISSING_SOURCE_FAILURE, d.map,
          "a key settable at a service-sourced level has a source-failure behaviour (SVC-1, SVC-2)");
    } else if (effective.get().equals("deny")) {
      if (!StrategyRules.isNarrowing(strategy)) {
        d.report(Code.E_DENY_NOT_NARROWING, d.map.get("combine").orElseThrow(),
            "deny needs a narrowing strategy at a service-sourced level (SVC-11)");
      } else if (strategy.equals("min") && type.min().isEmpty()) {
        d.report(Code.E_DENY_BOUND_REQUIRED, d.map, "deny with min needs a min bound in the type (SVC-12)");
      }
    }
    return effective;
  }

  /** Checks the constraints of a kind on a key that uses it (KIND-4, KIND-8, KIND-11, SVC-5). */
  private void kindConstraints(Declaration d, KindDef def, String strategy, Optional<String> ownSourceFailure) {
    if (def.strategies().isPresent() && !def.strategies().get().contains(strategy)) {
      d.report(Code.E_KIND_VIOLATION, d.map.get("combine").orElseThrow(),
          "the strategy " + strategy + " is not one the kind " + def.name() + " allows (KIND-4)");
    }
    if (def.requireDefault() && !d.map.has("default")) {
      d.report(Code.E_KIND_VIOLATION, d.map, "the kind " + def.name() + " requires a default (KIND-8)");
    }
    if (def.onSourceFailure().isPresent() && ownSourceFailure.isPresent()
        && !def.onSourceFailure().get().equals(ownSourceFailure.get())) {
      d.report(Code.E_KIND_VIOLATION, d.map.get("on_source_failure").orElseThrow(),
          "on_source_failure differs from the value of the kind " + def.name() + " (KIND-11, SVC-5)");
    }
  }
}
