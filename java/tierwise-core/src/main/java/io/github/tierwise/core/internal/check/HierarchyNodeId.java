package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.internal.project.Names;
import java.util.Optional;

/**
 * The validity rules of a node ID written in a source (SCOPE-33 to SCOPE-37, SCOPE-39).
 *
 * <p>The rules are applied to the NFC form of the ID, as SCOPE-36 orders them: the ID must be non-empty, at
 * most 128 code points, free of control characters U+0000 to U+001F and U+007F to U+009F, and free of
 * unpaired surrogates. A source ID that is not in NFC is normalised, not rejected (SCOPE-35).
 */
final class HierarchyNodeId {

  /** The greatest number of code points of a node ID (SCOPE-33). */
  static final int MAX_CODE_POINTS = 128;

  private HierarchyNodeId() {
  }

  /**
   * Normalises a source node ID and tells whether it is valid.
   *
   * @param written the ID as the source writes it
   * @return the NFC form when the ID is valid, otherwise empty
   */
  static Optional<String> admissible(String written) {
    String id = Names.nfc(written);
    return isValid(id) ? Optional.of(id) : Optional.empty();
  }

  /**
   * Tells whether a node ID in NFC meets the form rules of SCOPE-33, SCOPE-34 and SCOPE-37.
   *
   * @param id the NFC form
   * @return {@code true} when the ID is a valid node ID
   */
  static boolean isValid(String id) {
    if (id.isEmpty() || id.codePointCount(0, id.length()) > MAX_CODE_POINTS) {
      return false;
    }
    return !hasControlCharacter(id) && !hasUnpairedSurrogate(id);
  }

  /**
   * Tells whether a string contains a control character of SCOPE-34.
   *
   * @param text the text
   * @return {@code true} when a code point is in U+0000 to U+001F or U+007F to U+009F
   */
  static boolean hasControlCharacter(String text) {
    return text.codePoints().anyMatch(cp -> cp <= 0x1F || (cp >= 0x7F && cp <= 0x9F));
  }

  /**
   * Tells whether a string contains an unpaired surrogate code point (NORM-8, SCOPE-37).
   *
   * @param text the text
   * @return {@code true} when a surrogate is not part of a valid pair
   */
  static boolean hasUnpairedSurrogate(String text) {
    int length = text.length();
    for (int i = 0; i < length; i++) {
      char c = text.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 < length && Character.isLowSurrogate(text.charAt(i + 1))) {
          i++;
        } else {
          return true;
        }
      } else if (Character.isLowSurrogate(c)) {
        return true;
      }
    }
    return false;
  }
}
