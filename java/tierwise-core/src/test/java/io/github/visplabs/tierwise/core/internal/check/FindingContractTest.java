package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.CompileOptions;
import io.github.visplabs.tierwise.core.CompileResult;
import io.github.visplabs.tierwise.core.Finding;
import io.github.visplabs.tierwise.core.Severity;
import io.github.visplabs.tierwise.core.Tierwise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The contract of the findings of a whole validation, through the compile entry point: determinism (FIND-4),
 * the fields of a finding (FIND-10, FIND-11), severities (FIND-15, FIND-16), one finding per finding key
 * (FIND-25), the order of the list (FIND-26) and suggestions (FIND-28).
 */
class FindingContractTest {

  @TempDir
  Path dir;

  /** A valid hierarchy and one key without a description, so that only lint can report something. */
  private static final String LINTABLE_SCHEMA = """
      decision_hierarchy:
        levels: [global, region]
        scopes:
          region:
            nodes: [north, south]
            on_unknown_node: error
      keys:
        shop.checkout.enabled:
          type: boolean
          combine: any
          settable_at: [global, region]
      """;

  private static void write(Path root, String path, String content) throws IOException {
    Path target = root.resolve(path);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content);
  }

  private CompileResult compile(Path root) {
    return Tierwise.compile(root, CompileOptions.defaults());
  }

  /** The finding key and severity of each finding, in the order of the result. */
  private static List<String> keysAndSeverities(CompileResult result) {
    return result.findings().stream().map(f -> f.key() + " " + f.severity()).toList();
  }

  // ---- FIND-4 -------------------------------------------------------------------------------------

  /** Writes the same project in two directories, with the files created and listed in opposite orders. */
  private List<CompileResult> twoOrders(Map<String, String> files, String manifestA, String manifestB)
      throws IOException {
    Path first = dir.resolve("first");
    Path second = dir.resolve("second");
    List<Map.Entry<String, String>> entries = new ArrayList<>(files.entrySet());
    for (Map.Entry<String, String> file : entries) {
      write(first, file.getKey(), file.getValue());
    }
    for (int i = entries.size() - 1; i >= 0; i--) {
      write(second, entries.get(i).getKey(), entries.get(i).getValue());
    }
    write(first, "tierwise.yaml", manifestA);
    write(second, "tierwise.yaml", manifestB);
    return List.of(compile(first), compile(second));
  }

  @Test
  void FIND_4_engineFindingsHaveTheSameKeysAndSeveritiesWhateverTheOrderOfFilesAndPatterns() throws IOException {
    Map<String, String> files = Map.of(
        "s/a.yaml", "keys:\n  a.b.c: {type: boolean, combine: any, settable_at: [global]}\nlabels: 1\n",
        "s/b.yaml", "keys:\n  a.b.c: {type: boolean, combine: any, settable_at: [global]}\n",
        "s/h.yaml", "decision_hierarchy:\n  levels: [global]\n",
        "v/x.yaml", "global:\n  a.b.d: true\n",
        "v/y.yaml", "- 1\n");
    List<CompileResult> results = twoOrders(files,
        "specVersion: \"0.1\"\nsources:\n  schema: ['s/a.yaml', 's/b.yaml', 's/h.yaml']\n"
            + "  values:\n    - file: v/x.yaml\n    - file: v/y.yaml\n",
        "specVersion: \"0.1\"\nsources:\n  values:\n    - file: v/y.yaml\n    - file: v/x.yaml\n"
            + "  schema: ['s/h.yaml', 's/*.yaml']\n");
    assertTrue(results.get(0).findings().size() >= 3, results.get(0).findings().toString());
    // Line numbers in the manifest differ between the two manifests, so only findings outside it are compared.
    List<String> first = keysAndSeverities(results.get(0)).stream().filter(k -> !k.contains("tierwise.yaml")).toList();
    List<String> second = keysAndSeverities(results.get(1)).stream().filter(k -> !k.contains("tierwise.yaml")).toList();
    assertFalse(first.isEmpty());
    assertEquals(first, second);
  }

  @Test
  void FIND_4_lintFindingsHaveTheSameKeysAndSeveritiesWhateverTheOrderOfFilesAndPatterns() throws IOException {
    Map<String, String> files = Map.of(
        "s/h.yaml", LINTABLE_SCHEMA.substring(0, LINTABLE_SCHEMA.indexOf("keys:")),
        "s/k1.yaml", "keys:\n  shop.one.flag: {type: boolean, combine: any, settable_at: [global]}\n",
        "s/k2.yaml", "keys:\n  shop.two.flag: {type: boolean, combine: any, settable_at: [global]}\n");
    String lint = "lint:\n  rules:\n    schema/description-required: warn\n    schema/unused: info\n";
    List<CompileResult> results = twoOrders(files,
        "specVersion: \"0.1\"\n" + lint + "sources:\n  schema: ['s/h.yaml', 's/k1.yaml', 's/k2.yaml']\n",
        "specVersion: \"0.1\"\n" + lint + "sources:\n  schema: ['s/k*.yaml', 's/h.yaml']\n");
    assertTrue(results.get(0).findings().stream().anyMatch(f -> f.severity() == Severity.WARN));
    assertTrue(results.get(0).findings().stream().anyMatch(f -> f.severity() == Severity.INFO));
    assertEquals(keysAndSeverities(results.get(0)), keysAndSeverities(results.get(1)));
  }

  @Test
  void FIND_4_aRepeatedRunGivesTheSameFindings() throws IOException {
    write(dir, "tierwise.yaml", "specVersion: \"0.1\"\nlint:\n  rules:\n    schema/description-required: warn\n"
        + "sources:\n  schema: ['s/*.yaml']\n");
    write(dir, "s/core.yaml", LINTABLE_SCHEMA);
    CompileResult once = compile(dir);
    assertFalse(once.findings().isEmpty());
    for (int run = 0; run < 3; run++) {
      assertEquals(keysAndSeverities(once), keysAndSeverities(compile(dir)));
    }
  }

  // ---- FIND-10, FIND-11 ---------------------------------------------------------------------------

  /** Requires the six fields of FIND-10, with the types that FIND-17 to FIND-21 give them. */
  private static void assertSixFields(Finding finding) {
    assertNotNull(finding.code());
    assertTrue(Code.all().contains(finding.code()) || finding.code().startsWith("naming/"), finding.code());
    assertNotNull(finding.severity());
    assertTrue(Set.of(Severity.ERROR, Severity.WARN, Severity.INFO).contains(finding.severity()), finding.toString());
    assertFalse(finding.file().isEmpty(), finding.toString());
    assertFalse(finding.file().contains("\\"), "FIND-17: '/' is the separator: " + finding.file());
    for (Object segment : finding.path()) {
      assertTrue(segment instanceof String || segment instanceof Integer, "FIND-20: " + segment);
    }
    assertTrue(finding.line() >= 1, "FIND-21: " + finding);
    assertNotNull(finding.message());
    assertFalse(finding.message().isBlank(), finding.toString());
    assertNotNull(finding.suggestion(), "FIND-11: an absent suggestion is an empty Optional, never null");
  }

  @Test
  void FIND_10_everyEngineFindingHasCodeSeverityFilePathLineAndMessage() throws IOException {
    write(dir, "tierwise.yaml", "specVersion: \"0.1\"\nowner: x\nsources:\n  schema: ['s/*.yaml']\n"
        + "  values:\n    - file: v/root.yaml\n      scope: global\n");
    write(dir, "s/core.yaml", LINTABLE_SCHEMA);
    write(dir, "s/bad.yaml", "- 1\n");
    write(dir, "v/root.yaml", "shop.checkout.enabld: true\nshop.checkout.enabled: 5\n");
    CompileResult result = compile(dir);
    assertTrue(result.findings().size() >= 4, result.findings().toString());
    Set<String> files = new java.util.TreeSet<>();
    for (Finding finding : result.findings()) {
      assertSixFields(finding);
      assertTrue(Code.engine().contains(finding.code()), finding.toString());
      files.add(finding.file());
    }
    assertEquals(Set.of("tierwise.yaml", "s/bad.yaml", "v/root.yaml"), files);
  }

  @Test
  void FIND_10_everyLintFindingHasCodeSeverityFilePathLineAndMessage() throws IOException {
    write(dir, "tierwise.yaml", "specVersion: \"0.1\"\nlint:\n  rules:\n    schema/description-required: warn\n"
        + "    schema/unused: info\nsources:\n  schema: ['s/*.yaml']\n");
    write(dir, "s/core.yaml", LINTABLE_SCHEMA);
    CompileResult result = compile(dir);
    assertFalse(result.findings().isEmpty());
    for (Finding finding : result.findings()) {
      assertSixFields(finding);
      assertTrue(Code.lint().contains(finding.code()), finding.toString());
      assertEquals("s/core.yaml", finding.file());
    }
  }

  @Test
  void FIND_11_aFindingMayOmitTheSuggestionAndAPresentSuggestionIsNotPartOfTheKey() throws IOException {
    write(dir, "tierwise.yaml", "specVersion: \"0.1\"\nsources:\n  schema: ['s/*.yaml']\n"
        + "  values:\n    - file: v/root.yaml\n      scope: global\n");
    write(dir, "s/core.yaml", LINTABLE_SCHEMA);
    write(dir, "v/root.yaml", "shop.checkout.enabld: true\nzzzzzzzzzzzzzzzzzzzz.y: true\n");
    CompileResult result = compile(dir);
    Finding close = result.findings().stream().filter(f -> f.path().equals(List.of("shop.checkout.enabld")))
        .findFirst().orElseThrow();
    Finding far = result.findings().stream().filter(f -> f.path().equals(List.of("zzzzzzzzzzzzzzzzzzzz.y")))
        .findFirst().orElseThrow();
    assertEquals(Code.E_UNKNOWN_KEY, close.code());
    assertEquals(Optional.of("shop.checkout.enabled"), close.suggestion());
    assertEquals(Code.E_UNKNOWN_KEY, far.code());
    assertEquals(Optional.empty(), far.suggestion());
    Finding without = new Finding(close.code(), close.severity(), close.file(), close.path(), close.line(),
        close.message(), Optional.empty());
    assertEquals(close.key(), without.key(), "FIND-24: the suggestion is not part of the finding key");
  }

  // ---- FIND-15, FIND-16 ---------------------------------------------------------------------------

  @Test
  void FIND_15_everyEngineFindingHasSeverityErrorWhateverTheLintSettingsAre() throws IOException {
    write(dir, "presets/quiet.yaml", "lint:\n  rules:\n    E_UNKNOWN_KEY: info\n    E_TYPE_MISMATCH: \"off\"\n");
    write(dir, "tierwise.yaml", "specVersion: \"0.1\"\nextends:\n  - presets/quiet.yaml\nlint:\n  rules:\n"
        + "    E_UNKNOWN_KEY: warn\n    E_PARSE: \"off\"\nsources:\n  schema: ['s/*.yaml']\n"
        + "  values:\n    - file: v/root.yaml\n      scope: global\n");
    write(dir, "s/core.yaml", LINTABLE_SCHEMA);
    write(dir, "s/broken.yaml", "a: [\n");
    write(dir, "v/root.yaml", "shop.checkout.enabld: true\n");
    CompileResult result = compile(dir);
    List<String> codes = result.findings().stream().map(Finding::code).toList();
    assertTrue(codes.contains(Code.E_PARSE), codes.toString());
    assertTrue(codes.contains(Code.E_PRESET), "LINT-8: a setting that names an engine code: " + codes);
    for (Finding finding : result.findings()) {
      assertTrue(Code.engine().contains(finding.code()), finding.toString());
      assertEquals(Severity.ERROR, finding.severity(), finding.toString());
    }
    assertTrue(result.bundle().isEmpty());
  }

  @Test
  void FIND_15_anEngineFindingAfterTheLoaderAlsoHasSeverityErrorWhenASettingNamesItsCode() throws IOException {
    write(dir, "tierwise.yaml", "specVersion: \"0.1\"\nlint:\n  rules:\n    E_UNKNOWN_KEY: info\n"
        + "sources:\n  schema: ['s/*.yaml']\n  values:\n    - file: v/root.yaml\n      scope: global\n");
    write(dir, "s/core.yaml", LINTABLE_SCHEMA);
    write(dir, "v/root.yaml", "shop.checkout.enabld: true\n");
    CompileResult result = compile(dir);
    // The setting is reported by the loader (LINT-8), and E_UNKNOWN_KEY comes from the values stage.
    Finding unknown = result.findings().stream().filter(f -> f.code().equals(Code.E_UNKNOWN_KEY)).findFirst()
        .orElseThrow(() -> new AssertionError(result.findings().toString()));
    assertEquals(Severity.ERROR, unknown.severity());
  }

  private CompileResult lintProject(String presetLint, String manifestLint) throws IOException {
    StringBuilder manifest = new StringBuilder("specVersion: \"0.1\"\n");
    if (presetLint != null) {
      write(dir, "presets/base.yaml", "lint:\n  rules:\n" + presetLint);
      manifest.append("extends:\n  - presets/base.yaml\n");
    }
    if (manifestLint != null) {
      manifest.append("lint:\n  rules:\n").append(manifestLint);
    }
    manifest.append("sources:\n  schema: ['s/*.yaml']\n");
    write(dir, "tierwise.yaml", manifest.toString());
    write(dir, "s/core.yaml", LINTABLE_SCHEMA);
    return compile(dir);
  }

  private static List<Finding> ofRule(CompileResult result, String rule) {
    return result.findings().stream().filter(f -> f.code().equals(rule)).toList();
  }

  @Test
  void FIND_16_eachLintFindingCarriesTheResolvedSeverityOfItsRule() throws IOException {
    for (Severity severity : List.of(Severity.ERROR, Severity.WARN, Severity.INFO)) {
      String word = severity.name().toLowerCase(java.util.Locale.ROOT);
      CompileResult result = lintProject(null, "    schema/description-required: " + word + "\n");
      List<Finding> found = ofRule(result, "schema/description-required");
      assertFalse(found.isEmpty(), word + ": " + result.findings());
      for (Finding finding : found) {
        assertEquals(severity, finding.severity(), finding.toString());
      }
      assertEquals(severity == Severity.ERROR, result.bundle().isEmpty(), "FIND-5, FIND-6: " + word);
    }
  }

  @Test
  void FIND_16_theSeverityIsTheOneResolvedAcrossPresetAndManifest() throws IOException {
    CompileResult result = lintProject("    schema/description-required: error\n",
        "    schema/description-required: info\n");
    List<Finding> found = ofRule(result, "schema/description-required");
    assertFalse(found.isEmpty(), result.findings().toString());
    found.forEach(finding -> assertEquals(Severity.INFO, finding.severity(), finding.toString()));
    assertTrue(result.bundle().isPresent());
  }

  @Test
  void FIND_16_aRuleThatResolvesToOffReportsNothing() throws IOException {
    assertEquals(List.of(), ofRule(lintProject(null, "    schema/description-required: \"off\"\n"),
        "schema/description-required"));
    assertEquals(List.of(), ofRule(lintProject("    schema/description-required: warn\n",
        "    schema/description-required: \"off\"\n"), "schema/description-required"));
    assertEquals(List.of(), ofRule(lintProject(null, null), "schema/description-required"), "LINT-5: unnamed is off");
  }

  // ---- FIND-25, FIND-26 ---------------------------------------------------------------------------

  /** A stage that runs an action under a name. */
  private static Stage stage(String name, Consumer<CheckContext> action) {
    return new Stage() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public void run(CheckContext context) {
        action.accept(context);
      }
    };
  }

  private CompileResult runStages(Stage... stages) throws IOException {
    write(dir, "tierwise.yaml", "specVersion: \"0.1\"\n");
    return new Compiler(List.of(stages)).run(dir, CompileOptions.defaults());
  }

  @Test
  void FIND_25_twoChecksWithTheSameFindingKeyReportOneFinding() throws IOException {
    Finding first = Finding.of(Code.E_UNKNOWN_KEY, Severity.ERROR, "v/a.yaml", List.of("k", 0), 3, "first check");
    Finding second = new Finding(Code.E_UNKNOWN_KEY, Severity.ERROR, "v/a.yaml", List.of("k", 0), 3, "second check",
        Optional.of("k2"));
    Finding other = Finding.of(Code.E_UNKNOWN_KEY, Severity.ERROR, "v/a.yaml", List.of("k", 1), 3, "another node");
    CompileResult result = runStages(stage("one", c -> c.findings().add(first)),
        stage("two", c -> {
          c.findings().add(second);
          c.findings().add(other);
        }));
    assertEquals(2, result.findings().size(), result.findings().toString());
    assertEquals(1, result.findings().stream().filter(f -> f.key().equals(first.key())).count());
  }

  @Test
  void FIND_25_twoFindingsThatDifferOnlyInCodeOrLineAreTwoFindings() throws IOException {
    CompileResult result = runStages(stage("one", c -> {
      c.findings().add(Finding.of(Code.E_UNKNOWN_KEY, Severity.ERROR, "f.yaml", List.of("k"), 1, "m"));
      c.findings().add(Finding.of(Code.E_TYPE_MISMATCH, Severity.ERROR, "f.yaml", List.of("k"), 1, "m"));
      c.findings().add(Finding.of(Code.E_UNKNOWN_KEY, Severity.ERROR, "f.yaml", List.of("k"), 2, "m"));
      c.findings().add(Finding.of(Code.E_UNKNOWN_KEY, Severity.ERROR, "g.yaml", List.of("k"), 1, "m"));
    }));
    assertEquals(4, result.findings().size());
  }

  /** The order of FIND-26, written independently of the implementation. */
  private static final Comparator<Finding> FIND_26 = (a, b) -> {
    int file = compareCodePoints(a.file(), b.file());
    if (file != 0) {
      return file;
    }
    int line = Integer.compare(a.line(), b.line());
    if (line != 0) {
      return line;
    }
    int path = comparePaths(a.path(), b.path());
    return path != 0 ? path : compareCodePoints(a.code(), b.code());
  };

  private static int compareCodePoints(String a, String b) {
    int[] x = a.codePoints().toArray();
    int[] y = b.codePoints().toArray();
    for (int i = 0; i < Math.min(x.length, y.length); i++) {
      if (x[i] != y[i]) {
        return Integer.compare(x[i], y[i]);
      }
    }
    return Integer.compare(x.length, y.length);
  }

  private static int comparePaths(List<Object> a, List<Object> b) {
    for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
      Object x = a.get(i);
      Object y = b.get(i);
      int c;
      if (x instanceof Integer i1 && y instanceof Integer i2) {
        c = Integer.compare(i1, i2);
      } else if (x instanceof String s1 && y instanceof String s2) {
        c = compareCodePoints(s1, s2);
      } else {
        // FIND-26 compares keys with keys and positions with positions; a mixed pair is ordered here only
        // so that this comparator is total.
        c = x instanceof Integer ? -1 : 1;
      }
      if (c != 0) {
        return c;
      }
    }
    return Integer.compare(a.size(), b.size());
  }

  @Test
  void FIND_26_theValidationListsItsFindingsByFileLinePathAndCode() throws IOException {
    String supplementary = new String(Character.toChars(0x1F600));
    List<Finding> expected = List.of(
        Finding.of(Code.E_PARSE, Severity.ERROR, "Z.yaml", List.of(), 9, "upper case sorts first"),
        Finding.of(Code.E_PARSE, Severity.ERROR, "a.yaml", List.of(), 1, "m"),
        Finding.of(Code.E_MANIFEST, Severity.ERROR, "a.yaml", List.of("k"), 1, "a prefix sorts first"),
        Finding.of(Code.E_MANIFEST, Severity.ERROR, "a.yaml", List.of("k", 2), 1, "positions are numbers"),
        Finding.of(Code.E_MANIFEST, Severity.ERROR, "a.yaml", List.of("k", 10), 1, "m"),
        Finding.of(Code.E_MANIFEST, Severity.ERROR, "a.yaml", List.of("k￿"), 1, "m"),
        Finding.of(Code.E_MANIFEST, Severity.ERROR, "a.yaml", List.of("k" + supplementary), 1, "code points"),
        Finding.of(Code.E_DUPLICATE_KEY, Severity.ERROR, "a.yaml", List.of("z"), 1, "m"),
        Finding.of(Code.E_MANIFEST, Severity.ERROR, "a.yaml", List.of("z"), 1, "then by code"),
        Finding.of(Code.E_MANIFEST, Severity.ERROR, "a.yaml", List.of("a"), 2, "line before path"),
        Finding.of(Code.E_PARSE, Severity.ERROR, "a￿.yaml", List.of(), 1, "m"),
        Finding.of(Code.E_PARSE, Severity.ERROR, "a" + supplementary + ".yaml", List.of(), 1, "files by code point"),
        Finding.of("values/no-op", Severity.WARN, "b.yaml", List.of(), 1, "m"));
    List<Finding> reversed = new ArrayList<>(expected);
    java.util.Collections.reverse(reversed);
    CompileResult result = runStages(stage("half", c -> reversed.subList(0, 6).forEach(c.findings()::add)),
        stage("rest", c -> reversed.subList(6, reversed.size()).forEach(c.findings()::add)));
    assertEquals(expected, result.findings());
    List<Finding> sorted = new ArrayList<>(result.findings());
    sorted.sort(FIND_26);
    assertEquals(sorted, result.findings());
  }

  @Test
  void FIND_26_theFindingsOfARealProjectAreInTheOrderOfFind26() throws IOException {
    write(dir, "tierwise.yaml", "specVersion: \"0.1\"\nzzz: 1\nsources:\n"
        + "  schema: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 's/*.yaml']\naaa: 1\n");
    write(dir, "s/b.yaml", "- x\n");
    write(dir, "s/a.yaml", "labels: 1\nother: 2\n");
    write(dir, "s/A.yaml", "{x: 1, y: 2}\n");
    CompileResult result = compile(dir);
    assertTrue(result.findings().size() >= 15, result.findings().toString());
    List<Finding> sorted = new ArrayList<>(result.findings());
    sorted.sort(FIND_26);
    assertEquals(sorted, result.findings());
    List<Object> positions = result.findings().stream()
        .filter(f -> f.file().equals("tierwise.yaml") && f.path().size() == 3)
        .map(f -> f.path().get(2)).toList();
    assertEquals(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10), positions);
  }

  // ---- FIND-28 ------------------------------------------------------------------------------------

  @Test
  void FIND_28_aSuggestionIsTheExactTextOfADeclaredNameOfTheMatchingCategory() throws IOException {
    write(dir, "tierwise.yaml", "specVersion: \"0.1\"\nsources:\n  schema: ['s/*.yaml']\n  values:\n"
        + "    - file: v/root.yaml\n      scope: global\n    - file: v/nodes.yaml\n");
    write(dir, "s/core.yaml", LINTABLE_SCHEMA);
    write(dir, "v/root.yaml", "shop.checkout.enabld: true\n");
    write(dir, "v/nodes.yaml", "regin:\n  north:\n    shop.checkout.enabled: true\nregion:\n  nort:\n"
        + "    shop.checkout.enabled: true\n");
    CompileResult result = compile(dir);
    Map<String, Set<String>> declared = Map.of(
        Code.E_UNKNOWN_KEY, Set.of("shop.checkout.enabled"),
        Code.E_UNKNOWN_SCOPE, Set.of("global", "region"),
        Code.E_UNKNOWN_NODE, Set.of("north", "south"));
    Map<String, String> suggested = new java.util.TreeMap<>();
    for (Finding finding : result.findings()) {
      finding.suggestion().ifPresent(text -> {
        assertTrue(declared.containsKey(finding.code()), "a suggestion on " + finding);
        assertTrue(declared.get(finding.code()).contains(text), "FIND-28: " + text + " is not declared: " + finding);
        suggested.put(finding.code(), text);
      });
    }
    assertEquals(Map.of(Code.E_UNKNOWN_KEY, "shop.checkout.enabled", Code.E_UNKNOWN_SCOPE, "region",
        Code.E_UNKNOWN_NODE, "north"), suggested);
  }

  @Test
  void FIND_28_aSuggestionForAnUnknownKindIsADeclaredKindName() throws IOException {
    write(dir, "presets/kinds.yaml", "kinds:\n  inherited_kind: { strategies: [any] }\n");
    write(dir, "tierwise.yaml", "specVersion: \"0.1\"\nextends:\n  - presets/kinds.yaml\n"
        + "sources:\n  schema: ['s/*.yaml']\n");
    write(dir, "s/core.yaml", """
        decision_hierarchy:
          levels: [global]
        kinds:
          toggle_kind: { strategies: [any, all] }
        keys:
          shop.one.flag: { type: boolean, combine: any, settable_at: [global], kind: togle_kind }
          shop.two.flag: { type: boolean, combine: any, settable_at: [global], kind: inherited_knd }
          shop.three.flag: { type: boolean, combine: any, settable_at: [global], kind: zzzzzzzzzzzzzz }
        """);
    CompileResult result = compile(dir);
    Map<String, Optional<String>> suggestions = new java.util.TreeMap<>();
    for (Finding finding : result.findings()) {
      assertEquals(Code.E_UNKNOWN_KIND, finding.code(), result.findings().toString());
      suggestions.put((String) finding.path().get(1), finding.suggestion());
    }
    assertEquals(Map.of("shop.one.flag", Optional.of("toggle_kind"), "shop.two.flag", Optional.of("inherited_kind"),
        "shop.three.flag", Optional.empty()), suggestions, "FIND-27, FIND-28: a declared kind name, exactly");
  }

  @Test
  void FIND_28_aSuggestionForAnUnknownLintRuleIsARuleIdentifier() throws IOException {
    write(dir, "presets/p.yaml", "lint:\n  rules:\n    schema/unusd: warn\n");
    write(dir, "tierwise.yaml", "specVersion: \"0.1\"\nextends:\n  - presets/p.yaml\nlint:\n  rules:\n"
        + "    values/no-ops: warn\nsources:\n  schema: ['s/*.yaml']\n");
    write(dir, "s/core.yaml", LINTABLE_SCHEMA);
    CompileResult result = compile(dir);
    Finding manifest = result.findings().stream().filter(f -> f.file().equals("tierwise.yaml")).findFirst()
        .orElseThrow(() -> new AssertionError(result.findings().toString()));
    assertEquals(Code.E_MANIFEST, manifest.code());
    assertEquals(Optional.of("values/no-op"), manifest.suggestion());
    Finding preset = result.findings().stream().filter(f -> f.file().equals("presets/p.yaml")).findFirst()
        .orElseThrow(() -> new AssertionError(result.findings().toString()));
    assertEquals(Code.E_PRESET, preset.code());
    assertEquals(Optional.of("schema/unused"), preset.suggestion());
    for (Finding finding : result.findings()) {
      finding.suggestion().ifPresent(text -> assertTrue(Code.lint().contains(text), text));
    }
    assertNotEquals(0, result.findings().size());
    assertInstanceOf(String.class, manifest.suggestion().orElseThrow());
  }
}
