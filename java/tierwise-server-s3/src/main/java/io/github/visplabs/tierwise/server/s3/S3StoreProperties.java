package io.github.visplabs.tierwise.server.s3;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code tierwise.server.s3.*} settings of the S3 bundle store. They apply when {@code tierwise.server.store}
 * is {@code s3}.
 *
 * <p>Credentials are not set here. The AWS SDK's default provider chain supplies them (environment, profile,
 * web identity or instance role), so no secret lives in the configuration.
 */
@ConfigurationProperties("tierwise.server.s3")
public class S3StoreProperties {

  private String bucket;
  private String prefix = "";
  private String region;
  private URI endpoint;
  private boolean pathStyleAccess;

  /**
   * Returns the bucket that holds the store.
   *
   * @return the bucket, or null when not set
   */
  public String getBucket() {
    return bucket;
  }

  /**
   * Sets the bucket that holds the store.
   *
   * @param bucket the bucket name
   */
  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  /**
   * Returns the key prefix inside the bucket.
   *
   * @return the prefix, empty for none
   */
  public String getPrefix() {
    return prefix;
  }

  /**
   * Sets the key prefix inside the bucket.
   *
   * @param prefix the prefix, such as {@code tierwise/prod}
   */
  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  /**
   * Returns the AWS region.
   *
   * @return the region, or null to use the SDK's region chain
   */
  public String getRegion() {
    return region;
  }

  /**
   * Sets the AWS region.
   *
   * @param region the region, such as {@code eu-west-1}
   */
  public void setRegion(String region) {
    this.region = region;
  }

  /**
   * Returns the endpoint override, for S3-compatible services.
   *
   * @return the endpoint, or null for AWS
   */
  public URI getEndpoint() {
    return endpoint;
  }

  /**
   * Sets the endpoint override, for S3-compatible services.
   *
   * @param endpoint the endpoint URI
   */
  public void setEndpoint(URI endpoint) {
    this.endpoint = endpoint;
  }

  /**
   * Tells whether path-style addressing is forced, which some S3-compatible services need.
   *
   * @return whether path-style addressing is used
   */
  public boolean isPathStyleAccess() {
    return pathStyleAccess;
  }

  /**
   * Sets whether path-style addressing is forced.
   *
   * @param pathStyleAccess true for path-style addressing
   */
  public void setPathStyleAccess(boolean pathStyleAccess) {
    this.pathStyleAccess = pathStyleAccess;
  }

  /**
   * Returns the bucket, failing when it is not configured.
   *
   * @return the bucket name
   * @throws IllegalStateException when {@code tierwise.server.s3.bucket} is blank
   */
  public String requireBucket() {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException("tierwise.server.s3.bucket is required when tierwise.server.store is s3");
    }
    return bucket;
  }
}
