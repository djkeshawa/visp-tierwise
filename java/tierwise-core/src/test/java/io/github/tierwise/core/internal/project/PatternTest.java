package io.github.tierwise.core.internal.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Glob patterns (SRC-18) and path patterns (SRC-22, SRC-23). */
class PatternTest {

  private static boolean glob(String pattern, String path) {
    return GlobPattern.parse(pattern).matches(path);
  }

  @Test
  void SRC_18_starMatchesARunOfCharactersWithinOneSegment() {
    assertTrue(glob("schema/*.yaml", "schema/core.yaml"));
    assertTrue(glob("schema/*.yaml", "schema/.yaml"));
    assertTrue(glob("schema/c*e.yaml", "schema/core.yaml"));
    assertFalse(glob("schema/*.yaml", "schema/sub/core.yaml"));
    assertFalse(glob("schema/*.yaml", "schema/core.yml"));
    assertTrue(glob("*/core.yaml", "schema/core.yaml"));
    assertTrue(glob("a*b*c", "aXXbYYc"));
    assertFalse(glob("a*b*c", "aXXcYYb"));
  }

  @Test
  void SRC_18_doubleStarAsAWholeSegmentMatchesZeroOrMoreDirectories() {
    assertTrue(glob("schema/**/core.yaml", "schema/core.yaml"));
    assertTrue(glob("schema/**/core.yaml", "schema/a/core.yaml"));
    assertTrue(glob("schema/**/core.yaml", "schema/a/b/core.yaml"));
    assertTrue(glob("**/core.yaml", "core.yaml"));
    assertTrue(glob("**/*.yaml", "a/b/c.yaml"));
    assertFalse(glob("schema/**/core.yaml", "other/core.yaml"));
    assertFalse(glob("schema/**/core.yaml", "schema/core.yaml.bak"));
  }

  @Test
  void SRC_18_matchingIsCaseSensitiveAndHiddenFilesAreIncluded() {
    assertFalse(glob("schema/*.yaml", "schema/Core.YAML"));
    assertFalse(glob("Schema/*.yaml", "schema/core.yaml"));
    assertTrue(glob("schema/*.yaml", "schema/.draft.yaml"));
    assertTrue(glob("**/*.yaml", ".hidden/.draft.yaml"));
  }

  @Test
  void SRC_18_otherCharactersAreLiteralAndMatchByCodePoint() {
    assertTrue(glob("a?b/[x].yaml", "a?b/[x].yaml"));
    assertFalse(glob("a?b/[x].yaml", "aXb/x.yaml"));
    assertTrue(glob("café/*", "café/x"));
    assertFalse(glob("café/*", "café/x"));
    assertTrue(glob("😀*", "😀😀"));
  }

  @Test
  void aLeadingDotSegmentAndDoubledSlashesAreIgnoredInAPattern() {
    assertTrue(glob("./schema//core.yaml", "schema/core.yaml"));
  }

  @Test
  void theDirectoryPrefixIsTheLiteralLeadingDirectories() {
    assertEquals("schema", GlobPattern.parse("schema/**/core.yaml").directoryPrefix());
    assertEquals("a/b", GlobPattern.parse("a/b/*.yaml").directoryPrefix());
    assertEquals("", GlobPattern.parse("*.yaml").directoryPrefix());
    assertEquals("a", GlobPattern.parse("a/b*/c.yaml").directoryPrefix());
    assertEquals("a/b", GlobPattern.parse("a/b/c.yaml").directoryPrefix());
  }

  // ---- path patterns -------------------------------------------------------------------------------

  private static PathPattern path(String pattern) {
    return PathPattern.parse(pattern).orElseThrow(() -> new AssertionError(pattern + " should be valid"));
  }

  @Test
  void SRC_22_placeholdersMatchARunWithinOneSegmentAndTheRestMatchesItself() {
    PathPattern pattern = path("values/{scope}/{node}.yaml");
    PathPattern.Captures captures = pattern.match("values/division/south.yaml").orElseThrow();
    assertEquals("division", captures.scope());
    assertEquals("south", captures.node());
    assertTrue(pattern.match("values/division/south.yml").isEmpty());
    assertTrue(pattern.match("values/division/sub/south.yaml").isEmpty());
    assertTrue(pattern.match("other/division/south.yaml").isEmpty());
    assertTrue(pattern.match("values/south.yaml").isEmpty());
  }

