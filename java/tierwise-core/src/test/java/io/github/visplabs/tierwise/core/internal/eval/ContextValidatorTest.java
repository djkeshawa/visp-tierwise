package io.github.visplabs.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.ScopeKind;
import io.github.visplabs.tierwise.core.SkippedScope;
import io.github.visplabs.tierwise.core.TierwiseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** CTX-1 to CTX-23: validation steps, admission and the evaluation path. */
class ContextValidatorTest {

  private final ContextValidator validator = new ContextValidator(TestModels.standard().model());

  private static String codeOf(Runnable action) {
    return assertThrows(TierwiseException.class, action::run).code();
  }

  private String codeFor(Context context) {
    return codeOf(() -> validator.validate(context));
  }

  private static List<String> names(AdmittedContext admitted) {
    List<String> names = new ArrayList<>();
    admitted.path().forEach(scope -> names.add(scope.name()));
    return names;
  }

  @Test
  void CTX_20_theRootLevelIsOnEveryPathEvenForAnEmptyContext() {
    AdmittedContext admitted = validator.validate(Context.empty());
    assertEquals(List.of("global"), names(admitted));
    assertTrue(admitted.path().get(0).isRoot());
    assertEquals("", admitted.path().get(0).nodeKey());
    assertTrue(admitted.skippedScopes().isEmpty());
  }

  @Test
  void EVAL_54_aNullContextIsAnEmptyContext() {
    assertEquals(List.of("global"), names(validator.validate(null)));
  }

  @Test
  void MERGE_13_thePathIsRootThenLevelsInDeclaredOrderThenAxesInDeclaredOrder() {
    Context context = Context.builder().level("account", "a1").level("division", "north").axis("promo", "spring")
        .axis("seller", "acme").build();
    assertEquals(List.of("global", "division", "account", "seller", "promo"), names(validator.validate(context)));
  }

