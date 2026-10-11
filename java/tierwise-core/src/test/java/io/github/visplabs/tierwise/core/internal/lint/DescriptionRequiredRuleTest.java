package io.github.visplabs.tierwise.core.internal.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.internal.project.SourceSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@code schema/description-required} (LINT-44, LINT-45). */
class DescriptionRequiredRuleTest {

  @TempDir
  Path dir;

  private static final String SCHEMA = """
      keys:
        search.blank:
          type: boolean
          combine: all
          settable_at: [global]
          description: "  \\t "
        search.described:
          type: boolean
          combine: all
          settable_at: [global]
          description: Present.
      facts:
        item.bare:
          type: boolean
      """;

  @Test
  void aMissingOrBlankDescriptionIsFlaggedAtTheDeclaration() {
    SourceSet sources = LintFixture.sources(dir, Map.of("schema/core.yaml", SCHEMA));
    List<String> found = new DescriptionRequiredRule().check(LintContext.of(sources, LintFixture.emptyModel(),
        List.of())).stream().map(LintFixture::where).toList();
    assertEquals(List.of("2 [keys, search.blank]", "13 [facts, item.bare]"), found);
    assertTrue(DescriptionRequiredRule.blank(" \t\r\n"));
    assertFalse(DescriptionRequiredRule.blank(" x "));
  }
}
