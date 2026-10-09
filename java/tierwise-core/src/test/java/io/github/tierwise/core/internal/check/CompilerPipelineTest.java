package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.CompileOptions;
import io.github.tierwise.core.CompileResult;
import io.github.tierwise.core.Code;
import io.github.tierwise.core.Finding;
import io.github.tierwise.core.LoadedBundle;
import io.github.tierwise.core.Severity;
import io.github.tierwise.core.Tierwise;
import io.github.tierwise.core.internal.bundle.BundleWriter;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.model.Values;
import io.github.tierwise.core.internal.project.ProjectLoad;
import io.github.tierwise.core.internal.project.SourceSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The pipeline: the order of the stages, their gates, and when the bundle is written (FIND-3, FIND-7, FIND-8). */
class CompilerPipelineTest {

  private static final String MANIFEST = "specVersion: \"0.1\"\nschema:\n  keys: {}\n";

  @TempDir
  Path dir;

  /** A stage that records its name and runs an action. */
  private static Stage stage(String name, List<String> log, Consumer<CheckContext> action) {
    return new Stage() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public void run(CheckContext context) {
        log.add(name);
        action.accept(context);
      }
    };
  }

  private static Model emptyModel() {
    return new Model("1", "0.1", new Hierarchy(List.of("global"), List.of(), Map.of()), Map.of(), Map.of(), Map.of(),
        Values.EMPTY, Map.of());
  }

  private static void buildAllParts(CheckContext context) {
    context.parts().hierarchy(new Hierarchy(List.of("global"), List.of(), Map.of()));
    context.parts().entities(Map.of());
    context.parts().facts(Map.of());
    context.parts().keys(Map.of());
    context.parts().values(Values.EMPTY);
    context.parts().features(Map.of());
  }

  private void manifest(String content) throws IOException {
    Files.writeString(dir.resolve("tierwise.yaml"), content);
  }

  private CheckContext context() {
    return new CheckContext(new ProjectLoad(SourceSet.empty(), List.of()), CompileOptions.defaults());
  }

  @Test
  void theStandardStagesRunInTheFixedOrder() {
    assertEquals(List.of("manifest", "hierarchy", "entities-and-facts", "kinds", "keys", "values", "features", "examples", "lint"),
        Compiler.standardStages().stream().map(Stage::name).toList());
  }

  @Test
  void theCompilerRunsEveryStageInOrderEvenAfterAFault() throws IOException {
    manifest(MANIFEST);
    List<String> log = new ArrayList<>();
    Compiler compiler = new Compiler(List.of(
        stage("one", log, c -> c.findings().add(Finding.of(Code.E_PARSE, Severity.ERROR, "f", List.of(), 1, "m"))),
        stage("two", log, c -> { }), stage("three", log, c -> { })));
    CompileResult result = compiler.run(dir, CompileOptions.defaults());
    assertEquals(List.of("one", "two", "three"), log);
    assertEquals(1, result.findings().size());
    assertTrue(result.bundle().isEmpty());
  }

  @Test
  void aDisabledStageDoesNotRun() throws IOException {
    manifest(MANIFEST);
    List<String> log = new ArrayList<>();
    Stage disabled = new Stage() {
      @Override
      public String name() {
        return "disabled";
      }

      @Override
      public boolean enabled(CheckContext context) {
        return false;
      }

      @Override
      public void run(CheckContext context) {
        log.add("disabled");
      }
    };
    new Compiler(List.of(disabled, stage("next", log, c -> { }))).run(dir, CompileOptions.defaults());
    assertEquals(List.of("next"), log);
  }

  @Test
  void POL_47_examplesRunOnlyWhenNoOtherEngineFindingExists() {
    CheckContext context = context();
    assertTrue(new ExampleStage().enabled(context));
    context.findings().add(Finding.of(Code.LINT_VALUES_NO_OP, Severity.ERROR, "f", List.of(), 1, "m"));
    assertTrue(new ExampleStage().enabled(context), "a lint finding does not gate examples");
    context.findings().add(Finding.of(Code.E_UNKNOWN_KEY, Severity.ERROR, "f", List.of(), 2, "m"));
    assertFalse(new ExampleStage().enabled(context));
  }

  @Test
  void LINT_11_lintRunsOnlyWhenNoEngineFindingExistsIncludingAFailedExample() {
    CheckContext context = context();
    assertTrue(new LintStage().enabled(context));
    context.findings().add(Finding.of(Code.LINT_SCHEMA_UNUSED, Severity.WARN, "f", List.of(), 1, "m"));
    assertTrue(new LintStage().enabled(context));
    context.findings().add(Finding.of(Code.E_EXAMPLE_FAILED, Severity.ERROR, "f", List.of(), 2, "m"));
    assertFalse(new LintStage().enabled(context));
  }

  @Test
  void theOtherStagesAlwaysRun() {
    CheckContext context = context();
    context.findings().add(Finding.of(Code.E_PARSE, Severity.ERROR, "f", List.of(), 1, "m"));
    for (Stage stage : Compiler.standardStages()) {
      boolean gated = stage instanceof ExampleStage || stage instanceof LintStage;
      assertEquals(!gated, stage.enabled(context), stage.name());
    }
  }

  @Test
  void ManifestStageMovesTheLoaderFindingsIntoTheAccumulatorBeforeAnyOtherStage() throws IOException {
    manifest("specVersion: \"0.1\"\nowner: x\n");
    List<String> seen = new ArrayList<>();
    Compiler compiler = new Compiler(List.of(new ManifestStage(),
        stage("later", seen, c -> c.findings().sorted().forEach(f -> seen.add(f.code())))));
    CompileResult result = compiler.run(dir, CompileOptions.defaults());
    assertEquals(List.of("later", Code.E_MANIFEST), seen);
    assertEquals(1, result.findings().size());
  }

  @Test
  void FIND_8_aBundleIsWrittenWhenEveryPartIsBuiltAndNoFindingIsAnError() throws IOException {
    manifest(MANIFEST);
    CompileResult result = new Compiler(List.of(stage("parts", new ArrayList<>(), CompilerPipelineTest::buildAllParts)))
        .run(dir, CompileOptions.defaults());
    assertTrue(result.findings().isEmpty());
    assertArrayEquals(BundleWriter.write(emptyModel()).bytes(), result.bundle().orElseThrow());
    LoadedBundle loaded = Tierwise.loadBundle(result.bundle().orElseThrow());
    assertTrue(loaded.bundleVersion().startsWith("sha256:"));
  }

  @Test
  void FIND_6_aWarningOrInfoFindingDoesNotStopTheBundle() throws IOException {
    manifest(MANIFEST);
    CompileResult result = new Compiler(List.of(stage("parts", new ArrayList<>(), c -> {
      buildAllParts(c);
      c.findings().add(Finding.of(Code.LINT_VALUES_NO_OP, Severity.WARN, "f", List.of(), 1, "m"));
      c.findings().add(Finding.of(Code.LINT_SCHEMA_UNUSED, Severity.INFO, "f", List.of(), 2, "m"));
    }))).run(dir, CompileOptions.defaults());
    assertEquals(2, result.findings().size());
    assertTrue(result.bundle().isPresent());
  }

  @Test
  void FIND_7_noBundleIsWrittenWhenAFindingIsAnError() throws IOException {
    manifest(MANIFEST);
    CompileResult result = new Compiler(List.of(stage("parts", new ArrayList<>(), c -> {
      buildAllParts(c);
      c.findings().add(Finding.of(Code.LINT_VALUES_NO_OP, Severity.ERROR, "f", List.of(), 1, "m"));
    }))).run(dir, CompileOptions.defaults());
    assertTrue(result.bundle().isEmpty());
    assertTrue(result.hasErrors());
  }

  @Test
  void noBundleIsWrittenWhileAPartIsMissing() throws IOException {
    manifest(MANIFEST);
    CompileResult result = new Compiler(List.of(stage("parts", new ArrayList<>(), c -> {
      buildAllParts(c);
    }), stage("none", new ArrayList<>(), c -> { }))).run(dir, CompileOptions.defaults());
    assertTrue(result.bundle().isPresent());
    CompileResult missing = new Compiler(List.of(stage("some", new ArrayList<>(), c -> c.parts().entities(Map.of()))))
        .run(dir, CompileOptions.defaults());
    assertTrue(missing.bundle().isEmpty());
    assertTrue(missing.findings().isEmpty());
  }

  @Test
  void theStubStagesBuildNothingAndReportNothing() throws IOException {
    manifest(MANIFEST);
    CompileResult result = Compiler.compile(dir, CompileOptions.defaults());
    assertTrue(result.findings().isEmpty(), result.findings().toString());
    assertTrue(result.bundle().isEmpty(), "the stubs leave the parts incomplete, so no bundle is written");
  }

  @Test
  void FIND_26_theResultListsTheFindingsInTheOrderOfFind26() throws IOException {
    Files.writeString(dir.resolve("tierwise.yaml"), "specVersion: \"0.1\"\nzzz: 1\nsources:\n  schema: ['s/*.yaml']\naaa: 1\n");
    Files.createDirectories(dir.resolve("s"));
    Files.writeString(dir.resolve("s/b.yaml"), "- x\n");
    Files.writeString(dir.resolve("s/a.yaml"), "labels: 1\n");
    CompileResult result = Compiler.compile(dir, CompileOptions.defaults());
    assertEquals(List.of("s/a.yaml:1", "s/b.yaml:1", "tierwise.yaml:2", "tierwise.yaml:5"),
        result.findings().stream().map(f -> f.file() + ":" + f.line()).toList());
  }

  @Test
  void aCompileOfAMissingProjectReportsOneFinding() {
    CompileResult result = Compiler.compile(dir.resolve("nothing"), CompileOptions.defaults());
    assertEquals(1, result.findings().size());
    assertEquals(Code.E_MANIFEST, result.findings().get(0).code());
  }

  @Test
  void FIND_4_theSameProjectWithItsFilesAndPatternsListedInAnotherOrderGivesTheSameFindingKeys() throws IOException {
    Files.createDirectories(dir.resolve("s"));
    Files.createDirectories(dir.resolve("v"));
    Files.writeString(dir.resolve("s/a.yaml"), "keys:\n  a.b.c: {}\nlabels: 1\n");
    Files.writeString(dir.resolve("s/b.yaml"), "keys:\n  a.b.c: {}\n");
    Files.writeString(dir.resolve("v/x.yaml"), "region: [a]\n");
    Files.writeString(dir.resolve("v/y.yaml"), "- 1\n");
    Files.writeString(dir.resolve("tierwise.yaml"), "specVersion: \"0.1\"\nsources:\n  schema: ['s/a.yaml', 's/b.yaml']\n"
        + "  values:\n    - file: v/x.yaml\n    - file: v/y.yaml\n");
    CompileResult first = Compiler.compile(dir, CompileOptions.defaults());
    Files.writeString(dir.resolve("tierwise.yaml"), "specVersion: \"0.1\"\nsources:\n  values:\n    - file: v/y.yaml\n"
        + "    - file: v/x.yaml\n  schema: ['s/*.yaml']\n");
    CompileResult second = Compiler.compile(dir, CompileOptions.defaults());
    assertFalse(first.findings().isEmpty());
    java.util.function.Function<CompileResult, List<String>> codesAndFiles =
        result -> result.findings().stream().map(f -> f.code() + " " + f.file() + " " + f.path()).toList();
    assertEquals(codesAndFiles.apply(first), codesAndFiles.apply(second));
  }

  @Test
  void FIND_10_everyFindingHasCodeSeverityFilePathLineAndMessage() throws IOException {
    manifest("specVersion: 5\nowner: x\n");
    for (Finding finding : Compiler.compile(dir, CompileOptions.defaults()).findings()) {
      assertTrue(finding.code().startsWith("E_"));
      assertEquals(Severity.ERROR, finding.severity());
      assertEquals("tierwise.yaml", finding.file());
      assertTrue(finding.path().stream().allMatch(segment -> segment instanceof String || segment instanceof Integer));
      assertTrue(finding.line() >= 1);
      assertFalse(finding.message().isBlank());
    }
  }

  @Test
  void FIND_11_aFindingMayOmitTheSuggestion() throws IOException {
    manifest("specVersion: 5\n");
    assertTrue(Compiler.compile(dir, CompileOptions.defaults()).findings().get(0).suggestion().isEmpty());
  }

  @Test
  void FIND_15_everyEngineFindingOfTheLoaderHasSeverityErrorWhateverTheLintSettingsAre() throws IOException {
    manifest("specVersion: 5\nlint:\n  rules:\n    E_MANIFEST: \"off\"\n    values/no-op: \"off\"\n");
    CompileResult result = Compiler.compile(dir, CompileOptions.defaults());
    assertTrue(result.findings().size() >= 2);
    for (Finding finding : result.findings()) {
      assertEquals(Severity.ERROR, finding.severity());
    }
  }
}
