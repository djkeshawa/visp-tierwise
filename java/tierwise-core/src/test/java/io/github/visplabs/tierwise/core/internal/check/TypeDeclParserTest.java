package io.github.visplabs.tierwise.core.internal.check;

import static io.github.visplabs.tierwise.core.internal.check.CheckTestSupport.assertFindings;
import static io.github.visplabs.tierwise.core.internal.check.CheckTestSupport.root;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.internal.check.TypeDeclParser.Position;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The type-declaration grammar of section 4: TYPE-1 to TYPE-11, TYPE-13, TYPE-14, TYPE-22, TYPE-31 and TYPE-40 to TYPE-69. */
class TypeDeclParserTest {

  private final Findings findings = new Findings();
  private final TypeDeclParser parser = new TypeDeclParser(findings);

  private Optional<TypeDecl> parse(String yaml, Position position) {
    return parser.parse(root(yaml), position);
  }

  /** Parses with a fresh accumulator and returns the sorted descriptions of the faults; the result must be empty. */
  private static List<String> faults(String yaml, Position position) {
    Findings local = new Findings();
    assertTrue(new TypeDeclParser(local).parse(root(yaml), position).isEmpty(), yaml);
    return CheckTestSupport.descriptions(local);
  }

  private TypeDecl ok(String yaml) {
    Optional<TypeDecl> decl = parse(yaml, Position.KEY);
    assertTrue(decl.isPresent(), "findings: " + CheckTestSupport.descriptions(findings));
    assertTrue(findings.size() == 0);
    return decl.get();
  }

  // ---- the eight types -----------------------------------------------------------------------------

  @Test
  void TYPE_2_theEightTypesAreAccepted() {
    assertEquals(TypeDecl.simple("boolean"), ok("type: boolean\n"));
    assertEquals(TypeDecl.simple("decimal"), ok("type: decimal\n"));
    assertEquals(TypeDecl.simple("string"), ok("type: string\n"));
    assertEquals(TypeDecl.integer(null, null), ok("type: integer\n"));
    assertEquals(TypeDecl.enumeration(List.of("A", "B")), ok("type: enum\nvalues: [A, B]\n"));
    assertEquals(TypeDecl.set(List.of("x", "y")), ok("type: set\nuniverse: [x, y]\n"));
    assertEquals(TypeDecl.list(TypeDecl.simple("string")), ok("type: list\nitems: {type: string}\n"));
    assertEquals(TypeDecl.object(Map.of("a", TypeDecl.simple("boolean"))), ok("type: object\nfields:\n  a: {type: boolean}\n"));
  }

  @Test
  void TYPE_2_theTypeNameIsCaseSensitiveAndMustBeAString() {
    for (String bad : new String[] {"Boolean", "BOOLEAN", "int", "\"\"", "5", "true", "null", "[boolean]", "{a: b}", "~"}) {
      Findings local = new Findings();
      assertTrue(new TypeDeclParser(local).parse(root("k: x\ntype: " + bad + "\n"), Position.KEY).isEmpty(), bad);
      CheckTestSupport.assertFindings(local, "E_INVALID_DECLARATION 2 [type]");
    }
  }

  @Test
  void TYPE_1_aDeclarationThatIsNotAMapIsEInvalidDeclarationAtThatNode() {
    PMap holder = root("a: boolean\nb: [x]\nc:\n");
    for (String name : new String[] {"a", "b", "c"}) {
      assertTrue(parser.parse(holder.get(name).orElseThrow(), Position.KEY).isEmpty());
    }
    assertFindings(findings, "E_INVALID_DECLARATION 1 [a]", "E_INVALID_DECLARATION 2 [b]", "E_INVALID_DECLARATION 3 [c]");
  }

  @Test
  void TYPE_4_aDeclarationWithoutTypeIsETypeRequiredAtTheDeclaration() {
    PMap holder = root("key:\n  combine: any\nitems: {}\nfield:\n  optional: true\n");
    for (String name : new String[] {"key", "items", "field"}) {
      assertTrue(parser.parse(holder.get(name).orElseThrow(), Position.KEY).isEmpty());
    }
    assertFindings(findings, "E_TYPE_REQUIRED 1 [key]", "E_TYPE_REQUIRED 3 [items]", "E_TYPE_REQUIRED 4 [field]");
  }

