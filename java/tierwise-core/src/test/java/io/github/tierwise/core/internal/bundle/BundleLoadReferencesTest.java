package io.github.tierwise.core.internal.bundle;

import static io.github.tierwise.core.internal.bundle.BundleFixtures.arr;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.assertFault;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.assertLoads;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.mapAt;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.obj;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.richContent;

import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Load bundle, step 4 of EVAL-14: the reference and node-key checks of BUNDLE-120, BUNDLE-121, BUNDLE-127,
 * BUNDLE-129 and BUNDLE-133, one test for each clause, covering every case that the clause names. No invalid-bundle
 * file holds these faults in v0.1 (ADR 0016), so these tests are their enforcers (spec/conformance/enforcers.d/E7.yaml).
 * Each case edits one part of a valid bundle, and the digest is recomputed.
 */
class BundleLoadReferencesTest {

  private static final String FEATURE = "checkout.submission";

  private static Map<String, Object> feature(Map<String, Object> content) {
    return mapAt(content, "features", FEATURE);
  }

  private static Map<String, Object> policy(Map<String, Object> content) {
    return mapAt(content, "features", FEATURE, "policies", "credit");
  }

  /** Asserts that the edit of a fresh valid content raises E_BUNDLE_INTEGRITY. */
  private static void rejects(Consumer<Map<String, Object>> edit) {
    Map<String, Object> content = richContent();
    edit.accept(content);
    assertFault(content);
  }

  @Test
  void richContentLoadsSoEachCaseHasOneFault() {
    assertLoads(richContent());
  }

  @Test
  void BUNDLE_120_undeclaredFactInAConditionASettingOrRequiredFactsIsRejected() {
    rejects(c -> mapAt(policy(c), "rules", 0, "when", 0).put("fact", "customer.missing"));
    rejects(c -> mapAt(policy(c), "settings", "check_active").put("valueOf", "customer.missing"));
    rejects(c -> mapAt(policy(c), "settings", "allowed_states").put("listOf", "order.missing"));
    rejects(c -> policy(c).put("requiredFacts", arr("customer.active", "customer.missing", "order.status")));
  }

  @Test
  void BUNDLE_121_undeclaredEntityInDecidedPerAFactEntityBelongsToOrHasManyIsRejected() {
    rejects(c -> feature(c).put("decidedPer", "invoice"));
    rejects(c -> mapAt(c, "facts").put("invoice.paid", obj("entity", "invoice", "type", obj("type", "boolean"))));
    rejects(c -> mapAt(c, "entities", "order").put("belongsTo", arr("customer", "invoice")));
    rejects(c -> mapAt(c, "entities", "customer").put("hasMany", arr("invoice", "order")));
  }

  @Test
  void BUNDLE_127_undeclaredScopeNameOrNonLevelInBindingsOrBindingSettableAtIsRejected() {
    rejects(c -> mapAt(c, "keys", "checkout.limit").put("settableAt", arr("global", "division", "region")));
    rejects(c -> feature(c).put("bindingSettableAt", arr("global", "division", "region")));
    rejects(c -> mapAt(c, "values").put("region", obj("east", obj("checkout.limit", 1))));
    rejects(c -> mapAt(feature(c), "bindings").put("region",
        obj("east", obj("policy", "credit", "settings", obj()))));
    rejects(c -> mapAt(feature(c), "bindings").put("seller", obj("acme", obj("policy", "credit", "settings", obj()))));
    rejects(c -> feature(c).put("bindingSettableAt", arr("global", "division", "seller")));
  }

  @Test
  void BUNDLE_129_nodeKeyOutsideAClosedCatalogEmptyNonRootKeyOrEmptyNodeIdIsRejected() {
    rejects(c -> mapAt(c, "values", "division").put("west", obj("checkout.limit", 1)));
    rejects(c -> mapAt(feature(c), "bindings", "division").put("west", obj("policy", "credit", "settings", obj())));
    rejects(c -> mapAt(c, "values", "division").put("", obj("checkout.limit", 1)));
    rejects(c -> mapAt(c, "values", "seller").put("", obj("checkout.lead_hours", 1)));
    rejects(c -> mapAt(feature(c), "bindings", "division").put("", obj("policy", "credit", "settings", obj())));
    rejects(c -> mapAt(c, "hierarchy", "scopes", "division").put("nodes", arr("", "north", "south")));
  }

  @Test
  void BUNDLE_133_nodesOrListOfValueThatRepeatsAnElementIsRejected() {
    rejects(c -> mapAt(c, "hierarchy", "scopes", "division").put("nodes", arr("north", "north", "south")));
    rejects(c -> mapAt(policy(c), "settings", "allowed_states").put("default", arr("PAID", "PAID")));
    rejects(c -> mapAt(feature(c), "bindings", "division", "south", "settings")
        .put("allowed_states", arr("PAID", "PAID", "PENDING")));
  }
}
