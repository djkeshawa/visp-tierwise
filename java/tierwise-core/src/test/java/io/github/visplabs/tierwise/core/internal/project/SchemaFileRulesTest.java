package io.github.visplabs.tierwise.core.internal.project;

import static io.github.visplabs.tierwise.core.internal.project.ProjectFixture.assertFindings;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Schema files, namespaced files and inline schema: SRC-14, SRC-16, SRC-45, NS-1 to NS-12. */
class SchemaFileRulesTest {

  private static final String MANIFEST = "specVersion: \"0.1\"\nsources:\n  schema: ['schema/core.yaml']\n";
  private static final String NS = MANIFEST + "namespaces:\n  pos: {schema: schema/pos.yaml}\n";

  @TempDir
  Path dir;

  private ProjectFixture project(String manifest) {
    return new ProjectFixture(dir).manifest(manifest);
  }

  @Test
  void SRC_14_everyKeyThatSchemaFilesMayNotHaveIsEInvalidDeclarationAtItsEntry() {
    ProjectLoad load = project(MANIFEST).file("schema/core.yaml", "labels: {}\nspecVersion: \"0.1\"\nkeys: {}\nlint: {}\n"
        + "sources: {}\n").load();
    assertFindings(load, "E_INVALID_DECLARATION schema/core.yaml:1 [labels]",
        "E_INVALID_DECLARATION schema/core.yaml:2 [specVersion]", "E_INVALID_DECLARATION schema/core.yaml:4 [lint]",
        "E_INVALID_DECLARATION schema/core.yaml:5 [sources]");
  }

  @Test
  void SRC_14_everyAllowedKeyIsAccepted() {
    ProjectLoad load = project(MANIFEST).file("schema/core.yaml", """
        decision_hierarchy: {levels: [global]}
        entity_hierarchy: {}
        facts: {}
        kinds: {}
        naming: {}
        keys: {}
        features: {}
        """).load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
  }

  @Test
  void SRC_16_inlineSchemaIsCheckedLikeASchemaFileInTheManifest() {
    ProjectLoad load = project("specVersion: \"0.1\"\nschema:\n  keys: {}\n  labels: 1\n  namespace: pos\n").load();
    assertFindings(load, "E_INVALID_DECLARATION tierwise.yaml:4 [schema, labels]",
        "E_NAMESPACE_VIOLATION tierwise.yaml:5 [schema, namespace]");
  }

  @Test
  void SRC_45_aMemberThatIsNullOrNotAMappingIsEInvalidDeclaration() {
    ProjectLoad load = project(MANIFEST).file("schema/core.yaml", "facts:\nkeys: []\nkinds: x\nnaming: 1\nfeatures: [a]\n"
        + "entity_hierarchy:\n").load();
    assertFindings(load, "E_INVALID_DECLARATION schema/core.yaml:1 [facts]", "E_INVALID_DECLARATION schema/core.yaml:2 [keys]",
        "E_INVALID_DECLARATION schema/core.yaml:3 [kinds]", "E_INVALID_DECLARATION schema/core.yaml:4 [naming]",
        "E_INVALID_DECLARATION schema/core.yaml:5 [features]", "E_INVALID_DECLARATION schema/core.yaml:6 [entity_hierarchy]");
  }

