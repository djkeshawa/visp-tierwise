package io.github.visplabs.tierwise.server.s3;

import io.github.visplabs.tierwise.server.store.BundleVersions;
import io.github.visplabs.tierwise.server.store.PublicationRecord;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The object key layout of the S3 store. Every key lives under an optional prefix:
 *
 * <pre>
 * &lt;prefix&gt;/bundles/&lt;bundleVersion&gt;.json                             canonical bundle bytes
 * &lt;prefix&gt;/publications/&lt;bundleVersion&gt;/&lt;createdAt&gt;-&lt;n&gt;.json       publication records (JSON)
 * &lt;prefix&gt;/latest                                                 the latest pointer (version text)
 * </pre>
 *
 * <p>Versions are checked with {@link BundleVersions} and {@code createdAt} values with the rule of
 * {@link PublicationRecord} (BUNDLE-150 (b)), so the server and the store use one definition of each. {@code
 * createdAt} is UTC with whole seconds, which keeps keys in time order. {@code n} is the slot number of one record
 * among those that share a {@code createdAt}. It starts at 1 and has no leading zeros.
 */
final class S3KeyLayout {

  private static final String SUFFIX = ".json";
  private static final Pattern SLOT = Pattern.compile("[1-9][0-9]{0,8}");

  /**
   * A version that passes the form check. It is used only to ask {@link PublicationRecord} whether a {@code
   * createdAt} is valid, so the rule for timestamps is defined in one place.
   */
  private static final String PROBE_VERSION = "sha256:" + "0".repeat(64);

  private final String root;

  /**
   * Creates the layout under a prefix.
   *
   * @param prefix the key prefix; {@code null}, blank and {@code /} mean no prefix; leading and trailing slashes
   *     are ignored
   * @throws IllegalArgumentException when the prefix has an empty segment in the middle
   */
  S3KeyLayout(String prefix) {
    this.root = normalizePrefix(prefix);
  }

  /**
   * Normalizes a prefix to {@code ""} or {@code "segment/.../"}.
   *
   * @param prefix the configured prefix, possibly {@code null}
   * @return the root that every key starts with
   * @throws IllegalArgumentException when the prefix contains {@code //}
   */
  static String normalizePrefix(String prefix) {
    if (prefix == null) {
      return "";
    }
    String trimmed = prefix.replaceAll("^/+|/+$", "");
    if (trimmed.isEmpty()) {
      return "";
    }
    if (trimmed.contains("//")) {
      throw new IllegalArgumentException("prefix must not contain empty segments: " + prefix);
    }
    return trimmed + "/";
  }

  /**
   * Tells whether a string is a valid {@code createdAt} (RFC 3339, UTC, whole seconds).
   *
   * @param value the candidate, possibly {@code null}
   * @return whether {@link PublicationRecord} accepts the value as a {@code createdAt}
   */
  static boolean isCreatedAt(String value) {
    try {
      requireCreatedAt(value);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  /**
   * Returns a {@code createdAt} after checking it with the rule of {@link PublicationRecord}.
   *
   * @param value the candidate timestamp
   * @return the same value
   * @throws IllegalArgumentException when the value is not a valid {@code createdAt}
   */
  static String requireCreatedAt(String value) {
    // The record's constructor checks createdAt and nothing else here depends on the version.
    new PublicationRecord(PROBE_VERSION, value, Optional.empty(), Optional.empty());
    return value;
  }

  /**
   * Returns the key of a bundle's bytes.
   *
   * @param bundleVersion a valid version
   * @return the key
   * @throws IllegalArgumentException when the version is not a {@code bundleVersion}
   */
  String bundleKey(String bundleVersion) {
    return root + "bundles/" + BundleVersions.require(bundleVersion) + SUFFIX;
  }

  /**
   * Returns the key of the latest pointer.
   *
   * @return the key
   */
  String latestKey() {
    return root + "latest";
  }

  /**
   * Returns the common prefix of all publication records.
   *
   * @return the prefix, ending in a slash
   */
  String publicationsRoot() {
    return root + "publications/";
  }

  /**
   * Returns the prefix of one version's publication records.
   *
   * @param bundleVersion a valid version
   * @return the prefix, ending in a slash
   * @throws IllegalArgumentException when the version is not a {@code bundleVersion}
   */
  String publicationsPrefix(String bundleVersion) {
    return publicationsRoot() + BundleVersions.require(bundleVersion) + "/";
  }

  /**
   * Returns the key of one publication record.
   *
   * @param bundleVersion a valid version
   * @param createdAt a valid {@code createdAt}
   * @param slot the slot number, at least 1
   * @return the key
   * @throws IllegalArgumentException when an argument is invalid
   */
  String publicationKey(String bundleVersion, String createdAt, int slot) {
    if (slot < 1) {
      throw new IllegalArgumentException("slot must be at least 1: " + slot);
    }
    return publicationsPrefix(bundleVersion) + requireCreatedAt(createdAt) + "-" + slot + SUFFIX;
  }

  /**
   * Returns the version segment of a key under {@link #publicationsRoot()}.
   *
   * @param key a key from a listing of the publication records
   * @return the version named by the key's first segment, or empty when the key has no valid version segment
   */
  Optional<String> publicationVersionOf(String key) {
    String publications = publicationsRoot();
    if (!key.startsWith(publications)) {
      return Optional.empty();
    }
    String rest = key.substring(publications.length());
    int slash = rest.indexOf('/');
    if (slash < 0) {
      return Optional.empty();
    }
    String segment = rest.substring(0, slash);
    return BundleVersions.isValid(segment) ? Optional.of(segment) : Optional.empty();
  }

  /**
   * Parses the key of a publication record, accepting only the canonical spelling of the key.
   *
   * @param bundleVersion the version whose prefix the key must have
   * @param key the key to parse
   * @return the record's position, or empty when the key is not a canonical record key of the version
   * @throws IllegalArgumentException when the version is not a {@code bundleVersion}
   */
  Optional<PublicationSlot> parsePublicationKey(String bundleVersion, String key) {
    String prefix = publicationsPrefix(bundleVersion);
    if (!key.startsWith(prefix) || !key.endsWith(SUFFIX)) {
      return Optional.empty();
    }
    String stem = key.substring(prefix.length(), key.length() - SUFFIX.length());
    int dash = stem.lastIndexOf('-');
    if (dash < 0) {
      return Optional.empty();
    }
    String createdAt = stem.substring(0, dash);
    String slotText = stem.substring(dash + 1);
    if (!isCreatedAt(createdAt) || !SLOT.matcher(slotText).matches()) {
      return Optional.empty();
    }
    int number = Integer.parseInt(slotText);
    return publicationKey(bundleVersion, createdAt, number).equals(key)
        ? Optional.of(new PublicationSlot(createdAt, number))
        : Optional.empty();
  }

  /**
   * The position of a publication record: its {@code createdAt} and its slot number.
   *
   * @param createdAt the record's {@code createdAt}
   * @param number the slot number, at least 1
   */
  record PublicationSlot(String createdAt, int number) {

    /** Orders by {@code createdAt}, then by slot number. */
    static final Comparator<PublicationSlot> ORDER =
        Comparator.comparing(PublicationSlot::createdAt).thenComparingInt(PublicationSlot::number);
  }
}
