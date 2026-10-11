package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The portable regular expression subset of naming patterns: LINT-17 and the search semantics of LINT-18. */
class NamingRegexTest {

  private static boolean valid(String pattern) {
    return NamingRegex.compile(pattern).valid();
  }

  private static boolean found(String pattern, String name) {
    return NamingRegex.compile(pattern).regex().orElseThrow().matches(name);
  }

  @Test
  void LINT_17_everyPatternOfRequirementsSection49IsInTheSubset() {
    for (String pattern : List.of("^[a-z]+(\\.[a-z_]+){2}$", "\\.(allowed|allow_[a-z_]+|allowed_[a-z_]+)$",
        "^[A-Z][A-Z0-9_]*$", "^[a-z][a-z0-9_]*$", "^[a-z]+\\.[a-z_]+$")) {
      assertTrue(valid(pattern), pattern);
    }
  }

  @Test
  void LINT_17_theEmptyPatternIsInvalid() {
    assertFalse(valid(""));
  }

  @Test
  void LINT_17_literalsAndTheDotAreInTheSubset() {
    assertTrue(valid("abc"));
    assertTrue(valid("a.c"));
    assertTrue(valid("é-1_x"));
  }

  @Test
  void LINT_17_aMetacharacterIsLiteralOnlyWhenEscaped() {
    assertTrue(valid("\\\\\\.\\[\\]\\(\\)\\{\\}\\|\\?\\*\\+\\^\\$\\-"));
    assertTrue(found("\\{\\}", "a{}b"));
    assertFalse(valid("}"));
    assertFalse(valid("]"));
    assertFalse(valid("a)"));
    assertFalse(valid("(a"));
  }

  @Test
  void LINT_17_aBackslashBeforeAnyOtherCharacterIsOutsideTheSubset() {
    assertFalse(valid("\\d"));
    assertFalse(valid("\\w+"));
    assertFalse(valid("\\s"));
    assertFalse(valid("\\n"));
    assertFalse(valid("\\p{L}"));
    assertFalse(valid("a\\"));
  }

  @Test
  void LINT_17_bracketClassesRangesAndNegatedClassesAreInTheSubset() {
    assertTrue(valid("[abc]"));
    assertTrue(valid("[a-z0-9_]"));
    assertTrue(valid("[^a-z]"));
    assertTrue(valid("[\\]\\[\\\\\\^\\-]"));
    assertTrue(found("[\\-]", "a-b"));
  }

  @Test
  void LINT_17_aMalformedOrUnsupportedClassIsInvalid() {
    assertFalse(valid("[abc"));
    assertFalse(valid("[]"));
    assertFalse(valid("[^]"));
    assertFalse(valid("[a-]"));
    assertFalse(valid("[-a]"));
    assertFalse(valid("[z-a]"));
    assertFalse(valid("[a^b]"));
    assertFalse(valid("[a[b]]"));
    assertFalse(valid("[[:alpha:]]"));
    assertFalse(valid("[a&&b]"));
    assertFalse(valid("[\\d]"));
  }

  @Test
  void LINT_17_groupsAndAlternationAreInTheSubset() {
    assertTrue(valid("(ab|cd)e"));
    assertTrue(valid("a|b|c"));
    assertTrue(found("(ab|cd)e", "xcde"));
    assertFalse(found("^(ab|cd)e$", "xcde"));
  }

  @Test
  void LINT_17_theQuantifiersAreInTheSubset() {
    for (String pattern : List.of("a?", "a*", "a+", "a{2}", "a{2,}", "a{2,4}", "(ab){3}", "[a-z]{1,}")) {
      assertTrue(valid(pattern), pattern);
    }
  }

  @Test
  void LINT_17_aRepeatWhoseLowerBoundExceedsItsUpperBoundIsInvalid() {
    assertFalse(valid("a{3,2}"));
  }

  @Test
  void LINT_17_aBrokenRepeatIsInvalid() {
    assertFalse(valid("a{"));
    assertFalse(valid("a{x}"));
    assertFalse(valid("a{,3}"));
    assertFalse(valid("a{2"));
    assertFalse(valid("{2}"));
  }

  @Test
  void LINT_17_aQuantifierNeedsSomethingToRepeat() {
    assertFalse(valid("*a"));
    assertFalse(valid("+"));
    assertFalse(valid("(*a)"));
    assertFalse(valid("a|*b"));
    assertFalse(valid("^*"));
  }

  @Test
  void LINT_17_lazyPossessiveAndStackedQuantifiersAreOutsideTheSubset() {
    assertFalse(valid("a+?"));
    assertFalse(valid("a*?"));
    assertFalse(valid("a++"));
    assertFalse(valid("a{2}{3}"));
    assertFalse(valid("a??"));
  }

  @Test
  void LINT_17_lookaroundBackreferencesAndInlineFlagsAreOutsideTheSubset() {
    assertFalse(valid("(?=a)"));
    assertFalse(valid("(?!a)"));
    assertFalse(valid("(?<=a)b"));
    assertFalse(valid("(?i)a"));
    assertFalse(valid("(a)\\1"));
  }

  @Test
  void LINT_18_aNameFailsOnlyWhenThePatternMatchesNoPartOfIt() {
    assertTrue(found("allowed", "checkout.is_allowed_here"));
    assertFalse(found("allowed", "checkout.is_denied"));
  }

  @Test
  void LINT_18_theAnchorsMatchOnlyAtTheStartAndTheEnd() {
    assertTrue(found("^ab", "abc"));
    assertFalse(found("^bc", "abc"));
    assertTrue(found("bc$", "abc"));
    assertFalse(found("ab$", "abc"));
  }

  @Test
  void LINT_18_theDollarAnchorDoesNotSkipAFinalLineTerminator() {
    assertFalse(found("^[a-z]+$", "abc\n"));
    assertFalse(found("c$", "abc\n"));
    assertTrue(found("^[a-z]+\n$", "abc\n"));
  }

  @Test
  void LINT_18_noLineTerminatorIsTreatedSpecially() {
    assertTrue(found("a.c", "a\nc"));
    assertTrue(found("^a", "a\nb"));
    assertFalse(found("^b", "a\nb"));
  }

  @Test
  void LINT_17_aPatternComparesCodePoints() {
    assertTrue(found("^.$", "😀"));
    assertTrue(found("^[😀-😏]$", "😊"));
  }

  @Test
  void LINT_18_theKeyPatternOfRequirementsSection49BehavesAsWritten() {
    String key = "^[a-z]+(\\.[a-z_]+){2}$";
    assertTrue(found(key, "checkout.shipping.allowed_methods"));
    assertFalse(found(key, "checkout.shipping"));
    assertFalse(found(key, "Checkout.shipping.allowed"));
    assertTrue(found("\\.(allowed|allow_[a-z_]+|allowed_[a-z_]+)$", "a.b.allow_pickup"));
  }
}
