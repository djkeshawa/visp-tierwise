package io.github.visplabs.tierwise.core.internal.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.visplabs.tierwise.core.internal.project.SourceSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@code presets/override} (LINT-49, LINT-50). */
class PresetsOverrideRuleTest {

  @TempDir
  Path dir;

  private static final String MANIFEST = """
      specVersion: "0.1"
      extends:
        - tierwise:recommended
      sources:
        schema:
          - "schema/**/*.yaml"
        values:
          - file: "values/root.yaml"
            scope: global
      """;

  @Test
  void aNamingPatternThatReplacesThePresetsIsFlaggedAtTheProjectsEntry() {
    SourceSet sources = LintFixture.sources(dir, Map.of("tierwise.yaml", MANIFEST, "schema/core.yaml", """
        naming:
          key:
            category: key
            pattern: '^[a-z.]+$'
        """));
    List<String> found = new PresetsOverrideRule().check(LintContext.of(sources, LintFixture.emptyModel(), List.of()))
        .stream().map(LintFixture::where).toList();
    assertEquals(List.of("2 [naming, key]"), found);
  }
}
