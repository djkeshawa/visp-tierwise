package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Finding;
import io.github.visplabs.tierwise.core.Severity;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The accumulator: FIND-3, FIND-5, FIND-15, FIND-24 to FIND-26 and the gates of POL-47 and LINT-11. */
class FindingsTest {

  private static Finding finding(String code, String file, List<Object> path, int line) {
    return Finding.of(code, Severity.ERROR, file, path, line, "m");
  }

  private static List<String> keys(Findings findings) {
    List<String> list = new ArrayList<>();
    for (Finding f : findings.sorted()) {
      list.add(f.file() + ":" + f.line() + " " + f.path() + " " + f.code());
    }
    return list;
  }

  @Test
  void FIND_25_twoFindingsWithTheSameKeyAreOneFindingAndTheFirstStays() {
    Findings findings = new Findings();
    findings.add(new Finding("E_PARSE", Severity.ERROR, "a", List.of(), 1, "first", Optional.empty()));
    findings.add(new Finding("E_PARSE", Severity.ERROR, "a", List.of(), 1, "second", Optional.of("x")));
    assertEquals(1, findings.size());
    assertEquals("first", findings.sorted().get(0).message());
  }

  @Test
  void FIND_24_aDifferenceInCodeFileLineOrPathMakesAnotherFinding() {
    Findings findings = new Findings();
    findings.add(finding("E_PARSE", "a", List.of("x"), 1));
    findings.add(finding("E_MANIFEST", "a", List.of("x"), 1));
    findings.add(finding("E_PARSE", "b", List.of("x"), 1));
    findings.add(finding("E_PARSE", "a", List.of("x"), 2));
    findings.add(finding("E_PARSE", "a", List.of("y"), 1));
    assertEquals(5, findings.size());
  }

  @Test
  void FIND_26_findingsAreOrderedByFileThenLineThenPathThenCode() {
    Findings findings = new Findings();
    findings.add(finding("E_B", "schema/b.yaml", List.of(), 1));
    findings.add(finding("E_B", "tierwise.yaml", List.of("sources"), 9));
    findings.add(finding("E_B", "schema/a.yaml", List.of("keys", "z"), 5));
    findings.add(finding("E_B", "schema/a.yaml", List.of("keys", "a"), 5));
    findings.add(finding("E_A", "schema/a.yaml", List.of("keys", "a"), 5));
    findings.add(finding("E_B", "schema/a.yaml", List.of("keys"), 5));
    findings.add(finding("E_B", "schema/a.yaml", List.of("keys", "a", 10), 5));
    findings.add(finding("E_B", "schema/a.yaml", List.of("keys", "a", 2), 5));
    findings.add(finding("E_B", "schema/a.yaml", List.of(), 40));
    findings.add(finding("E_B", "tierwise.yaml", List.of(), 1));
    assertEquals(List.of(
        "schema/a.yaml:5 [keys] E_B",
        "schema/a.yaml:5 [keys, a] E_A",
        "schema/a.yaml:5 [keys, a] E_B",
        "schema/a.yaml:5 [keys, a, 2] E_B",
        "schema/a.yaml:5 [keys, a, 10] E_B",
        "schema/a.yaml:5 [keys, z] E_B",
        "schema/a.yaml:40 [] E_B",
        "schema/b.yaml:1 [] E_B",
        "tierwise.yaml:1 [] E_B",
        "tierwise.yaml:9 [sources] E_B"), keys(findings));
  }

  @Test
  void FIND_26_filesAreComparedByCodePointNotByUtf16Unit() {
    Findings findings = new Findings();
    findings.add(finding("E_A", "\uD83D\uDE00.yaml", List.of(), 1));
    findings.add(finding("E_A", "\uFF5E.yaml", List.of(), 1));
    assertEquals("\uFF5E.yaml:1 [] E_A", keys(findings).get(0));
  }

  @Test
  void FIND_26_theOrderDoesNotDependOnTheOrderOfInsertion() {
    Finding a = finding("E_A", "a", List.of(), 1);
    Finding b = finding("E_A", "b", List.of(), 1);
    Finding c = finding("E_A", "c", List.of("p"), 1);
    Findings first = new Findings();
    first.addAll(List.of(a, b, c));
    Findings second = new Findings();
    second.addAll(List.of(c, a, b));
    assertEquals(first.sorted(), second.sorted());
  }

  @Test
  void FIND_5_aFindingWithSeverityErrorFailsTheValidationAndOthersDoNot() {
    Findings findings = new Findings();
    assertFalse(findings.hasErrors());
    findings.add(Finding.of(Code.LINT_VALUES_NO_OP, Severity.WARN, "a", List.of(), 1, "m"));
    findings.add(Finding.of(Code.LINT_SCHEMA_UNUSED, Severity.INFO, "a", List.of(), 2, "m"));
    assertFalse(findings.hasErrors());
    findings.add(Finding.of(Code.LINT_SCHEMA_UNUSED, Severity.ERROR, "a", List.of(), 3, "m"));
    assertTrue(findings.hasErrors());
  }

  @Test
  void LINT_11_onlyEngineFindingsGateExamplesAndLint() {
    Findings findings = new Findings();
    assertFalse(findings.hasEngineFindings());
    findings.add(Finding.of(Code.LINT_SCHEMA_UNUSED, Severity.ERROR, "a", List.of(), 3, "m"));
    findings.add(Finding.of(Code.naming("key"), Severity.ERROR, "a", List.of(), 4, "m"));
    assertTrue(findings.hasErrors());
    assertFalse(findings.hasEngineFindings(), "a lint finding with severity error is not an engine finding");
    findings.add(finding(Code.E_UNKNOWN_KEY, "a", List.of(), 5));
    assertTrue(findings.hasEngineFindings());
  }

  @Test
  void hasCodeFindsAnyOfTheCodes() {
    Findings findings = new Findings();
    findings.add(finding(Code.E_PARSE, "a", List.of(), 1));
    assertTrue(findings.hasCode(Code.E_MANIFEST, Code.E_PARSE));
    assertFalse(findings.hasCode(Code.E_MANIFEST));
    assertFalse(findings.hasCode());
  }

  @Test
  void findingsAtANodeUseTheFileThePathAndTheLineOfTheNode() {
    PMap root = CheckTestSupport.root("a:\n  b: 1\n");
    PNode b = CheckTestSupport.at(root, "a", "b");
    Findings findings = new Findings();
    findings.error(Code.E_TYPE_MISMATCH, b, "m");
    findings.lint(Code.LINT_VALUES_NO_OP, Severity.WARN, root, "m");
    Finding error = findings.sorted().get(1);
    assertEquals(Code.E_TYPE_MISMATCH, error.code());
    assertEquals(CheckTestSupport.FILE, error.file());
    assertEquals(List.of("a", "b"), error.path());
    assertEquals(2, error.line());
    assertEquals(Severity.ERROR, error.severity());
    assertEquals(Severity.WARN, findings.sorted().get(0).severity());
  }

  @Test
  void aSuggestionIsKept() {
    PMap root = CheckTestSupport.root("a: 1\n");
    Findings findings = new Findings();
    findings.error(Code.E_UNKNOWN_KEY, root, "m", Optional.of("checkout.allowed"));
    assertEquals(Optional.of("checkout.allowed"), findings.sorted().get(0).suggestion());
  }
}
