package io.github.visplabs.tierwise.core.internal.eval;

import static io.github.visplabs.tierwise.core.internal.eval.TestModels.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.LevelValueProvider;
import io.github.visplabs.tierwise.core.Resolved;
import io.github.visplabs.tierwise.core.Resolved.Contribution;
import io.github.visplabs.tierwise.core.Resolved.Contribution.Label;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.core.internal.eval.TestModels.CountingProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Provider outcomes and calls (section 6): SVC-6 to SVC-41. */
class ProviderOutcomesTest {

  private static final Context ACCOUNT = Context.builder().level("account", "a1").build();

  private final TestModels.Builder builder = TestModels.standard();
  private final CountingProvider seat = new CountingProvider(Map.of());

  private static CountingProvider account(Map<String, Object> values) {
    return new CountingProvider(Map.of("a1", values));
  }

  private EvaluatorImpl evaluatorOf(LevelValueProvider provider) {
    return (EvaluatorImpl) TestModels.evaluator(builder, provider, seat, null);
  }

  private static Contribution only(Resolved resolved) {
    assertEquals(1, resolved.contributions().size(), resolved.contributions().toString());
    return resolved.contributions().get(0);
  }

  @Test
  void SVC_17_aProviderIsCalledOnlyForAnAdmittedLevelAtWhichAKeyIsSettable() {
    CountingProvider account = account(Map.of());
    EvaluatorImpl evaluator = evaluatorOf(account);
    evaluator.resolve(Context.empty(), "k.all");
    evaluator.resolve(ACCOUNT, "k.note");
    evaluator.resolve(Context.builder().level("account", "a1").level("seat", "t1").build(), "k.all");
    assertEquals(List.of("a1"), account.calls(), "called only for the call that names the level and a settable key");
    assertEquals(List.of(), seat.calls(), "no key being resolved is settable at seat in this call");
  }

  @Test
  void SVC_17_aSkippedScopeMakesNoProviderCall() {
    builder.hierarchy = withAccountIgnoring();
    CountingProvider account = account(Map.of("k.all", false));
    Resolved resolved = evaluatorOf(account).resolve(Context.builder().level("account", "unlisted").build(), "k.all");
    assertEquals(List.of(), account.calls());
    assertEquals(1, resolved.skippedScopes().size());
    assertTrue(resolved.contributions().isEmpty());
    assertEquals(true, resolved.value());
  }

  private io.github.visplabs.tierwise.core.internal.model.Hierarchy withAccountIgnoring() {
    var old = builder.hierarchy;
    Map<String, io.github.visplabs.tierwise.core.internal.model.ScopeDef> scopes = new java.util.LinkedHashMap<>(old.scopes());
    scopes.put("account", io.github.visplabs.tierwise.core.internal.model.ScopeDef.closedCatalog(
        io.github.visplabs.tierwise.core.internal.model.ScopeDef.ValuesSource.SERVICE, List.of("a1"),
        io.github.visplabs.tierwise.core.internal.model.ScopeDef.OnUnknownNode.IGNORE));
    return new io.github.visplabs.tierwise.core.internal.model.Hierarchy(old.levels(), old.axes(), scopes);
  }

  @Test
  void SVC_18_theProviderIsCalledEvenWhenAnotherScopeSuppliesTheWinningValue() {
    builder.value("global", "", "k.label", "root");
    CountingProvider account = account(Map.of("k.label", "from-account"));
    Resolved resolved = evaluatorOf(account).resolve(ACCOUNT, "k.label");
    assertEquals(List.of("a1"), account.calls());
    assertEquals("from-account", resolved.value());
    builder.value("division", "north", "k.min", 3L);
    CountingProvider second = account(Map.of("k.min", 9L));
    Resolved min = evaluatorOf(second).resolve(Context.builder().level("account", "a1").level("division", "north").build(),
        "k.min");
    assertEquals(3L, min.value());
    assertEquals(List.of("a1"), second.calls());
    assertEquals(2, min.contributions().size());
  }

  @Test
  void SVC_19_aProviderIsCalledAtMostOncePerEvaluationCall() {
    CountingProvider account = account(Map.of("k.all", true));
    evaluatorOf(account).resolve(ACCOUNT, "k.all");
    assertEquals(1, account.calls().size());
  }