  @Test
  void TYPE_4_aNestedDeclarationWithoutTypeIsReportedAtThatDeclaration() {
    assertTrue(parse("type: list\nitems:\n  min: 1\n", Position.KEY).isEmpty());
    assertFindings(findings, "E_TYPE_REQUIRED 2 [items]");
  }

  // ---- type members --------------------------------------------------------------------------------

  @Test
  void TYPE_7_aMemberThatTheTypeDoesNotDefineIsEInvalidDeclarationAtTheMember() {
    assertEquals(List.of("E_INVALID_DECLARATION 2 [min]"), faults("type: boolean\nmin: 1\n", Position.KEY));
    assertEquals(List.of("E_INVALID_DECLARATION 2 [values]"), faults("type: integer\nvalues: [a]\n", Position.KEY));
    assertEquals(List.of("E_INVALID_DECLARATION 2 [items]"), faults("type: string\nitems: {type: string}\n", Position.KEY));
    assertEquals(List.of("E_INVALID_DECLARATION 2 [format]"), faults("type: decimal\nformat: iana_tz\n", Position.KEY));
    assertEquals(List.of("E_INVALID_DECLARATION 2 [universe]"), faults("type: enum\nuniverse: [a]\nvalues: [a]\n", Position.KEY));
    assertEquals(List.of("E_INVALID_DECLARATION 2 [values]"), faults("type: set\nvalues: [a]\nuniverse: [a]\n", Position.KEY));
    assertEquals(List.of("E_INVALID_DECLARATION 2 [fields]"), faults("type: list\nfields: {}\nitems: {type: string}\n", Position.KEY));
    assertEquals(List.of("E_INVALID_DECLARATION 2 [items]"),
        faults("type: object\nitems: {type: string}\nfields:\n  a: {type: string}\n", Position.KEY));
  }

  @Test
  void TYPE_8_optionalIsAllowedOnlyInAnObjectFieldDeclaration() {
    assertEquals(List.of("E_INVALID_DECLARATION 2 [optional]"), faults("type: string\noptional: true\n", Position.KEY));
    assertEquals(List.of("E_INVALID_DECLARATION 2 [optional]"), faults("type: string\noptional: true\n", Position.FACT));
    assertEquals(List.of("E_INVALID_DECLARATION 2 [optional]"), faults("type: string\noptional: true\n", Position.ITEMS));
    assertEquals(List.of("E_INVALID_DECLARATION 4 [items, optional]"),
        faults("type: list\nitems:\n  type: string\n  optional: false\n", Position.KEY));
  }

  @Test
  void TYPE_9_optionalMustBeABoolean() {
    for (String bad : new String[] {"\"true\"", "1", "null", "yes", "[true]"}) {
      Findings local = new Findings();
      assertTrue(new TypeDeclParser(local).parse(root("type: string\noptional: " + bad + "\n"), Position.FIELD).isEmpty(), bad);
      CheckTestSupport.assertFindings(local, "E_INVALID_DECLARATION 2 [optional]");
    }
  }

  @Test
  void TYPE_10_aFieldIsRequiredUnlessOptionalIsTrue() {
    TypeDecl object = ok("type: object\nfields:\n  a: {type: string}\n  b: {type: string, optional: false}\n  c: {type: string, optional: true}\n");
    assertEquals(false, object.fields().get("a").optional());
    assertEquals(false, object.fields().get("b").optional());
    assertEquals(true, object.fields().get("c").optional());
  }

  @Test
  void TYPE_11_aMemberOfAFieldOrItemsDeclarationThatIsNotATypeMemberIsMalformedButAKeyMayHaveItsOwn() {
    TypeDecl key = ok("type: boolean\ncombine: any\ndefault: true\nsettable_at: [a]\n");
    assertEquals(TypeDecl.simple("boolean"), key);
    assertTrue(parse("type: boolean\ndescription: x\n", Position.FIELD).isEmpty());
    assertTrue(parse("type: boolean\ncombine: any\n", Position.ITEMS).isEmpty());
    assertFindings(findings, "E_INVALID_DECLARATION 2 [description]", "E_INVALID_DECLARATION 2 [combine]");
  }

