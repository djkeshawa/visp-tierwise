package io.github.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExplainCommandTest {

  private static List<String> engineCodes() throws IllegalAccessException {
    List<String> codes = new ArrayList<>();
    for (Field field : Code.class.getFields()) {
      if (Modifier.isStatic(field.getModifiers()) && field.getName().startsWith("E_")) {
        codes.add((String) field.get(null));
      }
    }
    return codes;
  }

  @Test
  void everyCodeOfTheCoreHasAnExplanation() throws IllegalAccessException {
    List<String> codes = engineCodes();
    assertTrue(codes.size() > 40, "found " + codes.size());
    for (String code : codes) {
      CliRun.Result result = CliRun.run("explain", code);
      assertEquals(0, result.code(), code + ": " + result.err());
      assertTrue(result.out().startsWith(code + "\n**Class:**"), code + ": " + result.out());
    }
  }

  @Test
  void everyLintRuleHasAnExplanation() {
    for (String rule : List.of("values/no-op", "rules/dead-rule", "examples/required-per-binding",
        "examples/mixed-collection", "schema/unused", "schema/description-required", "schema/owner-required",
        "presets/override", "naming/*")) {
      CliRun.Result result = CliRun.run("explain", rule);
      assertEquals(0, result.code(), rule + ": " + result.err());
      assertTrue(result.out().startsWith(rule + "\n**Class:** lint."), rule + ": " + result.out());
    }
  }

  @Test
  void theToolingCodeIsExplained() {
    CliRun.Result result = CliRun.run("explain", "E_PROVENANCE_INVALID");
    assertEquals(0, result.code());
    assertTrue(result.out().contains("provenance sidecar"), result.out());
  }

  @Test
  void anExplanationHasTheMeaningTheCauseAndTheFix() {
    String text = CliRun.run("explain", "E_UNKNOWN_KEY").out();
    assertTrue(text.contains("**Meaning:**"), text);
    assertTrue(text.contains("**Raised when:**"), text);
    assertTrue(text.contains("**Fix:**"), text);
  }

  @Test
  void anExplanationStopsAtTheNextEntry() {
    String text = CliRun.run("explain", "E_UNKNOWN_KEY").out();
    assertEquals(-1, text.indexOf("### "), text);
    assertEquals(-1, text.indexOf("E_UNKNOWN_KIND\n"), text);
  }

  @Test
  void aCodeMatchesWithoutRegardToCase() {
    assertEquals(CliRun.run("explain", "E_UNKNOWN_KEY").out(), CliRun.run("explain", "e_unknown_key").out());
  }

  @Test
  void aNamingRuleIsExplainedByTheNamingFamily() {
    CliRun.Result result = CliRun.run("explain", "naming/enum_value");
    assertEquals(0, result.code());
    assertTrue(result.out().startsWith("naming/*\n**Class:** lint."), result.out());
    assertTrue(result.out().contains("`naming/enum_value` checks the naming pattern called `enum_value`"), result.out());
  }

  @Test
  void anUnknownNameExitsWithOneAndSuggestsTheClosestOnes() {
    CliRun.Result result = CliRun.run("explain", "E_UNKNOWN_KY");
    assertEquals(1, result.code());
    assertEquals("", result.out());
    assertTrue(result.err().contains("Did you mean E_UNKNOWN_KEY"), result.err());
  }

  @Test
  void aNameWithNothingCloseGivesNoSuggestion() {
    CliRun.Result result = CliRun.run("explain", "zzzzzzzzzzzzzzzz");
    assertEquals(1, result.code());
    assertTrue(!result.err().contains("Did you mean"), result.err());
  }

  @Test
  void withoutAnArgumentItListsEveryCodeAndRule() throws IllegalAccessException {
    CliRun.Result result = CliRun.run("explain");
    assertEquals(0, result.code());
    List<String> listed = result.out().lines().toList();
    for (String code : engineCodes()) {
      assertTrue(listed.contains(code), code);
    }
    assertTrue(listed.contains("values/no-op"));
    assertTrue(listed.contains("naming/*"));
    assertTrue(!listed.contains("## Index"));
  }
}
