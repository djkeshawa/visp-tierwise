package io.github.visplabs.tierwise.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.LoadedModel;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.model.Values;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The skeleton entry points that later work items implement, and the small value types around them. */
class TierwiseTest {

  @Test
  void compileReportsAMissingManifestAndWritesNoBundle(@TempDir Path directory) {
    CompileResult result = Tierwise.compile(directory);
    assertEquals(1, result.findings().size());
    Finding finding = result.findings().get(0);
    assertEquals(Code.E_MANIFEST, finding.code());
    assertEquals("tierwise.yaml", finding.file());
    assertEquals(List.of(), finding.path());
    assertEquals(1, finding.line());
    assertTrue(result.bundle().isEmpty());
  }

  @Test
  void compileUsesTheDefaultOptionsWhenNoneAreGiven(@TempDir Path directory) {
    assertEquals(Tierwise.compile(directory, CompileOptions.defaults()), Tierwise.compile(directory));
  }

  @Test
  void loadBundleRejectsBytesThatAreNotABundle() {
    TierwiseException e = assertThrows(TierwiseException.class, () -> Tierwise.loadBundle(new byte[0]));
    assertEquals(Code.E_BUNDLE_INTEGRITY, e.code());
  }

  @Test
  void evaluatorIsCreatedForALoadedBundle() {
    LoadedBundle bundle = new LoadedModel("sha256:" + "0".repeat(64), new Model("1", "0.1",
        new Hierarchy(List.of("level_root"), List.of(), Map.of()), Map.of(), Map.of(), Map.of(), Values.EMPTY,
        Map.of()));
    Evaluator evaluator = Tierwise.evaluator(bundle, EvaluatorOptions.none());
    assertEquals("sha256:" + "0".repeat(64), bundle.bundleVersion());
    assertEquals(Code.E_UNKNOWN_KEY, assertThrows(TierwiseException.class,
        () -> evaluator.resolve(Context.empty(), "no.such.key")).code());
    assertEquals(Code.E_UNKNOWN_FEATURE, assertThrows(TierwiseException.class,
        () -> evaluator.decide(Context.empty(), "no.such.feature", FactSet.empty())).code());
    assertTrue(Tierwise.evaluator(bundle, null) != null, "omitted options count as empty (EVAL-28)");
  }

  @Test
  void exceptionsCarryTheirCode() {
    TierwiseException e = new TierwiseException(Code.E_UNKNOWN_KEY, "no such key");
    assertEquals("E_UNKNOWN_KEY", e.code());
    assertTrue(e.getMessage().startsWith("E_UNKNOWN_KEY"));
  }

  @Test
  void optionsKeepRegistrationsAsMade() {
    LevelValueProvider provider = nodeId -> Map.of();
    EvaluatorOptions options = EvaluatorOptions.builder()
        .provider("level_a", provider, Duration.ofSeconds(1))
        .provider("level_a", provider, null)
        .listener(event -> { })
        .build();
    assertEquals(2, options.providers().size());
    assertEquals(null, options.providers().get(1).timeout());
    assertTrue(options.listener().isPresent());
    assertTrue(EvaluatorOptions.none().providers().isEmpty());
    assertTrue(EvaluatorOptions.none().listener().isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> options.providers().clear());
  }

  @Test
  void compileResultsHoldABundleOnlyWithoutErrors() {
    Finding warning = Finding.of(Code.LINT_VALUES_NO_OP, Severity.WARN, "f", List.of(), 1, "m");
    Finding error = Finding.of(Code.E_PARSE, Severity.ERROR, "f", List.of(), 1, "m");
    assertTrue(new CompileResult(List.of(warning), new byte[] {1}).bundle().isPresent());
    assertTrue(new CompileResult(List.of(error), null).bundle().isEmpty());
    assertTrue(new CompileResult(List.of(error), null).hasErrors());
    assertThrows(IllegalArgumentException.class, () -> new CompileResult(List.of(error), new byte[] {1}));
  }

  @Test
  void compileOptionsDefaultToNoFetcher() {
    assertTrue(CompileOptions.defaults().presetFetcher().isEmpty());
    PresetFetcher fetcher = url -> new byte[0];
    assertEquals(fetcher, CompileOptions.builder().presetFetcher(fetcher).build().presetFetcher().orElseThrow());
  }

  @Test
  void eventsDescribeTheirSubject() {
    EvaluationEvent loaded = EvaluationEvent.loaded("v");
    assertEquals(EvaluationEvent.Type.BUNDLE_LOADED, loaded.type());
    assertTrue(loaded.level().isEmpty());
    EvaluationEvent failure = EvaluationEvent.forKey(EvaluationEvent.Type.SOURCE_UNAVAILABLE, "v", "level_a",
        "node_x", "key_x");
    assertEquals("level_a", failure.level().orElseThrow());
  }
}
