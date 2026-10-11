package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The validity rules of node IDs (SCOPE-33 to SCOPE-37). */
class HierarchyNodeIdTest {

  @Test
  void scope33_aNodeIdIsNonEmpty() {
    assertFalse(HierarchyNodeId.isValid(""));
    assertTrue(HierarchyNodeId.isValid("n"));
  }

  @Test
  void scope33_aNodeIdHasAtMost128CodePoints() {
    assertTrue(HierarchyNodeId.isValid("a".repeat(128)));
    assertFalse(HierarchyNodeId.isValid("a".repeat(129)));
  }

  @Test
  void scope33_codePointsNotUtf16Units() {
    // 128 supplementary code points are 256 UTF-16 units, and they are still a valid length.
    assertTrue(HierarchyNodeId.isValid("😀".repeat(128)));
    assertFalse(HierarchyNodeId.isValid("😀".repeat(129)));
  }

  @Test
  void scope34_aNodeIdHasNoControlCharacter() {
    assertFalse(HierarchyNodeId.isValid("a\u0000b"));
    assertFalse(HierarchyNodeId.isValid("a\u001Fb"));
    assertFalse(HierarchyNodeId.isValid("a\u007Fb"));
    assertFalse(HierarchyNodeId.isValid("a\u009Fb"));
    assertTrue(HierarchyNodeId.isValid("a b"));
  }

  @Test
  void scope35_aSourceIdIsNormalisedToNfcNotRejected() {
    // "e" followed by a combining acute accent is the decomposed form of "é".
    assertEquals("café", HierarchyNodeId.admissible("café").orElseThrow());
  }

  @Test
  void scope36_theLengthIsCheckedAfterNormalisation() {
    // 129 code points before NFC: one decomposed "é" (two code points) and 127 composed ones. After NFC there are 128.
    String written = "é" + "é".repeat(127);
    assertEquals(129, written.codePointCount(0, written.length()));
    assertEquals("é".repeat(128), HierarchyNodeId.admissible(written).orElseThrow());
  }

  @Test
  void scope37_anUnpairedSurrogateIsNotAValidNodeId() {
    assertFalse(HierarchyNodeId.isValid("a\uD800b"));
    assertFalse(HierarchyNodeId.isValid("a\uDC00b"));
    assertTrue(HierarchyNodeId.isValid("a𐀀b"));
    assertTrue(HierarchyNodeId.admissible("x\uD800").isEmpty());
  }
}
