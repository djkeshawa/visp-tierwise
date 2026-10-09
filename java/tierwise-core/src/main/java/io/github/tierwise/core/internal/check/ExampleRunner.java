package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Decision;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.LoadedBundle;
import io.github.tierwise.core.Tierwise;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.bundle.BundleWriter;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.ScopeDef;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs the valid examples through the evaluator over the in-memory model (POL-41, POL-42, POL-46). The model
 * is written as a bundle and read back, so the evaluator is the one that runs at evaluation time.
 *
 * <p>An example passes when its decision's effect equals {@code expect} (POL-41), compared under the equality
 * of NORM-19 (POL-48). An example whose decision fails, or whose decision raises an evaluation error, is
 * reported as {@code E_EXAMPLE_FAILED} at its {@code expect} entry (FIND-34). The examples call no provider
 * (SVC-22); each service-sourced level gets a placeholder provider, because creating the evaluator needs one.
 */
final class ExampleRunner {

  private static final Duration PLACEHOLDER_TIMEOUT = Duration.ofSeconds(1);

  private ExampleRunner() {
  }

  /**
   * Runs the examples.
   *
   * @param model the complete model
   * @param cases the valid examples, each naming a feature of the model
   * @param findings the findings to report to
   */
  static void run(Model model, List<ExampleCase> cases, Findings findings) {
    if (cases.isEmpty()) {
      return;
    }
    Evaluator evaluator;
    try {
      LoadedBundle bundle = Tierwise.loadBundle(BundleWriter.write(model).bytes());
      evaluator = Tierwise.evaluator(bundle, placeholders(model));
    } catch (TierwiseException e) {
      return;
    }
    for (ExampleCase example : cases) {
      Returns returns = model.features().get(example.feature()).returns();
      evaluate(evaluator, example, returns, findings);
    }
  }

  private static EvaluatorOptions placeholders(Model model) {
    EvaluatorOptions.Builder builder = EvaluatorOptions.builder();
    for (Map.Entry<String, ScopeDef> scope : model.hierarchy().scopes().entrySet()) {
      if (scope.getValue().isServiceSourced()) {
        builder.provider(scope.getKey(), node -> Map.of(), PLACEHOLDER_TIMEOUT);
      }
    }
    return builder.build();
  }

  /**
   * Runs one example and reports it when it fails (POL-41, POL-42, POL-46).
   *
   * @param evaluator the evaluator over the model
   * @param example the example
   * @param returns the {@code returns} of the example's feature, which decides how effects are compared
   * @param findings the findings to report to
   */
  static void evaluate(Evaluator evaluator, ExampleCase example, Returns returns, Findings findings) {
    Context context = contextOf(example);
    FactSet facts = FactSet.of(example.facts(), example.children());
    try {
      Decision decision = evaluator.decide(context, example.feature(), facts);
      if (!sameEffect(returns, decision.effect(), example.expected())) {
        findings.error(Code.E_EXAMPLE_FAILED, example.expectNode(), "the example \"" + example.name() + "\" expects "
            + example.expected() + " but the decision is " + decision.effect() + " (POL-41)");
      }
    } catch (TierwiseException e) {
      findings.error(Code.E_EXAMPLE_FAILED, example.expectNode(), "the example \"" + example.name()
          + "\" raised " + e.code() + " (POL-46): " + e.getMessage());
    }
  }

  /**
   * Compares a decided effect with an expected effect (POL-41, POL-48). A scalar effect is compared by equality,
   * and an object effect field by field under the equality of NORM-19 for each field's type, so the order of
   * the fields and of the members of a set field is not significant.
   *
   * @param returns the {@code returns} of the feature
   * @param actual the decided effect, plain
   * @param expected the expected effect, plain
   * @return {@code true} when the two are equal
   */
  static boolean sameEffect(Returns returns, Object actual, Object expected) {
    if (actual == null || expected == null) {
      return actual == expected;
    }
    if (!returns.objectForm()) {
      return Objects.equals(actual, expected);
    }
    return sameObject(returns.fields(), actual, expected);
  }

  /** NORM-19 for one value of a declared type. */
  private static boolean same(TypeDecl type, Object left, Object right) {
    if (left == null || right == null) {
      return left == right;
    }
    return switch (type.type()) {
      case "set" -> sameSet(left, right);
      case "list" -> sameList(type, left, right);
      case "object" -> sameObject(type.fields(), left, right);
      default -> Objects.equals(left, right);
    };
  }

  private static boolean sameSet(Object left, Object right) {
    return left instanceof List<?> a && right instanceof List<?> b && a.size() == b.size()
        && new HashSet<>(a).equals(new HashSet<>(b));
  }

  private static boolean sameList(TypeDecl type, Object left, Object right) {
    if (!(left instanceof List<?> a) || !(right instanceof List<?> b) || a.size() != b.size()) {
      return false;
    }
    if (type.items().isEmpty()) {
      return Objects.equals(left, right);
    }
    for (int i = 0; i < a.size(); i++) {
      if (!same(type.items().get(), a.get(i), b.get(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean sameObject(Map<String, TypeDecl> fields, Object left, Object right) {
    if (!(left instanceof Map<?, ?> a) || !(right instanceof Map<?, ?> b) || !a.keySet().equals(b.keySet())) {
      return false;
    }
    for (Map.Entry<?, ?> entry : a.entrySet()) {
      TypeDecl field = fields.get(entry.getKey());
      Object other = b.get(entry.getKey());
      boolean equal = field == null ? Objects.equals(entry.getValue(), other) : same(field, entry.getValue(), other);
      if (!equal) {
        return false;
      }
    }
    return true;
  }

  private static Context contextOf(ExampleCase example) {
    Context.Builder builder = Context.builder();
    example.levels().forEach(builder::level);
    example.axes().forEach(builder::axis);
    return builder.build();
  }
}