  @Test
  void SVC_20_aRequestForSeveralKeysIsOneEvaluationCall() {
    CountingProvider account = account(mapOf("k.all", true, "k.intersect", List.of("red"), "k.min", 5L));
    List<Resolved> results = evaluatorOf(account).resolveAll(ACCOUNT, List.of("k.all", "k.intersect", "k.min", "k.all"));
    assertEquals(List.of("a1"), account.calls());
    assertEquals(4, results.size());
    assertEquals(List.of("red"), results.get(1).value());
    assertEquals(5L, results.get(2).value());
    assertEquals(results.get(0), results.get(3));
  }

  @Test
  void SVC_22_decideCallsNoProvider() {
    CountingProvider account = account(Map.of());
    evaluatorOf(account).decide(ACCOUNT, "f.gate", io.github.visplabs.tierwise.core.FactSet.empty());
    assertEquals(List.of(), account.calls());
  }

  @Test
  void SVC_15_theProviderReceivesTheNodeIdExactlyAsSupplied() {
    List<String> seen = new ArrayList<>();
    LevelValueProvider recording = node -> {
      seen.add(node);
      return Map.of();
    };
    evaluatorOf(recording).resolve(Context.builder().level("account", "é x").build(), "k.all");
    assertEquals(List.of("é x"), seen);
  }

  @Test
  void SVC_23_aThrowIsAnUnavailableFailureForEachKeyBeingResolvedAtTheLevel() {
    EvaluatorImpl evaluator = evaluatorOf(TestModels.failing());
    List<Resolved> results = evaluator.resolveAll(ACCOUNT, List.of("k.all", "k.all_skip", "k.min"));
    for (Resolved resolved : results) {
      assertEquals(Label.UNAVAILABLE, only(resolved).effect());
    }
    assertEquals(false, results.get(0).value());
    assertEquals(true, results.get(1).value());
    assertEquals(1L, results.get(2).value());
  }

  @Test
  void SVC_25_aResultThatIsNotAMappingIsAnUnavailableFailureForEachKeyBeingResolved() {
    List<Resolved> results = evaluatorOf(node -> null).resolveAll(ACCOUNT, List.of("k.all", "k.intersect"));
    for (Resolved resolved : results) {
      Contribution entry = only(resolved);
      assertEquals(Label.UNAVAILABLE, entry.effect());
      assertEquals(Optional.of("deny"), entry.onSourceFailure());
    }
    assertEquals(false, results.get(0).value());
    assertEquals(List.of(), results.get(1).value());
  }

  @Test
  void SVC_8_underSkipAFailureContributesNothingAndHasNoValue() {
    Resolved resolved = evaluatorOf(TestModels.failing()).resolve(ACCOUNT, "k.all_skip");
    Contribution entry = only(resolved);
    assertEquals(Label.UNAVAILABLE, entry.effect());
    assertEquals(Optional.of("skip"), entry.onSourceFailure());
    assertTrue(entry.value().isEmpty());
    assertEquals(true, resolved.value());
  }

  @Test
  void SVC_9_underDenyAFailureContributesTheDenyValueAndKeepsItsFailureLabel() {
    Contribution all = only(evaluatorOf(TestModels.failing()).resolve(ACCOUNT, "k.all"));
    assertEquals(Optional.of(false), all.value());
    assertEquals(Label.UNAVAILABLE, all.effect(), "the label stays UNAVAILABLE, not VETO (PROV-41)");
    assertEquals(Optional.of(List.of()), only(evaluatorOf(TestModels.failing()).resolve(ACCOUNT, "k.intersect")).value());
    Resolved min = evaluatorOf(TestModels.failing()).resolve(ACCOUNT, "k.min");
    assertEquals(Optional.of(1L), only(min).value());
    assertEquals(1L, min.value());
  }

  @Test
  void SVC_10_aDenyValueIsNotPresentedAsAProviderValue() {
    Contribution entry = only(evaluatorOf(TestModels.failing()).resolve(ACCOUNT, "k.all"));
    assertTrue(entry.effect().isFailure());
    assertEquals(Optional.of("deny"), entry.onSourceFailure());
  }

