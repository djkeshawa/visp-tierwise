package io.github.tierwise.core.internal.yaml;

import io.github.tierwise.core.Code;

/**
 * The codes that a load reports for file-level faults and for values that a source cannot hold.
 *
 * <p>A source other than the manifest reports syntax, encoding, document-count and root-shape faults
 * as {@code E_PARSE} ([SRC-5], [SRC-6], [SRC-10]), and a float that plain data cannot hold as
 * {@code E_TYPE_MISMATCH}. The manifest reports the same faults as {@code E_MANIFEST} ([MANIFEST-2],
 * [SRC-5], [SRC-10]), and its float fault as {@code E_MANIFEST} ([MANIFEST-4]).
 *
 * @param shape the code for a file-level fault
 * @param type the code for a float that plain data cannot hold, and for a mapping key that is not a
 *     string
 */
record YamlCodes(String shape, String type) {

  /** The codes for a source that is not the manifest. */
  static final YamlCodes SOURCE = new YamlCodes(Code.E_PARSE, Code.E_TYPE_MISMATCH);

  /** The codes for the manifest. */
  static final YamlCodes MANIFEST = new YamlCodes(Code.E_MANIFEST, Code.E_MANIFEST);

  /**
   * Selects the codes for a source.
   *
   * @param manifest {@code true} for the manifest
   * @return the codes
   */
  static YamlCodes of(boolean manifest) {
    return manifest ? MANIFEST : SOURCE;
  }
}