  @Test
  void CTX_1_aMalformedContextRaisesContextInvalid() {
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.fromJson(List.of("levels"))));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.fromJson("levels")));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.fromJson(Map.of("levels", List.of()))));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.fromJson(Map.of("levels", Map.of("division", 7L)))));
  }

  @Test
  void CTX_2_aMemberOtherThanLevelsAndAxesRaisesContextInvalid() {
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.fromJson(Map.of("colour", Map.of()))));
  }

  @Test
  void EVAL_31_aContextThatIsNotAMapRaisesContextInvalid() {
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.fromJson(null)));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.fromJson("a string")));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.fromJson(List.of())));
  }

  @Test
  void CTX_3_namingTheRootLevelInEitherMapRaisesContextInvalid() {
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().level("global", "x").build()));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().axis("global", "x").build()));
  }

  @Test
  void CTX_4_anUndeclaredNameRaisesUnknownScope() {
    assertEquals(Code.E_UNKNOWN_SCOPE, codeFor(Context.builder().level("warehouse", "x").build()));
    assertEquals(Code.E_UNKNOWN_SCOPE, codeFor(Context.builder().axis("region", "x").build()));
  }

  @Test
  void CTX_4_namesAreComparedAsReceivedWithoutNormalisationOrCaseFolding() {
    assertEquals(Code.E_UNKNOWN_SCOPE, codeFor(Context.builder().level("Division", "north").build()));
    assertEquals(Code.E_UNKNOWN_SCOPE, codeFor(Context.builder().level("división", "north").build()));
  }

  @Test
  void CTX_5_aDeclaredNameInTheWrongMapRaisesUnknownScope() {
    assertEquals(Code.E_UNKNOWN_SCOPE, codeFor(Context.builder().level("seller", "acme").build()));
    assertEquals(Code.E_UNKNOWN_SCOPE, codeFor(Context.builder().axis("division", "north").build()));
  }

  @Test
  void CTX_11_aRootNameWinsOverAnUnknownNameWhateverTheEntryOrder() {
    Map<String, String> unknownFirst = new LinkedHashMap<>();
    unknownFirst.put("warehouse", "x");
    unknownFirst.put("global", "y");
    Map<String, String> rootFirst = new LinkedHashMap<>();
    rootFirst.put("global", "y");
    rootFirst.put("warehouse", "x");
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.of(unknownFirst, Map.of())));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.of(rootFirst, Map.of())));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.of(Map.of("warehouse", "x"), Map.of("global", "y"))));
  }

  @Test
  void CTX_6_aNodeIdMustBeNonEmptyAtMost128CodePointsWithoutControlCharacters() {
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().level("account", "").build()));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().level("account", "a".repeat(129)).build()));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().level("account", "a\u0000b").build()));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().level("account", "a\u007fb").build()));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().level("account", "a\u0085b").build()));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().level("account", "a\u001fb").build()));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().axis("seller", "\n").build()));
    validator.validate(Context.builder().level("account", "a".repeat(128)).build());
    validator.validate(Context.builder().level("account", " nbsp").build());
  }

  @Test
  void CTX_6_theLengthIsCountedInCodePointsNotCodeUnits() {
    String emoji = "😀";
    validator.validate(Context.builder().level("account", emoji.repeat(128)).build());
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().level("account", emoji.repeat(129)).build()));
  }

  @Test
  void SCOPE_37_aNodeIdWithAnUnpairedSurrogateRaisesContextInvalid() {
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().level("account", "x\uD83D").build()));
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().level("account", "\uDE00x").build()));
  }

  @Test
  void CTX_7_aNodeIdThatIsNotInNfcIsNotInvalidAndIsAdmittedAsReceivedInAnOpenCatalog() {
    AdmittedContext admitted = validator.validate(Context.builder().level("account", "é").build());
    assertEquals("é", admitted.path().get(1).node());
  }

  @Test
  void CTX_8_admissionComparesCodePointsSoANonNfcNodeIsNotACatalogMember() {
    assertEquals(Code.E_UNKNOWN_NODE, codeFor(Context.builder().level("division", "north́").build()));
  }

  @Test
  void CTX_12_anUnlistedNodeOfAClosedCatalogUnderErrorRaisesUnknownNode() {
    assertEquals(Code.E_UNKNOWN_NODE, codeFor(Context.builder().level("division", "west").build()));
    assertEquals(Code.E_UNKNOWN_NODE, codeFor(Context.builder().axis("seller", "other").build()));
  }

  @Test
  void CTX_13_anOpenCatalogAdmitsAnyValidNodeId() {
    AdmittedContext admitted = validator.validate(Context.builder().level("account", "never-seen-before").build());
    assertEquals(List.of("global", "account"), names(admitted));
  }

  @Test
  void CTX_14_anUnlistedNodeUnderIgnoreSkipsTheWholeScope() {
    AdmittedContext admitted = validator.validate(Context.builder().level("division", "north")
        .level("site", "s9").axis("promo", "winter").build());
    assertEquals(List.of("global", "division"), names(admitted));
    assertEquals(List.of(new SkippedScope(ScopeKind.LEVEL, "site", "s9"),
        new SkippedScope(ScopeKind.AXIS, "promo", "winter")), admitted.skippedScopes());
  }

  @Test
  void CTX_17_skippingOneScopeDoesNotChangeTheAdmissionOfAnother() {
    AdmittedContext admitted = validator.validate(Context.builder().level("site", "s9").level("account", "a1")
        .axis("seller", "acme").build());
    assertEquals(List.of("global", "account", "seller"), names(admitted));
  }

  @Test
  void CTX_21_anOmittedScopeContributesNothingAndIsNotSkipped() {
    AdmittedContext admitted = validator.validate(Context.builder().level("division", "north").build());
    assertEquals(List.of("global", "division"), names(admitted));
    assertTrue(admitted.skippedScopes().isEmpty());
  }

  @Test
  void PROV_58_skippedScopesAreOrderedByLevelsThenAxesInDeclaredOrder() {
    AdmittedContext admitted = validator.validate(Context.builder().axis("promo", "x").level("site", "y").build());
    assertEquals(List.of("site", "promo"), admitted.skippedScopes().stream().map(SkippedScope::name).toList());
  }

  @Test
  void PROV_57_theRootLevelNeverAppearsInSkippedScopes() {
    AdmittedContext admitted = validator.validate(Context.builder().level("site", "x").axis("promo", "y").build());
    assertTrue(admitted.skippedScopes().stream().noneMatch(scope -> scope.name().equals("global")));
  }

  @Test
  void PROV_59_aScopeIsSkippedAtMostOnce() {
    AdmittedContext admitted = validator.validate(Context.builder().level("site", "x").axis("promo", "y").build());
    assertEquals(admitted.skippedScopes().size(), admitted.skippedScopes().stream().map(SkippedScope::name).distinct().count());
  }

  @Test
  void CTX_9_theFirstFailingStepDecidesTheError() {
    // step 1 before step 2
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.fromJson(Map.of("levels", Map.of("warehouse", "x"), "extra", Map.of()))));
    // step 2 before step 3
    assertEquals(Code.E_UNKNOWN_SCOPE, codeFor(Context.builder().level("warehouse", "x").level("account", "").build()));
    // step 3 before step 4
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().level("division", "west").level("account", "").build()));
  }

  @Test
  void CTX_18_theOnUnknownNodeSettingChangesOnlyTheAdmissionStep() {
    assertEquals(Code.E_CONTEXT_INVALID, codeFor(Context.builder().level("site", "").build()));
    assertEquals(Code.E_UNKNOWN_SCOPE, codeFor(Context.builder().level("warehouse", "x").build()));
  }
}