  @Test
  void SRC_22_aPlaceholderMayShareASegmentWithLiteralText() {
    PathPattern pattern = path("v/{scope}-values/n_{node}.yaml");
    PathPattern.Captures captures = pattern.match("v/region-values/n_a.b.yaml").orElseThrow();
    assertEquals("region", captures.scope());
    assertEquals("a.b", captures.node().substring(0, 3));
  }

  @Test
  void SRC_23_anEmptyCaptureIsAMatch() {
    PathPattern.Captures captures = path("values/{scope}/{node}.yaml").match("values//.yaml").orElseThrow();
    assertEquals("", captures.scope());
    assertEquals("", captures.node());
  }

  @Test
  void SRC_22_theCapturedTextNeverContainsASlashAndLiteralTextIsNotARegularExpression() {
    PathPattern pattern = path("a.b/{scope}/{node}.yaml");
    assertTrue(pattern.match("aXb/s/n.yaml").isEmpty());
    assertTrue(pattern.match("a.b/s/n.yaml").isPresent());
    PathPattern.Captures dotted = pattern.match("a.b/s/n.x.yaml").orElseThrow();
    assertEquals("n.x", dotted.node());
  }

  @Test
  void SRC_23_exactlyOneScopeAndOneNodeInDifferentSegmentsAreRequired() {
    assertTrue(PathPattern.parse("values/{node}.yaml").isEmpty());
    assertTrue(PathPattern.parse("values/{scope}.yaml").isEmpty());
    assertTrue(PathPattern.parse("{scope}/{scope}/{node}.yaml").isEmpty());
    assertTrue(PathPattern.parse("{scope}/{node}/{node}.yaml").isEmpty());
    assertTrue(PathPattern.parse("values/{scope}{node}.yaml").isEmpty());
    assertTrue(PathPattern.parse("values/{scope}/{node}.yaml").isPresent());
    assertTrue(PathPattern.parse("{node}/{scope}.yaml").isPresent());
  }

  @Test
  void SRC_23_anotherPlaceholderOrAWildcardIsRefused() {
    for (String bad : new String[] {"v/{scope}/{node}/{other}.yaml", "v/{scope}/{node}*.yaml", "v/*/{scope}/{node}.yaml",
        "v/{scope}/{node}?.yaml", "v/[a]/{scope}/{node}.yaml", "v/{scope}/{node}.yaml}", "v/{ scope}/{node}.yaml"}) {
      assertTrue(PathPattern.parse(bad).isEmpty(), bad);
    }
  }

  @Test
  void theDirectoryPrefixOfAPathPatternStopsAtTheFirstPlaceholder() {
    assertEquals("values", path("values/{scope}/{node}.yaml").directoryPrefix());
    assertEquals("a/b", path("a/b/{scope}/{node}.yaml").directoryPrefix());
    assertEquals("", path("{scope}/{node}.yaml").directoryPrefix());
    assertEquals("a", path("a/x{scope}/{node}.yaml").directoryPrefix());
  }

  @Test
  void aNameSuggesterFindsTheClosestCandidate() {
    assertEquals(Optional.of("checkout.allowed"),
        NameSuggester.closest("checkout.alowed", java.util.List.of("checkout.allowed", "other.name.x")));
    assertEquals(Optional.of("values/no-op"), NameSuggester.closest("values/no-pp", java.util.List.of("values/no-op")));
    assertTrue(NameSuggester.closest("zzzzzz", java.util.List.of("checkout.allowed")).isEmpty());
    assertTrue(NameSuggester.closest("same", java.util.List.of("same")).isEmpty());
    assertEquals(Optional.of("ab"), NameSuggester.closest("ba", java.util.List.of("ab")), "a transposition costs one");
  }

  @Test
  void orderingFollowsCodePointsAndFind26() {
    assertTrue(CodePointOrder.compare("～", "😀") < 0, "U+FF5E sorts before U+1F600 by code point");
    assertTrue("～".compareTo("😀") > 0, "UTF-16 order would differ");
    assertTrue(PathOrder.compare(java.util.List.of("a"), java.util.List.of("a", "b")) < 0);
    assertTrue(PathOrder.compare(java.util.List.of("a", 2), java.util.List.of("a", 10)) < 0);
    assertTrue(PathOrder.compare(java.util.List.of("b"), java.util.List.of("a", 1)) > 0);
    assertEquals(0, PathOrder.compare(java.util.List.of(), java.util.List.of()));
  }
}
