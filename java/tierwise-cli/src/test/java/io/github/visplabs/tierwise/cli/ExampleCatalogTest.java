package io.github.visplabs.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExampleCatalogTest {

  @Test
  void theExamplesOfTheCommerceSampleAreListedFileByFile() {
    List<ExampleCatalog.Entry> entries = ExampleCatalog.list(CliRun.sample("commerce"));
    assertEquals(12, entries.size());
    ExampleCatalog.Entry first = entries.get(0);
    assertEquals("rules/checkout.invoice_release.yaml", first.file());
    assertEquals(List.of("examples", 0), first.path());
    assertEquals(41, first.line());
    assertEquals("A pending order holds the invoice under the root binding", first.name());
    assertEquals("rules/checkout.order_submission.yaml", entries.get(6).file());
  }

  @Test
  void aProjectWithoutExamplesListsNone() {
    assertEquals(List.of(), ExampleCatalog.list(CliRun.sample("saas-entitlements")));
  }

  @Test
  void anEntryContainsTheFindingPathsBelowIt() {
    ExampleCatalog.Entry entry = new ExampleCatalog.Entry("f.yaml", List.of("examples", 2), "n", 1);
    assertTrue(entry.contains(List.of("examples", 2, "expect")));
    assertTrue(entry.contains(List.of("examples", 2)));
    assertFalse(entry.contains(List.of("examples", 3, "expect")));
    assertFalse(entry.contains(List.of("examples")));
  }

  @Test
  void globsFollowSourcesRulesSemantics() {
    assertTrue(ExampleCatalog.Glob.parse("rules/**/*.yaml").matches("rules/a.yaml"));
    assertTrue(ExampleCatalog.Glob.parse("rules/**/*.yaml").matches("rules/x/y/a.yaml"));
    assertFalse(ExampleCatalog.Glob.parse("rules/**/*.yaml").matches("other/a.yaml"));
    assertTrue(ExampleCatalog.Glob.parse("rules/*.yaml").matches("rules/a.yaml"));
    assertFalse(ExampleCatalog.Glob.parse("rules/*.yaml").matches("rules/x/a.yaml"));
    assertTrue(ExampleCatalog.Glob.parse("./rules/pos.*.yaml").matches("rules/pos.basket.yaml"));
    assertFalse(ExampleCatalog.Glob.parse("rules/A.yaml").matches("rules/a.yaml"));
    assertTrue(ExampleCatalog.Glob.parse("**/*.yaml").matches("a.yaml"));
    // ** stands for directory segments only, so it never swallows the file name (as in the core).
    assertFalse(ExampleCatalog.Glob.parse("rules/**").matches("rules/a.yaml"));
  }
}
