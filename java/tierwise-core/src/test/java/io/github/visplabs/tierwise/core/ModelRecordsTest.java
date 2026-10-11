package io.github.visplabs.tierwise.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.internal.model.BindingDef;
import io.github.visplabs.tierwise.core.internal.model.ConditionDef;
import io.github.visplabs.tierwise.core.internal.model.EntityDef;
import io.github.visplabs.tierwise.core.internal.model.FeatureDef;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.KeyDef;
import io.github.visplabs.tierwise.core.internal.model.Plain;
import io.github.visplabs.tierwise.core.internal.model.PolicyDef;
import io.github.visplabs.tierwise.core.internal.model.Returns;
import io.github.visplabs.tierwise.core.internal.model.RuleDef;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import io.github.visplabs.tierwise.core.internal.model.SettingDef;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.model.Values;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import io.github.visplabs.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The model records copy defensively and refuse shapes the bundle format forbids. */
class ModelRecordsTest {

  @Test
  void plainCopiesDeeplyAndWidensIntegers() {
    List<Object> inner = new ArrayList<>(List.of(1, 2L));
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("list", inner);
    source.put("flag", true);
    Map<String, Object> copy = Plain.copyMap(source);
    inner.add(3);
    assertEquals(List.of(1L, 2L), copy.get("list"));
    assertThrows(UnsupportedOperationException.class, () -> copy.put("x", 1L));
    assertThrows(UnsupportedOperationException.class, () -> ((List<?>) copy.get("list")).clear());
  }

  @Test
  void plainRejectsNullAndForeignTypes() {
    assertThrows(IllegalArgumentException.class, () -> Plain.copy(null));
    assertThrows(IllegalArgumentException.class, () -> Plain.copy(1.5));
    assertThrows(IllegalArgumentException.class, () -> Plain.copy(List.of(new Object())));
    assertThrows(IllegalArgumentException.class, () -> Plain.copyMap(Map.of(1, "x")));
    assertEquals(null, Plain.copyEffect(null));
  }

