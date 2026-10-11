package io.github.visplabs.tierwise.core.internal.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.visplabs.tierwise.core.internal.model.KeyDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.model.Values;
import io.github.visplabs.tierwise.core.internal.project.SourceSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@code values/no-op} (LINT-23 to LINT-25). */
class ValuesNoOpRuleTest {

  @TempDir
  Path dir;

  @Test
  void aValueEqualToTheIdentityIsFlaggedAtItsEntry() {
    SourceSet sources = LintFixture.sources(dir, Map.of(
        "values/root.yaml", "search.fallback_on: false\nsearch.ranked: false\n"));
    KeyDef any = new KeyDef(TypeDecl.simple("boolean"), "any", Optional.empty(), List.of("global"), Optional.empty(),
        Optional.empty(), Optional.empty());
    KeyDef all = new KeyDef(TypeDecl.simple("boolean"), "all", Optional.empty(), List.of("global"), Optional.empty(),
        Optional.empty(), Optional.empty());
    Model model = LintFixture.model(List.of("global"), Map.of(), Map.of(),
        Map.of("search.fallback_on", any, "search.ranked", all),
        new Values(Map.of("global", Map.of("", Map.of("search.fallback_on", false, "search.ranked", false)))),
        Map.of());
    List<LintHit> hits = new ValuesNoOpRule().check(LintContext.of(sources, model, List.of()));
    assertEquals(List.of("1 [search.fallback_on]"), hits.stream().map(LintFixture::where).toList());
  }
}
