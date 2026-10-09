package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.CompileOptions;
import io.github.tierwise.core.CompileResult;
import io.github.tierwise.core.Finding;
import io.github.tierwise.core.Tierwise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * MANIFEST-33: a kind that a later layer replaces keeps none of the fields of the earlier definition. The earlier
 * definition sets every member of KIND-3, and the replacement sets none or a different one, so a merge of any
 * field would show in the effective definition or in the findings of a key that uses the kind.
 */
class KindReplacementTest {

  /** A preset kind with every member of KIND-3, and the key pattern its naming member needs. */
  private static final String FULL_PRESET_KIND = """
      naming:
        key_pattern: { category: key, pattern: '^[a-z.]+$' }
      kinds:
        shared:
          strategies: [override]
          narrowing_only: false
          require_default: true
          on_source_failure: skip
          naming: key_pattern
      """;

  @TempDir
  Path dir;

  private KindDef effective(CheckContext context) {
    return context.find(KindTable.class).orElseThrow().effective().get("shared");
  }

  @Test
  void MANIFEST_33_anEmptyReplacementKeepsNoFieldOfTheReplacedKind() {
    CheckContext context = new KeyValueHarness(dir).preset("presets/base.yaml", FULL_PRESET_KIND)
        .schema("kinds:\n  shared: {}\n").run();
    CheckTestSupport.assertFindings(context.findings());
    KindDef shared = effective(context);
    assertFalse(shared.preset());
    assertEquals(Optional.empty(), shared.strategies());
    assertFalse(shared.narrowingOnly());
    assertFalse(shared.requireDefault());
    assertEquals(Optional.empty(), shared.onSourceFailure());
    assertEquals(Optional.empty(), shared.naming());
  }

  @Test
  void MANIFEST_33_aPartialReplacementKeepsOnlyItsOwnFields() {
    CheckContext context = new KeyValueHarness(dir).preset("presets/base.yaml", FULL_PRESET_KIND)
        .schema("kinds:\n  shared: { on_source_failure: deny }\n").run();
    CheckTestSupport.assertFindings(context.findings());
    KindDef shared = effective(context);
    assertEquals(Optional.of("deny"), shared.onSourceFailure());
    assertEquals(Optional.empty(), shared.strategies());
    assertFalse(shared.requireDefault());
    assertEquals(Optional.empty(), shared.naming());
  }

  @Test
  void MANIFEST_33_aKeyThatUsesTheReplacedKindIsCheckedOnlyAgainstTheReplacement() {
    // Under the preset's definition this key would break KIND-4 (max is not listed) and KIND-8 (no default), and it
    // would take on_source_failure from the kind (KIND-9). Under the empty replacement it breaks none of these, and
    // it has no source-failure behaviour for its service-sourced level, which SVC-1 reports.
    CheckContext context = new KeyValueHarness(dir).preset("presets/base.yaml", FULL_PRESET_KIND)
        .schema("""
            kinds:
              shared: {}
            keys:
              limit.per.account:
                type: integer
                combine: max
                kind: shared
                settable_at: [global, account]
            """).run();
    List<String> codes = context.findings().sorted().stream().map(Finding::code).toList();
    assertEquals(List.of(Code.E_MISSING_SOURCE_FAILURE), codes);
  }

  @Test
  void MANIFEST_33_theSameKeyUnderTheUnreplacedKindShowsTheFieldsOfThePreset() {
    CheckContext context = new KeyValueHarness(dir).preset("presets/base.yaml", FULL_PRESET_KIND)
        .schema("""
            keys:
              limit.per.account:
                type: integer
                combine: max
                kind: shared
                settable_at: [global, account]
            """).run();
    List<String> codes = context.findings().sorted().stream().map(Finding::code).toList();
    assertTrue(codes.contains(Code.E_KIND_VIOLATION), codes.toString());
    assertFalse(codes.contains(Code.E_MISSING_SOURCE_FAILURE), "KIND-9: the kind supplies skip: " + codes);
  }

  @Test
  void MANIFEST_33_aKindReplacedByASecondPresetKeepsNoFieldOfTheFirst() throws IOException {
    write("presets/a.yaml", "kinds:\n  shared: { strategies: [override], require_default: true }\n");
    write("presets/b.yaml", "kinds:\n  shared: { on_source_failure: skip }\n");
    write("tierwise.yaml", """
        specVersion: "0.1"
        extends:
          - presets/a.yaml
          - presets/b.yaml
        schema:
          decision_hierarchy:
            levels: [global]
          keys:
            flag.any.on:
              type: boolean
              combine: any
              kind: shared
              settable_at: [global]
        """);
    CompileResult result = Tierwise.compile(dir, CompileOptions.defaults());
    // Under a's fields the key would break KIND-4 (any is not listed) and KIND-8 (no default). b keeps neither.
    assertEquals(List.of(), result.findings());
    assertTrue(result.bundle().isPresent());
  }

  private void write(String path, String content) throws IOException {
    Path target = dir.resolve(path);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content);
  }
}
