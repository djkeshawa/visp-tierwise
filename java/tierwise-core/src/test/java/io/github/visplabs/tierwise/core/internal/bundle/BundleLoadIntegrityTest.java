package io.github.visplabs.tierwise.core.internal.bundle;

import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.assertCode;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.assertLoads;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.bundle;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.envelope;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.mapAt;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.obj;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.richContent;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.TierwiseException;
import io.github.visplabs.tierwise.core.internal.json.CanonicalJson;
import io.github.visplabs.tierwise.core.internal.json.Sha256;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The byte, parse and envelope faults of BUNDLE-3, BUNDLE-13 and BUNDLE-116, and the check order of BUNDLE-117 and
 * EVAL-14. Conformance vectors cannot hold these faults (VEC-71, VEC-72, VEC-80), so these tests are their enforcers
 * (spec/conformance/enforcers.d/E7.yaml). Where a case changes only the form of the bytes, the parsed content is
 * unchanged, so the digest still matches and the form is the only fault.
 */
class BundleLoadIntegrityTest {

  private static final String WRONG_DIGEST = "sha256:" + "0".repeat(64);

  private static String text(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static byte[] bytesOf(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static String baseText() {
    return text(bundle(richContent()));
  }

  /** Replaces the first occurrence, and fails if the text does not contain it. */
  private static String replaceOnce(String text, String from, String to) {
    int at = text.indexOf(from);
    assertTrue(at >= 0, "the bundle contains " + from);
    return text.substring(0, at) + to + text.substring(at + from.length());
  }

  private static byte[] concat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] part : parts) {
      out.writeBytes(part);
    }
    return out.toByteArray();
  }

  private static void assertIntegrity(String text) {
    assertCode(bytesOf(text), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void baseBundleLoadsSoEachCaseHasOneFault() {
    assertDoesNotThrow(() -> BundleReader.read(bytesOf(baseText())));
  }

  @Test
  void BUNDLE_3_rejectsByteOrderMark() {
    byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    assertCode(concat(bom, bundle(richContent())), Code.E_BUNDLE_INTEGRITY);
    assertCode(concat(bundle(richContent()), bom), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_13_rejectsEscapedUnpairedSurrogate() {
    String text = baseText();
    for (String escape : List.of("\\ud800", "\\udbff", "\\udc00", "\\udfff", "\\ud800x", "\\ud800\\u0041",
        "a\\udc00\\ud800")) {
      assertIntegrity(replaceOnce(text, "\"Active customer\"", "\"" + escape + "\""));
    }
    assertIntegrity(replaceOnce(text, "\"south\":{", "\"\\ud800\":{"));
  }

  @Test
  void BUNDLE_116_rejectsInputThatIsNotUtf8() {
    byte[] bytes = bundle(richContent());
    int at = baseText().indexOf("Active");
    for (byte[] bad : List.of(new byte[] {(byte) 0xFF}, new byte[] {(byte) 0xC0, (byte) 0xAF},
        new byte[] {(byte) 0xE0, (byte) 0x80, (byte) 0xAF}, new byte[] {(byte) 0xED, (byte) 0xA0, (byte) 0x80},
        new byte[] {(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80}, new byte[] {(byte) 0x80},
        new byte[] {(byte) 0xC3})) {
      byte[] broken = concat(java.util.Arrays.copyOfRange(bytes, 0, at), bad,
          java.util.Arrays.copyOfRange(bytes, at + 1, bytes.length));
      assertCode(broken, Code.E_BUNDLE_INTEGRITY);
    }
  }

  @Test
  void BUNDLE_116_rejectsInputThatIsNotWellFormedJson() {
    String text = baseText();
    for (String broken : List.of(
        "",
        text.substring(0, text.length() - 1),
        text.substring(1),
        text + "}",
        text + text,
        replaceOnce(text, "\"Active customer\"", "'Active customer'"),
        replaceOnce(text, "\"Active customer\"", "\"Active\u0001customer\""),
        replaceOnce(text, "\"Active customer\"", "\"Active customer"),
        replaceOnce(text, "\"max\":168", "\"max\":NaN"),
        replaceOnce(text, "\"max\":168", "\"max\":/* c */168"),
        replaceOnce(text, "\"Active customer\"", "\"Active \\x41 customer\""),
        replaceOnce(text, "[\"north\",\"south\"]", "[\"north\",\"south\",]"))) {
      assertIntegrity(broken);
    }
  }

  @Test
  void BUNDLE_116_rejectsEnvelopeWithoutBundleVersionOrContent() {
    Map<String, Object> content = richContent();
    String version = "sha256:" + Sha256.hexDigest(CanonicalJson.toBytes(content));
    assertCode(CanonicalJson.toBytes(obj("content", content)), Code.E_BUNDLE_INTEGRITY);
    assertCode(CanonicalJson.toBytes(obj("bundleVersion", version)), Code.E_BUNDLE_INTEGRITY);
    assertCode(bytesOf("{}"), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_116_BUNDLE_11_rejectsNegativeZeroFractionAndExponentEvenForAnIntegralValue() {
    String text = baseText();
    assertIntegrity(replaceOnce(text, "\"min\":0", "\"min\":-0"));
    for (String form : List.of("168.0", "1.68e2", "168e0", "168E0", "1.68e+2", "1680e-1", "0168", "+168")) {
      assertIntegrity(replaceOnce(text, "\"max\":168", "\"max\":" + form));
    }
  }

  @Test
  void BUNDLE_116_BUNDLE_12_rejectsIntegerOutsideTheSafeRange() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.limit", "type").put("max", 9007199254740991L);
    String text = text(bundle(content));
    assertDoesNotThrow(() -> BundleReader.read(bytesOf(text)), "the limit itself loads");
    for (String number : List.of("9007199254740992", "-9007199254740992", "18446744073709551616")) {
      assertIntegrity(replaceOnce(text, "9007199254740991", number));
    }
  }

  @Test
  void BUNDLE_116_BUNDLE_2_rejectsBytesThatAreNotExactlyTheJcsSerialisation() {
    String text = baseText();
    Map<String, Object> content = richContent();
    String version = "sha256:" + Sha256.hexDigest(CanonicalJson.toBytes(content));
    String contentText = text(CanonicalJson.toBytes(content));
    for (String broken : List.of(
        text + "\n",
        " " + text,
        replaceOnce(text, "\"content\":", "\"content\": "),
        replaceOnce(text, "[\"north\",\"south\"]", "[\"north\", \"south\"]"),
        "{\"content\":" + contentText + ",\"bundleVersion\":\"" + version + "\"}",
        replaceOnce(text, "\"axes\":[\"seller\"],\"levels\":[\"global\",\"division\",\"account\"]",
            "\"levels\":[\"global\",\"division\",\"account\"],\"axes\":[\"seller\"]"),
        replaceOnce(text, "\"Active customer\"", "\"\\u0041ctive customer\""),
        replaceOnce(text, "\"Active customer\"", "\"Active\\/customer\""),
        replaceOnce(text, "\"Active customer\"", "\"Active \\u00e9\""),
        replaceOnce(text, "\"Active customer\"", "\"\\ud83d\\ude00\""))) {
      assertIntegrity(broken);
    }
    Map<String, Object> literal = richContent();
    mapAt(literal, "features", "checkout.submission", "policies", "credit", "rules", 0)
        .put("name", "Active \u00e9/\uD83D\uDE00");
    assertTrue(text(bundle(literal)).contains("\"Active \u00e9/\uD83D\uDE00\""));
    assertLoads(literal);
  }

  @Test
  void BUNDLE_116_rejectsUpperCaseEscapeOfAControlCharacter() {
    Map<String, Object> content = richContent();
    mapAt(content, "features", "checkout.submission", "policies", "credit", "rules", 0)
        .put("name", "Active\u001Fcustomer");
    String text = text(bundle(content));
    assertDoesNotThrow(() -> BundleReader.read(bytesOf(text)), "the canonical lower-case escape loads");
    assertIntegrity(replaceOnce(text, "\\u001f", "\\u001F"));
  }

  // BUNDLE-117: a bundle that fails several checks raises the code of the first, in the order of EVAL-14.

  private static Map<String, Object> unsupported() {
    Map<String, Object> content = richContent();
    content.put("specVersion", "0.2");
    return content;
  }

  /** A structural fault of step 4 (BUNDLE-132). */
  private static void breakStructure(Map<String, Object> content) {
    mapAt(content, "features", "checkout.submission", "policies", "credit", "rules", 1).put("id", "active");
  }

  @Test
  void BUNDLE_117_parseOrEnvelopeFaultWinsOverUnsupportedVersion() {
    String text = text(bundle(unsupported()));
    assertCode(bytesOf(text), Code.E_BUNDLE_UNSUPPORTED);
    assertCode(bytesOf(text + "\n"), Code.E_BUNDLE_INTEGRITY);
    assertCode(concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, bytesOf(text)), Code.E_BUNDLE_INTEGRITY);
    assertCode(bytesOf(replaceOnce(text, "\"max\":168", "\"max\":168.0")), Code.E_BUNDLE_INTEGRITY);
    Map<String, Object> envelope = obj("bundleVersion", WRONG_DIGEST, "content", unsupported(), "extra", 1);
    assertCode(CanonicalJson.toBytes(envelope), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_117_unsupportedVersionWinsOverDigestAndStructure() {
    Map<String, Object> both = richContent();
    both.put("bundleFormatVersion", "2");
    breakStructure(both);
    assertCode(envelope(WRONG_DIGEST, unsupported()), Code.E_BUNDLE_UNSUPPORTED);
    assertCode(bundle(both), Code.E_BUNDLE_UNSUPPORTED);
    assertCode(envelope(WRONG_DIGEST, both), Code.E_BUNDLE_UNSUPPORTED);
  }

  @Test
  void BUNDLE_117_digestMismatchIsReportedBeforeAStructuralFault() {
    Map<String, Object> broken = richContent();
    breakStructure(broken);
    BundleFixtures.assertIntegrity(bundle(broken), "BUNDLE-132");
    TierwiseException both = assertThrows(TierwiseException.class,
        () -> BundleReader.read(envelope(WRONG_DIGEST, broken)));
    assertEquals(Code.E_BUNDLE_INTEGRITY, both.code());
    assertTrue(both.getMessage().contains("BUNDLE-113"), both.getMessage());
    assertFalse(both.getMessage().contains("BUNDLE-132"), both.getMessage());
  }

  @Test
  void BUNDLE_117_faultsOfEveryStepRaiseTheCodeOfStepOne() {
    Map<String, Object> all = unsupported();
    breakStructure(all);
    assertCode(bytesOf(text(envelope(WRONG_DIGEST, all)) + " "), Code.E_BUNDLE_INTEGRITY);
  }
}
