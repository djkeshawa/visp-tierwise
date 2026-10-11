package io.github.visplabs.tierwise.cli.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The generation rules that act on whole projects: errors, collisions, packages and determinism. */
class CodegenProjectTest {

  /** Copies the rich project into a fresh directory, so that a test can change one file. */
  private static Path copyOfRichProject(Path directory) throws IOException {
    Path project = directory.resolve("project");
    CodegenTestSupport.copyTree(CodegenTestSupport.resourceProject("rich"), project);
    return project;
  }

  private static void replaceInFile(Path file, String before, String after) throws IOException {
    String text = Files.readString(file);
    assertTrue(text.contains(before), () -> "the test edit must apply: " + before);
    Files.writeString(file, text.replace(before, after));
  }

  @Test
  void aProjectWithAnErrorGivesNoCodeAndItsFindings(@TempDir Path directory) throws IOException {
    Path project = copyOfRichProject(directory);
    replaceInFile(project.resolve("tierwise.yaml"), "specVersion: \"0.1\"", "specVersion: \"9.9\"");
    CodegenException error = assertThrows(CodegenException.class,
        () -> CodegenTestSupport.generate(project, CodegenLanguage.TYPESCRIPT, null));
    assertFalse(error.findings().isEmpty(), "the findings of the compiler are reported");
    assertTrue(error.findings().stream().anyMatch(finding -> finding.severity().id().equals("error")));
  }

  @Test
  void twoKeysWithOneJavaConstantNameAreRejected(@TempDir Path directory) throws IOException {
    Path project = copyOfRichProject(directory);
    replaceInFile(project.resolve("schema").resolve("core.yaml"), "  rich.fee.rate:",
        "  rich.a_b.c:\n    type: boolean\n    combine: all\n    settable_at: [global]\n"
            + "  rich.a.b_c:\n    type: boolean\n    combine: all\n    settable_at: [global]\n"
            + "  rich.fee.rate:");
    CodegenException error = assertThrows(CodegenException.class,
        () -> CodegenTestSupport.generate(project, CodegenLanguage.JAVA, "rich.gen"));
    assertTrue(error.getMessage().contains("RICH_A_B_C"), error.getMessage());
  }

  @Test
  void twoEffectsWithOneJavaConstantNameAreRejected(@TempDir Path directory) throws IOException {
    Path project = copyOfRichProject(directory);
    replaceInFile(project.resolve("schema").resolve("core.yaml"),
        "returns: { enum: [ALLOW, DENY], nullable: true }",
        "returns: { enum: [ALLOW, DENY, allow], nullable: true }");
    CodegenException error = assertThrows(CodegenException.class,
        () -> CodegenTestSupport.generate(project, CodegenLanguage.JAVA, "rich.gen"));
    assertTrue(error.getMessage().contains("\"ALLOW\""), error.getMessage());
    assertTrue(error.getMessage().contains("\"allow\""), error.getMessage());
  }

  @Test
  void theJavaFilesGoIntoTheDirectoryOfThePackage() {
    List<String> paths = CodegenTestSupport.generate(CodegenTestSupport.resourceProject("rich"), CodegenLanguage.JAVA, "a.b")
        .files().stream().map(GeneratedFile::path).toList();
    assertEquals(7, paths.size());
    assertTrue(paths.stream().allMatch(path -> path.startsWith("a/b/")), paths.toString());
  }

  @Test
  void theTypeScriptModuleIgnoresThePackageName() {
    List<GeneratedFile> files = CodegenTestSupport.generate(
        CodegenTestSupport.resourceProject("rich"), CodegenLanguage.TYPESCRIPT, "not a package").files();
    assertEquals(List.of("tierwise.ts"), files.stream().map(GeneratedFile::path).toList());
  }

  @Test
  void aJavaPackageMustBeAJavaPackageName() {
    assertThrows(IllegalArgumentException.class, () -> Codegen.checkJavaPackage("class.x"));
    assertThrows(IllegalArgumentException.class, () -> Codegen.checkJavaPackage("a-b"));
  }

  @Test
  void theSameProjectGivesTheSameFilesEveryTime() {
    Path project = CodegenTestSupport.resourceProject("rich");
    List<GeneratedFile> first = CodegenTestSupport.generate(project, CodegenLanguage.JAVA, "rich.gen").files();
    List<GeneratedFile> second = CodegenTestSupport.generate(project, CodegenLanguage.JAVA, "rich.gen").files();
    assertEquals(first, second);
  }
}
