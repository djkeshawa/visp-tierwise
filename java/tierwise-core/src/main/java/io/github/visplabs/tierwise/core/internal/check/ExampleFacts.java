package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.FactDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.project.Names;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import io.github.visplabs.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses and checks the {@code facts} and, in the explicit form, the {@code children} of an example (POL-32 to
 * POL-39, POL-45, FACT-29 to FACT-33).
 *
 * <p>In the shorthand form a child fact with a list value is shorthand: one child map for each element (POL-33).
 * An example that declares {@code children} is in the explicit form, where a list under {@code facts} is a
 * structure violation (POL-32, POL-37). A {@code null} value is a missing fact, not an illegal value (POL-39).
 */
final class ExampleFacts {

  /**
   * The parsed facts.
   *
   * @param facts the single-value facts, plain; a {@code null} value is a missing fact
   * @param children the child maps of each child entity, plain
   * @param valid {@code false} when a finding was reported
   */
  record Parsed(Map<String, Object> facts, Map<String, List<Map<String, Object>>> children, boolean valid) {
  }

  private static final ValueRules RULES = ValueRules.DEFAULT.withOwnerCode(Code.E_ILLEGAL_FACT_VALUE);

  private final Model model;
  private final ExampleScope scope;
  private final ValueValidator validator;
  private final Findings findings;

  private ExampleFacts(Model model, ExampleScope scope, ValueValidator validator, Findings findings) {
    this.model = model;
    this.scope = scope;
    this.validator = validator;
    this.findings = findings;
  }

  /**
   * Parses the facts of one example.
   *
   * @param model the model
   * @param scope the eligible entities of the decided entity
   * @param validator the validator that reports the faults of fact values
   * @param findings the findings to report to
   * @param facts the {@code facts} node
   * @param children the {@code children} node, or {@code null} for the shorthand form
   * @return the parsed facts
   */
  static Parsed parse(Model model, ExampleScope scope, ValueValidator validator, Findings findings, PNode facts,
      PNode children) {
    return new ExampleFacts(model, scope, validator, findings).read(facts, children);
  }

  private Parsed read(PNode factsNode, PNode childrenNode) {
    if (!(factsNode instanceof PMap facts)) {
      findings.error(Code.E_INVALID_DECLARATION, factsNode, "the facts of an example are not a mapping (POL-45)");
      return new Parsed(Map.of(), Map.of(), false);
    }
    boolean explicit = childrenNode != null;
    Map<String, Object> single = new LinkedHashMap<>();
    Map<String, List<Map<String, Object>>> children = new LinkedHashMap<>();
    boolean valid = true;
    Set<String> shorthand = new HashSet<>();
    for (Map.Entry<String, PNode> entry : facts.entries().entrySet()) {
      String name = Names.nfc(entry.getKey());
      PNode value = entry.getValue();
      FactDef fact = model.facts().get(name);
      if (fact == null) {
        findings.error(Code.E_UNKNOWN_FACT, value, "the example gives " + name + ", which is not declared (POL-38)");
        valid = false;
      } else if (scope.isSingleValue(fact.entity())) {
        valid &= singleValue(name, fact, value, single);
      } else if (scope.isChild(fact.entity()) && !explicit && value instanceof PSeq list) {
        valid &= shorthand(name, fact, list, shorthand, children);
      } else {
        findings.error(Code.E_INVALID_DECLARATION, value, "the example gives " + name
            + " where a single-value fact or shorthand list of a child is expected (POL-45)");
        valid = false;
      }
    }
    if (explicit) {
      valid &= explicitChildren(childrenNode, children);
    }
    return new Parsed(single, children, valid);
  }

  private boolean singleValue(String name, FactDef fact, PNode value, Map<String, Object> into) {
    if (value instanceof PSeq) {
      findings.error(Code.E_INVALID_DECLARATION, value, "a list is not shorthand for the single-value fact " + name
          + " (POL-37)");
      return false;
    }
    if (isNull(value)) {
      into.put(name, null);
      return true;
    }
    Optional<Object> plain = validator.validate(fact.type(), value, RULES);
    plain.ifPresent(v -> into.put(name, v));
    return plain.isPresent();
  }

