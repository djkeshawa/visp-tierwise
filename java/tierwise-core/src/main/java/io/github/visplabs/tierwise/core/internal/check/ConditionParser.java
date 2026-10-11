package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.ConditionDef;
import io.github.visplabs.tierwise.core.internal.model.FactDef;
import io.github.visplabs.tierwise.core.internal.model.SettingDef;
import io.github.visplabs.tierwise.core.internal.project.NameSuggester;
import io.github.visplabs.tierwise.core.internal.project.Names;
import io.github.visplabs.tierwise.core.internal.yaml.OutOfRangeInteger;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import io.github.visplabs.tierwise.core.internal.yaml.PSeq;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks one condition of a rule and builds it (section 8.1 to 8.5: RULE-6, RULE-9 to RULE-26, RULE-28).
 *
 * <p>When a condition breaks more than one check, exactly one finding is reported, the finding of the first
 * failing check in the order of RULE-28: the fact checks, then the form checks, then the setting checks,
 * then the literal checks. A finding is located at the condition, that is at the value of its entry in
 * {@code when}. Two findings about a setting reference are located at the reference instead: an unknown
 * setting (RULE-22), and a reference that is the only element of a literal list (RULE-23). A check whose
 * input an earlier stage failed to build is skipped (FIND-35).
 */
final class ConditionParser {

  private static final Set<String> LIST_OPERATORS = Set.of("in", "all_in", "any_in");
  private static final long MAX_SAFE = (1L << 53) - 1;
  private static final ValueRules LITERAL_RULES = ValueRules.DEFAULT.withOwnerCode(Code.E_ILLEGAL_FACT_VALUE);

  /** The form of a condition: the trace operator and the operand node. */
  private record Form(String op, PNode operand) {
  }

  private final Findings findings;
  private final FeatureInputs inputs;
  private final Optional<FeatureScope> scope;
  private final Optional<Map<String, SettingEntry>> settings;

  /**
   * Creates the parser.
   *
   * @param findings the accumulator that receives the findings
   * @param inputs what the earlier stages built
   * @param scope the facts the decided entity may read, when the decided entity is known
   * @param settings the settings of the policy, when they were read
   */
  ConditionParser(Findings findings, FeatureInputs inputs, Optional<FeatureScope> scope,
      Optional<Map<String, SettingEntry>> settings) {
    this.findings = findings;
    this.inputs = inputs;
    this.scope = scope;
    this.settings = settings;
  }

  /**
   * Parses one condition.
   *
   * @param rawFact the fact name as written
   * @param node the condition node, the value of the entry
   * @return the condition, or empty when it has a fault (which is reported) or a check was skipped
   */
  Optional<ConditionDef> parse(String rawFact, PNode node) {
    String name = Names.nfc(rawFact);
    Optional<FactDef> fact = fact(name, node);
    if (fact.isEmpty()) {
      return Optional.empty();
    }
    Optional<FeatureScope.Position> position = scope.map(found -> found.position(fact.get().entity()));
    if (position.isPresent() && !inScope(position.get(), node, name)) {
      return Optional.empty();
    }
    Form form = form(node);
    if (form == null) {
      findings.error(Code.E_INVALID_DECLARATION, node,
          "a condition is a literal or one of in, all_in and any_in with a list or a setting reference (RULE-3, RULE-6)");
      return Optional.empty();
    }
    if (position.isPresent() && !quantifier(position.get(), form, node)) {
      return Optional.empty();
    }
    return operand(name, fact.get(), form, node);
  }

  // ---- facts (RULE-9 to RULE-12) -----------------------------------------------------------------

  private Optional<FactDef> fact(String name, PNode node) {
    if (inputs.facts().isEmpty()) {
      return Optional.empty();
    }
    Map<String, FactDef> facts = inputs.facts().get();
    int dot = name.indexOf('.');
    if (dot < 0) {
      findings.error(Code.E_UNKNOWN_ENTITY, node, name + " has no entity part (RULE-9, FACT-3)",
          inputs.entities().flatMap(entities -> NameSuggester.closest(name, entities.keySet())));
      return Optional.empty();
    }
    if (dot == name.length() - 1) {
      findings.error(Code.E_INVALID_DECLARATION, node, name + " has an empty attribute part (RULE-9, FACT-3)");
      return Optional.empty();
    }
    FactDef fact = facts.get(name);
    if (fact == null) {
      findings.error(Code.E_UNKNOWN_FACT, node, name + " is not a declared fact (RULE-9, RULE-10)",
          NameSuggester.closest(name, facts.keySet()));
      return Optional.empty();
    }
    return Optional.of(fact);
  }

  private boolean inScope(FeatureScope.Position position, PNode node, String name) {
    if (position == FeatureScope.Position.DEEP) {
      findings.error(Code.E_DEPTH_UNSUPPORTED, node,
          name + " lies two or more has_many edges below the decided entity (RULE-11)");
      return false;
    }
    if (position == FeatureScope.Position.OUT) {
      findings.error(Code.E_FACT_NOT_IN_SCOPE, node, name + " is not eligible for the decided entity (RULE-12)");
      return false;
    }
    return true;
  }

  // ---- form (RULE-3, RULE-6, RULE-13, RULE-14) ---------------------------------------------------

