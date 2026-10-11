package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.internal.model.Returns;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests the {@code expect} of an example against {@code returns} (POL-40, FEAT-9, FEAT-13, FEAT-14). */
class ExampleExpectTest {

  private static final Returns ENUM = Returns.enumeration(List.of("ALLOW", "DENY"), false);
  private static final Returns NULLABLE = Returns.enumeration(List.of("ALLOW", "DENY"), true);
  private static final Returns OBJECT = Returns.object(Map.of(
      "method", TypeDecl.enumeration(List.of("GROUND", "NEXT_DAY")),
      "days", TypeDecl.integer(1L, 30L)));

  private static PNode expect(String yaml) {
    return CheckTestSupport.root(yaml).get("expect").orElseThrow();
  }

  @Test
  void POL_40_anEffectThatIsNotAValueOfReturnsIsAnEffectMismatch() {
    Findings findings = new Findings();
    ExampleExpect.Parsed parsed = ExampleExpect.parse(expect("expect: MAYBE\n"), ENUM, new ValueValidator(findings));
    assertFalse(parsed.valid());
    CheckTestSupport.assertFindings(findings, "E_EFFECT_MISMATCH 1 [expect]");
  }

  @Test
  void POL_40_aValidEffectIsItsPlainValue() {
    Findings findings = new Findings();
    ExampleExpect.Parsed parsed = ExampleExpect.parse(expect("expect: ALLOW\n"), ENUM, new ValueValidator(findings));
    assertTrue(parsed.valid());
    assertEquals("ALLOW", parsed.value());
    assertEquals(List.of(), CheckTestSupport.descriptions(findings));
  }

  @Test
  void FEAT_9_NullIsAnEffectOnlyForANullableEnum() {
    Findings notNullable = new Findings();
    assertFalse(ExampleExpect.parse(expect("expect: null\n"), ENUM, new ValueValidator(notNullable)).valid());
    CheckTestSupport.assertFindings(notNullable, "E_EFFECT_MISMATCH 1 [expect]");

    Findings nullable = new Findings();
    ExampleExpect.Parsed parsed = ExampleExpect.parse(expect("expect: null\n"), NULLABLE,
        new ValueValidator(nullable));
    assertTrue(parsed.valid());
    assertEquals(null, parsed.value());
    assertEquals(List.of(), CheckTestSupport.descriptions(nullable));
  }

  @Test
  void POL_40_anObjectEffectIsItsPlainMapAndAFaultyFieldIsAnEffectMismatch() {
    Findings valid = new Findings();
    ExampleExpect.Parsed parsed = ExampleExpect.parse(expect("expect: { days: 1, method: NEXT_DAY }\n"), OBJECT,
        new ValueValidator(valid));
    assertTrue(parsed.valid());
    assertEquals(Map.of("method", "NEXT_DAY", "days", 1L), parsed.value());

    Findings faulty = new Findings();
    assertFalse(ExampleExpect.parse(expect("expect: { days: 99, method: NEXT_DAY }\n"), OBJECT,
        new ValueValidator(faulty)).valid());
    CheckTestSupport.assertFindings(faulty, "E_EFFECT_MISMATCH 1 [expect, days]");
  }
}
