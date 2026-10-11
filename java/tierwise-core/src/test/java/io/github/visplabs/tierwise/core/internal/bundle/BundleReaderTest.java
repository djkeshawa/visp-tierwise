package io.github.visplabs.tierwise.core.internal.bundle;

import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.assertCode;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.assertIntegrity;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.assertLoads;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.bundle;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.envelope;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.mapAt;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.obj;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.richContent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.json.CanonicalJson;
import io.github.visplabs.tierwise.core.internal.json.Sha256;
import io.github.visplabs.tierwise.core.internal.model.LoadedModel;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The envelope, version and digest checks of EVAL-14 (steps 1 to 3), with the clauses that define each one.
 */
class BundleReaderTest {

  private static final String ZERO_DIGEST = "sha256:" + "0".repeat(64);

  private static String text(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }

  private static byte[] bytesOf(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void EVAL_2_validBundleLoadsWithItsDigest() {
    Map<String, Object> content = richContent();
    LoadedModel loaded = BundleReader.read(bundle(content));
    assertEquals("sha256:" + Sha256.hexDigest(CanonicalJson.toBytes(content)), loaded.bundleVersion());
    assertEquals("1", loaded.model().bundleFormatVersion());
    assertEquals("0.1", loaded.model().specVersion());
    assertLoads(content);
  }

  @Test
  void BUNDLE_1_envelopeIsAnObjectWithTheTwoMembers() {
    assertIntegrity(bytesOf("[1,2]"), "BUNDLE-1");
    assertIntegrity(bytesOf("\"text\""), "BUNDLE-1");
  }

  @Test
  void BUNDLE_112_envelopeMayNotHaveAnyOtherMember() {
    Map<String, Object> envelope = obj("bundleVersion", ZERO_DIGEST, "content", richContent(), "note", "x");
    assertIntegrity(CanonicalJson.toBytes(envelope), "BUNDLE-112");
  }

  @Test
  void BUNDLE_116_envelopeWithoutContentIsIntegrity() {
    assertIntegrity(CanonicalJson.toBytes(obj("bundleVersion", ZERO_DIGEST)), "BUNDLE-1");
  }

  @Test
  void BUNDLE_4_noByteMayFollowTheSerialisation() {
    byte[] bytes = bundle(richContent());
    byte[] withNewline = new byte[bytes.length + 1];
    System.arraycopy(bytes, 0, withNewline, 0, bytes.length);
    withNewline[bytes.length] = '\n';
    assertIntegrity(withNewline, "BUNDLE-116");
  }

  @Test
  void BUNDLE_8_BUNDLE_2_insignificantWhitespaceIsRejected() {
    String spaced = text(bundle(richContent())).replaceFirst("\"content\":", "\"content\": ");
    assertIntegrity(bytesOf(spaced), "BUNDLE-116");
  }

  @Test
  void BUNDLE_9_envelopeMembersMustBeInUtf16Order() {
    String content = text(CanonicalJson.toBytes(richContent()));
    String unsorted = "{\"content\":" + content + ",\"bundleVersion\":\"" + ZERO_DIGEST + "\"}";
    assertIntegrity(bytesOf(unsorted), "BUNDLE-116");
  }

  @Test
  void BUNDLE_111_duplicateMemberAtDepthIsIntegrity() {
    String canonical = text(bundle(richContent()));
    String duplicated = canonical.replace("\"default\":4,", "\"default\":4,\"default\":4,");
    assertNotEquals(canonical, duplicated);
    assertIntegrity(bytesOf(duplicated), "BUNDLE-111");
  }

  @Test
  void BUNDLE_116_nonIntegerNumberIsIntegrity() {
    String canonical = text(bundle(richContent()));
    assertCode(bytesOf(canonical.replace("\"default\":4,", "\"default\":4.5,")), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_116_outOfRangeIntegerIsIntegrity() {
    String canonical = text(bundle(richContent()));
    assertCode(bytesOf(canonical.replace("\"default\":4,", "\"default\":9007199254740992,")),
        Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_13_escapedUnpairedSurrogateIsIntegrity() {
    String canonical = text(bundle(richContent()));
    assertCode(bytesOf(canonical.replace("\"capability\"", "\"\\ud800\"")), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_3_byteOrderMarkIsIntegrity() {
    byte[] bytes = bundle(richContent());
    byte[] withBom = new byte[bytes.length + 3];
    withBom[0] = (byte) 0xEF;
    withBom[1] = (byte) 0xBB;
    withBom[2] = (byte) 0xBF;
    System.arraycopy(bytes, 0, withBom, 3, bytes.length);
    assertCode(withBom, Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_116_bytesThatAreNotUtf8AreIntegrity() {
    assertCode(new byte[] {'{', (byte) 0xFF, '}'}, Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_115_unsupportedFormatVersionIsUnsupported() {
    Map<String, Object> content = richContent();
    content.put("bundleFormatVersion", "2");
    assertCode(bundle(content), Code.E_BUNDLE_UNSUPPORTED);
  }

  @Test
  void BUNDLE_115_unsupportedSpecVersionIsUnsupported() {
    Map<String, Object> content = richContent();
    content.put("specVersion", "0.2");
    assertCode(bundle(content), Code.E_BUNDLE_UNSUPPORTED);
  }

  @Test
  void BUNDLE_137_versionsAreComparedAsExactStrings() {
    Map<String, Object> content = richContent();
    content.put("specVersion", "0.1 ");
    assertCode(bundle(content), Code.E_BUNDLE_UNSUPPORTED);
    Map<String, Object> numeric = richContent();
    numeric.put("bundleFormatVersion", 1);
    assertCode(bundle(numeric), Code.E_BUNDLE_UNSUPPORTED);
  }

  @Test
  void EVAL_56_missingSpecVersionIsIntegrity() {
    Map<String, Object> content = richContent();
    content.remove("specVersion");
    assertCode(bundle(content), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void EVAL_56_missingFormatVersionIsIntegrity() {
    Map<String, Object> content = richContent();
    content.remove("bundleFormatVersion");
    assertCode(bundle(content), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void EVAL_56_nonStringSpecVersionIsUnsupported() {
    Map<String, Object> content = richContent();
    content.put("specVersion", 1);
    assertCode(bundle(content), Code.E_BUNDLE_UNSUPPORTED);
  }

  @Test
  void EVAL_14_versionCheckComesBeforeTheDigest() {
    Map<String, Object> content = richContent();
    content.put("bundleFormatVersion", "2");
    assertCode(envelope(ZERO_DIGEST, content), Code.E_BUNDLE_UNSUPPORTED);
  }

  @Test
  void EVAL_14_envelopeCheckComesBeforeTheVersions() {
    Map<String, Object> content = richContent();
    content.put("bundleFormatVersion", "2");
    Map<String, Object> envelope = obj("bundleVersion", ZERO_DIGEST, "content", content, "note", 1);
    assertCode(CanonicalJson.toBytes(envelope), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_117_firstFailingCheckDecidesTheCode() {
    Map<String, Object> content = richContent();
    content.put("specVersion", "0.2");
    String unsupported = text(bundle(content));
    assertCode(bytesOf(unsupported + "\n"), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_5_bundleVersionMustBeSha256WithSixtyFourLowerCaseDigits() {
    Map<String, Object> content = richContent();
    assertCode(envelope("sha256:" + "A".repeat(64), content), Code.E_BUNDLE_INTEGRITY);
    assertCode(envelope("sha256:" + "a".repeat(63), content), Code.E_BUNDLE_INTEGRITY);
    assertCode(envelope("md5:" + "a".repeat(64), content), Code.E_BUNDLE_INTEGRITY);
    assertCode(envelope(42, content), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_113_digestMustMatchTheContent() {
    Map<String, Object> original = richContent();
    String digest = "sha256:" + Sha256.hexDigest(CanonicalJson.toBytes(original));
    Map<String, Object> changed = richContent();
    mapAt(changed, "keys", "checkout.lead_hours").put("default", 5);
    assertCode(envelope(digest, changed), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_6_digestOfTheEnvelopeIsNotAccepted() {
    Map<String, Object> content = richContent();
    byte[] envelopeOnly = envelope(ZERO_DIGEST, content);
    String digestOfEnvelope = "sha256:" + Sha256.hexDigest(envelopeOnly);
    assertCode(envelope(digestOfEnvelope, content), Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void BUNDLE_138_stringsAreComparedAsReceivedAndNotNormalised() {
    Map<String, Object> content = richContent();
    mapAt(content, "keys", "checkout.allowed").put("kind", "cafe\u0301");
    assertLoads(content);
  }
}
