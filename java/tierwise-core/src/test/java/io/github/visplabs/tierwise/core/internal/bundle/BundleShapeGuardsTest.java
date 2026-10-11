package io.github.visplabs.tierwise.core.internal.bundle;

import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.arr;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.assertFault;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.assertLoads;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.listAt;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.mapAt;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.obj;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What a bundle cannot carry, so that it cannot reach a result: a preset or a project setting (PROV-2), and a
 * condition literal of another type than its fact, which a comparison would otherwise have to coerce (RULE-38).
 */
class BundleShapeGuardsTest {

  @Test
  void PROV_2_aBundleCannotCarryAPresetOrAProjectSetting() {
    assertLoads(BundleFixtures.richContent());
    for (String member : List.of("presets", "settings", "lint", "naming", "resultShape")) {
      Map<String, Object> content = BundleFixtures.richContent();
      content.put(member, obj());
      assertFault(content);
    }
    Map<String, Object> presetOnKey = BundleFixtures.richContent();
    mapAt(presetOnKey, "keys", "checkout.limit").put("preset", "x");
    assertFault(presetOnKey);
    Map<String, Object> settingOnFeature = BundleFixtures.richContent();
    mapAt(settingOnFeature, "features", "checkout.submission").put("provenance", false);
    assertFault(settingOnFeature);
  }

  @Test
  @SuppressWarnings("unchecked")
  void RULE_38_aConditionLiteralOfAnotherTypeThanItsFactIsNotLoaded() {
    for (Object literal : List.of("true", 1, arr(true))) {
      Map<String, Object> content = BundleFixtures.richContent();
      List<Object> rules = listAt(content, "features", "checkout.submission", "policies", "credit", "rules");
      Map<String, Object> condition = (Map<String, Object>) ((List<Object>) ((Map<String, Object>) rules.get(0))
          .get("when")).get(0);
      condition.put("value", literal);
      assertFault(content);
    }
    for (Object literal : List.of(true, 1, arr("PAID"))) {
      Map<String, Object> content = BundleFixtures.richContent();
      List<Object> rules = listAt(content, "features", "checkout.submission", "policies", "credit", "rules");
      Map<String, Object> condition = (Map<String, Object>) ((List<Object>) ((Map<String, Object>) rules.get(2))
          .get("when")).get(1);
      condition.put("value", literal);
      assertFault(content);
    }
    Map<String, Object> setting = BundleFixtures.richContent();
    mapAt(setting, "features", "checkout.submission", "policies", "credit", "settings", "check_active")
        .put("default", "true");
    assertFault(setting);
  }
}
