package io.github.tierwise.core.internal.bundle;

import static io.github.tierwise.core.internal.bundle.BundleFixtures.bundle;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.richContent;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tierwise.core.internal.model.ConditionDef;
import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.KeyDef;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.RuleDef;
import io.github.tierwise.core.internal.model.SettingDef;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The writer puts every order-sensitive array into the order that the format requires, whatever order the model
 * holds, so that a model built by a compiler in any order still gives valid bytes (BUNDLE-54, BUNDLE-75,
 * BUNDLE-81, BUNDLE-90, BUNDLE-92, BUNDLE-96).
 */
class BundleWriterOrderTest {

  private static final String FEATURE = "checkout.submission";

  private static <T> List<T> reversed(List<T> items) {
    List<T> copy = new ArrayList<>(items);
    Collections.reverse(copy);
    return copy;
  }

  private static Model shuffled(Model model) {
    Map<String, EntityDef> entities = new LinkedHashMap<>(model.entities());
    // The edges are acyclic (ENTITY-17): the parents and the new child have no edges of their own, and the child
    // does not parent the customer. Each array holds two names, so the writer has an order to fix.
    entities.put("alpha", new EntityDef(List.of(), List.of()));
    entities.put("beta", new EntityDef(List.of(), List.of()));
    entities.put("invoice", new EntityDef(List.of(), List.of()));
    entities.put("customer", new EntityDef(List.of("beta", "alpha"), List.of("order", "invoice")));
    Map<String, KeyDef> keys = new LinkedHashMap<>(model.keys());
    KeyDef lead = keys.get("checkout.lead_hours");
    keys.put("checkout.lead_hours", new KeyDef(lead.type(), lead.combine(), lead.defaultValue(),
        reversed(lead.settableAt()), lead.combineAcrossAxes(), lead.onSourceFailure(), lead.kind()));
    FeatureDef feature = model.features().get(FEATURE);
    PolicyDef policy = feature.policies().get("credit");
    Map<String, SettingDef> settings = new LinkedHashMap<>(policy.settings());
    settings.put("allowed_states",
        new SettingDef(SettingDef.Kind.LIST_OF, "order.status", List.of("PENDING", "PAID")));
    List<RuleDef> rules = new ArrayList<>();
    for (RuleDef rule : policy.rules()) {
      List<ConditionDef> when = reversed(rule.when());
      rules.add(new RuleDef(rule.id(), rule.name(), when, rule.then()));
    }
    PolicyDef reorderedPolicy = new PolicyDef(settings, policy.defaultEffect(), reversed(policy.requiredFacts()), rules);
    FeatureDef reorderedFeature = new FeatureDef(feature.decidedPer(), feature.returns(), feature.hitPolicy(),
        feature.ifFactMissing(), reversed(feature.bindingSettableAt()), Map.of("credit", reorderedPolicy),
        feature.bindings());
    return new Model(model.bundleFormatVersion(), model.specVersion(), model.hierarchy(), entities, model.facts(), keys,
        model.values(), Map.of(FEATURE, reorderedFeature));
  }

  @Test
  void BUNDLE_75_BUNDLE_54_BUNDLE_81_BUNDLE_90_BUNDLE_92_BUNDLE_96_writerNormalisesOrderSensitiveArrays() {
    Model model = BundleReader.read(bundle(richContent())).model();
    // The reader enforces the orders, so reading the written bytes back proves that the writer produced them.
    Model back = BundleReader.read(BundleWriter.write(shuffled(model)).bytes()).model();
    assertEquals(List.of("alpha", "beta"), back.entities().get("customer").belongsTo());
    assertEquals(List.of("invoice", "order"), back.entities().get("customer").hasMany());
    assertEquals(List.of("global", "division", "seller"), back.keys().get("checkout.lead_hours").settableAt());
    FeatureDef feature = back.features().get(FEATURE);
    assertEquals(List.of("global", "division"), feature.bindingSettableAt());
    PolicyDef policy = feature.policies().get("credit");
    assertEquals(List.of("customer.active", "order.status"), policy.requiredFacts());
    assertEquals(List.of("PAID", "PENDING"), policy.settings().get("allowed_states").defaultValue());
    List<ConditionDef> both = policy.rules().get(2).when();
    assertEquals(List.of("customer.active", "order.status"), both.stream().map(ConditionDef::fact).toList());
  }
}