  @Test
  void SVC_27_anEntryForAnUndeclaredKeyAffectsNothing() {
    Resolved resolved = evaluatorOf(account(mapOf("k.all", true, "not.a.key", null, "other.key", 5L)))
        .resolve(ACCOUNT, "k.all");
    assertEquals(Label.IGNORED, only(resolved).effect());
    assertEquals(true, resolved.value());
  }

  @Test
  void SVC_28_aDeclaredKeyThatIsNotBeingResolvedIsNeverValidated() {
    Resolved resolved = evaluatorOf(account(mapOf("k.all", true, "k.min", "not an integer", "k.union", null)))
        .resolve(ACCOUNT, "k.all");
    assertEquals(1, resolved.contributions().size());
    assertEquals(Label.IGNORED, resolved.contributions().get(0).effect());
  }

  @Test
  void SVC_29_anEntryForAKeyBeingResolvedThatIsNotSettableAtTheLevelIsIgnored() {
    EvaluatorImpl evaluator = evaluatorOf(account(mapOf("k.all", false, "k.note", "from-account")));
    List<Resolved> results = evaluator.resolveAll(ACCOUNT, List.of("k.all", "k.note"));
    assertEquals(false, results.get(0).value());
    assertEquals("none", results.get(1).value());
    assertTrue(results.get(1).contributions().isEmpty(), "no value and no failure (SVC-29)");
  }

  @Test
  void SVC_30_anIgnoredEntryAddsNoContributionEntryOfAnyLabel() {
    EvaluatorImpl evaluator = evaluatorOf(account(mapOf("k.note", null)));
    Resolved note = evaluator.resolveAll(ACCOUNT, List.of("k.all", "k.note")).get(1);
    assertTrue(note.contributions().isEmpty());
    assertEquals("none", note.value());
    assertEquals(Resolved.Source.DEFAULT, note.source().orElseThrow());
  }

  @Test
  void SVC_37_anIgnoredOrInvalidEntryChangesNoOtherKey() {
    EvaluatorImpl evaluator = evaluatorOf(account(mapOf("k.all", false, "k.min", "bad", "k.note", "x")));
    List<Resolved> results = evaluator.resolveAll(ACCOUNT, List.of("k.all", "k.min", "k.note"));
    assertEquals(Label.VETO, only(results.get(0)).effect());
    assertEquals(false, results.get(0).value());
    assertEquals(Label.INVALID, only(results.get(1)).effect());
    assertTrue(results.get(2).contributions().isEmpty());
    Resolved alone = evaluatorOf(account(mapOf("k.all", false))).resolve(ACCOUNT, "k.all");
    assertEquals(alone.toJson(), results.get(0).toJson());
  }

  @Test
  void SVC_31_aValueThatFailsTypeValidationIsAnInvalidFailureForThatKeyOnly() {
    Map<String, Object> bad = mapOf("k.min", 0L, "k.max", 3L);
    assertEquals(Label.INVALID, only(evaluatorOf(account(bad)).resolve(ACCOUNT, "k.min")).effect());
    for (Object invalid : new Object[] {"x", 1.5, true, 501L, List.of()}) {
      Resolved resolved = evaluatorOf(account(mapOf("k.min", invalid))).resolve(ACCOUNT, "k.min");
      assertEquals(Label.INVALID, only(resolved).effect(), String.valueOf(invalid));
    }
  }

  @Test
  void SVC_34_anInvalidValueIsTheSameFailureAsFarAsDenyAndSkipAreConcerned() {
    Contribution deny = only(evaluatorOf(account(mapOf("k.all", "yes"))).resolve(ACCOUNT, "k.all"));
    assertEquals(Label.INVALID, deny.effect());
    assertEquals(Optional.of(false), deny.value());
    Contribution skip = only(evaluatorOf(account(mapOf("k.all_skip", "yes"))).resolve(ACCOUNT, "k.all_skip"));
    assertEquals(Label.INVALID, skip.effect());
    assertTrue(skip.value().isEmpty());
  }

  @Test
  void SVC_35_aNullValueForAKeyBeingResolvedIsInvalidNotOmitted() {
    Resolved resolved = evaluatorOf(account(mapOf("k.all", null))).resolve(ACCOUNT, "k.all");
    assertEquals(Label.INVALID, only(resolved).effect());
    assertEquals(false, resolved.value());
  }

