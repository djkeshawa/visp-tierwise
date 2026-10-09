package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.json.CanonicalJson;
import io.github.tierwise.core.internal.json.JsonFormatException;
import io.github.tierwise.core.internal.json.StrictJson;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The envelope of a bundle after the strict parse (EVAL-14, step 1): the members {@code bundleVersion} and
 * {@code content} (BUNDLE-1, BUNDLE-112), the strict document rules (BUNDLE-111, BUNDLE-116), and the exact
 * byte form of BUNDLE-2 and BUNDLE-4.
 *
 * @param bundleVersion the envelope's {@code bundleVersion}, of any JSON type until the digest check
 * @param content the {@code content} object
 */
record Envelope(Object bundleVersion, Map<String, Object> content) {

  /**
   * Parses and checks the bytes of one bundle.
   *
   * @param bytes the exact bytes of the bundle
   * @return the envelope
   * @throws BundleFault with {@code E_BUNDLE_INTEGRITY} for every failure of this step
   */
  static Envelope parse(byte[] bytes) {
    Object root;
    try {
      root = StrictJson.parse(bytes);
    } catch (JsonFormatException e) {
      throw BundleFault.integrity(e.getMessage());
    }
    if (!(root instanceof Map<?, ?> envelope)) {
      throw BundleFault.integrity("a bundle is a JSON object (BUNDLE-1)");
    }
    for (Object name : envelope.keySet()) {
      if (!"bundleVersion".equals(name) && !"content".equals(name)) {
        throw BundleFault.integrity("the envelope has the member " + name + " (BUNDLE-112)");
      }
    }
    if (!envelope.containsKey("bundleVersion") || !envelope.containsKey("content")) {
      throw BundleFault.integrity("the envelope lacks bundleVersion or content (BUNDLE-1, BUNDLE-116)");
    }
    if (!(envelope.get("content") instanceof Map<?, ?>)) {
      throw BundleFault.integrity("content is not an object (BUNDLE-14)");
    }
    if (!Arrays.equals(CanonicalJson.toBytes(envelope), bytes)) {
      throw BundleFault.integrity("the bytes are not the JCS serialisation of the envelope (BUNDLE-2, BUNDLE-116)");
    }
    return new Envelope(envelope.get("bundleVersion"), copy((Map<?, ?>) envelope.get("content")));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> copy(Map<?, ?> content) {
    // The strict parser builds maps with String names only, so the cast cannot fail.
    return new LinkedHashMap<>((Map<String, Object>) content);
  }
}
