package io.github.visplabs.tierwise.core.internal.yaml;

import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.at;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.assertNoFindings;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.load;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.root;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Paths and lines of the tree (FIND-19 to FIND-22), and the keys as written (FIND-20). */
class YamlStructureTest {

  @Test
  void rootIsAMappingAtTheEmptyPathOnLineOne() {
    PMap root = root(load("a: 1\n"));
    assertEquals(List.of(), root.path());
    assertEquals(1, root.line());
    assertNoFindings(load("a: 1\n"));
  }

  @Test
  void valueOfAnEntryBeginsOnTheLineOfItsKey() {
    PMap root = root(load("outer:\n  inner:\n    leaf: 1\n"));
    assertEquals(1, at(root, "outer").line());
    assertEquals(2, at(root, "outer", "inner").line());
    assertEquals(3, at(root, "outer", "inner", "leaf").line());
    assertEquals(List.of("outer", "inner", "leaf"), at(root, "outer", "inner", "leaf").path());
  }

  @Test
  void blockSequenceItemBeginsOnItsDashIndicator() {
    PMap root = root(load("list:\n  - first\n  -\n    second\n"));
    assertEquals(2, at(root, "list", 0).line());
    assertEquals(3, at(root, "list", 1).line());
    assertEquals("second", scalar(at(root, "list", 1)).text());
    assertEquals(List.of("list", 1), at(root, "list", 1).path());
  }

  @Test
  void itemWithACommentBetweenDashAndContentBeginsOnTheDash() {
    PMap root = root(load("list:\n  - # note\n    value\n  - two\n"));
    assertEquals(2, at(root, "list", 0).line());
    assertEquals(4, at(root, "list", 1).line());
  }

  @Test
  void itemWithACommentOnlyLineBeforeContentBeginsOnTheDash() {
    PMap root = root(load("list:\n  -\n    # about the value\n    value\n"));
    assertEquals(2, at(root, "list", 0).line());
  }

  @Test
  void flowSequenceItemBeginsOnItsFirstCharacter() {
    PMap root = root(load("list: [a,\n  b]\n"));
    assertEquals(1, at(root, "list", 0).line());
    assertEquals(2, at(root, "list", 1).line());
  }

  @Test
  void mappingItemInSequenceBeginsOnItsDashAndItsEntriesOnTheirKeys() {
    PMap root = root(load("items:\n  - name: a\n    size: 2\n"));
    assertEquals(2, at(root, "items", 0).line());
    assertEquals(2, at(root, "items", 0, "name").line());
    assertEquals(3, at(root, "items", 0, "size").line());
  }

  @Test
  void carriageReturnLinefeedAndLoneCarriageReturnEachCountAsOneBreak() {
    PMap root = root(load("a: 1\r\nb: 2\rc: 3\r\n"));
    assertEquals(1, at(root, "a").line());
    assertEquals(2, at(root, "b").line());
    assertEquals(3, at(root, "c").line());
  }

  @Test
  void unicodeLineSeparatorDoesNotBreakALine() {
    PMap root = root(load("a: \"x\u2028y\"\nb: 2\n"));
    assertEquals(2, at(root, "b").line());
  }

  @Test
  void astralCharactersBeforeAKeyDoNotShiftItsLine() {
    PMap root = root(load("a: \"\uD83D\uDE00\"\nb: 1\n"));
    assertEquals(2, at(root, "b").line());
  }

  @Test
  void pathSegmentsAreKeysAndSequencePositions() {
    PMap root = root(load("a:\n  - {k: v}\n  - w\n"));
    assertEquals(List.of("a", 0, "k"), at(root, "a", 0, "k").path());
    assertEquals(List.of("a", 1), at(root, "a", 1).path());
  }

  @Test
  void keysAreKeptAsWrittenAndNotNormalised() {
    String decomposed = "cafe\u0301";
    PMap root = root(load(decomposed + ": 1\n"));
    assertTrue(root.has(decomposed));
    assertEquals(List.of(decomposed), at(root, decomposed).path());
  }

  @Test
  void treeOfNestedSequencesKeepsEveryItem() {
    PMap root = root(load("grid:\n  - [1, 2]\n  - [3]\n"));
    assertEquals(2, ((PSeq) at(root, "grid")).items().size());
    assertEquals(2, ((PSeq) at(root, "grid", 0)).items().size());
    assertEquals(3L, scalar(at(root, "grid", 1, 0)).value());
  }
}
