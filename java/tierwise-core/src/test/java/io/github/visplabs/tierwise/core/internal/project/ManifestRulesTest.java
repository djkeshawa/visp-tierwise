package io.github.visplabs.tierwise.core.internal.project;

import static io.github.visplabs.tierwise.core.internal.project.ProjectFixture.assertFindings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The manifest rules of section 1.1, 1.3, 1.5 and 1.7: MANIFEST-1 to MANIFEST-20 and MANIFEST-26. */
class ManifestRulesTest {

  @TempDir
  Path dir;

  private ProjectLoad load(String manifest) {
    return new ProjectFixture(dir).manifest(manifest).load();
  }

  @Test
  void MANIFEST_2_aProjectWithoutAManifestIsReportedAtTheRootOfTheManifest() {
    assertFindings(new ProjectFixture(dir).load(), "E_MANIFEST tierwise.yaml:1 []");
  }

  @Test
  void MANIFEST_2_aManifestThatIsADirectoryIsReported() throws Exception {
    java.nio.file.Files.createDirectories(dir.resolve("tierwise.yaml"));
    assertFindings(new ProjectFixture(dir).load(), "E_MANIFEST tierwise.yaml:1 []");
  }

  @Test
  void MANIFEST_2_aRootThatIsNotAMappingIsReported() {
    assertFindings(load("- a\n- b\n"), "E_MANIFEST tierwise.yaml:1 []");
  }

  @Test
  void MANIFEST_2_anEmptyManifestIsReported() {
    assertFindings(load(""), "E_MANIFEST tierwise.yaml:1 []");
  }

  @Test
  void SRC_10_aManifestThatIsNotUtf8IsEManifest() {
    new ProjectFixture(dir).bytes("tierwise.yaml", new byte[] {'a', ':', ' ', (byte) 0xff, '\n'});
    assertFindings(new ProjectFixture(dir).load(), "E_MANIFEST tierwise.yaml:1 []");
  }

  @Test
  void MANIFEST_3_everyUnknownTopLevelKeyIsReportedAtItsEntry() {
    assertFindings(load("specVersion: \"0.1\"\nowner: x\nsources: {}\nteam: y\n"),
        "E_MANIFEST tierwise.yaml:2 [owner]", "E_MANIFEST tierwise.yaml:4 [team]");
  }

  @Test
  void MANIFEST_4_aMissingSpecVersionIsReportedAtTheRootMapping() {
    assertFindings(load("sources: {}\n"), "E_MANIFEST tierwise.yaml:1 []");
  }

  @Test
  void MANIFEST_4_aSpecVersionThatIsNotAStringIsReported() {
    assertFindings(load("specVersion: 0.1\n"), "E_MANIFEST tierwise.yaml:1 [specVersion]");
    assertFindings(load("specVersion: 1\n"), "E_MANIFEST tierwise.yaml:1 [specVersion]");
    assertFindings(load("specVersion: true\n"), "E_MANIFEST tierwise.yaml:1 [specVersion]");
    assertFindings(load("specVersion:\n"), "E_MANIFEST tierwise.yaml:1 [specVersion]");
  }

  @Test
  void MANIFEST_5_aSpecVersionIsComparedExactly() {
    assertFindings(load("specVersion: \"0.1.0\"\n"), "E_MANIFEST tierwise.yaml:1 [specVersion]");
    assertFindings(load("specVersion: \"0.1 \"\n"), "E_MANIFEST tierwise.yaml:1 [specVersion]");
    assertFindings(load("specVersion: \"0.2\"\n"), "E_MANIFEST tierwise.yaml:1 [specVersion]");
    assertFindings(load("specVersion: \"0.1\"\n"));
  }

  @Test
  void MANIFEST_18_aManifestWithNoContentAndNoSourcesIsValid() {
    ProjectLoad load = load("specVersion: \"0.1\"\n");
    assertTrue(load.findings().isEmpty());
    assertTrue(load.sources().documents().isEmpty());
  }

  @Test
  void MANIFEST_19_extendsMustBeAList() {
    assertFindings(load("specVersion: \"0.1\"\nextends: \"tierwise:minimal\"\n"), "E_MANIFEST tierwise.yaml:2 [extends]");
    assertFindings(load("specVersion: \"0.1\"\nextends:\n"), "E_MANIFEST tierwise.yaml:2 [extends]");
  }