  // ---- integer bounds ------------------------------------------------------------------------------

  @Test
  void TYPE_13_boundsAreSourceIntegersWithinThePlusOrMinus2To53Minus1Range() {
    assertEquals(TypeDecl.integer(-9007199254740991L, 9007199254740991L),
        ok("type: integer\nmin: -9007199254740991\nmax: 9007199254740991\n"));
    assertEquals(TypeDecl.integer(0L, 0L), ok("type: integer\nmin: -0\nmax: 0x0\n"));
    assertEquals(TypeDecl.integer(15L, 15L), ok("type: integer\nmin: 0xF\nmax: 0o17\n"));
    assertEquals(TypeDecl.integer(5L, null), ok("type: integer\nmin: +5\n"));
  }

  @Test
  void TYPE_22_aBoundOutsideTheEngineRangeIsEIntegerRangeAtTheMember() {
    assertEquals(List.of("E_INTEGER_RANGE 2 [max]"), faults("type: integer\nmax: 9007199254740992\n", Position.KEY));
    assertEquals(List.of("E_INTEGER_RANGE 2 [min]"), faults("type: integer\nmin: -9007199254740992\n", Position.KEY));
    assertEquals(List.of("E_INTEGER_RANGE 2 [max]"), faults("type: integer\nmax: 99999999999999999999\n", Position.KEY));
    assertEquals(List.of("E_INTEGER_RANGE 2 [max]"),
        faults("type: integer\nmax: 999999999999999999999999999999\n", Position.KEY));
    assertEquals(List.of("E_INTEGER_RANGE 2 [min]"),
        faults("type: integer\nmin: -999999999999999999999999999999\n", Position.KEY));
    // Long.MIN_VALUE has no positive counterpart: it must not slip through an absolute-value test.
    assertEquals(List.of("E_INTEGER_RANGE 2 [min]"),
        faults("type: integer\nmin: -9223372036854775808\n", Position.KEY));
  }

  @Test
  void TYPE_15_aBoundThatIsNotASourceIntegerIsEInvalidDeclarationAtTheMember() {
    for (String bad : new String[] {"\"5\"", "5.5", "null", "true", "[1]", "1_000", "0b11"}) {
      Findings local = new Findings();
      assertTrue(new TypeDeclParser(local).parse(root("type: integer\nmin: " + bad + "\n"), Position.KEY).isEmpty(), bad);
      assertTrue(local.size() >= 1, bad);
      assertTrue(local.sorted().stream().allMatch(f -> f.code().equals("E_INVALID_DECLARATION")
          || f.code().equals("E_TYPE_MISMATCH")), bad + local.sorted());
    }
  }

  @Test
  void TYPE_14_aMinGreaterThanMaxIsEInvalidDeclarationAtTheDeclaration() {
    assertEquals(List.of("E_INVALID_DECLARATION 1 []"), faults("type: integer\nmin: 5\nmax: 4\n", Position.KEY));
    assertEquals(TypeDecl.integer(4L, 4L), ok2("type: integer\nmin: 4\nmax: 4\n"));
  }

  private TypeDecl ok2(String yaml) {
    return new TypeDeclParser(new Findings()).parse(root(yaml), Position.KEY).orElseThrow();
  }

  // ---- format --------------------------------------------------------------------------------------

  @Test
  void TYPE_31_aFormatIsTimeHhmmOrIanaTz() {
    TypeDecl time = ok("type: string\nformat: time_hhmm\n");
    assertEquals(Optional.of("time_hhmm"), time.format());
    assertEquals(Optional.of("iana_tz"), ok2("type: string\nformat: iana_tz\n").format());
    for (String bad : new String[] {"date", "TIME_HHMM", "5", "null", "\"\""}) {
      Findings local = new Findings();
      assertTrue(new TypeDeclParser(local).parse(root("type: string\nformat: " + bad + "\n"), Position.KEY).isEmpty(), bad);
      CheckTestSupport.assertFindings(local, "E_INVALID_DECLARATION 2 [format]");
    }
  }

  // ---- enum and set --------------------------------------------------------------------------------

