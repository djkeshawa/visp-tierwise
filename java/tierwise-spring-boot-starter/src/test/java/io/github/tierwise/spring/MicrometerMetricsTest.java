package io.github.tierwise.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Context;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.Tierwise;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MicrometerMetricsTest {

  private byte[] bundleA;
  private byte[] bundleB;
  private String versionA;
  private String versionB;
  private StubBundleServer server;
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @BeforeEach
  void setUp() throws IOException {
    bundleA = TestBundles.limit(10);
    bundleB = TestBundles.limit(20);
    versionA = Tierwise.loadBundle(bundleA).bundleVersion();
    versionB = Tierwise.loadBundle(bundleB).bundleVersion();
    server = new StubBundleServer(bundleA, versionA);
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  private ApplicationContextRunner runner(String... properties) {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TierwiseAutoConfiguration.class))
        .withBean("meterRegistry", MeterRegistry.class, () -> registry)
        .withBean("tenantProvider", TierwiseProvider.class, () -> TierwiseProvider.of("tenant", nodeId -> Map.of()))
        .withPropertyValues("tierwise.refresh-interval=0", "tierwise.server.url=" + server.baseUrl())
        .withPropertyValues(properties);
  }

  private Gauge versionGauge(String version) {
    return registry.find("tierwise.bundle.version").tag("version", version).gauge();
  }

  @Test
  void theBundleVersionAndAgeAndFailureMetersExistAfterStartup() {
    runner().run(context -> {
      assertInstanceOf(MicrometerTierwiseObserver.class, context.getBean(TierwiseObserver.class));
      assertNotNull(versionGauge(versionA), "tierwise.bundle.version{version=<bundleVersion>}");
      assertEquals(1.0, versionGauge(versionA).value());
      Gauge age = registry.find("tierwise.bundle.age").gauge();
      assertNotNull(age);
      assertEquals("seconds", age.getId().getBaseUnit());
      assertTrue(age.value() >= 0 && age.value() < 60);
      assertEquals(0.0, registry.get("tierwise.refresh.failures").counter().count());
    });
  }

  @Test
  void onlyTheCurrentVersionIsRegisteredAfterASwap() {
    runner().run(context -> {
      server.publish(bundleB, versionB);

      assertEquals(RefreshResult.SWAPPED, context.getBean(TierwiseEvaluators.class).refresh());

      assertNull(versionGauge(versionA), "the old version's gauge is removed");
      assertNotNull(versionGauge(versionB));
      assertEquals(1, registry.find("tierwise.bundle.version").gauges().size());
    });
  }

  @Test
  void aFailedRefreshCountsAndTheVersionStaysTheSame() {
    runner().run(context -> {
      server.failWith(500);
      TierwiseEvaluators evaluators = context.getBean(TierwiseEvaluators.class);

      evaluators.refresh();
      evaluators.refresh();

      assertEquals(2.0, registry.get("tierwise.refresh.failures").counter().count());
      assertNotNull(versionGauge(versionA));
    });
  }

  @Test
  void theAgeGaugeFollowsTheStaleness() {
    TestClock clock = new TestClock();
    SimpleMeterRegistry local = new SimpleMeterRegistry();
    EvaluatorOptions options = EvaluatorOptions.builder().provider("tenant", nodeId -> Map.of(), Duration.ofMillis(200)).build();
    try (TierwiseEvaluators evaluators = TierwiseEvaluators.start(
        new BundleSource() {
          @Override
          public String describe() {
            return "fixture";
          }

          @Override
          public BundleFetch fetch(String currentVersion) throws IOException {
            if (currentVersion != null) {
              throw new IOException("down");
            }
            return BundleFetch.of(bundleA, null);
          }
        },
        null, options, Duration.ZERO, Duration.ofMinutes(5), new MicrometerTierwiseObserver(local), clock)) {
      assertEquals(0.0, local.get("tierwise.bundle.age").gauge().value());

      clock.advance(Duration.ofSeconds(90));
      evaluators.refresh();

      assertEquals(90.0, local.get("tierwise.bundle.age").gauge().value());
      assertEquals(1.0, local.get("tierwise.refresh.failures").counter().count());
    }
  }

  @Test
  void decisionsAreCountedByFeatureAndReason() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TierwiseAutoConfiguration.class))
        .withBean("meterRegistry", MeterRegistry.class, () -> registry)
        .withBean("tenantProvider", TierwiseProvider.class, () -> TierwiseProvider.of("tenant", nodeId -> Map.of()))
        .withBean("partnerProvider", TierwiseProvider.class, () -> TierwiseProvider.of("partner", nodeId -> Map.of()))
        .withBean("seatProvider", TierwiseProvider.class, () -> TierwiseProvider.of("seat", nodeId -> Map.of()))
        .withPropertyValues("tierwise.refresh-interval=0", "tierwise.source=file:" + TestBundles.specBundle("edge-providers.json"))
        .run(context -> {
          DecisionEngine engine = context.getBean(DecisionEngine.class);
          String feature = "order.express_allowed";

          engine.decide(Context.empty(), feature, FactSet.builder().fact("order.express", true).build());
          engine.decide(Context.empty(), feature, FactSet.builder().fact("order.express", true).build());
          engine.decide(Context.empty(), feature, FactSet.builder().fact("order.express", false).build());
          engine.decide(Context.empty(), feature, FactSet.empty());

          assertEquals(2.0, registry.get("tierwise.decisions").tag("feature", feature).tag("reason", "RULE").counter().count());
          assertEquals(1.0, registry.get("tierwise.decisions").tag("feature", feature).tag("reason", "DEFAULT").counter().count());
          assertEquals(1.0, registry.get("tierwise.decisions").tag("feature", feature).tag("reason", "MISSING_FACT").counter().count());
        });
  }

  @Test
  void providerFailuresAreCountedByLevelAndOutcome() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TierwiseAutoConfiguration.class))
        .withBean("meterRegistry", MeterRegistry.class, () -> registry)
        .withBean("tenantProvider", TierwiseProvider.class, () -> TierwiseProvider.of("tenant", nodeId -> {
          throw new IllegalStateException("down");
        }))
        .withBean("partnerProvider", TierwiseProvider.class, () -> TierwiseProvider.of("partner", nodeId -> Map.of("access.allowed", "not a boolean")))
        .withBean("seatProvider", TierwiseProvider.class, () -> TierwiseProvider.of("seat", nodeId -> Map.of()))
        .withPropertyValues("tierwise.refresh-interval=0", "tierwise.source=file:" + TestBundles.specBundle("edge-providers.json"))
        .run(context -> {
          Resolver resolver = context.getBean(Resolver.class);

          resolver.resolve(Context.builder().level("tenant", "t-1").build(), "access.allowed");
          resolver.resolve(Context.builder().level("tenant", "t-1").build(), "display.theme");
          resolver.resolve(Context.builder().level("partner", "p-1").build(), "access.allowed");

          assertEquals(2.0, registry.get("tierwise.provider.failures").tag("level", "tenant").tag("outcome", "unavailable").counter().count());
          assertEquals(1.0, registry.get("tierwise.provider.failures").tag("level", "partner").tag("outcome", "invalid").counter().count());
        });
  }

  @Test
  void withoutARegistryTheStarterWorksAndReportsNothing() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TierwiseAutoConfiguration.class))
        .withBean("tenantProvider", TierwiseProvider.class, () -> TierwiseProvider.of("tenant", nodeId -> Map.of()))
        .withPropertyValues("tierwise.refresh-interval=0", "tierwise.server.url=" + server.baseUrl())
        .run(context -> assertSame(TierwiseObserver.NOOP, context.getBean(TierwiseObserver.class)));
  }

  @Test
  void withSeveralRegistriesAndNoPrimaryTheStarterStillStarts() {
    runner().withBean("second", MeterRegistry.class, SimpleMeterRegistry::new).run(context -> {
      assertTrue(context.getStartupFailure() == null, () -> String.valueOf(context.getStartupFailure()));
      assertSame(TierwiseObserver.NOOP, context.getBean(TierwiseObserver.class));
    });
  }

  @Test
  void withoutMicrometerOnTheClasspathTheStarterStillStarts() {
    runner().withClassLoader(new FilteredClassLoader("io.micrometer")).run(context -> {
      assertTrue(context.getStartupFailure() == null, () -> String.valueOf(context.getStartupFailure()));
      assertEquals(versionA, context.getBean(TierwiseEvaluators.class).bundleVersion());
      assertTrue(context.getBeansOfType(TierwiseObserver.class).isEmpty(), "no observer bean without Micrometer");
    });
  }

  @Test
  void anApplicationObserverReplacesTheMicrometerOne() {
    RecordingObserver mine = new RecordingObserver();

    runner().withBean("observer", TierwiseObserver.class, () -> mine).run(context -> {
      assertSame(mine, context.getBean(TierwiseObserver.class));
      assertEquals(java.util.List.of(versionA), mine.activated);
      assertNull(registry.find("tierwise.bundle.version").gauge());
    });
  }
}
