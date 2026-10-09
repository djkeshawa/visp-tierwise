package io.github.tierwise.core.internal.bundle;

import static io.github.tierwise.core.internal.bundle.BundleFixtures.arr;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.assertFault;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.assertLoads;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.mapAt;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.obj;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.richContent;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Integrity checks beyond the literal list of BUNDLE-118 to BUNDLE-136. Each one rejects a bundle that no valid
 * source can produce and that an evaluator could not use (see the open point on EVAL-24). The test name starts
 * with the clause that owns the rule.
 */
class BundleExtraChecksTest {

  @Test
  void KEY_7_settableAtMayNotBeEmpty() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.limit").put("settableAt", arr());
    assertFault(content, "KEY-7");
  }

  @Test
  void SCOPE_3_aLevelAndAnAxisMayNotShareAName() {
    Map<String, Object> content = richContent();
    mapAt(content, "hierarchy").put("axes", arr("seller", "division"));
    assertFault(content, "SCOPE-3");
  }

  @Test
  void SCOPE_3_aLevelNameMayNotRepeat() {
    Map<String, Object> content = richContent();
    mapAt(content, "hierarchy").put("levels", arr("global", "division", "division"));
    assertFault(content, "SCOPE-3");
  }

  @Test
  void SCOPE_33_nodeIdHasAtMost128CodePoints() {
    Map<String, Object> content = richContent();
    mapAt(content, "hierarchy", "scopes", "seller").put("nodes", arr("x".repeat(129)));
    assertFault(content, "SCOPE-33");
  }

  @Test
  void SCOPE_34_nodeIdHasNoControlCharacter() {
    Map<String, Object> content = richContent();
    mapAt(content, "hierarchy", "scopes", "seller").put("nodes", arr("a\u0001b"));
    assertFault(content, "SCOPE-34");
  }

  @Test
  void ENTITY_18_entityNameHasNoDot() {
    Map<String, Object> content = richContent();
    mapAt(content, "entities").put("a.b", obj("belongsTo", arr(), "hasMany", arr()));
    assertFault(content, "ENTITY-18");
  }

  @Test
  void BUNDLE_82_optionalIsWrittenOnlyWhenTrue() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.window", "type", "fields", "slot").put("optional", false);
    assertFault(content, "BUNDLE-82");
  }

  @Test
  void BIND_13_featureNeedsARootBinding() {
    Map<String, Object> noRootNode = richContent();
    mapAt(noRootNode, "features", "checkout.submission", "bindings").remove("global");
    assertFault(noRootNode, "BIND-13");
    Map<String, Object> noBindings = richContent();
    mapAt(noBindings, "features", "checkout.submission").put("bindings", obj());
    assertFault(noBindings, "BIND-13");
  }

  @Test
  void BUNDLE_81_bindingLevelMustBeOneOfBindingSettableAt() {
    Map<String, Object> content = richContent();
    mapAt(content, "features", "checkout.submission").put("bindingSettableAt", arr("global"));
    assertFault(content, "BUNDLE-81");
  }

  @Test
  void SCOPE_8_levelNameMayNotBeEmpty() {
    Map<String, Object> content = richContent();
    mapAt(content, "hierarchy").put("levels", arr("global", "", "account"));
    assertFault(content, "SCOPE-8");
  }

  @Test
  void SCOPE_8_axisNameMayNotBeEmpty() {
    Map<String, Object> content = richContent();
    mapAt(content, "hierarchy").put("axes", arr(""));
    assertFault(content, "SCOPE-8");
  }

  @Test
  void ENTITY_14_aBelongsToCycleIsRejected() {
    Map<String, Object> content = richContent();
    mapAt(content, "entities").put("customer", obj("belongsTo", arr("order"), "hasMany", arr()));
    assertFault(content, "ENTITY-14");
  }

  @Test
  void ENTITY_15_anEntityThatHasItselfAsAChildIsRejected() {
    Map<String, Object> content = richContent();
    mapAt(content, "entities").put("customer", obj("belongsTo", arr(), "hasMany", arr("customer")));
    assertFault(content, "ENTITY-15");
  }

  @Test
  void ENTITY_16_aParentThatIsAlsoItsChildIsRejected() {
    Map<String, Object> content = richContent();
    mapAt(content, "entities").put("customer", obj("belongsTo", arr("order"), "hasMany", arr("order")));
    assertFault(content, "ENTITY-16");
  }

  @Test
  void ENTITY_17_aCycleThroughBothKindsIsRejected() {
    Map<String, Object> content = richContent();
    mapAt(content, "entities").put("A", obj("belongsTo", arr("C"), "hasMany", arr("B")));
    mapAt(content, "entities").put("B", obj("belongsTo", arr(), "hasMany", arr("C")));
    mapAt(content, "entities").put("C", obj("belongsTo", arr(), "hasMany", arr()));
    assertFault(content, "ENTITY-17");
  }

  @Test
  void ENTITY_17_aChainOfEdgesWithoutACycleLoads() {
    Map<String, Object> content = richContent();
    mapAt(content, "entities").put("stage_one", obj("belongsTo", arr(), "hasMany", arr("stage_two")));
    mapAt(content, "entities").put("stage_two", obj("belongsTo", arr("stage_one"), "hasMany", arr("stage_three")));
    mapAt(content, "entities").put("stage_three", obj("belongsTo", arr("stage_two"), "hasMany", arr()));
    assertLoads(content);
  }

  @Test
  void SVC_11_denyNeedsANarrowingStrategy() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.allowed").put("combine", "override");
    assertFault(content, "SVC-11");
  }

  @Test
  void SVC_11_aNarrowingStrategyMayCarryDeny() {
    assertLoads(richContent());
  }

  @Test
  void SVC_12_denyUnderMinNeedsAMinimum() {
    assertFault(limitSettableAtService(), "SVC-12");
  }

  @Test
  void SVC_12_denyUnderMinWithAMinimumLoads() {
    Map<String, Object> content = limitSettableAtService();
    mapAt(content, "keys", "checkout.limit", "type").put("min", 0);
    assertLoads(content);
  }

  /**
   * Returns the valid content with {@code checkout.limit} settable at the service-sourced account level and denied on a
   * source failure, which makes its {@code min} strategy need a lower bound (SVC-12).
   */
  private static Map<String, Object> limitSettableAtService() {
    Map<String, Object> content = richContent();
    Map<String, Object> limit = mapAt(content, "keys", "checkout.limit");
    limit.put("settableAt", arr("global", "division", "account"));
    limit.put("onSourceFailure", "deny");
    return content;
  }
}
