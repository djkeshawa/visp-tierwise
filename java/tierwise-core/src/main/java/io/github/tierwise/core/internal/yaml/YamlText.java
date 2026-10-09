package io.github.tierwise.core.internal.yaml;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Decodes the bytes of a source as UTF-8 and refuses a byte-order mark (SRC-10). */
final class YamlText {

  private YamlText() {
  }

  /**
   * Decodes a source.
   *
   * @param bytes the bytes of the source
   * @param codes the codes for a file-level fault
   * @return the text
   * @throws YamlFault when the source begins with a byte-order mark or is not valid UTF-8 (SRC-10)
   */
  static String decode(byte[] bytes, YamlCodes codes) {
    if (startsWithByteOrderMark(bytes)) {
      throw new YamlFault(codes.shape(), "the source begins with a byte-order mark (SRC-10)");
    }
    try {
      return StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      throw new YamlFault(codes.shape(), "the source is not valid UTF-8 (SRC-10)");
    }
  }

  private static boolean startsWithByteOrderMark(byte[] bytes) {
    return bytes.length >= 3
        && (bytes[0] & 0xFF) == 0xEF
        && (bytes[1] & 0xFF) == 0xBB
        && (bytes[2] & 0xFF) == 0xBF;
  }
}
