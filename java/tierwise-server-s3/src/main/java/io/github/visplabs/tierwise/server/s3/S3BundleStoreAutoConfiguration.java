package io.github.visplabs.tierwise.server.s3;

import io.github.visplabs.tierwise.server.TierwiseServerAutoConfiguration;
import io.github.visplabs.tierwise.server.store.BundleStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * Registers the S3 bundle store when {@code tierwise.server.store=s3}.
 *
 * <p>It registers an {@link S3Client} and a {@link BundleStore}. Either bean is skipped when the application
 * defines its own. Settings come from {@link S3StoreProperties}.
 *
 * <p>The configuration runs before {@link TierwiseServerAutoConfiguration}, whose filesystem store is conditional
 * on the absence of any {@link BundleStore} bean. Ordering here is what makes the filesystem default back off when
 * S3 is selected. The ordering is covered by {@code S3BundleStoreAutoConfigurationTest}.
 */
@AutoConfiguration(before = TierwiseServerAutoConfiguration.class)
@ConditionalOnClass(S3Client.class)
@ConditionalOnProperty(prefix = "tierwise.server", name = "store", havingValue = "s3")
@EnableConfigurationProperties(S3StoreProperties.class)
public class S3BundleStoreAutoConfiguration {

  /**
   * Creates the S3 client from the configured region, endpoint and addressing style.
   *
   * @param properties the S3 settings
   * @return the client, closed by the container at shutdown
   */
  @Bean
  @ConditionalOnMissingBean
  S3Client tierwiseS3Client(S3StoreProperties properties) {
    S3ClientBuilder builder = S3Client.builder().forcePathStyle(properties.isPathStyleAccess());
    if (properties.getRegion() != null && !properties.getRegion().isBlank()) {
      builder.region(Region.of(properties.getRegion()));
    }
    if (properties.getEndpoint() != null) {
      builder.endpointOverride(properties.getEndpoint());
    }
    return builder.build();
  }

  /**
   * Creates the bundle store over the configured bucket and prefix.
   *
   * @param client the S3 client
   * @param properties the S3 settings
   * @return the store
   * @throws IllegalStateException when the bucket is not configured
   */
  @Bean
  @ConditionalOnMissingBean(BundleStore.class)
  BundleStore tierwiseS3BundleStore(S3Client client, S3StoreProperties properties) {
    return new S3BundleStore(client, properties.requireBucket(), properties.getPrefix());
  }
}
