package io.github.visplabs.tierwise.core.differential;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.LoadedBundle;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.core.internal.bundle.BundleWriter;
import io.github.visplabs.tierwise.core.internal.model.KeyDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Checks the generator of the differential run without the cross-language run, so that a normal build catches a
 * generator that drifts from the rules it is meant to follow. These tests are fast, and they are tagged with the
 * differential harness they support.
 */
@Tag("differential")
class DiffGeneratorTest {

  private static final int SEEDS = 100;

  @Test
  void sameSeedGivesTheSameBundleBytes() {
    byte[] first = BundleWriter.write(DiffModelBuilder.build(new Random(42L))).bytes();
    byte[] second = BundleWriter.write(DiffModelBuilder.build(new Random(42L))).bytes();
    assertArrayEquals(first, second);
  }

  @Test
  void everyModelOfTheSeedRangeIsAValidBundle() {
    for (long seed = 0; seed < SEEDS; seed++) {
      byte[] bytes = BundleWriter.write(DiffModelBuilder.build(new Random(seed))).bytes();
      LoadedBundle loaded = Tierwise.loadBundle(bytes);
      assertTrue(loaded.bundleVersion().startsWith("sha256:"), "seed " + seed);
    }
  }

  @Test
  void orderDependentKeysHaveADefaultAndCommutativeKeysDoNot() {
    for (long seed = 0; seed < SEEDS; seed++) {
      for (KeyDef key : DiffModelBuilder.build(new Random(seed)).keys().values()) {
        boolean orderDependent = "override".equals(key.combine()) || "deep_merge".equals(key.combine());
        assertEquals(orderDependent, key.defaultValue().isPresent(), "seed " + seed + " " + key.combine());
      }
    }
  }

  @Test
  void denyAppearsOnlyForNarrowingStrategiesAtServiceLevels() {
    for (long seed = 0; seed < SEEDS; seed++) {
      Model model = DiffModelBuilder.build(new Random(seed));
      for (KeyDef key : model.keys().values()) {
        if (key.onSourceFailure().isPresent() && "deny".equals(key.onSourceFailure().get())) {
          assertTrue(List.of("all", "intersect", "min").contains(key.combine()), "seed " + seed);
        }
        boolean service = key.settableAt().stream().anyMatch(scope -> isService(model, scope));
        assertEquals(service, key.onSourceFailure().isPresent(), "seed " + seed);
      }
    }
  }

  @Test
  void aBindingExistsAtTheRootOfEveryFeature() {
    for (long seed = 0; seed < SEEDS; seed++) {
      Model model = DiffModelBuilder.build(new Random(seed));
      for (var feature : model.features().values()) {
        Map<String, ?> root = feature.bindings().get(model.hierarchy().rootLevel());
        assertNotNull(root, "seed " + seed);
        assertTrue(root.containsKey(""), "seed " + seed);
      }
    }
  }

  @Test
  void codePointOrderDiffersFromUtf16OrderOnTheGeneratedPool() {
    assertTrue(DiffNames.compareCodePoints("�", "😀") < 0);
    assertTrue("�".compareTo("😀") > 0);
  }

  @Test
  void theInputOfACaseSurvivesItsCanonicalJsonForm() {
    Model model = DiffModelBuilder.build(new Random(5L));
    byte[] bundle = BundleWriter.write(model).bytes();
    Map<String, Object> input = DiffInputs.draw(model, bundle, new Random(6L));
    assertEquals(DiffRunner.canonicalText(input), DiffRunner.canonicalText(DiffRunner.wire(input)));
  }

  @Test
  void everyRecordHasEitherAResultOrAnErrorCode() {
    for (long seed = 0; seed < 20; seed++) {
      Model model = DiffModelBuilder.build(new Random(seed));
      byte[] bundle = BundleWriter.write(model).bytes();
      Map<String, Object> input = DiffInputs.draw(model, bundle, new Random(seed + 1000));
      Map<String, Object> expect = DiffRunner.map(DiffRunner.record("c", seed, input).get("expect"));
      assertEquals(1, expect.size(), "seed " + seed);
      assertTrue(expect.containsKey("result") || expect.containsKey("error"), "seed " + seed);
      assertFalse(expect.containsKey("result") && expect.containsKey("error"), "seed " + seed);
    }
  }

  private static boolean isService(Model model, String scope) {
    ScopeDef def = model.hierarchy().scope(scope);
    return def != null && def.isServiceSourced();
  }
}
