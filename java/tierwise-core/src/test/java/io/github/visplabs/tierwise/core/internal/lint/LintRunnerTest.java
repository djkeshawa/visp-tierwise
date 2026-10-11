package io.github.visplabs.tierwise.core.internal.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.visplabs.tierwise.core.Severity;
import io.github.visplabs.tierwise.core.internal.project.SourceSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests the severity resolution and the gating of the runner (LINT-4 to LINT-7). */
class LintRunnerTest {

  @TempDir
  Path dir;

  @Test
  void aRuleWithNoSettingIsOffAndAManifestSettingGivesItsSeverity() {
    String schema = """
        keys:
          search.unlisted:
            type: boolean
            combine: all
            settable_at: [global]
        """;
    SourceSet off = LintFixture.sources(dir.resolve("off"), Map.of("schema/core.yaml", schema));
    assertEquals(List.of(), LintRunner.run(LintContext.of(off, LintFixture.emptyModel(), List.of())));

    String withSetting = "specVersion: \"0.1\"\nsources:\n  schema:\n    - \"schema/**/*.yaml\"\n"
        + "  values:\n    - file: \"values/root.yaml\"\n      scope: global\n"
        + "lint:\n  rules:\n    schema/description-required: info\n";
    SourceSet on = LintFixture.sources(dir.resolve("on"), Map.of("tierwise.yaml", withSetting,
        "schema/core.yaml", schema));
    List<LintFinding> findings = LintRunner.run(LintContext.of(on, LintFixture.emptyModel(), List.of()));
    assertEquals(List.of(Severity.INFO), findings.stream().map(LintFinding::severity).toList());
    assertEquals(List.of("schema/description-required 2 [keys, search.unlisted]"),
        findings.stream().map(LintFixture::describe).toList());
  }
}
