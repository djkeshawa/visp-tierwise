package io.github.visplabs.tierwise.spring.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.spring.BundleFetch;
import io.github.visplabs.tierwise.spring.BundleSource;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;

class S3BundleSourceAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(S3BundleSourceAutoConfiguration.class));

  @Test
  void nothingIsCreatedWithoutTheSourceProperty() {
    runner.run(context -> {
      assertEquals(0, context.getBeansOfType(S3BundleSource.class).size());
      assertEquals(0, context.getBeansOfType(S3Client.class).size());
    });
  }

  @Test
  void nothingIsCreatedForAnotherScheme() {
    runner.withPropertyValues("tierwise.source=https://example.test/commerce.json").run(context -> {
      assertEquals(0, context.getBeansOfType(S3BundleSource.class).size());
      assertEquals(0, context.getBeansOfType(S3Client.class).size());
    });
  }

  @Test
  void anS3SourceCreatesTheSourceAndTheClientWithTheConfiguredRegionAndEndpoint() {
    runner.withPropertyValues(
            "tierwise.source=s3://bundles/prod/commerce.json",
            "tierwise.bundle.s3.aws-region=eu-west-1",
            "tierwise.bundle.s3.endpoint=http://localhost:4566",
            "tierwise.bundle.s3.path-style-access=true")
        .run(context -> {
          assertEquals(1, context.getBeansOfType(S3Client.class).size());
          S3BundleSource source = context.getBean(S3BundleSource.class);
          assertEquals(new S3Location("bundles", "prod/commerce.json"), source.location());
          assertEquals(1, context.getBeansOfType(BundleSource.class).size());
          assertSame(source, context.getBean(BundleSource.class));
          assertEquals("s3://bundles/prod/commerce.json", source.describe());
          S3BundleSourceProperties properties = context.getBean(S3BundleSourceProperties.class);
          assertEquals("eu-west-1", properties.awsRegion());
          assertEquals(URI.create("http://localhost:4566"), properties.endpoint());
          assertTrue(properties.pathStyleAccess());
          assertEquals(Duration.ofSeconds(30), properties.apiCallTimeout());
        });
  }

  @Test
  void theClientUsesTheConfiguredRegionEndpointAndTimeout() {
    runner.withPropertyValues("tierwise.source=s3://bundles/commerce.json",
            "tierwise.bundle.s3.aws-region=eu-west-1",
            "tierwise.bundle.s3.endpoint=http://localhost:4566",
            "tierwise.bundle.s3.api-call-timeout=12s")
        .run(context -> {
          S3Client client = context.getBean(S3Client.class);
          assertEquals("eu-west-1", client.serviceClientConfiguration().region().id());
          assertEquals(URI.create("http://localhost:4566"),
              client.serviceClientConfiguration().endpointOverride().orElseThrow());
          assertEquals(Duration.ofSeconds(12),
              client.serviceClientConfiguration().overrideConfiguration().apiCallTimeout().orElseThrow());
        });
  }

  @Test
  void theSchemeIsMatchedWithoutRegardToCase() {
    runner.withPropertyValues("tierwise.source=S3://bundles/commerce.json",
            "tierwise.bundle.s3.aws-region=eu-west-1")
        .run(context -> assertEquals(new S3Location("bundles", "commerce.json"),
            context.getBean(S3BundleSource.class).location()));
  }

  @Test
  void anApplicationSuppliedClientReplacesTheGeneratedOne() {
    runner.withUserConfiguration(ClientConfiguration.class)
        .withPropertyValues("tierwise.source=s3://bundles/commerce.json",
            "tierwise.bundle.s3.aws-region=eu-west-1")
        .run(context -> {
          assertEquals(1, context.getBeansOfType(S3Client.class).size());
          assertSame(context.getBean("suppliedClient"), context.getBean(S3Client.class));
          assertInstanceOf(FakeS3Client.class, context.getBean(S3Client.class));
        });
  }

  @Test
  void aBundleSourceSuppliedByTheApplicationReplacesTheS3Source() {
    runner.withUserConfiguration(OwnSourceConfiguration.class)
        .withPropertyValues("tierwise.source=s3://bundles/commerce.json",
            "tierwise.bundle.s3.aws-region=eu-west-1")
        .run(context -> {
          assertEquals(0, context.getBeansOfType(S3BundleSource.class).size());
          assertEquals(1, context.getBeansOfType(BundleSource.class).size());
          assertInstanceOf(OwnSource.class, context.getBean(BundleSource.class));
        });
  }

  @Test
  void aMalformedS3LocationFailsTheContextAtStartup() {
    runner.withPropertyValues("tierwise.source=s3://bundles",
            "tierwise.bundle.s3.aws-region=eu-west-1")
        .run(context -> {
          assertNotNull(context.getStartupFailure());
          assertInstanceOf(IllegalArgumentException.class, rootCause(context.getStartupFailure()));
        });
  }

  @Test
  void aNonPositiveTimeoutFailsTheContextAtStartup() {
    runner.withPropertyValues("tierwise.source=s3://bundles/commerce.json",
            "tierwise.bundle.s3.aws-region=eu-west-1",
            "tierwise.bundle.s3.api-call-timeout=0s")
        .run(context -> assertNotNull(context.getStartupFailure()));
    runner.withPropertyValues("tierwise.source=s3://bundles/commerce.json",
            "tierwise.bundle.s3.aws-region=eu-west-1",
            "tierwise.bundle.s3.api-call-timeout=-1s")
        .run(context -> assertNotNull(context.getStartupFailure()));
  }

  @Test
  void nothingIsCreatedWhenTheAwsSdkIsAbsent() {
    runner.withClassLoader(new FilteredClassLoader(S3Client.class))
        .withPropertyValues("tierwise.source=s3://bundles/commerce.json")
        .run(context -> {
          assertEquals(0, context.getBeansOfType(S3BundleSource.class).size());
          assertNull(context.getStartupFailure());
        });
  }

  @Test
  void theAutoConfigurationIsRegisteredForSpringBoot() {
    assertTrue(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
        .getCandidates().contains(S3BundleSourceAutoConfiguration.class.getName()));
  }

  private static Throwable rootCause(Throwable t) {
    Throwable current = t;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  @Configuration(proxyBeanMethods = false)
  static class ClientConfiguration {

    @Bean
    S3Client suppliedClient() {
      return new FakeS3Client();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class OwnSourceConfiguration {

    @Bean
    BundleSource ownSource() {
      return new OwnSource();
    }
  }

  static final class OwnSource implements BundleSource {

    @Override
    public String describe() {
      return "own";
    }

    @Override
    public BundleFetch fetch(String currentVersion) {
      return BundleFetch.notModified();
    }
  }
}