  @Test
  void TYPE_40_valuesAndUniverseAreRequiredAtTheDeclaration() {
    assertEquals(List.of("E_INVALID_DECLARATION 1 []"), faults("type: enum\n", Position.KEY));
    assertEquals(List.of("E_INVALID_DECLARATION 1 []"), faults("type: set\n", Position.KEY));
  }

  @Test
  void TYPE_41_valuesMustBeAListOfStringsEachEntryReportedAtItself() {
    assertTrue(parse("type: enum\nvalues: A\n", Position.KEY).isEmpty());
    assertTrue(parse("type: enum\nvalues:\n  - A\n  - 5\n  - true\n  - null\n  - [x]\n", Position.KEY).isEmpty());
    assertFindings(findings, "E_INVALID_DECLARATION 2 [values]", "E_INVALID_DECLARATION 4 [values, 1]",
        "E_INVALID_DECLARATION 5 [values, 2]", "E_INVALID_DECLARATION 6 [values, 3]", "E_INVALID_DECLARATION 7 [values, 4]");
  }

  @Test
  void TYPE_42_anEmptyValuesOrUniverseListIsEInvalidDeclarationAtTheMember() {
    assertTrue(parse("type: enum\nvalues: []\n", Position.KEY).isEmpty());
    assertTrue(parse("type: set\nuniverse: []\n", Position.KEY).isEmpty());
    assertFindings(findings, "E_INVALID_DECLARATION 2 [values]", "E_INVALID_DECLARATION 2 [universe]");
  }

  @Test
  void TYPE_43_aRepeatedStringIsEDuplicateValueAtTheLaterEntry() {
    assertTrue(parse("type: enum\nvalues:\n  - A\n  - B\n  - A\n  - A\n", Position.KEY).isEmpty());
    assertTrue(parse("type: set\nuniverse: [a, b, b]\n", Position.KEY).isEmpty());
    assertFindings(findings, "E_DUPLICATE_VALUE 5 [values, 2]", "E_DUPLICATE_VALUE 6 [values, 3]",
        "E_DUPLICATE_VALUE 2 [universe, 2]");
  }

  @Test
  void NORM_1_entriesAreNormalisedToNfcBeforeTheRepetitionCheck() {
    assertEquals(List.of("caf\u00e9"), ok2("type: enum\nvalues: [\"caf\u00e9\"]\n").values());
    assertEquals(List.of("caf\u00e9"), ok2("type: enum\nvalues: [\"cafe\u0301\"]\n").values());
    assertTrue(parse("type: enum\nvalues: [\"caf\u00e9\", \"cafe\u0301\"]\n", Position.KEY).isEmpty());
    assertFindings(findings, "E_DUPLICATE_VALUE 2 [values, 1]");
  }

  @Test
  void NORM_8_aStringWithAnUnpairedSurrogateIsNotAValidEntry() {
    assertTrue(parse("type: enum\nvalues: [\"\\ud800\"]\n", Position.KEY).isEmpty());
    assertFindings(findings, "E_INVALID_DECLARATION 2 [values, 0]");
    assertEquals(List.of("\uD83D\uDE00"), ok2("type: enum\nvalues: [\"\\ud83d\\ude00\"]\n").values());
  }

  @Test
  void TYPE_50_theUniverseKeepsItsDeclaredOrderAndValuesKeepTheirs() {
    assertEquals(List.of("z", "a", "m"), ok("type: set\nuniverse: [z, a, m]\n").universe());
    assertEquals(List.of("z", "a", "m"), ok2("type: enum\nvalues: [z, a, m]\n").values());
  }

  // ---- list and object -----------------------------------------------------------------------------

  @Test
  void TYPE_53_aListNeedsItemsAtTheDeclaration() {
    assertTrue(parse("type: list\n", Position.KEY).isEmpty());
    assertFindings(findings, "E_INVALID_DECLARATION 1 []");
  }

  @Test
  void TYPE_54_itemsMayBeAnyTypeAndNestingIsUnlimited() {
    TypeDecl deep = ok("""
        type: list
        items:
          type: object
          fields:
            a:
              type: list
              items:
                type: set
                universe: [x]
            b: {type: object, fields: {c: {type: decimal, optional: true}}}
        """);
    TypeDecl a = deep.items().orElseThrow().fields().get("a");
    assertEquals(TypeDecl.set(List.of("x")), a.items().orElseThrow());
    assertTrue(deep.items().orElseThrow().fields().get("b").fields().get("c").optional());
  }