  /** Reads the form of a condition, or returns {@code null} when its syntax is malformed (RULE-6). */
  private static Form form(PNode node) {
    if (node instanceof PMap map) {
      if (map.entries().size() != 1) {
        return null;
      }
      Map.Entry<String, PNode> entry = map.entries().entrySet().iterator().next();
      PNode operand = entry.getValue();
      boolean listOperand = operand instanceof PSeq || FeatureNodes.isReference(operand);
      return LIST_OPERATORS.contains(entry.getKey()) && listOperand ? new Form(entry.getKey(), operand) : null;
    }
    return node instanceof PSeq ? null : new Form("eq", node);
  }

  /** Checks the quantifier of the form against the kind of the fact (RULE-13, RULE-14, RULE-26). */
  private boolean quantifier(FeatureScope.Position position, Form form, PNode node) {
    boolean quantified = form.op().equals("all_in") || form.op().equals("any_in");
    if (position == FeatureScope.Position.CHILD && !quantified) {
      findings.error(Code.E_QUANTIFIER_REQUIRED, node, "a child fact needs all_in or any_in (RULE-13)");
      return false;
    }
    if (position == FeatureScope.Position.SINGLE && quantified) {
      findings.error(Code.E_QUANTIFIER_NOT_ALLOWED, node, "a single-value fact takes eq or in (RULE-14)");
      return false;
    }
    return true;
  }

  // ---- operand ----------------------------------------------------------------------------------

  private Optional<ConditionDef> operand(String factName, FactDef fact, Form form, PNode node) {
    boolean list = form.operand() instanceof PSeq;
    List<PNode> elements = list ? ((PSeq) form.operand()).items() : List.of(form.operand());
    List<PNode> references = new ArrayList<>();
    for (PNode element : elements) {
      if (FeatureNodes.isReference(element)) {
        references.add(element);
      }
    }
    if (!references.isEmpty()) {
      return reference(factName, form, node, list, elements, references);
    }
    return literals(factName, fact, form, node, elements);
  }

  /** The setting checks of RULE-22 to RULE-25. */
  private Optional<ConditionDef> reference(String factName, Form form, PNode node, boolean list,
      List<PNode> elements, List<PNode> references) {
    if (settings.isEmpty()) {
      return Optional.empty();
    }
    Map<String, SettingEntry> declared = settings.get();
    for (PNode reference : references) {
      String name = FeatureNodes.referenceName(reference);
      if (!declared.containsKey(name)) {
        findings.error(Code.E_UNKNOWN_SETTING, reference, "the setting " + name + " is not declared by the policy (RULE-22)",
            NameSuggester.closest(name, declared.keySet()));
        return Optional.empty();
      }
    }
    if (list) {
      PNode at = references.size() == elements.size() ? references.get(0) : node;
      findings.error(Code.E_INVALID_SETTING, at, "a setting reference is the whole operand, not an element of a list (RULE-23)");
      return Optional.empty();
    }
    String name = FeatureNodes.referenceName(references.get(0));
    Optional<SettingDef> setting = declared.get(name).definition();
    if (setting.isEmpty()) {
      return Optional.empty();
    }
    SettingDef.Kind wanted = form.op().equals("eq") ? SettingDef.Kind.VALUE_OF : SettingDef.Kind.LIST_OF;
    if (setting.get().kind() != wanted) {
      findings.error(Code.E_INVALID_SETTING, node, "the setting " + name + " has the wrong kind for the form (RULE-24)");
      return Optional.empty();
    }
    if (!setting.get().fact().equals(factName)) {
      findings.error(Code.E_INVALID_SETTING, node, "the setting " + name + " is declared for another fact (RULE-25)");
      return Optional.empty();
    }
    return Optional.of(new ConditionDef(factName, form.op(), ConditionDef.SETTING_PREFIX + name));
  }

  /** The literal checks of RULE-17 to RULE-21, in the order of RULE-28. */
  private Optional<ConditionDef> literals(String factName, FactDef fact, Form form, PNode node, List<PNode> elements) {
    if (fact.type().type().equals("integer") && elements.stream().anyMatch(ConditionParser::outOfRange)) {
      findings.error(Code.E_INTEGER_RANGE, node, "an integer literal lies outside plus or minus 2^53 - 1 (RULE-20)");
      return Optional.empty();
    }
    ValueValidator validator = new ValueValidator(new Findings());
    List<Object> values = new ArrayList<>();
    boolean legal = true;
    for (PNode element : elements) {
      Optional<Object> value = validator.validate(fact.type(), element, LITERAL_RULES);
      if (value.isPresent()) {
        values.add(value.get());
      } else {
        legal = false;
      }
    }
    if (!legal) {
      findings.error(Code.E_ILLEGAL_FACT_VALUE, node, "a literal is not a legal value of the fact's type (RULE-17 to RULE-19)");
      return Optional.empty();
    }
    if (new HashSet<>(values).size() != values.size()) {
      findings.error(Code.E_DUPLICATE_VALUE, node, "a literal list repeats an element (RULE-21)");
      return Optional.empty();
    }
    return Optional.of(new ConditionDef(factName, form.op(), form.op().equals("eq") ? values.get(0) : values));
  }

  private static boolean outOfRange(PNode element) {
    if (!(element instanceof PScalar scalar) || scalar.kind() != PScalar.Kind.INTEGER) {
      return false;
    }
    Object value = scalar.value();
    if (value == OutOfRangeInteger.INSTANCE) {
      return true;
    }
    if (value instanceof BigInteger big) {
      return big.abs().compareTo(BigInteger.valueOf(MAX_SAFE)) > 0;
    }
    long number = (Long) value;
    return number > MAX_SAFE || number < -MAX_SAFE;
  }
}