  @Test
  void hierarchyKnowsItsRootAndScopeOrder() {
    Hierarchy hierarchy = new Hierarchy(List.of("level_root", "level_a"), List.of("axis_a"),
        Map.of("level_a", ScopeDef.openCatalog(ScopeDef.ValuesSource.SERVICE),
            "axis_a", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("node_x"),
                ScopeDef.OnUnknownNode.IGNORE)));
    assertEquals("level_root", hierarchy.rootLevel());
    assertEquals(List.of("level_root", "level_a", "axis_a"), hierarchy.scopeOrder());
    assertTrue(hierarchy.isLevel("level_a"));
    assertTrue(hierarchy.isAxis("axis_a"));
    assertTrue(hierarchy.scope("level_a").isServiceSourced());
    assertEquals(null, hierarchy.scope("level_root"));
    assertThrows(IllegalArgumentException.class, () -> new Hierarchy(List.of(), List.of(), Map.of()));
  }

  @Test
  void onUnknownNodeIsPresentExactlyForClosedCatalogs() {
    assertThrows(IllegalArgumentException.class,
        () -> new ScopeDef(ScopeDef.ValuesSource.FILES, true, List.of(), Optional.of(ScopeDef.OnUnknownNode.ERROR)));
    assertThrows(IllegalArgumentException.class,
        () -> new ScopeDef(ScopeDef.ValuesSource.FILES, false, List.of("a"), Optional.empty()));
    assertThrows(IllegalArgumentException.class,
        () -> new ScopeDef(ScopeDef.ValuesSource.FILES, true, List.of("a"), Optional.empty()));
    assertEquals(Optional.of(ScopeDef.ValuesSource.SERVICE), ScopeDef.ValuesSource.parse("service"));
    assertEquals(Optional.empty(), ScopeDef.OnUnknownNode.parse("warn"));
  }

  @Test
  void keysAndEntitiesCopyTheirLists() {
    List<String> settable = new ArrayList<>(List.of("level_root"));
    KeyDef key = new KeyDef(TypeDecl.integer(0L, 10L), "override", Optional.of(4), settable, Optional.empty(),
        Optional.empty(), Optional.empty());
    settable.add("level_a");
    assertEquals(List.of("level_root"), key.settableAt());
    assertEquals(Optional.of(4L), key.defaultValue().map(Long.class::cast));
    List<String> edges = new ArrayList<>(List.of("entity_b"));
    EntityDef entity = new EntityDef(edges, List.of());
    edges.clear();
    assertEquals(List.of("entity_b"), entity.belongsTo());
  }

  @Test
  void typeDeclarationsBuildEveryForm() {
    TypeDecl object = TypeDecl.object(Map.of("field_a", TypeDecl.simple("string").asOptional(),
        "field_b", TypeDecl.list(TypeDecl.enumeration(List.of("A", "B")))));
    assertTrue(object.fields().get("field_a").optional());
    assertFalse(object.fields().get("field_b").optional());
    assertEquals(Optional.of("enum"), object.fields().get("field_b").items().map(TypeDecl::type));
    assertEquals(List.of("A", "B"), TypeDecl.set(List.of("A", "B")).universe());
  }

  @Test
  void returnsHasExactlyOneForm() {
    assertTrue(Returns.enumeration(List.of("A"), true).nullable());
    assertTrue(Returns.object(Map.of("field_a", TypeDecl.simple("boolean"))).objectForm());
    assertThrows(IllegalArgumentException.class, () -> new Returns(List.of("A"), false, Map.of(), true));
    assertThrows(IllegalArgumentException.class,
        () -> new Returns(List.of(), false, Map.of("f", TypeDecl.simple("boolean")), false));
  }

  @Test
  void featuresCopyBindingsAndKeepTheNullEffect() {
    Map<String, BindingDef> nodes = new LinkedHashMap<>();
    nodes.put("", new BindingDef("policy_x", Map.of("setting_x", List.of("A"))));
    Map<String, Map<String, BindingDef>> bindings = new LinkedHashMap<>();
    bindings.put("level_root", nodes);
    PolicyDef policy = new PolicyDef(
        Map.of("setting_x", new SettingDef(SettingDef.Kind.LIST_OF, "entity_a.fact_x", List.of("A"))),
        null, List.of("entity_a.fact_x"),
        List.of(new RuleDef("rule-1", "Rule one",
            List.of(new ConditionDef("entity_a.fact_x", "in", "$setting_x")), null)));
    FeatureDef feature = new FeatureDef("entity_a", Returns.enumeration(List.of("A"), true), "FIRST", null,
        List.of("level_root"), Map.of("policy_x", policy), bindings);
    nodes.clear();
    assertEquals(1, feature.bindings().get("level_root").size());
    assertEquals(null, feature.ifFactMissing());
    assertEquals(null, feature.policies().get("policy_x").defaultEffect());
    assertThrows(UnsupportedOperationException.class, () -> feature.bindings().get("level_root").clear());
    ConditionDef reference = feature.policies().get("policy_x").rules().get(0).when().get(0);
    assertTrue(reference.isSettingReference());
    assertEquals("setting_x", reference.settingName());
    assertFalse(new ConditionDef("entity_a.fact_x", "eq", true).isSettingReference());
  }

  @Test
  void valuesAreIndexedByScopeNodeAndKey() {
    Values values = new Values(Map.of("level_a", Map.of("node_x", Map.of("key_x", 6))));
    assertEquals(6L, values.get("level_a", "node_x", "key_x"));
    assertEquals(null, values.get("level_a", "node_y", "key_x"));
    assertEquals(null, values.get("level_b", "node_x", "key_x"));
    assertTrue(Values.EMPTY.byScope().isEmpty());
  }

  @Test
  void parsedNodesCarryTheirLocation() {
    PScalar scalar = new PScalar("tierwise.yaml", List.of("a", 0), 3, PScalar.Kind.INTEGER, 5L, "5");
    PSeq seq = new PSeq("tierwise.yaml", List.of("a"), 2, List.of(scalar));
    PMap map = new PMap("tierwise.yaml", List.of(), 1, Map.of("a", seq));
    PNode node = map.get("a").orElseThrow();
    assertEquals(List.of("a"), node.path());
    assertEquals(2, node.line());
    assertTrue(map.has("a"));
    assertFalse(map.has("b"));
    assertEquals("tierwise.yaml", scalar.file());
    assertThrows(IllegalArgumentException.class,
        () -> new PScalar("f", List.of(), 1, PScalar.Kind.BOOLEAN, "true", "true"));
  }
}