  @Test
  void TYPE_59_anObjectNeedsFieldsAMapWithAtLeastOneField() {
    assertEquals(List.of("E_INVALID_DECLARATION 1 []"), faults("type: object\n", Position.KEY));
    assertEquals(List.of("E_INVALID_DECLARATION 2 [fields]"), faults("type: object\nfields: []\n", Position.KEY));
    assertEquals(List.of("E_INVALID_DECLARATION 2 [fields]"), faults("type: object\nfields: {}\n", Position.KEY));
    assertEquals(List.of("E_INVALID_DECLARATION 2 [fields]"), faults("type: object\nfields:\n", Position.KEY));
  }

  @Test
  void TYPE_68_anEmptyFieldNameIsEInvalidDeclarationAtThatEntry() {
    assertTrue(parse("type: object\nfields:\n  \"\": {type: string}\n  ok: {type: string}\n", Position.KEY).isEmpty());
    assertFindings(findings, "E_INVALID_DECLARATION 3 [fields, ]");
  }

  @Test
  void TYPE_68_fieldNamesAreNormalisedToNfc() {
    TypeDecl object = ok("type: object\nfields:\n  \"cafe\u0301\": {type: string}\n");
    assertEquals(List.of("caf\u00e9"), List.copyOf(object.fields().keySet()));
  }

  @Test
  void TYPE_23_everyFaultOfADeclarationIsReportedNotJustTheFirst() {
    assertTrue(parse("""
        type: object
        fields:
          a: {type: nope}
          b: {min: 1}
          c: {type: enum, values: []}
          d: {type: integer, min: 9, max: 1}
          e: {type: string, format: x, optional: 5}
          f: {type: list}
          g: {type: string}
        """, Position.KEY).isEmpty());
    assertFindings(findings, "E_INVALID_DECLARATION 3 [fields, a, type]", "E_TYPE_REQUIRED 4 [fields, b]",
        "E_INVALID_DECLARATION 5 [fields, c, values]", "E_INVALID_DECLARATION 6 [fields, d]",
        "E_INVALID_DECLARATION 7 [fields, e, format]", "E_INVALID_DECLARATION 7 [fields, e, optional]",
        "E_INVALID_DECLARATION 8 [fields, f]");
  }

  @Test
  void aFieldDeclarationBuildsAnOptionalFieldOnlyInTheFieldPosition() {
    Optional<TypeDecl> field = parse("type: string\noptional: true\n", Position.FIELD);
    assertEquals(TypeDecl.simple("string").asOptional(), field.orElseThrow());
    Optional<TypeDecl> required = parse("type: string\n", Position.FIELD);
    assertEquals(false, required.orElseThrow().optional());
  }

  @Test
  void parseFieldsServesTheFieldsOfAFeatureReturnsObject() {
    PMap holder = root("fields:\n  a: {type: string}\n  b: {type: integer, optional: true}\n");
    Map<String, TypeDecl> expected = new LinkedHashMap<>();
    expected.put("a", TypeDecl.simple("string"));
    expected.put("b", TypeDecl.integer(null, null).asOptional());
    assertEquals(Optional.of(expected), parser.parseFields(holder.get("fields").orElseThrow()));
    assertEquals(0, findings.size());
  }

  @Test
  void parseStringsServesTheEnumListOfAFeatureReturns() {
    PMap holder = root("good: [ALLOW, DENY]\nbad: [A, A]\nempty: []\nnot: x\n");
    assertEquals(Optional.of(List.of("ALLOW", "DENY")), parser.parseStrings(holder.get("good").orElseThrow()));
    assertTrue(parser.parseStrings(holder.get("bad").orElseThrow()).isEmpty());
    assertTrue(parser.parseStrings(holder.get("empty").orElseThrow()).isEmpty());
    assertTrue(parser.parseStrings(holder.get("not").orElseThrow()).isEmpty());
    assertFindings(findings, "E_DUPLICATE_VALUE 2 [bad, 1]", "E_INVALID_DECLARATION 3 [empty]", "E_INVALID_DECLARATION 4 [not]");
  }
}
