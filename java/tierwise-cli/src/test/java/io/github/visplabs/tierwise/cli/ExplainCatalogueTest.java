package io.github.visplabs.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * FIND-29: every engine error code of requirements §6.1 and every lint rule of LINT-1 other than
 * {@code naming/<name>} has an explanation in {@code spec/error-codes.md} that states the rationale and the fix, and
 * {@code tierwise explain} prints that explanation. The catalogue is read from the repository, not from the jar, so a
 * stale copy in the build also fails this test.
 */
class ExplainCatalogueTest {

  private static Path repository() {
    return Path.of(System.getProperty("tierwise.spec.dir")).resolve("..").normalize();
  }

  /** The entries of the catalogue: each third-level heading between "## Engine errors" and "## Index", with its text. */
  private static Map<String, String> catalogue() throws IOException {
    List<String> lines = Files.readAllLines(repository().resolve("spec/error-codes.md"), StandardCharsets.UTF_8);
    Map<String, String> entries = new LinkedHashMap<>();
    boolean inEntries = false;
    String name = null;
    StringBuilder body = new StringBuilder();
    for (String line : lines) {
      boolean section = line.startsWith("## ");
      boolean entry = line.startsWith("### ");
      if ((section || entry) && name != null) {
        entries.put(name, body.toString().strip());
        name = null;
      }
      if (section) {
        inEntries = line.equals("## Engine errors") || (inEntries && !line.equals("## Index"));
      } else if (entry && inEntries) {
        name = line.substring(4).trim();
        body = new StringBuilder();
      } else if (name != null) {
        body.append(line).append('\n');
      }
    }
    return entries;
  }

  /** The codes in backticks in the table of requirements §6.1. */
  private static List<String> requirementsEngineCodes() throws IOException {
    String text = Files.readString(repository().resolve("docs/requirements.md"), StandardCharsets.UTF_8);
    int start = text.indexOf("### 6.1 Engine errors");
    int end = text.indexOf("### 6.2", start);
    assertTrue(start >= 0 && end > start, "requirements §6.1 not found");
    List<String> codes = new ArrayList<>();
    Matcher matcher = Pattern.compile("`(E_[A-Z_]+)`").matcher(text.substring(start, end));
    while (matcher.find()) {
      codes.add(matcher.group(1));
    }
    return codes;
  }

  /** The fixed lint rules listed by LINT-1 in section 13. */
  private static List<String> lintRules() throws IOException {
    String text = Files.readString(repository().resolve("spec/sections/13-findings-and-lint.md"), StandardCharsets.UTF_8);
    int start = text.indexOf("**[LINT-1]**");
    int end = text.indexOf("**[LINT-2]**", start);
    List<String> rules = new ArrayList<>();
    Matcher matcher = Pattern.compile("- `([a-z]+/[a-z-]+)`").matcher(text.substring(start, end));
    while (matcher.find()) {
      rules.add(matcher.group(1));
    }
    return rules;
  }

  @Test
  void FIND_29_everyEngineCodeAndLintRuleHasAnEntryWithTheRationaleAndTheFix() throws IOException {
    Map<String, String> entries = catalogue();
    List<String> codes = requirementsEngineCodes();
    assertEquals(51, codes.size(), "the table of requirements §6.1: " + codes);
    List<String> rules = lintRules();
    assertEquals(8, rules.size(), "LINT-1 without naming/<name>: " + rules);
    List<String> required = new ArrayList<>(codes);
    required.addAll(rules);
    for (String name : required) {
      String body = entries.get(name);
      assertTrue(body != null, "FIND-29: no catalogue entry for " + name);
      assertTrue(body.contains("**Meaning:**"), name + " has no rationale");
      assertTrue(body.contains("**Fix:**"), name + " has no fix");
    }
  }

  @Test
  void FIND_29_explainPrintsTheCatalogueEntryOfEveryCodeAndRule() throws IOException {
    Map<String, String> entries = catalogue();
    assertTrue(entries.size() > 60, "entries: " + entries.size());
    for (Map.Entry<String, String> entry : entries.entrySet()) {
      CliRun.Result result = CliRun.run("explain", entry.getKey());
      assertEquals(0, result.code(), entry.getKey() + ": " + result.err());
      assertEquals(entry.getKey() + "\n" + entry.getValue() + "\n", result.out(), entry.getKey());
    }
  }
}