  @Test
  void MANIFEST_20_anExtendsItemMustBeAReference() {
    assertFindings(load("specVersion: \"0.1\"\nextends:\n  - 42\n  - true\n  - [a]\n  -\n"),
        "E_MANIFEST tierwise.yaml:3 [extends, 0]", "E_MANIFEST tierwise.yaml:4 [extends, 1]",
        "E_MANIFEST tierwise.yaml:5 [extends, 2]", "E_MANIFEST tierwise.yaml:6 [extends, 3]");
  }

  @Test
  void MANIFEST_26_aUrlReferenceHasExactlyUrlAndSha256() {
    String digest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    assertFindings(load("specVersion: \"0.1\"\nextends:\n  - url: \"https://x.invalid/p.yaml\"\n"),
        "E_MANIFEST tierwise.yaml:3 [extends, 0]");
    assertFindings(load("specVersion: \"0.1\"\nextends:\n  - url: \"https://x.invalid/p.yaml\"\n    sha256: \"" + digest
        + "\"\n    extra: 1\n"), "E_MANIFEST tierwise.yaml:3 [extends, 0]");
  }

  @Test
  void MANIFEST_26_theUrlMustBeHttpsAndTheDigestLowerCaseHex() {
    String digest = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    assertFindings(load("specVersion: \"0.1\"\nextends:\n  - url: \"http://x.invalid/p.yaml\"\n    sha256: \"" + digest + "\"\n"),
        "E_MANIFEST tierwise.yaml:3 [extends, 0, url]");
    assertFindings(load("specVersion: \"0.1\"\nextends:\n  - url: 5\n    sha256: \"" + digest + "\"\n"),
        "E_MANIFEST tierwise.yaml:3 [extends, 0, url]");
    assertFindings(load("specVersion: \"0.1\"\nextends:\n  - url: \"https://x.invalid/p.yaml\"\n    sha256: \"abc\"\n"),
        "E_MANIFEST tierwise.yaml:4 [extends, 0, sha256]");
    assertFindings(load("specVersion: \"0.1\"\nextends:\n  - url: \"https://x.invalid/p.yaml\"\n    sha256: \""
        + digest.toUpperCase() + "\"\n"), "E_MANIFEST tierwise.yaml:4 [extends, 0, sha256]");
    assertFindings(load("specVersion: \"0.1\"\nextends:\n  - url: \"https:///p.yaml\"\n    sha256: \"" + digest + "\"\n"),
        "E_MANIFEST tierwise.yaml:3 [extends, 0, url]");
  }

  @Test
  void MANIFEST_10_sourcesIsAMappingWithOnlyTheThreeRoles() {
    assertFindings(load("specVersion: \"0.1\"\nsources: []\n"), "E_MANIFEST tierwise.yaml:2 [sources]");
    assertFindings(load("specVersion: \"0.1\"\nsources:\n  other: []\n"), "E_MANIFEST tierwise.yaml:3 [sources, other]");
    assertFindings(load("specVersion: \"0.1\"\nsources:\n"), "E_MANIFEST tierwise.yaml:2 [sources]");
  }

  @Test
  void MANIFEST_11_schemaAndRulesAreListsOfStringsAndValuesIsAList() {
    assertFindings(load("specVersion: \"0.1\"\nsources:\n  schema: a.yaml\n  rules: {}\n  values: x\n"),
        "E_MANIFEST tierwise.yaml:3 [sources, schema]", "E_MANIFEST tierwise.yaml:4 [sources, rules]",
        "E_MANIFEST tierwise.yaml:5 [sources, values]");
    assertFindings(load("specVersion: \"0.1\"\nsources:\n  schema:\n    - 5\n    - null\n  rules:\n    - true\n"),
        "E_MANIFEST tierwise.yaml:4 [sources, schema, 0]", "E_MANIFEST tierwise.yaml:5 [sources, schema, 1]",
        "E_MANIFEST tierwise.yaml:7 [sources, rules, 0]");
  }

  @Test
  void MANIFEST_14_inlineSchemaMustBeAMapping() {
    assertFindings(load("specVersion: \"0.1\"\nschema: []\n"), "E_MANIFEST tierwise.yaml:2 [schema]");
    assertFindings(load("specVersion: \"0.1\"\nschema:\n"), "E_MANIFEST tierwise.yaml:2 [schema]");
  }

