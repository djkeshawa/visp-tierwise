package io.github.visplabs.tierwise.core.internal.bundle;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.Model;
import java.util.Map;

/**
 * The versions check of EVAL-14 (step 2): BUNDLE-115 with the supported values of BUNDLE-137, and the
 * missing-member and non-string cases of EVAL-56.
 */
final class VersionCheck {

  private VersionCheck() {
  }

  /**
   * Requires that both version members are present and that they name a supported format. The check runs in
   * the order of ADR 0018: {@code bundleFormatVersion} completely first, then {@code specVersion}. A bundle of
   * a future format therefore reports {@code E_BUNDLE_UNSUPPORTED} even when it has no {@code specVersion}.
   *
   * @param content the {@code content} object
   * @throws BundleFault with {@code E_BUNDLE_INTEGRITY} when a version member is missing (EVAL-56), or with
   *     {@code E_BUNDLE_UNSUPPORTED} when a present value is not one of the supported strings (BUNDLE-115)
   */
  static void check(Map<String, Object> content) {
    requireSupported(content, "bundleFormatVersion", Model.FORMAT_VERSION);
    requireSupported(content, "specVersion", Model.SPEC_VERSION);
  }

  private static void requireSupported(Map<String, Object> content, String member, String supported) {
    if (!content.containsKey(member)) {
      throw BundleFault.integrity("content lacks " + member + " (EVAL-56)");
    }
    if (!supported.equals(content.get(member))) {
      throw new BundleFault(Code.E_BUNDLE_UNSUPPORTED,
          member + " is not the supported value (BUNDLE-115, BUNDLE-137)");
    }
  }
}
