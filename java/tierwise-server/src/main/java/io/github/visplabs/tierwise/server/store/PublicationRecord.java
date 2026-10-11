package io.github.visplabs.tierwise.server.store;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A publication record: the provenance of one publication of a bundle (BUNDLE-141 to BUNDLE-150). It has the
 * members of a provenance sidecar and is kept in the store, never in the bundle, so it never changes the
 * bundle bytes or the digest (BUNDLE-140, DET-3).
 *
 * <p>Several records may exist for one {@code bundleVersion} (BUNDLE-148), and a record is never replaced.
 *
 * @param bundleVersion the {@code bundleVersion} of the bundle this record describes
 * @param createdAt an RFC 3339 time in UTC with an uppercase {@code T}, a {@code Z} offset, seconds and no
 *     fraction (BUNDLE-150 (b))
 * @param gitCommit the full commit identifier, 40 or 64 lower-case hexadecimal digits, when the source has
 *     one (BUNDLE-150 (a))
 * @param publisher an opaque label that names the publisher; the server does not authenticate it
 *     (BUNDLE-150 (c))
 */
public record PublicationRecord(
    String bundleVersion, String createdAt, Optional<String> gitCommit, Optional<String> publisher) {

  private static final Pattern CREATED_AT = Pattern.compile(
      "([0-9]{4})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):[0-5][0-9]:(60|[0-5][0-9])Z");
  private static final Pattern GIT_COMMIT = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");

  /**
   * Validates the members against BUNDLE-141 and BUNDLE-150.
   *
   * @throws IllegalArgumentException when a member breaks one of those clauses
   */
  public PublicationRecord {
    BundleVersions.require(bundleVersion);
    Objects.requireNonNull(gitCommit, "gitCommit");
    Objects.requireNonNull(publisher, "publisher");
    checkCreatedAt(createdAt);
    gitCommit.ifPresent(PublicationRecord::checkGitCommit);
  }

  /**
   * Creates a record from the members of its JSON form.
   *
   * <p>The JSON form is the one of {@code provenance.schema.json}: an object with {@code createdAt} (required),
   * {@code gitCommit}, {@code publisher} and {@code bundleVersion}. Any other member is rejected
   * (BUNDLE-143). {@code bundleVersion} may be omitted, and then {@code version} is used; when it is given it
   * must equal {@code version} (BUNDLE-144).
   *
   * @param raw the parsed JSON object
   * @param version the {@code bundleVersion} of the bundle the record is attached to
   * @return the record
   * @throws IllegalArgumentException when the object breaks BUNDLE-141 to BUNDLE-144 or BUNDLE-150
   */
  public static PublicationRecord fromJson(Map<String, Object> raw, String version) {
    for (String name : raw.keySet()) {
      if (!name.equals("bundleVersion") && !name.equals("createdAt") && !name.equals("gitCommit")
          && !name.equals("publisher")) {
        throw new IllegalArgumentException("the record has the unknown member " + name);
      }
    }
    Object declared = raw.get("bundleVersion");
    if (raw.containsKey("bundleVersion")) {
      if (!(declared instanceof String text)) {
        throw new IllegalArgumentException("bundleVersion is not a string");
      }
      if (!text.equals(version)) {
        throw new IllegalArgumentException("bundleVersion differs from the version of the bundle");
      }
    }
    String createdAt = requiredString(raw, "createdAt");
    return new PublicationRecord(version, createdAt, optionalString(raw, "gitCommit"),
        optionalString(raw, "publisher"));
  }

  /**
   * Returns the JSON form of the record.
   *
   * @return an unmodifiable object with {@code bundleVersion}, {@code createdAt} and, when present,
   *     {@code gitCommit} and {@code publisher}
   */
  public Map<String, Object> toJson() {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("bundleVersion", bundleVersion);
    json.put("createdAt", createdAt);
    gitCommit.ifPresent(commit -> json.put("gitCommit", commit));
    publisher.ifPresent(label -> json.put("publisher", label));
    return Collections.unmodifiableMap(json);
  }

  private static String requiredString(Map<String, Object> raw, String name) {
    Object value = raw.get(name);
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    if (!(value instanceof String text)) {
      throw new IllegalArgumentException(name + " is not a string");
    }
    return text;
  }

  private static Optional<String> optionalString(Map<String, Object> raw, String name) {
    if (!raw.containsKey(name)) {
      return Optional.empty();
    }
    Object value = raw.get(name);
    if (!(value instanceof String text)) {
      throw new IllegalArgumentException(name + " is not a string");
    }
    return Optional.of(text);
  }

  private static void checkCreatedAt(String createdAt) {
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
    Matcher matcher = CREATED_AT.matcher(createdAt);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "createdAt is not an RFC 3339 time in UTC like 2026-01-31T09:30:00Z (no fraction, uppercase T and Z)");
    }
    try {
      LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
          Integer.parseInt(matcher.group(3)));
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("createdAt is not a real calendar date");
    }
  }

  private static void checkGitCommit(String commit) {
    if (!GIT_COMMIT.matcher(commit).matches()) {
      throw new IllegalArgumentException("gitCommit is not 40 or 64 lower-case hexadecimal digits");
    }
  }
}
