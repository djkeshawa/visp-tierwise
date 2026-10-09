package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Decision;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests how an example's decision is judged: an evaluation error is an example failure (POL-46, FIND-34), and
 * an object effect is compared under the equality of NORM-19 (POL-41, POL-48).
 */
class ExampleRunnerTest {

  private static final Returns ENUM = Returns.enumeration(List.of("ALLOW", "DENY"), false);

  /** An object effect with a set field and a list field, whose members have a fixed type. */
  private static final Returns OBJECT = Returns.object(Map.of(
      "method", TypeDecl.enumeration(List.of("GROUND", "NEXT_DAY")),
      "days", TypeDecl.integer(1L, 30L),
      "tags", TypeDecl.set(List.of("A", "B", "C")),
      "steps", TypeDecl.list(TypeDecl.enumeration(List.of("ONE", "TWO")))));

  private static ExampleCase example(String expect) {
    return new ExampleCase("checkout.order_submission", "Example", CheckTestSupport.root("expect: " + expect + "\n")
        .get("expect").orElseThrow(), Map.of(), Map.of(), Map.of(), Map.of(), expect);
  }

  @Test
  void POL_46_AndFIND_34_anEvaluationErrorIsAnExampleFailureThatNamesTheCode() {
    Evaluator raises = new Evaluator() {
      @Override
      public Resolved resolve(Context context, String key) {
        throw new UnsupportedOperationException("the example test does not resolve keys");
      }

      @Override
      public Resolved resolveLocal(Context context, String key) {
        throw new UnsupportedOperationException("the example test does not resolve keys");
      }

      @Override
      public Decision decide(Context context, String feature, FactSet facts) {
        throw new TierwiseException(Code.E_FACTS_INVALID, "the facts are not valid");
      }
    };
    Findings findings = new Findings();
    ExampleRunner.evaluate(raises, example("ALLOW"), ENUM, findings);
    CheckTestSupport.assertFindings(findings, "E_EXAMPLE_FAILED 1 [expect]");
    String message = findings.sorted().get(0).message();
    assertTrue(message.contains(Code.E_FACTS_INVALID), message);
  }

  @Test
  void POL_48_twoObjectEffectsAreEqualWhenTheirFieldsAreEqualAndNoOrderMatters() {
    Map<String, Object> decided = Map.of("method", "GROUND", "days", 2L, "tags", List.of("A", "B"),
        "steps", List.of("ONE", "TWO"));
    Map<String, Object> reordered = new LinkedHashMap<>();
    reordered.put("steps", List.of("ONE", "TWO"));
    reordered.put("tags", List.of("B", "A"));
    reordered.put("days", 2L);
    reordered.put("method", "GROUND");
    assertTrue(ExampleRunner.sameEffect(OBJECT, decided, reordered));
  }

  @Test
  void POL_48_aListFieldIsOrderedAndAFieldThatDiffersIsNotEqual() {
    Map<String, Object> decided = Map.of("method", "GROUND", "days", 2L, "tags", List.of("A"),
        "steps", List.of("ONE", "TWO"));
    Map<String, Object> swappedSteps = Map.of("method", "GROUND", "days", 2L, "tags", List.of("A"),
        "steps", List.of("TWO", "ONE"));
    assertFalse(ExampleRunner.sameEffect(OBJECT, decided, swappedSteps));

    Map<String, Object> otherDays = Map.of("method", "GROUND", "days", 3L, "tags", List.of("A"),
        "steps", List.of("ONE", "TWO"));
    assertFalse(ExampleRunner.sameEffect(OBJECT, decided, otherDays));

    Map<String, Object> missingField = Map.of("method", "GROUND", "days", 2L, "steps", List.of("ONE", "TWO"));
    assertFalse(ExampleRunner.sameEffect(OBJECT, decided, missingField));
  }

  @Test
  void POL_41_aScalarEffectIsComparedByEquality() {
    assertTrue(ExampleRunner.sameEffect(ENUM, "ALLOW", "ALLOW"));
    assertFalse(ExampleRunner.sameEffect(ENUM, "ALLOW", "DENY"));
    assertFalse(ExampleRunner.sameEffect(ENUM, null, "DENY"));
  }
}