  @Test
  void SRC_45_theDecisionHierarchyBelongsToSection2() {
    ProjectLoad load = project(MANIFEST).file("schema/core.yaml", "decision_hierarchy:\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
  }

  @Test
  void SRC_3_aRepeatedKeyAtAnyDepthIsEDuplicateKeyAtTheSecondOccurrence() {
    ProjectLoad load = project(MANIFEST).file("schema/core.yaml", "naming:\n  a: {}\n  b: {}\n  a: {}\n").load();
    assertFindings(load, "E_DUPLICATE_KEY schema/core.yaml:4 [naming, a]");
  }

  @Test
  void SRC_4_keysAreDuplicatesWhenEqualAfterNfcAndThePathHoldsTheKeyAsWritten() {
    ProjectLoad load = project(MANIFEST).file("schema/core.yaml", "naming:\n  café: {}\n  café: {}\n").load();
    assertFindings(load, "E_DUPLICATE_KEY schema/core.yaml:3 [naming, café]");
  }

  @Test
  void FACT_3_theNamesOfFactsAreHeldNfcNormalisedInTheTreeAndInPaths() {
    ProjectLoad load = project(MANIFEST).file("schema/core.yaml", "facts:\n  café.x: {type: boolean}\n").load();
    SourceDocument core = ProjectFixture.document(load, "schema/core.yaml");
    assertTrue(((io.github.visplabs.tierwise.core.internal.yaml.PMap) core.root().get("facts").orElseThrow()).has("café.x"));
  }

  // ---- namespaces --------------------------------------------------------------------------------

  @Test
  void NS_1_namespacesIsAMapping() {
    assertFindings(project("specVersion: \"0.1\"\nnamespaces: [pos]\n").load(), "E_MANIFEST tierwise.yaml:2 [namespaces]");
    assertTrue(project("specVersion: \"0.1\"\nnamespaces: {}\n").load().findings().isEmpty());
    assertFindings(project("specVersion: \"0.1\"\nnamespaces:\n").load(), "E_MANIFEST tierwise.yaml:2 [namespaces]");
  }

  @Test
  void NS_2_aNamespaceEntryIsAMappingWhoseOnlyKeyIsASchemaString() {
    String manifest = "specVersion: \"0.1\"\nnamespaces:\n  a: schema/a.yaml\n  b: {}\n  c: {schema: 5}\n  d: {schema: s/d.yaml, x: 1}\n";
    assertFindings(project(manifest).load(), "E_MANIFEST tierwise.yaml:3 [namespaces, a]",
        "E_MANIFEST tierwise.yaml:4 [namespaces, b]", "E_MANIFEST tierwise.yaml:5 [namespaces, c, schema]",
        "E_MANIFEST tierwise.yaml:6 [namespaces, d, x]");
  }

  @Test
  void NS_9_aNamespaceNameIsNonEmptyAndHasNoDot() {
    String manifest = "specVersion: \"0.1\"\nnamespaces:\n  pos.x: {schema: schema/pos.yaml}\n  \"\": {schema: schema/pos.yaml}\n";
    assertFindings(project(manifest).file("schema/pos.yaml", "namespace: pos.x\n").load(),
        "E_MANIFEST tierwise.yaml:3 [namespaces, pos.x]", "E_MANIFEST tierwise.yaml:4 [namespaces, ]");
  }

  @Test
  void NS_3_aNamespacedFileCarriesItsNamespaceAndSharedFilesCarryNone() {
    ProjectLoad load = project(NS).file("schema/core.yaml", "keys: {}\n").file("schema/pos.yaml", "namespace: pos\nkeys: {}\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertTrue(ProjectFixture.document(load, "schema/pos.yaml").isNamespaced());
    assertTrue(!ProjectFixture.document(load, "schema/core.yaml").isNamespaced());
  }

  @Test
  void NS_4_aNamespacedFileHasOnlyKeysFeaturesAndNamespace() {
    ProjectLoad load = project(NS).file("schema/core.yaml", "keys: {}\n").file("schema/pos.yaml", """
        namespace: pos
        decision_hierarchy: {}
        entity_hierarchy: {}
        facts: {}
        kinds: {}
        naming: {}
        labels: {}
        keys: {}
        features: {}
        """).load();
    assertFindings(load, "E_NAMESPACE_VIOLATION schema/pos.yaml:2 [decision_hierarchy]",
        "E_NAMESPACE_VIOLATION schema/pos.yaml:3 [entity_hierarchy]", "E_NAMESPACE_VIOLATION schema/pos.yaml:4 [facts]",
        "E_NAMESPACE_VIOLATION schema/pos.yaml:5 [kinds]", "E_NAMESPACE_VIOLATION schema/pos.yaml:6 [naming]",
        "E_NAMESPACE_VIOLATION schema/pos.yaml:7 [labels]");
  }

  @Test
  void NS_5_everyKeyAndFeatureOfANamespacedFileIsInItsNamespace() {
    ProjectLoad load = project(NS).file("schema/core.yaml", "keys: {}\n").file("schema/pos.yaml", """
        namespace: pos
        keys:
          pos.a.b: {}
          other.flag: {}
          posx.flag: {}
          pos: {}
        features:
          pos.f: {}
          shop.f: {}
        """).load();
    assertFindings(load, "E_NAMESPACE_VIOLATION schema/pos.yaml:4 [keys, other.flag]",
        "E_NAMESPACE_VIOLATION schema/pos.yaml:5 [keys, posx.flag]", "E_NAMESPACE_VIOLATION schema/pos.yaml:6 [keys, pos]",
        "E_NAMESPACE_VIOLATION schema/pos.yaml:9 [features, shop.f]");
  }

  @Test
  void NS_10_aSharedFileMayDeclareNamesWithAnyNamespacePrefix() {
    ProjectLoad load = project(NS).file("schema/core.yaml", "keys:\n  pos.a.b: {}\n  shop.a.b: {}\n")
        .file("schema/pos.yaml", "namespace: pos\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
  }

  @Test
  void NS_7_theNamespaceKeyIsRequiredAndMustEqualTheListedName() {
    ProjectLoad missing = project(NS).file("schema/core.yaml", "keys: {}\n").file("schema/pos.yaml", "keys: {}\n").load();
    assertFindings(missing, "E_NAMESPACE_VIOLATION schema/pos.yaml:1 []");
    ProjectLoad differs = project(NS).file("schema/pos.yaml", "namespace: shop\n").load();
    assertFindings(differs, "E_NAMESPACE_VIOLATION schema/pos.yaml:1 [namespace]");
    ProjectLoad notString = project(NS).file("schema/pos.yaml", "\nnamespace: 5\n").load();
    assertFindings(notString, "E_NAMESPACE_VIOLATION schema/pos.yaml:2 [namespace]");
  }

  @Test
  void NS_7_aSharedFileMustNotHaveTheNamespaceKey() {
    ProjectLoad load = project(MANIFEST).file("schema/core.yaml", "keys: {}\nnamespace: pos\n").load();
    assertFindings(load, "E_NAMESPACE_VIOLATION schema/core.yaml:2 [namespace]");
  }

  @Test
  void NS_12_aPresetMustNotHaveTheNamespaceKey() {
    ProjectLoad load = project("specVersion: \"0.1\"\nextends: [presets/p.yaml]\n").file("presets/p.yaml", "namespace: pos\n").load();
    assertFindings(load, "E_PRESET presets/p.yaml:1 [namespace]");
  }

  @Test
  void NS_11_valuesAndRulesFilesBelongToNoNamespace() {
    String manifest = "specVersion: \"0.1\"\nsources:\n  values:\n    - file: v.yaml\n  rules: ['r/*.yaml']\n";
    ProjectLoad load = project(manifest).file("v.yaml", "{}\n").file("r/a.yaml", "{}\n").load();
    for (SourceDocument document : load.sources().documents()) {
      assertTrue(document.namespace().isEmpty());
    }
  }
}
