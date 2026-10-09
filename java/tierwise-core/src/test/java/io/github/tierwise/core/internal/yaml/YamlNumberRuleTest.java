package io.github.tierwise.core.internal.yaml;

import static io.github.tierwise.core.internal.yaml.YamlTestSupport.assertFinding;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.assertNoFindings;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.load;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.loadManifest;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.onlyFinding;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.root;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Floats that plain data cannot hold (the rule of the reference loader, see VEC-35) and integers that
 * the engine range check will see (TYPE-18, TYPE-20).
 */
class YamlNumberRuleTest {

  @Test
  void nonIntegralFloatIsKeptAsAFloatForTheChecks() {
    PMap root = root(load("x: 1.5\n"));
    PScalar value = scalar(root.get("x").orElseThrow());
    assertEquals(PScalar.Kind.FLOAT, value.kind());
    assertEquals(1.5, value.value());
    assertNoFindings(load("x: 1.5\n"));
  }

  @Test
  void integralFloatTokensAreReportedAsTypeMismatchAndKeptAsFloats() {
    for (String token : new String[] {"1.0", "1e3", "-0.0", "1.00000000000000000001"}) {
      YamlLoadResult result = load("x: " + token + "\n");
      onlyFinding(result, Code.E_TYPE_MISMATCH);
      PScalar value = scalar(root(result).get("x").orElseThrow());
      assertEquals(PScalar.Kind.FLOAT, value.kind(), token);
    }
  }

  @Test
  void integralFloatIsReportedAtTheEntryThatHoldsIt() {
    YamlLoadResult result = load("a: 1\nb: 2.0\n");
    assertFinding(onlyFinding(result, Code.E_TYPE_MISMATCH), Code.E_TYPE_MISMATCH, List.of("b"), 2);
  }

  @Test
  void nonFiniteFloatTokensAreReportedAsTypeMismatch() {
    for (String token : new String[] {".inf", "-.inf", ".nan", ".NaN", "1e400"}) {
      onlyFinding(load("x: " + token + "\n"), Code.E_TYPE_MISMATCH);
    }
  }

  @Test
  void integralFloatInTheManifestIsEManifestAndAPresentSpecVersionIsNotMissing() {
    YamlLoadResult result = loadManifest("specVersion: 1.0\n");
    assertFinding(onlyFinding(result, Code.E_MANIFEST), Code.E_MANIFEST, List.of("specVersion"), 1);
    assertTrue(root(result).has("specVersion"));
  }

  @Test
  void integerBeyondTheEngineRangeIsKeptForTheRangeCheck() {
    PMap root = root(load("x: 9007199254740993\n"));
    assertEquals(9007199254740993L, scalar(root.get("x").orElseThrow()).value());
    assertNoFindings(load("x: 9007199254740993\n"));
  }

  @Test
  void integerBeyondLongIsABigIntegerUpToTwentyDigitsAndAMarkerBeyondThatWithoutAFinding() {
    YamlLoadResult result = load("x: 99999999999999999999\ny: 99999999999999999999999\n");
    assertNoFindings(result);
    PScalar value = scalar(root(result).get("x").orElseThrow());
    assertEquals(PScalar.Kind.INTEGER, value.kind());
    assertEquals(new BigInteger("99999999999999999999"), value.value());
    PScalar long23 = scalar(root(result).get("y").orElseThrow());
    assertEquals(PScalar.Kind.INTEGER, long23.kind());
    assertEquals(OutOfRangeInteger.INSTANCE, long23.value());
  }
}
