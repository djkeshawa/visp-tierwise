package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Finding;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The scope and node lookup that later stages call (SCOPE-35, SCOPE-38, SCOPE-39, SCOPE-43 to SCOPE-46). */
class ScopeIndexTest {

  private static final String FILE = "values/scopes.yaml";

  private static PScalar at(String text) {
    return new PScalar(FILE, List.of("division", text), 4, PScalar.Kind.STRING, text, text);
  }

  private static ScopeIndex index() {
    Hierarchy hierarchy = new Hierarchy(List.of("global", "division", "site"), List.of("seller"), Map.of(
        "division", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("north", "south"),
            ScopeDef.OnUnknownNode.ERROR),
        "site", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("s1"), ScopeDef.OnUnknownNode.IGNORE),
        "seller", ScopeDef.openCatalog(ScopeDef.ValuesSource.FILES)));
    return new ScopeIndex(hierarchy);
  }

  private static Findings findings() {
    return new Findings();
  }

  private static List<String> codes(Findings findings) {
    return findings.sorted().stream().map(Finding::code).toList();
  }

  @Test
  void scope46_aDeclaredLevelOrAxisResolves() {
    Findings findings = findings();
    assertEquals("division", index().resolveScope(findings, at("division"), "division").orElseThrow());
    assertEquals("seller", index().resolveScope(findings, at("seller"), "seller").orElseThrow());
    assertEquals("global", index().resolveScope(findings, at("global"), "global").orElseThrow(),
        "the root level is a declared level");
    assertTrue(findings.sorted().isEmpty());
  }

  @Test
  void scope46_anUndeclaredNameIsUnknownScopeWithASuggestion() {
    Findings findings = findings();
    assertTrue(index().resolveScope(findings, at("divisoin"), "divisoin").isEmpty());
    Finding finding = findings.sorted().get(0);
    assertEquals(Code.E_UNKNOWN_SCOPE, finding.code());
    assertEquals(java.util.Optional.of("division"), finding.suggestion());
  }

  @Test
  void scope35_aScopeNameIsNormalisedBeforeTheLookup() {
    Findings findings = findings();
    ScopeIndex index = new ScopeIndex(new Hierarchy(List.of("global", "café"), List.of(), Map.of(
        "café", ScopeDef.openCatalog(ScopeDef.ValuesSource.FILES))));
    assertEquals("café", index.resolveScope(findings, at("x"), "café").orElseThrow());
    assertTrue(findings.sorted().isEmpty());
  }

  @Test
  void scope43_44_aNodeOutsideAClosedCatalogIsUnknownWhateverTheSetting() {
    Findings findings = findings();
    assertEquals(ScopeIndex.NodeStatus.UNKNOWN,
        index().checkNode(findings, at("west"), "division", "west"));
    assertEquals(ScopeIndex.NodeStatus.UNKNOWN,
        index().checkNode(findings, at("s9"), "site", "s9"));
    assertEquals(List.of(Code.E_UNKNOWN_NODE, Code.E_UNKNOWN_NODE), codes(findings));
  }

  @Test
  void scope43_aListedNodeIsAdmitted() {
    Findings findings = findings();
    assertEquals(ScopeIndex.NodeStatus.ADMITTED, index().checkNode(findings, at("north"), "division", "north"));
    assertTrue(findings.sorted().isEmpty());
  }

  @Test
  void scope44_anUnknownNodeSuggestsAListedNode() {
    Findings findings = findings();
    index().checkNode(findings, at("nrth"), "division", "nrth");
    assertEquals(java.util.Optional.of("north"), findings.sorted().get(0).suggestion());
  }

  @Test
  void scope45_anOpenCatalogAdmitsAnyValidNodeId() {
    Findings findings = findings();
    assertEquals(ScopeIndex.NodeStatus.ADMITTED, index().checkNode(findings, at("anything"), "seller", "anything"));
    assertTrue(findings.sorted().isEmpty());
  }

  @Test
  void scope38_39_anInvalidNodeIdIsInvalidAndNotCheckedForMembership() {
    Findings findings = findings();
    assertEquals(ScopeIndex.NodeStatus.INVALID, index().checkNode(findings, at(""), "division", ""));
    assertEquals(List.of(Code.E_INVALID_NODE_ID), codes(findings));
  }

  @Test
  void scope13_theRootLevelHasNoNodeIds() {
    Findings findings = findings();
    assertEquals(ScopeIndex.NodeStatus.NOT_APPLICABLE, index().checkNode(findings, at("x"), "global", "x"));
    assertTrue(findings.sorted().isEmpty());
  }

  @Test
  void scope7_theScopeOrderIsLevelsThenAxes() {
    assertEquals(List.of("global", "division", "site", "seller"), index().hierarchy().scopeOrder());
    assertFalse(index().isDeclared("missing"));
    assertTrue(index().isDeclared("seller"));
  }

  @Test
  void scope44_theScopeDefinitionIsFoundByItsNormalisedName() {
    ScopeIndex index = index();
    assertTrue(index.definition("division").isPresent());
    assertTrue(index.definition("global").isEmpty(), "the root level has no definition");
    assertEquals(ScopeDef.OnUnknownNode.IGNORE, index.definition("site").orElseThrow().onUnknownNode().orElseThrow());
    assertEquals("global", index.rootLevel());
    assertFalse(index.definition("x").isPresent());
  }
}
