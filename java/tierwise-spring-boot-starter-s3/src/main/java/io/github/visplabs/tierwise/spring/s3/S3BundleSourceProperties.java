package io.github.visplabs.tierwise.spring.s3;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the S3 client that reads the bundle, under {@code tierwise.bundle.s3}. The location itself is
 * {@code tierwise.source}, for example {@code s3://bundles/commerce.json}.
 *
 * <p>Credentials come from the AWS SDK default provider chain. This module adds no credential setting.
 *
 * @param awsRegion the AWS region, for example {@code eu-west-1}; when {@code null} the SDK's region chain
 *     decides
 * @param endpoint an absolute endpoint override for S3-compatible stores; {@code null} for AWS
 * @param pathStyleAccess whether to address buckets in the path rather than in the host name; many
 *     S3-compatible stores need it
 * @param apiCallTimeout the timeout of one bundle read, retries included; it must be positive
 */
@ConfigurationProperties("tierwise.bundle.s3")
public record S3BundleSourceProperties(
    String awsRegion,
    URI endpoint,
    @DefaultValue("false") boolean pathStyleAccess,
    @DefaultValue("30s") Duration apiCallTimeout) {

  /**
   * Validates the settings.
   *
   * @throws IllegalArgumentException for a missing or non-positive timeout, or an endpoint that is not an
   *     absolute URI
   */
  public S3BundleSourceProperties {
    Objects.requireNonNull(apiCallTimeout, "apiCallTimeout");
    if (apiCallTimeout.isNegative() || apiCallTimeout.isZero()) {
      throw new IllegalArgumentException("tierwise.bundle.s3.api-call-timeout must be positive");
    }
    if (endpoint != null && !endpoint.isAbsolute()) {
      throw new IllegalArgumentException("tierwise.bundle.s3.endpoint must be an absolute URI");
    }
  }
}
