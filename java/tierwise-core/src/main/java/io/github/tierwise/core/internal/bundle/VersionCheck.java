package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.model.Model;
import java.util.Map;

/**
 * The versions check of EVAL-14 (step 2): BUNDLE-115 with the supported values of BUNDLE-137, and the
 * missing-member and non-string cases of EVAL-56.
 */
final class VersionCheck {

  private VersionCheck() {
  }

  /**
   * Requires that both version members are present and that they name a supported format.
   *
   * @param content the {@code content} object
   * @throws BundleFault with {@code E_BUNDLE_INTEGRITY} when a version member is missing (EVAL-56), or with
   *     {@code E_BUNDLE_UNSUPPORTED} when a present value is not one of the supported strings (BUNDLE-115)
   */
  static void check(Map<String, Object> content) {
    // A missing member is integrity; the unsupported-value case follows once both members are present.
    // EVAL-14 does not order the two cases within this step, so this choice is recorded as an open point.
    if (!content.containsKey("bundleFormatVersion")) {
      throw BundleFault.integrity("content lacks bundleFormatVersion (EVAL-56)");
    }
    if (!content.containsKey("specVersion")) {
      throw BundleFault.integrity("content lacks specVersion (EVAL-56)");
    }
    if (!Model.FORMAT_VERSION.equals(content.get("bundleFormatVersion"))) {
      throw new BundleFault(Code.E_BUNDLE_UNSUPPORTED,
          "bundleFormatVersion is not the supported value (BUNDLE-115, BUNDLE-137)");
    }
    if (!Model.SPEC_VERSION.equals(content.get("specVersion"))) {
      throw new BundleFault(Code.E_BUNDLE_UNSUPPORTED, "specVersion is not the supported value (BUNDLE-115, BUNDLE-137)");
    }
  }
}
