package io.github.tierwise.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Context;
import io.github.tierwise.core.EvaluationEvent;
import io.github.tierwise.core.EvaluationListener;
import io.github.tierwise.core.Tierwise;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TierwiseAutoConfigurationTest {

  @TempDir
  Path dir;

  private byte[] bundleA;
  private byte[] bundleB;
  private String versionA;
  private String versionB;
  private StubBundleServer server;

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

  /** The runner for the small test project, whose one service-sourced level is {@code tenant}. */
  private ApplicationContextRunner runner(String... properties) {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TierwiseAutoConfiguration.class))
        .withBean("tenantProvider", TierwiseProvider.class, () -> TierwiseProvider.of("tenant", nodeId -> Map.of()))
        .withPropertyValues("tierwise.refresh-interval=0")
        .withPropertyValues(properties);
  }

  private Path write(String name, byte[] bundle) throws IOException {
    return Files.write(dir.resolve(name), bundle);
  }

  private static boolean mentions(Throwable failure, String text) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      if (t.getMessage() != null && t.getMessage().contains(text)) {
        return true;
      }
    }
    return false;
  }

  private static void await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (!condition.getAsBoolean()) {
      assertTrue(System.nanoTime() < deadline, "timed out waiting for the condition");
      Thread.sleep(10);
    }
  }

  @Test
  void theDefaultsAreTheDocumentedOnes() {
    TierwiseProperties properties = new TierwiseProperties();

    assertTrue(properties.isEnabled());
    assertEquals(Duration.ofSeconds(30), properties.getRefreshInterval());
    assertEquals(Duration.ofMinutes(5), properties.getStalenessThreshold());
    assertEquals(Duration.ofMillis(200), properties.getProviderTimeout());
    assertEquals(LogLevel.DEBUG, properties.getDecisionLogLevel());
    assertTrue(properties.getProviderTimeouts().isEmpty());
  }

  @Test
  void propertiesBindWithRelaxedNames() throws IOException {
    Path file = write("b.json", bundleA);
    runner("tierwise.source=file:" + file, "tierwise.staleness-threshold=2m", "tierwise.PROVIDER_TIMEOUT=350ms",
        "tierwise.decision-log-level=info", "tierwise.provider-timeouts.tenant=1s", "tierwise.http.request-timeout=3s")
        .run(context -> {
          TierwiseProperties properties = context.getBean(TierwiseProperties.class);
          assertEquals(Duration.ofMinutes(2), properties.getStalenessThreshold());
          assertEquals(Duration.ofMillis(350), properties.getProviderTimeout());
          assertEquals(LogLevel.INFO, properties.getDecisionLogLevel());
          assertEquals(Duration.ofSeconds(1), properties.getProviderTimeouts().get("tenant"));
          assertEquals(Duration.ofSeconds(3), properties.getHttp().getRequestTimeout());
          assertEquals(Duration.ofMinutes(2), context.getBean(TierwiseEvaluators.class).stalenessThreshold());
        });
  }

  @Test
  void aFileSourceDefinesTheBeans() throws IOException {
    Path file = write("b.json", bundleA);

    runner("tierwise.source=file:" + file).run(context -> {
      assertTrue(context.getStartupFailure() == null, () -> String.valueOf(context.getStartupFailure()));
      TierwiseEvaluators evaluators = context.getBean(TierwiseEvaluators.class);
      assertEquals(versionA, evaluators.bundleVersion());
      assertEquals(10L, context.getBean(Resolver.class).resolve(Context.empty(), "limit.max_items").value());
      assertNotNull(context.getBean(DecisionEngine.class));
      assertSame(TierwiseObserver.NOOP, context.getBean(TierwiseObserver.class));
    });
  }

  @Test
  void aServerUrlReadsTheLatestBundleAndSendsConfiguredHeaders() {
    runner("tierwise.server.url=" + server.baseUrl() + "/", "tierwise.http.headers.Authorization=Bearer test-token")
        .run(context -> {
          assertEquals(versionA, context.getBean(TierwiseEvaluators.class).bundleVersion());
          StubBundleServer.Request request = server.requests().get(0);
          assertEquals("/v1/bundles/latest", request.path());
          assertEquals("Bearer test-token", request.authorization());
        });
  }

  @Test
  void anHttpUrlIsReadAsTheBundleItself() {
    runner("tierwise.source=" + server.latestUrl()).run(context ->
        assertEquals(versionA, context.getBean(TierwiseEvaluators.class).bundleVersion()));
  }

  @Test
  void aClasspathSourceIsRead() throws IOException {
    Path resources = Files.createDirectories(dir.resolve("cp/bundles"));
    Files.write(resources.resolve("b.json"), bundleA);
    try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.resolve("cp").toUri().toURL()}, getClass().getClassLoader())) {
      runner("tierwise.source=classpath:/bundles/b.json").withClassLoader(loader).run(context ->
          assertEquals(versionA, context.getBean(TierwiseEvaluators.class).bundleVersion()));
    }
  }

  @Test
  void startupFailsFastWithoutAnySource() {
    runner().run(context -> {
      assertNotNull(context.getStartupFailure());
      assertTrue(mentions(context.getStartupFailure(), "No Tierwise bundle source is configured"));
    });
  }

  @Test
  void startupFailsFastWhenNoBundleLoadsAndNoSnapshotIsConfigured() {
    server.stop();

    runner("tierwise.server.url=" + server.baseUrl()).run(context -> {
      assertNotNull(context.getStartupFailure());
      assertTrue(mentions(context.getStartupFailure(), "no snapshot is configured"), String.valueOf(context.getStartupFailure()));
      assertInstanceOf(BundleLoadException.class, rootBundleLoadFailure(context.getStartupFailure()));
    });
  }

  private static Throwable rootBundleLoadFailure(Throwable failure) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      if (t instanceof BundleLoadException) {
        return t;
      }
    }
    return failure;
  }

  @Test
  void aSnapshotLetsStartupSucceedWhileTheServerIsDown() throws IOException {
    Path snapshot = write("snapshot.json", bundleA);
    server.stop();

    runner("tierwise.server.url=" + server.baseUrl(), "tierwise.snapshot=file:" + snapshot).run(context -> {
      assertTrue(context.getStartupFailure() == null, () -> String.valueOf(context.getStartupFailure()));
      TierwiseEvaluators evaluators = context.getBean(TierwiseEvaluators.class);
      assertTrue(evaluators.isUsingSnapshot());
      assertEquals(versionA, evaluators.bundleVersion());
    });
  }

  @Test
  void aSnapshotAloneIsAValidConfiguration() throws IOException {
    Path snapshot = write("snapshot.json", bundleA);

    runner("tierwise.snapshot=file:" + snapshot).run(context -> {
      assertEquals(versionA, context.getBean(TierwiseEvaluators.class).bundleVersion());
      assertFalse(context.getBean(TierwiseEvaluators.class).isUsingSnapshot(), "it is the primary source");
    });
  }

  @Test
  void aSnapshotMustBeOnTheClasspathOrInAFile() {
    runner("tierwise.server.url=" + server.baseUrl(), "tierwise.snapshot=https://example.com/b.json").run(context ->
        assertTrue(mentions(context.getStartupFailure(), "tierwise.snapshot must be a classpath: or file: location")));
  }

  @Test
  void sourceAndServerUrlTogetherAreRejected() throws IOException {
    Path file = write("b.json", bundleA);

    runner("tierwise.source=file:" + file, "tierwise.server.url=" + server.baseUrl()).run(context ->
        assertTrue(mentions(context.getStartupFailure(), "Set only one of tierwise.source and tierwise.server.url")));
  }

  @Test
  void anUnsupportedSourceSchemeIsRejected() {
    runner("tierwise.source=ftp://host/b.json").run(context ->
        assertTrue(mentions(context.getStartupFailure(), "unsupported bundle location")));
  }

  @Test
  void aBundleSourceBeanIsThePrimarySource() throws IOException {
    Path ignored = write("ignored.json", bundleB);
    BundleSource custom = new BundleSource() {
      @Override
      public String describe() {
        return "custom";
      }

      @Override
      public BundleFetch fetch(String currentVersion) {
        return BundleFetch.of(bundleA, null);
      }
    };

    runner("tierwise.source=file:" + ignored).withBean("customSource", BundleSource.class, () -> custom).run(context -> {
      assertEquals(versionA, context.getBean(TierwiseEvaluators.class).bundleVersion());
      assertEquals("custom", context.getBean(TierwiseEvaluators.class).sourceDescription());
    });
  }

  @Test
  void twoBundleSourceBeansAreRejected() {
    BundleSource one = new FileBundleSource(Path.of("one.json"));
    BundleSource two = new FileBundleSource(Path.of("two.json"));

    runner().withBean("one", BundleSource.class, () -> one).withBean("two", BundleSource.class, () -> two).run(context ->
        assertTrue(mentions(context.getStartupFailure(), "More than one BundleSource bean")));
  }

  @Test
  void theAutoConfigurationCanBeSwitchedOff() throws IOException {
    Path file = write("b.json", bundleA);

    runner("tierwise.source=file:" + file, "tierwise.enabled=false").run(context -> {
      assertTrue(context.getStartupFailure() == null);
      assertTrue(context.getBeansOfType(TierwiseEvaluators.class).isEmpty());
      assertTrue(context.getBeansOfType(Resolver.class).isEmpty());
    });
  }

  @Test
  void beansTheApplicationDefinesBackOff() throws IOException {
    Path file = write("b.json", bundleA);
    Resolver none = new Resolver() {
      @Override
      public io.github.tierwise.core.Resolved resolve(Context context, String key) {
        return null;
      }

      @Override
      public io.github.tierwise.core.Resolved resolveLocal(Context context, String key) {
        return null;
      }
    };

    runner("tierwise.source=file:" + file).withBean("resolver", Resolver.class, () -> none).run(context -> {
      assertSame(none, context.getBean(Resolver.class));
      assertEquals(1, context.getBeansOfType(Resolver.class).size());
    });
  }

  @Test
  void invalidIntervalsFailStartup() throws IOException {
    Path file = write("b.json", bundleA);

    runner("tierwise.source=file:" + file, "tierwise.refresh-interval=-1s").run(context ->
        assertTrue(mentions(context.getStartupFailure(), "tierwise.refresh-interval must be zero or positive")));
    runner("tierwise.source=file:" + file, "tierwise.staleness-threshold=0s").run(context ->
        assertTrue(mentions(context.getStartupFailure(), "tierwise.staleness-threshold must be positive")));
  }

  @Test
  void theRefreshIntervalPropertySchedulesRefreshes() {
    runner("tierwise.server.url=" + server.baseUrl(), "tierwise.refresh-interval=50ms").run(context -> {
      TierwiseEvaluators evaluators = context.getBean(TierwiseEvaluators.class);
      assertEquals(versionA, evaluators.bundleVersion());
      server.publish(bundleB, versionB);

      await(() -> evaluators.bundleVersion().equals(versionB));

      assertEquals(20L, context.getBean(Resolver.class).resolve(Context.empty(), "limit.max_items").value());
    });
  }

  @Test
  void closingTheContextStopsTheRefresh() throws InterruptedException {
    int[] requests = new int[1];
    runner("tierwise.server.url=" + server.baseUrl(), "tierwise.refresh-interval=50ms").run(context ->
        requests[0] = server.requests().size());

    int afterClose = server.requests().size();
    Thread.sleep(300);

    assertEquals(afterClose, server.requests().size(), "no refresh after the context closed");
    assertTrue(requests[0] >= 1);
  }

  @Test
  void applicationListenersReceiveTheEvaluatorEvents() throws IOException {
    Path file = write("b.json", bundleA);
    List<EvaluationEvent> events = new CopyOnWriteArrayList<>();
    EvaluationListener listener = events::add;

    runner("tierwise.source=file:" + file).withBean("listener", EvaluationListener.class, () -> listener).run(context ->
        assertEquals(List.of(EvaluationEvent.Type.BUNDLE_LOADED), events.stream().map(EvaluationEvent::type).toList()));
    assertEquals(versionA, events.get(0).bundleVersion());
  }

  @Test
  void aListenerThatThrowsDoesNotStopTheOthersOrTheEvaluation() throws IOException {
    Path file = write("b.json", bundleA);
    List<EvaluationEvent> events = new CopyOnWriteArrayList<>();
    EvaluationListener failing = event -> {
      throw new IllegalStateException("listener failure");
    };

    runner("tierwise.source=file:" + file)
        .withBean("failing", EvaluationListener.class, () -> failing)
        .withBean("recording", EvaluationListener.class, () -> events::add)
        .run(context -> {
          assertTrue(context.getStartupFailure() == null);
          assertEquals(1, events.size());
        });
  }
}
