package io.github.tierwise.core.internal.yaml;

import static io.github.tierwise.core.internal.yaml.YamlTestSupport.at;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.assertNoFindings;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.load;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.root;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

/** Plain scalars resolve under the YAML 1.2 core schema (SRC-1, TYPE-15, TYPE-16). */
class CoreSchemaTest {

  private static PScalar.Kind kindOf(String text) {
    return CoreSchema.resolve(text).kind();
  }

  @Test
  void nullTokensOfTheCoreSchemaAreNull() {
    for (String text : new String[] {"", "~", "null", "Null", "NULL"}) {
      assertEquals(PScalar.Kind.NULL, kindOf(text), text);
      assertEquals(null, CoreSchema.resolve(text).value(), text);
    }
  }

  @Test
  void onlyTheCoreSpellingsAreBooleans() {
    assertEquals(Boolean.TRUE, CoreSchema.resolve("true").value());
    assertEquals(Boolean.TRUE, CoreSchema.resolve("True").value());
    assertEquals(Boolean.TRUE, CoreSchema.resolve("TRUE").value());
    assertEquals(Boolean.FALSE, CoreSchema.resolve("false").value());
    assertEquals(Boolean.FALSE, CoreSchema.resolve("False").value());
    assertEquals(Boolean.FALSE, CoreSchema.resolve("FALSE").value());
  }

  @Test
  void yesNoOnOffAndYAreStringsUnderTheCoreSchema() {
    for (String text : new String[] {"yes", "no", "on", "off", "y", "Yes", "NO", "On"}) {
      assertEquals(PScalar.Kind.STRING, kindOf(text), text);
      assertEquals(text, CoreSchema.resolve(text).value(), text);
    }
  }

  @Test
  void integersAreDecimalHexadecimalOrOctalTokens() {
    assertEquals(1L, CoreSchema.resolve("+1").value());
    assertEquals(-1L, CoreSchema.resolve("-1").value());
    assertEquals(0L, CoreSchema.resolve("-0").value());
    assertEquals(31L, CoreSchema.resolve("0x1F").value());
    assertEquals(15L, CoreSchema.resolve("0o17").value());
    assertEquals(PScalar.Kind.INTEGER, kindOf("0x1F"));
  }

  @Test
  void tokensOutsideTheCoreIntegerFormsAreStrings() {
    for (String text : new String[] {"0b101", "1_000", "0x", "0xG1", "0o8", "--1"}) {
      assertEquals(PScalar.Kind.STRING, kindOf(text), text);
    }
  }

  @Test
  void floatsResolveWhenTheWholeTokenMatchesTheCoreForm() {
    assertEquals(1.5, CoreSchema.resolve("1.5").value());
    assertEquals(0.5, CoreSchema.resolve(".5").value());
    assertEquals(1000.0, CoreSchema.resolve("1e3").value());
    assertEquals(PScalar.Kind.FLOAT, kindOf("1e3"));
    assertEquals(Double.NEGATIVE_INFINITY, CoreSchema.resolve("-.inf").value());
    assertEquals(Double.POSITIVE_INFINITY, CoreSchema.resolve(".Inf").value());
    assertEquals(PScalar.Kind.FLOAT, kindOf(".NAN"));
  }

  @Test
  void integersOutsideTheLongRangeAreBigIntegersAndLongRangeIntegersAreKept() {
    assertEquals(new BigInteger("99999999999999999999999"), CoreSchema.resolve("99999999999999999999999").value());
    assertEquals(1152921504606846976L, CoreSchema.resolve("1152921504606846976").value());
  }

  @Test
  void quotedAndBlockScalarsAreStringsWhateverTheyLookLike() {
    PMap root = root(load("a: \"true\"\nb: '1'\nc: |\n  2\nd: null\n"));
    assertEquals("true", scalar(at(root, "a")).value());
    assertEquals("1", scalar(at(root, "b")).value());
    assertEquals("2\n", scalar(at(root, "c")).value());
    assertEquals(PScalar.Kind.NULL, scalar(at(root, "d")).kind());
    assertNoFindings(load("a: \"true\"\n"));
  }

  @Test
  void theNoTrapKeyIsAStringKey() {
    PMap root = root(load("no: 1\nyes: 2\n"));
    assertEquals(PScalar.Kind.INTEGER, scalar(at(root, "no")).kind());
    assertEquals(2L, scalar(at(root, "yes")).value());
    assertNoFindings(load("no: 1\nyes: 2\n"));
  }
}