  @Test
  void SVC_36_aKeyThatTheResultOmitsMakesNoContribution() {
    Resolved resolved = evaluatorOf(account(Map.of())).resolve(ACCOUNT, "k.all");
    assertTrue(resolved.contributions().isEmpty());
    assertEquals(true, resolved.value());
  }

  @Test
  void SVC_33_aProviderValueEqualToTheIdentityIsValidAndLabelledIgnored() {
    Resolved resolved = evaluatorOf(account(mapOf("k.all", true))).resolve(ACCOUNT, "k.all");
    assertEquals(Label.IGNORED, only(resolved).effect());
    assertEquals(Optional.of(true), only(resolved).value());
  }

  @Test
  void SVC_16_keyNamesAreMatchedByExactCodePointEquality() {
    Resolved resolved = evaluatorOf(account(mapOf("K.ALL", false))).resolve(ACCOUNT, "k.all");
    assertTrue(resolved.contributions().isEmpty());
  }

  @Test
  void SVC_31_decimalValuesAreCanonicalisedAndSetsReturnedInUniverseOrder() {
    Resolved decimal = evaluatorOf(account(mapOf("k.decimal", "+01.500"))).resolve(ACCOUNT, "k.decimal");
    assertEquals("1.5", decimal.value());
    assertEquals(Optional.of("1.5"), only(decimal).value());
    Resolved set = evaluatorOf(account(mapOf("k.union", List.of("blue", "red")))).resolve(ACCOUNT, "k.union");
    assertEquals(List.of("red", "blue"), set.value());
    assertEquals(Label.INVALID,
        only(evaluatorOf(account(mapOf("k.decimal", 1.5))).resolve(ACCOUNT, "k.decimal")).effect());
    assertEquals(Label.INVALID,
        only(evaluatorOf(account(mapOf("k.union", List.of("red", "red")))).resolve(ACCOUNT, "k.union")).effect());
    assertEquals(Label.INVALID,
        only(evaluatorOf(account(mapOf("k.union", List.of("pink")))).resolve(ACCOUNT, "k.union")).effect());
  }

  @Test
  void SVC_32_aPartialObjectIsValidForADeepMergeKey() {
    Resolved resolved = evaluatorOf(account(mapOf("k.deep", mapOf("cap", 50L)))).resolve(ACCOUNT, "k.deep");
    assertEquals(Label.SET, only(resolved).effect());
    assertEquals(Map.of("cap", 50L), only(resolved).value().orElseThrow());
    assertEquals(50L, ((Map<?, ?>) resolved.value()).get("cap"));
    assertEquals("items", ((Map<?, ?>) resolved.value()).get("unit"));
  }

  @Test
  void MERGE_26_anOptionalObjectTheStartingValueDoesNotSetMustBeCompleteInAProviderValue() {
    Map<String, Object> partialWindow = mapOf("window", mapOf("to", "17:00"));
    assertEquals(Label.INVALID, only(evaluatorOf(account(mapOf("k.deep", partialWindow))).resolve(ACCOUNT, "k.deep")).effect());
    Map<String, Object> completeWindow = mapOf("window", mapOf("from", "08:00", "to", "17:00"));
    assertEquals(Label.SET, only(evaluatorOf(account(mapOf("k.deep", completeWindow))).resolve(ACCOUNT, "k.deep")).effect());
  }

  @Test
  void SVC_32_aPartialObjectIsNotValidForAnOverrideKey() {
    Resolved resolved = evaluatorOf(account(mapOf("k.object_override", mapOf("to", "17:00")))).resolve(ACCOUNT,
        "k.object_override");
    assertEquals(Label.INVALID, only(resolved).effect());
  }

  @Test
  void SVC_6_noSourceFailureIsRecordedForALevelThatIsNotServiceSourced() {
    builder.value("division", "north", "k.all", true);
    Resolved resolved = evaluatorOf(TestModels.failing()).resolve(Context.builder().level("division", "north").build(),
        "k.all");
    assertTrue(resolved.contributions().stream().noneMatch(entry -> entry.effect().isFailure()));
  }

