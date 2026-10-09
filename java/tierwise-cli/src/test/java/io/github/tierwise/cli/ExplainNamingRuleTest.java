package io.github.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * FIND-30: the explanation of a naming rule {@code naming/<name>} is the {@code description} declared for its
 * pattern, or, when the pattern has none, the naming family explanation of {@code spec/error-codes.md} followed by
 * the pattern. {@code tierwise explain} reads the pattern from the project named by {@code --project} (ADR 0026).
 */
class ExplainNamingRuleTest {

  private static final String SCHEMA = """
      naming:
        dotted_key:
          category: key
          pattern: '^[a-z]+(\\.[a-z_]+)+$'
          description: A key is two or more dotted lower-case words.
        upper_value: { category: enum_value, pattern: '^[A-Z_]+$' }
      """;

  @TempDir
  Path project;

  private void write(String path, String content) throws IOException {
    Path target = project.resolve(path);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content);
  }

  private void standardProject() throws IOException {
    write("tierwise.yaml", "specVersion: \"0.1\"\nsources:\n  schema: ['schema/*.yaml']\n");
    write("schema/naming.yaml", SCHEMA);
  }

  /** The text of the naming family entry, as the command prints it for {@code naming/*}. */
  private static String family() {
    String out = CliRun.run("explain", "naming/*").out();
    assertTrue(out.startsWith("naming/*\n"), out);
    return out.substring("naming/*\n".length()).stripTrailing();
  }

  @Test
  void FIND_30_aPatternWithADescriptionIsExplainedByItsDescription() throws IOException {
    standardProject();
    CliRun.Result result = CliRun.run("explain", "--project", project.toString(), "naming/dotted_key");
    assertEquals(0, result.code(), result.err());
    assertEquals("naming/dotted_key\nA key is two or more dotted lower-case words.\n", result.out());
  }

  @Test
  void FIND_30_aPatternWithoutADescriptionIsExplainedByTheFamilyFollowedByThePattern() throws IOException {
    standardProject();
    CliRun.Result result = CliRun.run("explain", "--project", project.toString(), "naming/upper_value");
    assertEquals(0, result.code(), result.err());
    assertEquals("naming/upper_value\n" + family() + "\n\n**Category:** enum_value\n**Pattern:** ^[A-Z_]+$\n",
        result.out());
  }

  @Test
  void FIND_30_aPatternFromAPresetIsExplainedAndALaterLayerReplacesIt() throws IOException {
    write("presets/base.yaml", """
        naming:
          from_preset: { category: feature, pattern: '^[a-z.]+$', description: Inherited text. }
          replaced: { category: key, pattern: '^a$', description: The preset's text. }
        """);
    write("tierwise.yaml", """
        specVersion: "0.1"
        extends:
          - presets/base.yaml
        schema:
          naming:
            replaced: { category: key, pattern: '^b$' }
        """);
    assertEquals("naming/from_preset\nInherited text.\n",
        CliRun.run("explain", "--project", project.toString(), "naming/from_preset").out());
    String replaced = CliRun.run("explain", "--project", project.toString(), "naming/replaced").out();
    assertTrue(replaced.endsWith("\n**Pattern:** ^b$\n"), replaced);
    assertTrue(!replaced.contains("The preset's text."), "MANIFEST-33: " + replaced);
  }

  @Test
  void FIND_30_aProjectWithErrorFindingsStillExplainsThePatternsItCouldRead() throws IOException {
    standardProject();
    write("schema/broken.yaml", "keys: [\n");
    assertEquals(1, CliRun.run("validate", project.toString()).code());
    CliRun.Result result = CliRun.run("explain", "--project", project.toString(), "naming/dotted_key");
    assertEquals(0, result.code(), result.err());
    assertEquals("naming/dotted_key\nA key is two or more dotted lower-case words.\n", result.out());
  }

  @Test
  void FIND_30_aNamingRuleThatTheProjectDoesNotDeclareExitsWithOneAndSuggestsACloseName() throws IOException {
    standardProject();
    CliRun.Result close = CliRun.run("explain", "--project", project.toString(), "naming/doted_key");
    assertEquals(1, close.code());
    assertEquals("", close.out());
    assertTrue(close.err().contains("declares no naming pattern 'doted_key'"), close.err());
    assertTrue(close.err().contains("Did you mean naming/dotted_key?"), close.err());
    CliRun.Result far = CliRun.run("explain", "--project", project.toString(), "naming/zzzzzzzzzzzz");
    assertEquals(1, far.code());
    assertTrue(!far.err().contains("Did you mean"), far.err());
  }

  @Test
  void FIND_30_withoutAProjectTheFamilyEntryIsPrintedWithANoteThatThePatternNeedsAProject() {
    CliRun.Result result = CliRun.run("explain", "naming/dotted_key");
    assertEquals(0, result.code(), result.err());
    assertTrue(result.out().startsWith("naming/*\n" + family()), result.out());
    assertTrue(result.out().contains("The pattern needs a project"), result.out());
    assertTrue(result.out().contains("tierwise explain --project DIR naming/dotted_key"), result.out());
  }

  @Test
  void theFamilyEntryAndOtherCodesIgnoreTheProject() throws IOException {
    standardProject();
    assertEquals(CliRun.run("explain", "naming/*").out(),
        CliRun.run("explain", "--project", project.toString(), "naming/*").out());
    assertEquals(CliRun.run("explain", "E_UNKNOWN_KEY").out(),
        CliRun.run("explain", "--project", project.toString(), "E_UNKNOWN_KEY").out());
  }

  @Test
  void aProjectOptionThatIsNotAProjectIsAUsageError() throws IOException {
    CliRun.Result missing = CliRun.run("explain", "--project", project.resolve("nothing").toString(), "naming/x");
    assertEquals(2, missing.code());
    assertTrue(missing.err().contains("not a directory"), missing.err());
    CliRun.Result empty = CliRun.run("explain", "--project", project.toString(), "naming/x");
    assertEquals(2, empty.code());
    assertTrue(empty.err().contains("no tierwise.yaml"), empty.err());
  }
}