  private boolean shorthand(String name, FactDef fact, PSeq list, Set<String> seen,
      Map<String, List<Map<String, Object>>> children) {
    if (!seen.add(fact.entity())) {
      findings.error(Code.E_INVALID_DECLARATION, list, "the example has two shorthand lists for " + fact.entity()
          + " (POL-36)");
      return false;
    }
    boolean valid = true;
    List<Map<String, Object>> maps = new ArrayList<>();
    for (PNode element : list.items()) {
      if (isNull(element)) {
        maps.add(childMap(name, null));
        continue;
      }
      Optional<Object> plain = validator.validate(fact.type(), element, RULES);
      if (plain.isPresent()) {
        maps.add(childMap(name, plain.get()));
      } else {
        valid = false;
      }
    }
    children.put(fact.entity(), maps);
    return valid;
  }

  private boolean explicitChildren(PNode childrenNode, Map<String, List<Map<String, Object>>> children) {
    if (!(childrenNode instanceof PMap entities)) {
      findings.error(Code.E_INVALID_DECLARATION, childrenNode, "the children of an example are not a mapping (POL-45)");
      return false;
    }
    boolean valid = true;
    for (Map.Entry<String, PNode> entry : entities.entries().entrySet()) {
      String entity = entry.getKey();
      PNode list = entry.getValue();
      if (!scope.isChild(entity)) {
        findings.error(Code.E_INVALID_DECLARATION, list, entity + " is not a child of the decided entity (FACT-29)");
        valid = false;
        continue;
      }
      if (!(list instanceof PSeq items)) {
        findings.error(Code.E_INVALID_DECLARATION, list, "the children of " + entity + " are not a list (FACT-31)");
        valid = false;
        continue;
      }
      List<Map<String, Object>> maps = new ArrayList<>();
      for (PNode item : items.items()) {
        Optional<Map<String, Object>> map = explicitChild(entity, item);
        if (map.isPresent()) {
          maps.add(map.get());
        } else {
          valid = false;
        }
      }
      children.put(entity, maps);
    }
    return valid;
  }

  private Optional<Map<String, Object>> explicitChild(String entity, PNode item) {
    if (!(item instanceof PMap child)) {
      findings.error(Code.E_INVALID_DECLARATION, item, "a child of " + entity + " is not a mapping (FACT-32)");
      return Optional.empty();
    }
    boolean valid = true;
    Map<String, Object> values = new LinkedHashMap<>();
    for (Map.Entry<String, PNode> entry : child.entries().entrySet()) {
      String name = Names.nfc(entry.getKey());
      PNode value = entry.getValue();
      FactDef fact = model.facts().get(name);
      if (fact == null) {
        findings.error(Code.E_UNKNOWN_FACT, value, "the example gives " + name + ", which is not declared (POL-38)");
        valid = false;
      } else if (!fact.entity().equals(entity)) {
        findings.error(Code.E_INVALID_DECLARATION, value, name + " is not a fact of " + entity + " (FACT-33)");
        valid = false;
      } else if (value instanceof PSeq) {
        findings.error(Code.E_INVALID_DECLARATION, value, "a list is not allowed for the child fact " + name
            + " (POL-37)");
        valid = false;
      } else if (isNull(value)) {
        values.put(name, null);
      } else {
        Optional<Object> plain = validator.validate(fact.type(), value, RULES);
        if (plain.isPresent()) {
          values.put(name, plain.get());
        } else {
          valid = false;
        }
      }
    }
    return valid ? Optional.of(values) : Optional.empty();
  }

  private static Map<String, Object> childMap(String name, Object value) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put(name, value);
    return map;
  }

  private static boolean isNull(PNode node) {
    return node instanceof PScalar scalar && scalar.kind() == PScalar.Kind.NULL;
  }
}
