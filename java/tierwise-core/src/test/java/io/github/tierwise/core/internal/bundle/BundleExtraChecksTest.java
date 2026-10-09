package io.github.tierwise.core.internal.bundle;

import static io.github.tierwise.core.internal.bundle.BundleFixtures.arr;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.assertFault;
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
}
