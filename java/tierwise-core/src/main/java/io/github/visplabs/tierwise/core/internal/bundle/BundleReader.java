package io.github.visplabs.tierwise.core.internal.bundle;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.TierwiseException;
import io.github.visplabs.tierwise.core.internal.json.CanonicalJson;
import io.github.visplabs.tierwise.core.internal.json.Sha256;
import io.github.visplabs.tierwise.core.internal.model.LoadedModel;
import io.github.visplabs.tierwise.core.internal.model.Model;
import java.util.regex.Pattern;

/**
 * Reads the bytes of one bundle into a {@link LoadedModel}, applying the checks of EVAL-14 in order: strict parse
 * and envelope, versions, digest, and structure. The first failing check decides the code (BUNDLE-117).
 */
public final class BundleReader {

  private static final Pattern BUNDLE_VERSION = Pattern.compile("sha256:[0-9a-f]{64}");

  private BundleReader() {
  }

  /**
   * Loads the bytes of one bundle.
   *
   * @param bytes the exact bytes of the bundle
   * @return the loaded bundle; nothing in it was evaluated before the checks passed (BUNDLE-114)
   * @throws TierwiseException with {@code E_BUNDLE_INTEGRITY} or {@code E_BUNDLE_UNSUPPORTED}
   */
  public static LoadedModel read(byte[] bytes) {
    try {
      Envelope envelope = Envelope.parse(bytes);
      VersionCheck.check(envelope.content());
      String bundleVersion = verifyDigest(envelope);
      Model model = ContentReader.read(envelope.content());
      return new LoadedModel(bundleVersion, model);
    } catch (BundleFault fault) {
      throw new TierwiseException(fault.code(), fault.getMessage());
    } catch (IllegalArgumentException e) {
      // A model record rejected a combination that a reader did not check. The bytes are still not a valid bundle.
      throw new TierwiseException(Code.E_BUNDLE_INTEGRITY, "the bundle does not form a model: " + e.getMessage(), e);
    }
  }

  /**
   * Requires the digest of the content to be the {@code bundleVersion} (BUNDLE-5, BUNDLE-6, BUNDLE-113).
   */
  private static String verifyDigest(Envelope envelope) {
    if (!(envelope.bundleVersion() instanceof String declared) || !BUNDLE_VERSION.matcher(declared).matches()) {
      throw BundleFault.integrity("bundleVersion is not sha256: followed by 64 lower-case hexadecimal digits (BUNDLE-5)");
    }
    String actual = "sha256:" + Sha256.hexDigest(CanonicalJson.toBytes(envelope.content()));
    if (!actual.equals(declared)) {
      throw BundleFault.integrity("bundleVersion is not the digest of content (BUNDLE-113)");
    }
    return declared;
  }
}
