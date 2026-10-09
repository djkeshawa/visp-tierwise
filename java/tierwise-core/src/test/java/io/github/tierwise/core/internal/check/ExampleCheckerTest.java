package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tierwise.core.internal.project.Origin;
import io.github.tierwise.core.internal.project.SourceDocument;
import io.github.tierwise.core.internal.project.SourceRole;
import io.github.tierwise.core.internal.yaml.PMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Tests the shape and the name checks of examples when the model is absent (POL-28, POL-29, POL-30, FEAT-2):
 * the shape checks run in every case, so these tests need no model (FIND-35).
 */
class ExampleCheckerTest {

  private static List<ExampleCase> check(String yaml, Findings findings) {
    PMap root = CheckTestSupport.root(yaml);
    SourceDocument document = new SourceDocument("rules/checkout.order_submission.yaml", Origin.FILE, 1,
        SourceRole.RULES, Optional.empty(), Optional.empty(), root, List.of());
    return new ExampleChecker(null, findings).check(document);
  }

  @Test
  void POL_28_theExamplesMemberIsAListOfMappings() {
    Findings notList = new Findings();
    assertEquals(List.of(), check("examples: {}\n", notList));
    CheckTestSupport.assertFindings(notList, "E_INVALID_DECLARATION 1 [examples]");

    Findings notMapping = new Findings();
    assertEquals(List.of(), check("examples:\n  - 3\n", notMapping));
    CheckTestSupport.assertFindings(notMapping, "E_INVALID_DECLARATION 2 [examples, 0]");
  }

  @Test
  void FEAT_2_AnExampleNeedsEveryRequiredMemberAndAllowsNoOtherMember() {
    Findings missing = new Findings();
    check("examples:\n  - name: One\n    facts: {}\n", missing);
    CheckTestSupport.assertFindings(missing, "E_INVALID_DECLARATION 2 [examples, 0]");

    Findings extra = new Findings();
    check("examples:\n  - name: One\n    context: {}\n    facts: {}\n    expect: ALLOW\n    note: x\n", extra);
    CheckTestSupport.assertFindings(extra, "E_INVALID_DECLARATION 6 [examples, 0, note]");
  }

  @Test
  void POL_29_aNameThatIsNotAStringIsInvalid() {
    Findings findings = new Findings();
    check("examples:\n  - name: 3\n    context: {}\n    facts: {}\n    expect: ALLOW\n", findings);
    CheckTestSupport.assertFindings(findings, "E_INVALID_DECLARATION 2 [examples, 0, name]");
  }

  @Test
  void POL_30_aRepeatedNameIsReportedAtTheLaterName() {
    Findings findings = new Findings();
    check("examples:\n"
        + "  - name: Same\n    context: {}\n    facts: {}\n    expect: ALLOW\n"
        + "  - name: Same\n    context: {}\n    facts: {}\n    expect: ALLOW\n", findings);
    CheckTestSupport.assertFindings(findings, "E_DUPLICATE_VALUE 6 [examples, 1, name]");
  }
}