  @Test
  void SVC_39_aProviderThatExceedsItsTimeoutIsUnavailable() {
    LevelValueProvider slow = node -> {
      Thread.sleep(2_000);
      return mapOf("k.all", true);
    };
    Resolved resolved = evaluatorWithTimeout(slow, Duration.ofMillis(60)).resolve(ACCOUNT, "k.all");
    assertEquals(Label.UNAVAILABLE, only(resolved).effect());
    assertEquals(false, resolved.value());
  }

  @Test
  void SVC_40_aResultThatArrivesAfterTheTimeoutIsNeverUsed() throws Exception {
    java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
    LevelValueProvider late = node -> {
      try {
        release.await(5, java.util.concurrent.TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        // the evaluator abandons the call: still return a value, which must be ignored
      }
      return mapOf("k.min_skip", 7L);
    };
    Resolved resolved = evaluatorWithTimeout(late, Duration.ofMillis(60)).resolve(ACCOUNT, "k.min_skip");
    release.countDown();
    assertEquals(Label.UNAVAILABLE, only(resolved).effect());
    assertNull(resolved.value());
    assertFalse(only(resolved).value().isPresent());
  }

  @Test
  void SVC_41_aResultThatIsAvailableBeforeTheTimeoutIsUsed() {
    LevelValueProvider quick = node -> {
      Thread.sleep(20);
      return mapOf("k.min_skip", 7L);
    };
    Resolved resolved = evaluatorWithTimeout(quick, Duration.ofSeconds(5)).resolve(ACCOUNT, "k.min_skip");
    assertEquals(7L, resolved.value());
    assertEquals(Label.NARROWED, only(resolved).effect());
  }

  @Test
  void SVC_21_twoSlowProvidersRunConcurrentlySoTheirTimeoutsDoNotAdd() {
    LevelValueProvider slow = node -> {
      Thread.sleep(400);
      return mapOf("k.all", true, "k.seat", true);
    };
    EvaluatorOptions options = EvaluatorOptions.builder().provider("account", slow, Duration.ofSeconds(5))
        .provider("seat", slow, Duration.ofSeconds(5)).build();
    long start = System.nanoTime();
    builder.key("k.both", TestModels.key(io.github.visplabs.tierwise.core.internal.model.TypeDecl.simple("boolean"), "all", null,
        List.of("global", "account", "seat"), null, "deny"));
    Resolved resolved = Tierwise.evaluator(builder.loaded(), options)
        .resolve(Context.builder().level("account", "a1").level("seat", "t1").build(), "k.both");
    long millis = (System.nanoTime() - start) / 1_000_000;
    assertTrue(millis < 750, "both providers ran at once: " + millis + " ms");
    assertEquals(0, resolved.contributions().size(), "the providers returned no entry for k.both");
  }

  private io.github.visplabs.tierwise.core.Evaluator evaluatorWithTimeout(LevelValueProvider account, Duration timeout) {
    EvaluatorOptions options = EvaluatorOptions.builder().provider("account", account, timeout)
        .provider("seat", seat, Duration.ofSeconds(5)).build();
    return Tierwise.evaluator(builder.loaded(), options);
  }

  @Test
  void SVC_14_thePathCallsTheProviderOfEachServiceLevelOnceWhenSeveralLevelsAreNeeded() {
    builder.key("k.both", TestModels.key(io.github.visplabs.tierwise.core.internal.model.TypeDecl.simple("boolean"), "all", null,
        List.of("global", "account", "seat"), null, "deny"));
    CountingProvider accountProvider = account(mapOf("k.both", false));
    CountingProvider seatProvider = new CountingProvider(Map.of("t1", mapOf("k.both", true)));
    io.github.visplabs.tierwise.core.Evaluator evaluator = TestModels.evaluator(builder, accountProvider, seatProvider, null);
    Resolved resolved = evaluator.resolve(Context.builder().level("account", "a1").level("seat", "t1").build(), "k.both");
    assertEquals(List.of("a1"), accountProvider.calls());
    assertEquals(List.of("t1"), seatProvider.calls());
    assertEquals(false, resolved.value());
    assertEquals(List.of("account", "seat"), resolved.contributions().stream().map(Contribution::name).toList());
  }
}
