package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.json.CanonicalJson;
import io.github.tierwise.core.internal.json.Sha256;
import io.github.tierwise.core.internal.model.Model;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a {@link Model} as canonical bundle bytes (BUNDLE-2, BUNDLE-6). The content is serialised once to take its
 * digest, which becomes the {@code bundleVersion}, and the envelope is serialised with the same content.
 * {@link BundleReader} reads the result back to an equal model, and the bytes are identical (DET-1).
 */
public final class BundleWriter {

  private BundleWriter() {
  }

  /**
   * The bytes of a bundle and the digest that names it.
   *
   * @param bundleVersion {@code sha256:} followed by the 64 lower-case hexadecimal digits of the content digest
   * @param bytes the exact bytes of the bundle, with no trailing newline (BUNDLE-4)
   */
  public record Written(String bundleVersion, byte[] bytes) {
  }

  /**
   * Writes a model.
   *
   * @param model the model
   * @return the bundle bytes and their {@code bundleVersion}
   */
  public static Written write(Model model) {
    Map<String, Object> content = ContentWriter.write(model);
    String bundleVersion = "sha256:" + Sha256.hexDigest(CanonicalJson.toBytes(content));
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("bundleVersion", bundleVersion);
    envelope.put("content", content);
    return new Written(bundleVersion, CanonicalJson.toBytes(envelope));
  }
}
