package io.github.tierwise.core.internal.bundle;

import static io.github.tierwise.core.internal.bundle.BundleFixtures.arr;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.assertFault;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.assertLoads;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.listAt;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.mapAt;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.obj;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.richContent;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The structural checks of load bundle (BUNDLE-118 to BUNDLE-136). Each test edits one part of a valid bundle and
 * asserts the clause that the failed check cites. The test name starts with that clause ID.
 */
class BundleStructureTest {

  private static final String FEATURE = "checkout.submission";
  private static final String POLICY = "credit";

  private static Map<String, Object> feature(Map<String, Object> content) {
    return mapAt(content, "features", FEATURE);
  }

  private static Map<String, Object> policy(Map<String, Object> content) {
    return mapAt(content, "features", FEATURE, "policies", POLICY);
  }

  @Test
  void richContentIsAValidBundle() {
    assertLoads(richContent());
  }

  @Test
  void BUNDLE_14_unknownContentMemberIsRejected() {
    Map<String, Object> content = richContent();
    content.put("note", "x");
    assertFault(content);
  }

  @Test
  void BUNDLE_15_requiredContentMemberIsRequired() {
    Map<String, Object> content = richContent();
    content.remove("values");
    assertFault(content);
  }

  @Test
  void BUNDLE_22_emptyScopeOrNodeEntryIsRejected() {
    Map<String, Object> scope = richContent();
    mapAt(scope, "values").put("seller", obj());
    assertFault(scope, "BUNDLE-22");
    Map<String, Object> node = richContent();
    mapAt(node, "values", "division").put("north", obj());
    assertFault(node, "BUNDLE-22");
  }

