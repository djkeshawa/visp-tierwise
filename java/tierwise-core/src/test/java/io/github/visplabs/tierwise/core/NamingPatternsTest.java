package io.github.visplabs.tierwise.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link Tierwise#namingPatterns(Path, CompileOptions)}: the naming patterns in effect for a project, which a tool
 * needs to explain {@code naming/<name>} (FIND-30, ADR 0026).
 */
class NamingPatternsTest {

  @TempDir
  Path dir;

  private void write(String path, String content) throws IOException {
    Path target = dir.resolve(path);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content);
  }

  private Map<String, NamingPattern> patterns() {
    return Tierwise.namingPatterns(dir, CompileOptions.defaults());
  }

  @Test
  void theDeclaredPatternsAreReturnedByNameWithTheirTextsAsWritten() throws IOException {
    write("tierwise.yaml", "specVersion: \"0.1\"\nsources:\n  schema: ['schema/*.yaml']\n");
    write("schema/naming.yaml", """
        naming:
          zeta: { category: node_id, pattern: '^[a-z]+$' }
          alpha:
            category: key
            pattern: '^[a-z]+(\\.[a-z]+)+$'
            description: Keys are dotted lower-case words.
        """);
    Map<String, NamingPattern> patterns = patterns();
    assertEquals(List.of("alpha", "zeta"), List.copyOf(patterns.keySet()));
    assertEquals(new NamingPattern("alpha", "key", "^[a-z]+(\\.[a-z]+)+$",
        Optional.of("Keys are dotted lower-case words.")), patterns.get("alpha"));
    assertEquals(new NamingPattern("zeta", "node_id", "^[a-z]+$", Optional.empty()), patterns.get("zeta"));
    assertThrows(UnsupportedOperationException.class, () -> patterns.put("x", patterns.get("zeta")));
  }

  @Test
  void aPatternOfAPresetIsInheritedAndALaterLayerReplacesIt() throws IOException {
    write("presets/base.yaml", """
        naming:
          shared: { category: key, pattern: '^a$', description: From the preset. }
          inherited: { category: feature, pattern: '^b$' }
        """);
    write("tierwise.yaml", """
        specVersion: "0.1"
        extends:
          - presets/base.yaml
        schema:
          naming:
            shared: { category: enum_value, pattern: '^C$' }
        """);
    Map<String, NamingPattern> patterns = patterns();
    assertEquals(new NamingPattern("shared", "enum_value", "^C$", Optional.empty()), patterns.get("shared"),
        "MANIFEST-33: the replacement keeps no field of the preset's pattern");
    assertEquals(new NamingPattern("inherited", "feature", "^b$", Optional.empty()), patterns.get("inherited"));
  }

  @Test
  void aProjectWithFaultsStillGivesThePatternsItCouldRead() throws IOException {
    write("tierwise.yaml", "specVersion: \"0.1\"\nunknown: 1\nsources:\n  schema: ['schema/*.yaml']\n");
    write("schema/naming.yaml", """
        naming:
          readable: { category: key, pattern: '^[a-z.]+$', extra: 1 }
          bad_pattern: { category: key, pattern: '(' }
          no_pattern: { category: key }
          not_a_map: 5
        keys:
          a.b: { type: nothing }
        """);
    write("schema/broken.yaml", "a: [\n");
    assertTrue(Tierwise.compile(dir, CompileOptions.defaults()).hasErrors());
    Map<String, NamingPattern> patterns = patterns();
    assertEquals(List.of("bad_pattern", "readable"), List.copyOf(patterns.keySet()));
    assertEquals("(", patterns.get("bad_pattern").pattern());
  }

  @Test
  void aProjectWithoutAManifestOrPatternsGivesAnEmptyMap() throws IOException {
    assertEquals(Map.of(), patterns());
    write("tierwise.yaml", "specVersion: \"0.1\"\n");
    assertEquals(Map.of(), patterns());
  }

  @Test
  void aNamespacedFileDeclaresNoPattern() throws IOException {
    write("tierwise.yaml", "specVersion: \"0.1\"\nnamespaces:\n  pos: {schema: schema/pos.yaml}\n");
    write("schema/pos.yaml", "namespace: pos\nnaming:\n  p: { category: key, pattern: '^a$' }\n");
    assertEquals(Map.of(), patterns(), "NS-4");
  }
}
