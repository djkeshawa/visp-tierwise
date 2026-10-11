package io.github.visplabs.tierwise.core.internal.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.TierwiseException;
import io.github.visplabs.tierwise.core.internal.json.CanonicalJson;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The version check order of EVAL-56 under ADR 0018: the format version completely, then the spec version. */
class VersionOrderTest {

  private static final String ZERO_DIGEST = "sha256:" + "0".repeat(64);

  private static String codeOf(Map<String, Object> content) {
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("bundleVersion", ZERO_DIGEST);
    envelope.put("content", content);
    return assertThrows(TierwiseException.class, () -> BundleReader.read(CanonicalJson.toBytes(envelope))).code();
  }

  @Test
  void EVAL_56_aFutureFormatIsUnsupportedEvenWithoutASpecVersion() {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("bundleFormatVersion", "2");
    assertEquals(Code.E_BUNDLE_UNSUPPORTED, codeOf(content));
  }

  @Test
  void EVAL_56_aMissingFormatVersionIsIntegrityEvenWithAnUnsupportedSpecVersion() {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("specVersion", "9.9");
    assertEquals(Code.E_BUNDLE_INTEGRITY, codeOf(content));
  }

  @Test
  void EVAL_56_aSupportedFormatWithoutASpecVersionIsIntegrity() {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("bundleFormatVersion", "1");
    assertEquals(Code.E_BUNDLE_INTEGRITY, codeOf(content));
  }

  @Test
  void EVAL_56_aSupportedFormatWithAnUnsupportedSpecVersionIsUnsupported() {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("bundleFormatVersion", "1");
    content.put("specVersion", "0.2");
    assertEquals(Code.E_BUNDLE_UNSUPPORTED, codeOf(content));
  }
}
