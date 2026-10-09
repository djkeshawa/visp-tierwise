package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A key declared twice (KEY-1, SRC-40, SRC-41) and two values for one key at one scope and node (KEY-12, SRC-42),
 * from every pair of sources the clauses name. Each test checks the code and the files involved; the location is
 * the later occurrence in document order (FIND-32): the manifest's inline content first, then files in code-point
 * order of their paths.
 */
class KeyDuplicateTest {

  private static final String HIERARCHY = """
      decision_hierarchy:
        levels: [global, region]
        scopes:
          region: { nodes: [north, south], on_unknown_node: error }
      """;

  private static final String KEY = """
      keys:
        shop.checkout.limit:
          type: integer
          combine: override
          default: 1
          settable_at: [global, region]
      """;

  @TempDir
  Path dir;

  private void write(String path, String content) throws IOException {
    Path target = dir.resolve(path);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content);
  }

  private CompileResult compile() {
    return Tierwise.compile(dir, CompileOptions.defaults());
  }

  /** The findings with one code, as {@code file [path]}. */
  private static List<String> located(CompileResult result, String code) {
    return result.findings().stream().filter(f -> f.code().equals(code)).map(f -> f.file() + " " + f.path()).toList();
  }

  private static void assertOnly(CompileResult result, String code) {
    for (Finding finding : result.findings()) {
      assertEquals(code, finding.code(), result.findings().toString());
    }
    assertTrue(result.bundle().isEmpty());
  }

  // ---- KEY-1 --------------------------------------------------------------------------------------

  @Test
  void KEY_1_aKeyDeclaredInTwoSchemaFilesIsEDuplicateDefinition() throws IOException {
    write("tierwise.yaml", "specVersion: \"0.1\"\nsources:\n  schema: ['schema/*.yaml']\n");
    write("schema/core.yaml", HIERARCHY);
    write("schema/b.yaml", KEY);
    write("schema/a.yaml", KEY.replace("default: 1", "default: 2"));
    CompileResult result = compile();
    assertOnly(result, Code.E_DUPLICATE_DEFINITION);
    assertEquals(List.of("schema/b.yaml [keys, shop.checkout.limit]"), located(result, Code.E_DUPLICATE_DEFINITION));
  }

  @Test
  void KEY_1_anIdenticalSecondDeclarationIsStillADuplicate() throws IOException {
    write("tierwise.yaml", "specVersion: \"0.1\"\nsources:\n  schema: ['schema/*.yaml']\n");
    write("schema/core.yaml", HIERARCHY + KEY);
    write("schema/more.yaml", KEY);
    CompileResult result = compile();
    assertOnly(result, Code.E_DUPLICATE_DEFINITION);
    assertEquals(List.of("schema/more.yaml [keys, shop.checkout.limit]"), located(result, Code.E_DUPLICATE_DEFINITION));
  }

  @Test
  void KEY_1_aKeyDeclaredInAPresetAndInTheProjectIsADuplicate() throws IOException {
    write("presets/base.yaml", KEY);
    write("tierwise.yaml", "specVersion: \"0.1\"\nextends:\n  - presets/base.yaml\nsources:\n  schema: ['schema/*.yaml']\n");
    write("schema/core.yaml", HIERARCHY + KEY);
    CompileResult result = compile();
    assertOnly(result, Code.E_DUPLICATE_DEFINITION);
    assertEquals(List.of("schema/core.yaml [keys, shop.checkout.limit]"), located(result, Code.E_DUPLICATE_DEFINITION));
  }

  @Test
  void KEY_1_aKeyDeclaredInlineAndInAFileIsReportedInTheFile() throws IOException {
    write("tierwise.yaml", "specVersion: \"0.1\"\nschema:\n" + (HIERARCHY + KEY).indent(2)
        + "sources:\n  schema: ['schema/*.yaml']\n");
    write("schema/more.yaml", KEY);
    CompileResult result = compile();
    assertOnly(result, Code.E_DUPLICATE_DEFINITION);
    assertEquals(List.of("schema/more.yaml [keys, shop.checkout.limit]"), located(result, Code.E_DUPLICATE_DEFINITION));
  }

  // ---- KEY-12 -------------------------------------------------------------------------------------

  private void project(String valuesEntries, String inlineValues) throws IOException {
    StringBuilder manifest = new StringBuilder("specVersion: \"0.1\"\nsources:\n  schema: [schema.yaml]\n  values:\n")
        .append(valuesEntries);
    if (inlineValues != null) {
      manifest.append("values:\n").append(inlineValues.indent(2));
    }
    write("tierwise.yaml", manifest.toString());
    write("schema.yaml", HIERARCHY + KEY);
  }

  @Test
  void KEY_12_aRootValuesFileAndARootEntryOfAMultiNodeFileAreEDuplicateValue() throws IOException {
    project("    - file: values/root.yaml\n      scope: global\n    - file: values/all.yaml\n", null);
    write("values/root.yaml", "shop.checkout.limit: 3\n");
    write("values/all.yaml", "global:\n  shop.checkout.limit: 4\n");
    CompileResult result = compile();
    assertOnly(result, Code.E_DUPLICATE_VALUE);
    assertEquals(List.of("values/root.yaml [shop.checkout.limit]"), located(result, Code.E_DUPLICATE_VALUE));
  }

  @Test
  void KEY_12_aPathMappedFileAndAMultiNodeFileForOneNodeAreEDuplicateValue() throws IOException {
    project("    - pattern: 'values/{scope}/{node}.yaml'\n    - file: values/all.yaml\n", null);
    write("values/region/north.yaml", "shop.checkout.limit: 3\n");
    write("values/all.yaml", "region:\n  north:\n    shop.checkout.limit: 3\n  south:\n    shop.checkout.limit: 3\n");
    CompileResult result = compile();
    assertOnly(result, Code.E_DUPLICATE_VALUE);
    assertEquals(List.of("values/region/north.yaml [shop.checkout.limit]"), located(result, Code.E_DUPLICATE_VALUE));
  }

  @Test
  void KEY_12_twoMultiNodeFilesWithTheSameNodeAreEDuplicateValue() throws IOException {
    project("    - file: values/b.yaml\n    - file: values/a.yaml\n", null);
    write("values/a.yaml", "region:\n  north:\n    shop.checkout.limit: 3\n");
    write("values/b.yaml", "region:\n  south:\n    shop.checkout.limit: 3\n  north:\n    shop.checkout.limit: 5\n");
    CompileResult result = compile();
    assertOnly(result, Code.E_DUPLICATE_VALUE);
    assertEquals(List.of("values/b.yaml [region, north, shop.checkout.limit]"), located(result, Code.E_DUPLICATE_VALUE));
  }

  @Test
  void KEY_12_inlineValuesAndAValuesFileForOneNodeAreReportedInTheFile() throws IOException {
    project("    - file: values/all.yaml\n", "region:\n  north:\n    shop.checkout.limit: 3\n");
    write("values/all.yaml", "region:\n  north:\n    shop.checkout.limit: 3\n");
    CompileResult result = compile();
    assertOnly(result, Code.E_DUPLICATE_VALUE);
    assertEquals(List.of("values/all.yaml [region, north, shop.checkout.limit]"), located(result, Code.E_DUPLICATE_VALUE));
  }

  @Test
  void KEY_12_everyValueAfterTheFirstGetsOneFinding() throws IOException {
    project("    - file: values/root.yaml\n      scope: global\n    - file: values/all.yaml\n",
        "global:\n  shop.checkout.limit: 1\n");
    write("values/root.yaml", "shop.checkout.limit: 3\n");
    write("values/all.yaml", "global:\n  shop.checkout.limit: 4\n");
    CompileResult result = compile();
    assertOnly(result, Code.E_DUPLICATE_VALUE);
    Map<String, Integer> byFile = new TreeMap<>();
    result.findings().forEach(f -> byFile.merge(f.file(), 1, Integer::sum));
    assertEquals(Map.of("values/all.yaml", 1, "values/root.yaml", 1), byFile);
  }
}
