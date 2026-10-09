package io.github.tierwise.core.internal.bundle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The value rules of section 4 as a bundle needs them (BUNDLE-131, BUNDLE-135, BUNDLE-134) and the merge modes. */
class ValueCheckerTest {

  private static final ValueChecker.Mode COMPLETE = ValueChecker.Mode.COMPLETE;
  private static final ValueChecker.Mode PARTIAL = ValueChecker.Mode.PARTIAL;

  private static TypeDecl string(String format) {
    return new TypeDecl("string", Optional.empty(), Optional.empty(), Optional.of(format), List.of(), List.of(),
        Optional.empty(), Map.of(), false);
  }

  private static TypeDecl field(TypeDecl type, boolean optional) {
    return optional ? type.asOptional() : type;
  }

  private static void accepts(TypeDecl type, Object value) {
    assertDoesNotThrow(() -> ValueChecker.check(type, value, COMPLETE, "value"));
  }

  private static void rejects(TypeDecl type, Object value) {
    assertThrows(BundleFault.class, () -> ValueChecker.check(type, value, COMPLETE, "value"));
  }

  @Test
  void TYPE_12_booleanValueIsABoolean() {
    accepts(TypeDecl.simple("boolean"), true);
    rejects(TypeDecl.simple("boolean"), "true");
    rejects(TypeDecl.simple("boolean"), 1L);
  }

  @Test
  void TYPE_19_integerBoundsAreInclusive() {
    TypeDecl bounded = TypeDecl.integer(0L, 10L);
    accepts(bounded, 0L);
    accepts(bounded, 10L);
    rejects(bounded, -1L);
    rejects(bounded, 11L);
  }

  @Test
  void TYPE_17_TYPE_18_integerIsALongWithinTheSafeRange() {
    accepts(TypeDecl.integer(null, null), 9007199254740991L);
    rejects(TypeDecl.integer(null, null), 9007199254740992L);
    rejects(TypeDecl.integer(null, null), 1.0);
  }

  @Test
  void NORM_9_decimalValueIsACanonicalDecimalString() {
    TypeDecl decimal = TypeDecl.simple("decimal");
    for (String ok : List.of("0", "-1.5", "10", "0.25", "-0.5")) {
      accepts(decimal, ok);
    }
    for (String bad : List.of("-0", "01", "1.50", "1.", ".5", "+1", "1e3", "")) {
      rejects(decimal, bad);
    }
  }

  @Test
  void TYPE_33_timeHhmmFormat() {
    accepts(string("time_hhmm"), "00:00");
    accepts(string("time_hhmm"), "23:59");
    rejects(string("time_hhmm"), "24:00");
    rejects(string("time_hhmm"), "9:00");
    rejects(string("time_hhmm"), "12:60");
  }

  @Test
  void TYPE_37_ianaTzIsCheckedForSyntaxOnly() {
    accepts(string("iana_tz"), "Europe/Paris");
    accepts(string("iana_tz"), "Etc/GMT+5");
    rejects(string("iana_tz"), "Europe//Paris");
    rejects(string("iana_tz"), "Europe Paris");
    rejects(string("iana_tz"), "");
  }

  @Test
  void TYPE_29_TYPE_30_stringWithoutFormatAcceptsEveryString() {
    accepts(TypeDecl.simple("string"), "");
    accepts(TypeDecl.simple("string"), "any text at all");
  }

  @Test
  void TYPE_44_enumValueIsOneOfItsValues() {
    TypeDecl enumeration = TypeDecl.enumeration(List.of("PAID", "PENDING"));
    accepts(enumeration, "PAID");
    rejects(enumeration, "paid");
  }

  @Test
  void TYPE_47_TYPE_48_BUNDLE_74_setMembersAreInUniverseOrder() {
    TypeDecl set = TypeDecl.set(List.of("GROUND", "PICKUP", "NEXT_DAY"));
    accepts(set, List.of("GROUND", "NEXT_DAY"));
    accepts(set, List.of());
    rejects(set, List.of("NEXT_DAY", "GROUND"));
    rejects(set, List.of("AIR"));
  }

  @Test
  void BUNDLE_134_setMembersAreNotRepeated() {
    rejects(TypeDecl.set(List.of("A", "B")), List.of("A", "A"));
  }

  @Test
  void TYPE_56_listElementsAreValidForTheItemType() {
    TypeDecl list = TypeDecl.list(TypeDecl.integer(0L, 5L));
    accepts(list, List.of(1L, 1L, 5L));
    rejects(list, List.of(1L, 6L));
  }

  @Test
  void TYPE_62_completeObjectHasEveryRequiredField() {
    TypeDecl object = TypeDecl.object(fields(false));
    accepts(object, map("open", "08:00", "close", "18:00"));
    rejects(object, map("open", "08:00"));
  }

  @Test
  void MERGE_11_MERGE_12_partialModeAllowsMissingRequiredFieldsAtEveryDepth() {
    TypeDecl object = TypeDecl.object(fields(false));
    assertDoesNotThrow(() -> ValueChecker.check(object, map("open", "08:00"), PARTIAL, "value"));
    assertDoesNotThrow(() -> ValueChecker.check(object, map(), PARTIAL, "value"));
  }

  @Test
  void TYPE_63_optionalFieldIsNeverRequired() {
    TypeDecl object = TypeDecl.object(Map.of("slot", field(TypeDecl.integer(null, null), true)));
    accepts(object, map());
  }

  @Test
  void TYPE_64_objectFieldMustBeDeclared() {
    TypeDecl object = TypeDecl.object(fields(false));
    rejects(object, map("open", "08:00", "close", "18:00", "extra", 1L));
  }

  @Test
  void TYPE_76_nullIsNeverAValue() {
    rejects(TypeDecl.simple("boolean"), null);
    rejects(TypeDecl.object(fields(true)), null);
  }

  private static Map<String, TypeDecl> fields(boolean allOptional) {
    Map<String, TypeDecl> out = new LinkedHashMap<>();
    out.put("open", field(string("time_hhmm"), allOptional));
    out.put("close", field(string("time_hhmm"), allOptional));
    return out;
  }

  private static Map<String, Object> map(Object... pairs) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      out.put((String) pairs[i], pairs[i + 1]);
    }
    return out;
  }
}
