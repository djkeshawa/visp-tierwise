package io.github.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Decision;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.Finding;
import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.TierwiseException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * FIND-9 and EVAL-22: an evaluation operation does not perform a validation, and an evaluation error is raised as a
 * {@link TierwiseException}, not returned as a finding.
 */
class EvaluationNotValidationTest {

  private final Evaluator evaluator = TestModels.evaluator(TestModels.standard(),
      TestModels.values(Map.of()), TestModels.values(Map.of()), null);

  private static final FactSet VALID = FactSet.fromJson(Map.of("facts",
      Map.of("customer.active", true, "customer.credit_hold", false)));

  private static void assertRaises(String code, Supplier<?> operation) {
    TierwiseException error = assertThrows(TierwiseException.class, operation::get);
    assertEquals(code, error.code());
    assertTrue(Code.evaluation().contains(error.code()), "EVAL-38: " + error.code());
  }

  @Test
  void FIND_9_everyFailingResolveRaisesAnEvaluationError() {
    for (boolean local : List.of(false, true)) {
      java.util.function.BiFunction<Context, String, Resolved> resolve =
          local ? evaluator::resolveLocal : evaluator::resolve;
      assertRaises(Code.E_UNKNOWN_KEY, () -> resolve.apply(Context.empty(), "no.such.key"));
      assertRaises(Code.E_UNKNOWN_SCOPE, () -> resolve.apply(Context.builder().level("warehouse", "x").build(), "k.all"));
      assertRaises(Code.E_UNKNOWN_NODE, () -> resolve.apply(Context.builder().level("division", "east").build(), "k.all"));
    }
  }

  @Test
  void FIND_9_everyFailingDecideRaisesAnEvaluationError() {
    assertRaises(Code.E_UNKNOWN_FEATURE, () -> evaluator.decide(Context.empty(), "no.such.feature", VALID));
    assertRaises(Code.E_FACTS_INVALID, () -> evaluator.decide(Context.empty(), "f.gate",
        FactSet.fromJson(Map.of("facts", Map.of("customer.active", "not a boolean")))));
    assertRaises(Code.E_UNKNOWN_SCOPE, () -> evaluator.decide(Context.builder().level("warehouse", "x").build(),
        "f.gate", VALID));
  }

  @Test
  void FIND_9_aResultHasNoFindings() {
    assertTrue(evaluator.decide(Context.empty(), "f.gate", VALID) != null);
    for (Class<?> type : List.of(Resolved.class, Decision.class)) {
      for (RecordComponent component : type.getRecordComponents()) {
        assertFalse(component.getType() == Finding.class, type + "." + component.getName());
        assertFalse(component.getGenericType().getTypeName().contains(Finding.class.getName()),
            type + "." + component.getName());
      }
    }
  }

  @Test
  void FIND_9_theEvaluatorInterfaceOffersNoValidation() {
    List<String> names = new ArrayList<>();
    for (Method method : Evaluator.class.getMethods()) {
      if (!method.isDefault() && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
        names.add(method.getName());
      }
      assertFalse(method.getReturnType().getName().contains("Finding")
          || method.getReturnType().getName().contains("CompileResult"), method.toString());
    }
    names.sort(null);
    assertEquals(List.of("decide", "resolve", "resolveLocal"), names);
  }

  @Test
  void FIND_9_theEvaluatorCodeUsesNoValidationCode() throws IOException {
    // The evaluator is built from a loaded bundle only. Its sources must not reach the compiler, the project loader
    // or the lint rules, which are the only code that determines findings.
    Path eval = Path.of("src/main/java/io/github/tierwise/core/internal/eval");
    assertTrue(Files.isDirectory(eval), "run from the tierwise-core module directory: " + eval.toAbsolutePath());
    List<String> offending = new ArrayList<>();
    try (Stream<Path> files = Files.list(eval)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
        String text = Files.readString(file);
        for (String forbidden : List.of("core.internal.check", "core.internal.lint", "core.internal.project",
            "core.Finding", "Findings")) {
          if (text.contains(forbidden)) {
            offending.add(file.getFileName() + " uses " + forbidden);
          }
        }
      }
    }
    assertEquals(List.of(), offending);
  }
}