  @Test
  void BUNDLE_23_nullOutsideAnEffectIsRejected() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.lead_hours").put("default", null);
    assertFault(content, "TYPE-76");
  }

  @Test
  void BUNDLE_29_hierarchyHasExactlyLevelsAxesAndScopes() {
    Map<String, Object> content = richContent();
    mapAt(content, "hierarchy").put("extra", arr());
    assertFault(content);
  }

  @Test
  void BUNDLE_30_levelsStartWithTheRootLevel() {
    Map<String, Object> content = richContent();
    mapAt(content, "hierarchy").put("levels", arr());
    assertFault(content, "BUNDLE-30");
  }

  @Test
  void BUNDLE_32_scopesHaveOneMemberPerNonRootLevelAndAxis() {
    Map<String, Object> missing = richContent();
    mapAt(missing, "hierarchy", "scopes").remove("seller");
    assertFault(missing, "BUNDLE-32");
    Map<String, Object> extra = richContent();
    mapAt(extra, "hierarchy", "scopes").put("global", obj("values", "files", "nodes", "open"));
    assertFault(extra, "BUNDLE-32");
  }

  @Test
  void BUNDLE_34_valuesMemberIsFilesOrService() {
    Map<String, Object> content = richContent();
    mapAt(content, "hierarchy", "scopes", "division").put("values", "remote");
    assertFault(content);
  }

  @Test
  void BUNDLE_36_serviceIsOnlyForALevel() {
    Map<String, Object> content = richContent();
    mapAt(content, "hierarchy", "scopes", "seller").put("values", "service");
    assertFault(content, "BUNDLE-36");
  }

  @Test
  void BUNDLE_37_nodesIsOpenOrAnArray() {
    Map<String, Object> content = richContent();
    mapAt(content, "hierarchy", "scopes", "division").put("nodes", "closed");
    assertFault(content, "BUNDLE-37");
  }

  @Test
  void BUNDLE_119_BUNDLE_133_nodesHaveNoRepeatsAndAreInCodePointOrder() {
    Map<String, Object> unsorted = richContent();
    mapAt(unsorted, "hierarchy", "scopes", "division").put("nodes", arr("south", "north"));
    assertFault(unsorted, "BUNDLE-39");
    Map<String, Object> repeated = richContent();
    mapAt(repeated, "hierarchy", "scopes", "division").put("nodes", arr("north", "north"));
    assertFault(repeated, "BUNDLE-38");
  }

  @Test
  void BUNDLE_42_BUNDLE_43_onUnknownNodeIsPresentExactlyWhenClosedAndIsErrorOrIgnore() {
    Map<String, Object> open = richContent();
    mapAt(open, "hierarchy", "scopes", "account").put("onUnknownNode", "error");
    assertFault(open, "BUNDLE-42");
    Map<String, Object> closed = richContent();
    mapAt(closed, "hierarchy", "scopes", "division").remove("onUnknownNode");
    assertFault(closed, "BUNDLE-42");
    Map<String, Object> value = richContent();
    mapAt(value, "hierarchy", "scopes", "division").put("onUnknownNode", "maybe");
    assertFault(value);
  }

  @Test
  void BUNDLE_44_entityHasExactlyBelongsToAndHasMany() {
    Map<String, Object> content = richContent();
    mapAt(content, "entities", "customer").remove("hasMany");
    assertFault(content);
  }

  @Test
  void BUNDLE_49_factHasExactlyEntityAndType() {
    Map<String, Object> content = richContent();
    mapAt(content, "facts", "customer.active").remove("entity");
    assertFault(content);
  }

  @Test
  void BUNDLE_50_factTypeIsASimpleType() {
    Map<String, Object> content = richContent();
    mapAt(content, "facts", "customer.active").put("type", obj("type", "list", "items", obj("type", "boolean")));
    assertFault(content, "BUNDLE-50");
  }

  @Test
  void BUNDLE_52_keyHasTypeCombineAndSettableAt() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.lead_hours").remove("combine");
    assertFault(content);
  }

  @Test
  void BUNDLE_53_keyHasNoOtherMember() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.lead_hours").put("description", "text");
    assertFault(content);
  }

  @Test
  void BUNDLE_54_settableAtIsInScopeOrder() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.lead_hours").put("settableAt", arr("division", "global"));
    assertFault(content, "BUNDLE-54");
  }

  @Test
  void BUNDLE_55_MERGE_4_commutativeKeyMayNotDeclareADefault() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.limit").put("default", 3);
    assertFault(content, "MERGE-4");
  }

  @Test
  void BUNDLE_57_combineAcrossAxesIsPresentExactlyWhenOrderDependentAndAtAnAxis() {
    Map<String, Object> missing = richContent();
    mapAt(missing, "keys", "checkout.lead_hours").remove("combineAcrossAxes");
    assertFault(missing, "BUNDLE-57");
    Map<String, Object> extra = richContent();
    mapAt(extra, "keys", "checkout.window").put("combineAcrossAxes", "max");
    assertFault(extra, "BUNDLE-57");
  }

  @Test
  void BUNDLE_58_combineAcrossAxesIsOneOfTheFour() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.lead_hours").put("combineAcrossAxes", "average");
    assertFault(content);
  }

  @Test
  void BUNDLE_59_onSourceFailureIsPresentExactlyAtAServiceLevel() {
    Map<String, Object> missing = richContent();
    mapAt(missing, "keys", "checkout.allowed").remove("onSourceFailure");
    assertFault(missing, "BUNDLE-59");
    Map<String, Object> extra = richContent();
    mapAt(extra, "keys", "checkout.limit").put("onSourceFailure", "skip");
    assertFault(extra, "BUNDLE-59");
  }

  @Test
  void BUNDLE_60_onSourceFailureIsSkipOrDeny() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.allowed").put("onSourceFailure", "retry");
    assertFault(content);
  }

  @Test
  void BUNDLE_63_kindIsTheNameOfAKind() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.allowed").put("kind", 5);
    assertFault(content);
  }

  @Test
  void BUNDLE_64_BUNDLE_67_valuesNeverHoldAServiceLevel() {
    Map<String, Object> content = richContent();
    mapAt(content, "values").put("account", obj("acct-1", obj("checkout.allowed", true)));
    assertFault(content, "BUNDLE-67");
  }

  @Test
  void BUNDLE_66_rootLevelHasOnlyTheEmptyNodeKey() {
    Map<String, Object> content = richContent();
    mapAt(content, "values", "global").put("north", obj("checkout.lead_hours", 3));
    assertFault(content, "BUNDLE-66");
  }

  @Test
  void BUNDLE_74_setValueIsInUniverseOrder() {
    Map<String, Object> content = richContent();
    mapAt(content, "values", "global", "").put("checkout.methods", arr("PICKUP", "GROUND"));
    assertFault(content, "BUNDLE-74");
  }

  @Test
  void BUNDLE_75_enumValuesAreInCodePointOrder() {
    Map<String, Object> content = richContent();
    mapAt(content, "facts", "order.status", "type").put("values", arr("PENDING", "PAID"));
    assertFault(content, "BUNDLE-75");
  }

  @Test
  void BUNDLE_76_featureHasExactlyItsSevenMembers() {
    Map<String, Object> content = richContent();
    feature(content).put("note", "x");
    assertFault(content);
  }

  @Test
  void BUNDLE_78_hitPolicyIsFirst() {
    Map<String, Object> content = richContent();
    feature(content).put("hitPolicy", "LAST");
    assertFault(content);
  }

  @Test
  void BUNDLE_79_BUNDLE_80_ifFactMissingIsPresentAndValid() {
    Map<String, Object> missing = richContent();
    feature(missing).remove("ifFactMissing");
    assertFault(missing);
    Map<String, Object> invalid = richContent();
    feature(invalid).put("ifFactMissing", "MAYBE");
    assertFault(invalid, "FEAT-13");
  }

  @Test
  void BUNDLE_81_bindingSettableAtIsInLevelOrder() {
    Map<String, Object> content = richContent();
    feature(content).put("bindingSettableAt", arr("division", "global"));
    assertFault(content, "BUNDLE-81");
  }

  @Test
  void BUNDLE_82_returnsIsEnumOrObjectWithTheRightMembers() {
    Map<String, Object> both = richContent();
    feature(both).put("returns", obj("enum", arr("ALLOW"), "object", obj("a", obj("type", "boolean"))));
    assertFault(both, "FEAT-6");
    Map<String, Object> falseNullable = richContent();
    feature(falseNullable).put("returns", obj("enum", arr("ALLOW", "DENY"), "nullable", false));
    assertFault(falseNullable, "BUNDLE-82");
    Map<String, Object> objectNullable = richContent();
    feature(objectNullable).put("returns", obj("object", obj("a", obj("type", "boolean")), "nullable", true));
    assertFault(objectNullable);
  }

  @Test
  void BUNDLE_83_policyHasExactlyItsFourMembers() {
    Map<String, Object> content = richContent();
    policy(content).remove("requiredFacts");
    assertFault(content);
  }

  @Test
  void BUNDLE_85_policyDefaultIsAValidEffect() {
    Map<String, Object> content = richContent();
    policy(content).put("default", "MAYBE");
    assertFault(content, "FEAT-13");
  }

  @Test
  void BUNDLE_86_BUNDLE_87_settingIsValueOfOrListOfWithADefault() {
    Map<String, Object> both = richContent();
    mapAt(policy(both), "settings", "check_active").put("listOf", "customer.active");
    assertFault(both);
    Map<String, Object> noDefault = richContent();
    mapAt(policy(noDefault), "settings", "check_active").remove("default");
    assertFault(noDefault);
  }

  @Test
  void BUNDLE_88_valueOfDefaultHasTheTypeOfItsFact() {
    Map<String, Object> content = richContent();
    mapAt(policy(content), "settings", "check_active").put("default", "yes");
    assertFault(content);
  }

  @Test
  void BUNDLE_133_BUNDLE_89_listOfValueHasNoRepeats() {
    Map<String, Object> content = richContent();
    mapAt(policy(content), "settings", "allowed_states").put("default", arr("PAID", "PAID"));
    assertFault(content, "BUNDLE-133");
  }

  @Test
  void BUNDLE_90_listOfValueIsInBundleOrder() {
    Map<String, Object> content = richContent();
    mapAt(policy(content), "settings", "allowed_states").put("default", arr("PENDING", "PAID"));
    assertFault(content, "BUNDLE-90");
  }

  @Test
  void BUNDLE_136_BUNDLE_119_requiredFactsAreTheDistinctRuleFactsInOrder() {
    Map<String, Object> missing = richContent();
    policy(missing).put("requiredFacts", arr("customer.active"));
    assertFault(missing, "BUNDLE-136");
    Map<String, Object> unsorted = richContent();
    policy(unsorted).put("requiredFacts", arr("order.status", "customer.active"));
    assertFault(unsorted, "BUNDLE-92");
  }

  @Test
  void BUNDLE_94_ruleHasExactlyItsFourMembers() {
    Map<String, Object> content = richContent();
    listAt(policy(content), "rules").remove(0);
    listAt(policy(content), "rules").add(obj("id", "x", "when", arr(), "then", "ALLOW"));
    assertFault(content);
  }

  @Test
  void BUNDLE_132_ruleIdsAreUniqueInAPolicy() {
    Map<String, Object> content = richContent();
    mapAt(policy(content), "rules", 1).put("id", "active");
    assertFault(content, "BUNDLE-132");
  }

  @Test
  void BUNDLE_119_BUNDLE_96_conditionsAreSortedByFact() {
    Map<String, Object> content = richContent();
    java.util.Collections.reverse(listAt(policy(content), "rules", 2, "when"));
    assertFault(content, "BUNDLE-96");
  }

  @Test
  void BUNDLE_98_conditionHasExactlyFactOpAndValue() {
    Map<String, Object> content = richContent();
    mapAt(policy(content), "rules", 0, "when", 0).remove("op");
    assertFault(content);
  }

  @Test
  void BUNDLE_99_operatorIsOneOfTheFour() {
    Map<String, Object> content = richContent();
    mapAt(policy(content), "rules", 0, "when", 0).put("op", "gt");
    assertFault(content);
  }

  @Test
  void BUNDLE_102_eqLiteralHasTheTypeOfItsFact() {
    Map<String, Object> content = richContent();
    mapAt(policy(content), "rules", 0, "when", 0).put("value", "yes");
    assertFault(content);
  }

  @Test
  void BUNDLE_101_inOperandIsAListOrASettingReference() {
    Map<String, Object> content = richContent();
    mapAt(policy(content), "rules", 1, "when", 0).put("value", "PAID");
    assertFault(content, "BUNDLE-101");
  }

  @Test
  void BUNDLE_125_BUNDLE_104_settingReferenceNamesASettingOfThePolicy() {
    Map<String, Object> content = richContent();
    mapAt(policy(content), "rules", 1, "when", 0).put("value", "$nope");
    assertFault(content, "BUNDLE-125");
  }

  @Test
  void BUNDLE_120_factNamedByASettingIsDeclared() {
    Map<String, Object> content = richContent();
    mapAt(policy(content), "settings", "check_active").put("valueOf", "customer.missing");
    assertFault(content, "BUNDLE-120");
  }

  @Test
  void BUNDLE_120_factNamedByAConditionIsDeclared() {
    Map<String, Object> content = richContent();
    mapAt(policy(content), "rules", 0, "when", 0).put("fact", "customer.missing");
    assertFault(content, "BUNDLE-120");
  }

  @Test
  void BUNDLE_121_entityNamedByDecidedPerIsDeclared() {
    Map<String, Object> content = richContent();
    feature(content).put("decidedPer", "invoice");
    assertFault(content, "BUNDLE-121");
  }

  @Test
  void BUNDLE_121_entityNamedByAnEdgeIsDeclared() {
    Map<String, Object> content = richContent();
    mapAt(content, "entities", "customer").put("hasMany", arr("invoice"));
    assertFault(content, "BUNDLE-121");
  }

  @Test
  void BUNDLE_122_factEntityIsThePrefixOfItsName() {
    Map<String, Object> content = richContent();
    mapAt(content, "facts", "customer.active").put("entity", "order");
    assertFault(content, "BUNDLE-122");
  }

  @Test
  void BUNDLE_123_bindingPolicyIsDeclaredByTheFeature() {
    Map<String, Object> content = richContent();
    mapAt(feature(content), "bindings", "global", "").put("policy", "missing");
    assertFault(content, "BUNDLE-123");
  }

  @Test
  void BUNDLE_124_bindingSettingIsDeclaredByItsPolicy() {
    Map<String, Object> content = richContent();
    mapAt(feature(content), "bindings", "division", "south", "settings").put("bogus", arr("PAID"));
    assertFault(content, "BUNDLE-124");
  }

  @Test
  void BUNDLE_126_settingReferenceIsOnlyWhereRuleAllowsIt() {
    Map<String, Object> eqToList = richContent();
    mapAt(policy(eqToList), "rules", 0, "when", 0).put("value", "$allowed_states");
    assertFault(eqToList, "BUNDLE-126");
    Map<String, Object> insideList = richContent();
    mapAt(policy(insideList), "rules", 1, "when", 0).put("value", arr("$allowed_states"));
    assertFault(insideList, "BUNDLE-126");
  }

  @Test
  void BUNDLE_127_scopeNamesAreDeclared() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.lead_hours").put("settableAt", arr("global", "region"));
    assertFault(content, "BUNDLE-127");
  }

  @Test
  void BUNDLE_127_bindingsHoldOnlyLevels() {
    Map<String, Object> content = richContent();
    mapAt(feature(content), "bindings").put("seller", obj("acme", obj("policy", "credit", "settings", obj())));
    assertFault(content, "BUNDLE-106");
  }

  @Test
  void BUNDLE_128_bindingSettableAtHasTheRootAndNoServiceLevel() {
    Map<String, Object> noRoot = richContent();
    feature(noRoot).put("bindingSettableAt", arr("division"));
    assertFault(noRoot, "BUNDLE-128");
    Map<String, Object> service = richContent();
    feature(service).put("bindingSettableAt", arr("global", "account"));
    assertFault(service, "BUNDLE-128");
  }

  @Test
  void BUNDLE_129_nodeKeyIsAdmittedByTheClosedCatalog() {
    Map<String, Object> content = richContent();
    mapAt(content, "values", "division").put("west", obj("checkout.limit", 1));
    assertFault(content, "BUNDLE-129");
  }

  @Test
  void BUNDLE_129_emptyNodeKeyIsOnlyForTheRootLevel() {
    Map<String, Object> content = richContent();
    mapAt(content, "values", "division").put("", obj("checkout.limit", 1));
    assertFault(content, "BUNDLE-129");
  }

  @Test
  void BUNDLE_130_valueKeyIsDeclaredAndSettableAtItsScope() {
    Map<String, Object> undeclared = richContent();
    mapAt(undeclared, "values", "division", "south").put("checkout.unknown", 1);
    assertFault(undeclared, "BUNDLE-130");
    Map<String, Object> notSettable = richContent();
    mapAt(notSettable, "values", "division", "south").put("checkout.methods", arr("GROUND"));
    assertFault(notSettable, "BUNDLE-130");
  }

  @Test
  void BUNDLE_131_valueIsValidForItsKeyType() {
    Map<String, Object> wrongType = richContent();
    mapAt(wrongType, "values", "global", "").put("checkout.lead_hours", "six");
    assertFault(wrongType);
    Map<String, Object> outOfBounds = richContent();
    mapAt(outOfBounds, "values", "global", "").put("checkout.lead_hours", 500);
    assertFault(outOfBounds);
  }

  @Test
  void BUNDLE_131_bindingValueIsValidForItsFact() {
    Map<String, Object> content = richContent();
    mapAt(feature(content), "bindings", "division", "south", "settings").put("allowed_states", arr("SHIPPED"));
    assertFault(content);
  }

  @Test
  void BUNDLE_134_setValueHasNoRepeatAndNoMemberOutsideTheUniverse() {
    Map<String, Object> repeated = richContent();
    mapAt(repeated, "values", "global", "").put("checkout.methods", arr("PICKUP", "PICKUP"));
    assertFault(repeated, "BUNDLE-134");
    Map<String, Object> outside = richContent();
    mapAt(outside, "values", "global", "").put("checkout.methods", arr("AIR"));
    assertFault(outside, "BUNDLE-134");
  }

  @Test
  void BUNDLE_135_decimalStringIsCanonical() {
    Map<String, Object> trailingZero = richContent();
    mapAt(trailingZero, "values", "global", "").put("checkout.rate", "2.50");
    assertFault(trailingZero, "BUNDLE-135");
    Map<String, Object> negativeZero = richContent();
    mapAt(negativeZero, "values", "global", "").put("checkout.rate", "-0");
    assertFault(negativeZero, "BUNDLE-135");
  }

  @Test
  void BUNDLE_118_unknownTypeNameIsRejected() {
    Map<String, Object> content = richContent();
    mapAt(content, "facts", "customer.active", "type").put("type", "float");
    assertFault(content);
  }

  @Test
  void BUNDLE_118_MERGE_3_strategyMustAllowTheKeyType() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.rate").put("combine", "min");
    assertFault(content, "MERGE-3");
  }

  @Test
  void MERGE_10_startingValueOfADeepMergeKeyIsComplete() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.window").put("default", obj("open", "08:00"));
    assertFault(content, "TYPE-62");
  }

  @Test
  void MERGE_7_orderDependentKeyNeedsADefaultOrARootValue() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.lead_hours").remove("default");
    mapAt(content, "values", "global", "").remove("checkout.lead_hours");
    assertFault(content, "MERGE-7");
  }

  @Test
  void MERGE_11_MERGE_12_partialValuesOfADeepMergeKeyLoad() {
    Map<String, Object> content = richContent();
    mapAt(content, "values", "division", "south", "checkout.window").put("close", "16:00");
    assertLoads(content);
  }
}
