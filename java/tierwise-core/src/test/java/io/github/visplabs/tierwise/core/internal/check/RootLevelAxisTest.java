package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.CompileOptions;
import io.github.visplabs.tierwise.core.CompileResult;
import io.github.visplabs.tierwise.core.Finding;
import io.github.visplabs.tierwise.core.Tierwise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SCOPE-11: a hierarchy whose {@code axes} list the root level is reported as {@code E_SCOPE_DECLARATION}, a repeated
 * name under SCOPE-3, and never as {@code E_DUPLICATE_DEFINITION} (SCOPE-4). SCOPE-14 and SCOPE-12 may add findings
 * for the same declaration, so a compile case cannot compare the whole set; this test checks the codes and the
 * finding at the repeated name.
 */
class RootLevelAxisTest {

  @TempDir
  Path dir;

  private CompileResult compile(String hierarchy) throws IOException {
    Files.writeString(dir.resolve("tierwise.yaml"), "specVersion: \"0.1\"\nsources:\n  schema: [schema.yaml]\n");
    Files.writeString(dir.resolve("schema.yaml"), hierarchy);
    return Tierwise.compile(dir, CompileOptions.defaults());
  }

  private static void assertOnlyScopeDeclarationAtTheAxis(CompileResult result, int axisPosition, int line) {
    List<Finding> findings = result.findings();
    assertFalse(findings.isEmpty());
    for (Finding finding : findings) {
      assertEquals(Code.E_SCOPE_DECLARATION, finding.code(), "SCOPE-4: " + finding);
    }
    assertTrue(findings.stream().noneMatch(f -> f.code().equals(Code.E_DUPLICATE_DEFINITION)));
    assertTrue(findings.stream().anyMatch(f -> f.file().equals("schema.yaml")
        && f.path().equals(List.of("decision_hierarchy", "axes", axisPosition)) && f.line() == line),
        "FIND-32: the repeated name is reported at its later occurrence, in axes: " + findings);
    assertTrue(result.bundle().isEmpty());
  }

  @Test
  void SCOPE_11_theRootLevelListedInAxesIsEScopeDeclaration() throws IOException {
    CompileResult result = compile("""
        decision_hierarchy:
          levels: [global, region]
          axes: [global]
          scopes:
            region: { nodes: [north], on_unknown_node: error }
        """);
    assertOnlyScopeDeclarationAtTheAxis(result, 0, 3);
  }

  @Test
  void SCOPE_11_theRootLevelListedInAxesWithAScopesEntryIsStillOnlyEScopeDeclaration() throws IOException {
    CompileResult result = compile("""
        decision_hierarchy:
          levels: [global, region]
          axes: [channel, global]
          scopes:
            global: { nodes: [main], on_unknown_node: error }
            region: { nodes: [north], on_unknown_node: error }
            channel: { nodes: [web], on_unknown_node: error }
        """);
    assertOnlyScopeDeclarationAtTheAxis(result, 1, 3);
  }

  @Test
  void SCOPE_11_aHierarchyWithOnlyARootLevelThatIsAlsoAnAxisIsEScopeDeclaration() throws IOException {
    CompileResult result = compile("""
        decision_hierarchy:
          levels: [global]
          axes:
            - global
        """);
    assertOnlyScopeDeclarationAtTheAxis(result, 0, 4);
  }
}
