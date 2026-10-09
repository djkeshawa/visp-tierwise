package io.github.tierwise.spring.s3;

import io.github.tierwise.spring.BundleSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Creates the S3 bundle source when {@code tierwise.source} names an {@code s3://} location and the AWS
 * SDK is on the classpath (ADR 0020).
 *
 * <p>The beans are an {@link S3Client} and an {@link S3BundleSource}, which is a {@link BundleSource} that the
 * starter uses as its primary source. A {@link S3Client} that the application defines is used as it is. A
 * {@link BundleSource} that the application defines means this configuration creates no source, so the two
 * never compete. The starter refreshes the bundle and keeps the last-known-good one, so this configuration
 * creates no refresher.
 *
 * <p>The configuration runs before the starter's auto-configuration, so that the starter sees the source bean
 * when it decides whether to use its default source.
 */
@AutoConfiguration(beforeName = "io.github.tierwise.spring.TierwiseAutoConfiguration")
@ConditionalOnClass(S3Client.class)
@Conditional(OnS3BundleSourceCondition.class)
@EnableConfigurationProperties(S3BundleSourceProperties.class)
public class S3BundleSourceAutoConfiguration {

  /**
   * Creates the S3 client, unless the application supplies one.
   *
   * @param properties the {@code tierwise.bundle.s3} settings
   * @return the client
   */
  @Bean
  @ConditionalOnMissingBean(S3Client.class)
  public S3Client tierwiseBundleS3Client(S3BundleSourceProperties properties) {
    // The same timeout bounds the whole call and each socket operation, so that a stalled connection cannot
    // hold the synchronized refresh.
    UrlConnectionHttpClient.Builder http = UrlConnectionHttpClient.builder()
        .connectionTimeout(properties.apiCallTimeout())
        .socketTimeout(properties.apiCallTimeout());
    S3ClientBuilder builder = S3Client.builder()
        .httpClientBuilder(http)
        .overrideConfiguration(ClientOverrideConfiguration.builder()
            .apiCallTimeout(properties.apiCallTimeout())
            .build());
    if (properties.awsRegion() != null) {
      builder.region(Region.of(properties.awsRegion()));
    }
    if (properties.endpoint() != null) {
      builder.endpointOverride(properties.endpoint());
    }
    if (properties.pathStyleAccess()) {
      builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }
    return builder.build();
  }

  /**
   * Creates the source for the configured object.
   *
   * @param source the value of {@code tierwise.source}
   * @param client the S3 client
   * @return the source
   * @throws IllegalArgumentException when the location is malformed, which fails the context at startup
   */
  @Bean
  @ConditionalOnMissingBean(BundleSource.class)
  public S3BundleSource tierwiseBundleS3Source(
      @Value("${" + OnS3BundleSourceCondition.SOURCE_PROPERTY + "}") String source, S3Client client) {
    return new S3BundleSource(client, S3Location.parse(source));
  }
}
