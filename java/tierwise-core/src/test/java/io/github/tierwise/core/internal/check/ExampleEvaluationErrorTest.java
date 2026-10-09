package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Decision;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.Finding;
import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.Severity;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.model.Returns;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * POL-46: an example whose decision raises an evaluation error is reported as {@code E_EXAMPLE_FAILED}, and the
 * message names the error's code. No compile case can reach this, because POL-38 to POL-45 reject every input
 * that would make an example's decision raise; the evaluator here raises on purpose.
 */
class ExampleEvaluationErrorTest {

  private static final Returns ENUM = Returns.enumeration(List.of("ALLOW", "DENY"), false);

  /** An evaluator whose decide raises the given code and counts its calls. */
  private static Evaluator raising(String code, AtomicInteger calls) {
    return new Evaluator() {
      @Override
      public Resolved resolve(Context context, String key) {
        throw new AssertionError("an example does not resolve keys");
      }

      @Override
      public Resolved resolveLocal(Context context, String key) {
        throw new AssertionError("an example does not resolve keys");
      }

      @Override
      public Decision decide(Context context, String feature, FactSet facts) {
        calls.incrementAndGet();
        throw new TierwiseException(code, "raised by the test evaluator");
      }
    };
  }

  /** An example whose expect entry is on line 2 of a small document, so that its location is real. */
  private static ExampleCase example() {
    return new ExampleCase("feature.under_test", "holds",
        CheckTestSupport.root("name: holds\nexpect: ALLOW\n").get("expect").orElseThrow(), Map.of(), Map.of(),
        Map.of(), Map.of(), "ALLOW");
  }

  @Test
  void POL_46_everyEvaluationErrorOfDecideIsAnExampleFailureThatNamesTheCode() {
    List<String> codes = Code.evaluation().stream().sorted().toList();
    assertEquals(11, codes.size(), "EVAL-38");
    for (String code : codes) {
      AtomicInteger calls = new AtomicInteger();
      Findings findings = new Findings();
      ExampleRunner.evaluate(raising(code, calls), example(), ENUM, findings);
      assertEquals(1, calls.get(), code);
      List<Finding> list = findings.sorted();
      assertEquals(1, list.size(), code + ": " + list);
      Finding finding = list.get(0);
      assertEquals(Code.E_EXAMPLE_FAILED, finding.code(), code);
      assertEquals(Severity.ERROR, finding.severity());
      assertEquals(List.of("expect"), finding.path(), "FIND-34: at the example's expect entry");
      assertEquals(2, finding.line());
      assertTrue(finding.message().contains(code), "POL-46: the message names " + code + ": " + finding.message());
    }
  }
}