  @Test
  void MANIFEST_15_inlineValuesMustBeAMapping() {
    assertFindings(load("specVersion: \"0.1\"\nvalues: [a]\n"), "E_MANIFEST tierwise.yaml:2 [values]");
  }

  @Test
  void MANIFEST_16_inlineRulesMustBeAListOfMappings() {
    assertFindings(load("specVersion: \"0.1\"\nrules: {}\n"), "E_MANIFEST tierwise.yaml:2 [rules]");
    assertFindings(load("specVersion: \"0.1\"\nrules:\n  - x\n  - {}\n"), "E_MANIFEST tierwise.yaml:3 [rules, 0]");
  }

  @Test
  void MANIFEST_13_inlineContentBecomesDocumentsOfTheManifestFile() {
    ProjectLoad load = load("""
        specVersion: "0.1"
        schema:
          keys: {}
        values:
          global: {}
        rules:
          - feature: a.b
          - feature: c.d
        """);
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertEquals(4, load.sources().documents().size());
    assertEquals(SourceRole.SCHEMA, load.sources().documents().get(0).role());
    assertEquals(SourceRole.VALUES, load.sources().documents().get(1).role());
    assertEquals(SourceRole.RULES, load.sources().documents().get(2).role());
    assertEquals(java.util.List.of("rules", 0), load.sources().documents().get(2).root().path());
    assertEquals(java.util.List.of("rules", 1), load.sources().documents().get(3).root().path());
    assertEquals(7, load.sources().documents().get(2).root().line());
    for (SourceDocument document : load.sources().documents()) {
      assertEquals("tierwise.yaml", document.file());
      assertEquals(Origin.INLINE, document.origin());
    }
  }

  @Test
  void SRC_5_aSyntaxErrorInTheManifestIsEManifest() {
    assertFindings(load("specVersion: \"0.1\"\nsources: [schema\n"), "E_MANIFEST tierwise.yaml:1 []");
  }

  @Test
  void SRC_3_aRepeatedManifestKeyIsReportedAtTheSecondOccurrence() {
    assertFindings(load("specVersion: \"0.1\"\nspecVersion: \"0.1\"\n"), "E_DUPLICATE_KEY tierwise.yaml:2 [specVersion]");
  }

  @Test
  void SRC_7_anAnchorOrTagInTheManifestIsEYamlFeature() {
    assertFindings(load("specVersion: \"0.1\"\nsources: &s {}\n"), "E_YAML_FEATURE tierwise.yaml:2 [sources]");
    assertFindings(load("specVersion: !!str \"0.1\"\n"), "E_YAML_FEATURE tierwise.yaml:1 [specVersion]");
  }

  @Test
  void SRC_10_aByteOrderMarkInTheManifestIsEManifest() {
    new ProjectFixture(dir).bytes("tierwise.yaml", ("﻿specVersion: \"0.1\"\n").getBytes(StandardCharsets.UTF_8));
    assertFindings(new ProjectFixture(dir).load(), "E_MANIFEST tierwise.yaml:1 []");
  }

  @Test
  void aProblemInOnePartDoesNotHideAProblemInAnother() {
    assertFindings(load("specVersion: 5\nowner: x\nsources: []\nextends: a\nnamespaces: []\nlint: []\nschema: []\n"
            + "values: []\nrules: {}\n"),
        "E_MANIFEST tierwise.yaml:1 [specVersion]", "E_MANIFEST tierwise.yaml:2 [owner]",
        "E_MANIFEST tierwise.yaml:3 [sources]", "E_MANIFEST tierwise.yaml:4 [extends]",
        "E_MANIFEST tierwise.yaml:5 [namespaces]", "E_MANIFEST tierwise.yaml:6 [lint]",
        "E_MANIFEST tierwise.yaml:7 [schema]", "E_MANIFEST tierwise.yaml:8 [values]", "E_MANIFEST tierwise.yaml:9 [rules]");
  }

  @Test
  void codesAreEngineCodes() {
    for (var finding : load("specVersion: 5\n").findings()) {
      assertTrue(Code.engine().contains(finding.code()));
    }
  }
}
