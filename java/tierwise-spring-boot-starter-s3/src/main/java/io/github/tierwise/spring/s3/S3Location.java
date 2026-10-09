package io.github.tierwise.spring.s3;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The bucket and key of one bundle object, written {@code s3://bucket/key} in configuration.
 *
 * <p>Everything after the first slash that follows the bucket is the key, so keys may contain further
 * slashes. The scheme is matched without regard to case, as URI schemes are.
 *
 * @param bucket the bucket name: one or more letters, digits, dots, hyphens or underscores
 * @param key the object key: not empty and free of control characters
 */
public record S3Location(String bucket, String key) {

  /** The scheme prefix of an S3 location. */
  public static final String SCHEME_PREFIX = "s3://";

  private static final Pattern BUCKET = Pattern.compile("[A-Za-z0-9._-]{1,255}");

  /**
   * Validates the components.
   *
   * @throws IllegalArgumentException when the bucket or the key is not acceptable
   */
  public S3Location {
    Objects.requireNonNull(bucket, "bucket");
    Objects.requireNonNull(key, "key");
    if (!BUCKET.matcher(bucket).matches()) {
      throw new IllegalArgumentException("Invalid S3 bucket name in " + SCHEME_PREFIX + "bucket/key");
    }
    if (key.isEmpty()) {
      throw new IllegalArgumentException("The S3 key in " + SCHEME_PREFIX + "bucket/key is empty");
    }
    if (key.chars().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("The S3 key contains a control character");
    }
  }

  /**
   * Tells whether a configured source names an S3 location. It does not check the rest of the value.
   *
   * @param value the value of {@code tierwise.source}, or {@code null}
   * @return {@code true} when the value starts with {@code s3://}, in any letter case
   */
  public static boolean isS3Source(String value) {
    return value != null && value.regionMatches(true, 0, SCHEME_PREFIX, 0, SCHEME_PREFIX.length());
  }

  /**
   * Parses {@code s3://bucket/key}.
   *
   * @param value the configured value
   * @return the location
   * @throws IllegalArgumentException when the value is not an S3 location or its parts are not acceptable
   */
  public static S3Location parse(String value) {
    Objects.requireNonNull(value, "value");
    if (!isS3Source(value)) {
      throw new IllegalArgumentException("Expected an s3:// location in tierwise.source");
    }
    String rest = value.substring(SCHEME_PREFIX.length());
    int slash = rest.indexOf('/');
    if (slash < 0) {
      throw new IllegalArgumentException("Expected s3://bucket/key in tierwise.source");
    }
    return new S3Location(rest.substring(0, slash), rest.substring(slash + 1));
  }

  /**
   * Returns the location in the configured form.
   *
   * @return {@code s3://bucket/key}
   */
  @Override
  public String toString() {
    return SCHEME_PREFIX + bucket + "/" + key;
  }
}
