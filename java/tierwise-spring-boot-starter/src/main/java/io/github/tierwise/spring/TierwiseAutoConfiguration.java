package io.github.tierwise.spring;

import io.github.tierwise.core.EvaluationListener;
import io.github.tierwise.core.EvaluatorOptions;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration for Tierwise.
 *
 * <p>It defines {@link TierwiseEvaluators} (which loads the first bundle, so a context fails to start when
 * none can be loaded), {@link Resolver} and {@link DecisionEngine}, and, when Micrometer and a single
 * {@code MeterRegistry} bean exist, a {@link MicrometerTierwiseObserver}. Every bean backs off when the
 * application defines its own.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "tierwise", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TierwiseProperties.class)
public class TierwiseAutoConfiguration {

  /**
   * Creates the evaluators and loads the first bundle.
   *
   * @param properties the {@code tierwise.*} properties
   * @param sources a {@link BundleSource} bean, if the application or another module defines one
   * @param providers the provider beans, one per service-sourced level
   * @param listeners the application's own evaluation listeners
   * @param observer the observer bean, if any
   * @param resourceLoader the loader whose class loader serves {@code classpath:} locations
   * @return the started evaluators
   * @throws BundleLoadException when no bundle can be loaded
   * @throws IllegalStateException when the source properties are inconsistent
   * @throws IllegalArgumentException when a property has an invalid value
   */
  @Bean
  @ConditionalOnMissingBean
  public TierwiseEvaluators tierwiseEvaluators(
      TierwiseProperties properties,
      ObjectProvider<BundleSource> sources,
      ObjectProvider<TierwiseProvider> providers,
      ObjectProvider<EvaluationListener> listeners,
      ObjectProvider<TierwiseObserver> observer,
      ResourceLoader resourceLoader) {
    TierwiseObserver tierwiseObserver = observer.getIfAvailable(() -> TierwiseObserver.NOOP);
    Duration interval = properties.getRefreshInterval();
    if (interval == null || interval.isNegative()) {
      throw new IllegalArgumentException("tierwise.refresh-interval must be zero or positive");
    }
    Duration threshold = properties.getStalenessThreshold();
    if (threshold == null || threshold.isZero() || threshold.isNegative()) {
      throw new IllegalArgumentException("tierwise.staleness-threshold must be positive");
    }
    ClassLoader classLoader = resourceLoader.getClassLoader() != null
        ? resourceLoader.getClassLoader()
        : TierwiseAutoConfiguration.class.getClassLoader();
    BundleSource beanSource = sources.getIfUnique();
    if (beanSource == null && sources.stream().count() > 1) {
      throw new IllegalStateException("More than one BundleSource bean is defined; mark one as @Primary");
    }
    Sources chosen = chooseSources(properties, beanSource, classLoader);
    EvaluationListener listener = new ListenerAdapter(tierwiseObserver, listeners.orderedStream().toList());
    EvaluatorOptions options = evaluatorOptions(properties, providers.orderedStream().toList(), listener);
    return TierwiseEvaluators.start(
        chosen.primary(), chosen.snapshot(), options, interval, threshold, tierwiseObserver, Clock.systemUTC());
  }

  /**
   * Creates the resolver.
   *
   * @param evaluators the evaluators
   * @return a resolver that uses the evaluator current at each call
   */
  @Bean
  @ConditionalOnMissingBean
  public Resolver tierwiseResolver(TierwiseEvaluators evaluators) {
    return new DefaultResolver(evaluators);
  }

  /**
   * Creates the decision engine.
   *
   * @param evaluators the evaluators
   * @param observer the observer bean, if any
   * @param properties the {@code tierwise.*} properties
   * @return a decision engine that counts and logs each decision
   */
  @Bean
  @ConditionalOnMissingBean
  public DecisionEngine tierwiseDecisionEngine(
      TierwiseEvaluators evaluators, ObjectProvider<TierwiseObserver> observer, TierwiseProperties properties) {
    DecisionLogger logger = new DecisionLogger(
        properties.getDecisionLogLevel(), DecisionLogger.sink(LogFactory.getLog(DecisionEngine.class)));
    return new DefaultDecisionEngine(evaluators, observer.getIfAvailable(() -> TierwiseObserver.NOOP), logger);
  }

  private record Sources(BundleSource primary, BundleSource snapshot) {
  }

  private static Sources chooseSources(TierwiseProperties properties, BundleSource bean, ClassLoader classLoader) {
    String source = StringUtils.hasText(properties.getSource()) ? properties.getSource().trim() : null;
    String serverUrl = StringUtils.hasText(properties.getServer().getUrl()) ? properties.getServer().getUrl().trim() : null;
    String snapshot = StringUtils.hasText(properties.getSnapshot()) ? properties.getSnapshot().trim() : null;
    BundleSource snapshotSource = snapshot == null ? null : snapshotSource(snapshot, classLoader, properties);
    if (bean != null) {
      if ((source != null && BundleSources.isBuiltIn(source)) || serverUrl != null) {
        LogFactory.getLog(TierwiseAutoConfiguration.class).warn(
            "A BundleSource bean is defined, so tierwise.source and tierwise.server.url are ignored");
      }
      return new Sources(bean, snapshotSource);
    }
    if (source != null && serverUrl != null) {
      throw new IllegalStateException("Set only one of tierwise.source and tierwise.server.url");
    }
    if (source != null) {
      return new Sources(BundleSources.forLocation(source, classLoader, properties.getHttp()), snapshotSource);
    }
    if (serverUrl != null) {
      return new Sources(BundleSources.forServer(serverUrl, properties.getHttp()), snapshotSource);
    }
    if (snapshotSource != null) {
      return new Sources(snapshotSource, null);
    }
    throw new IllegalStateException("No Tierwise bundle source is configured. Set tierwise.source, "
        + "tierwise.server.url or tierwise.snapshot, or define a BundleSource bean");
  }

  private static BundleSource snapshotSource(String snapshot, ClassLoader classLoader, TierwiseProperties properties) {
    String lower = snapshot.toLowerCase(java.util.Locale.ROOT);
    if (!(lower.startsWith("classpath:") || lower.startsWith("file:"))) {
      throw new IllegalArgumentException("tierwise.snapshot must be a classpath: or file: location");
    }
    return BundleSources.forLocation(snapshot, classLoader, properties.getHttp());
  }

  private static EvaluatorOptions evaluatorOptions(
      TierwiseProperties properties, List<TierwiseProvider> providers, EvaluationListener listener) {
    Duration fallback = requireTimeout("tierwise.provider-timeout", properties.getProviderTimeout());
    Map<String, Duration> byLevel = properties.getProviderTimeouts();
    for (Map.Entry<String, Duration> entry : byLevel.entrySet()) {
      requireTimeout("tierwise.provider-timeouts." + entry.getKey(), entry.getValue());
    }
    EvaluatorOptions.Builder options = EvaluatorOptions.builder().listener(listener);
    List<String> seen = new ArrayList<>();
    for (TierwiseProvider provider : providers) {
      String level = provider.level();
      Duration timeout = byLevel.get(level);
      if (timeout == null) {
        timeout = provider.timeout().map(suggested -> requireTimeout("the timeout of the provider for level " + level, suggested)).orElse(fallback);
      }
      seen.add(level);
      options.provider(level, provider, timeout);
    }
    for (String level : byLevel.keySet()) {
      if (!seen.contains(level)) {
        LogFactory.getLog(TierwiseAutoConfiguration.class).warn(
            "tierwise.provider-timeouts." + level + " is set, but no TierwiseProvider bean serves that level");
      }
    }
    return options.build();
  }

  private static Duration requireTimeout(String name, Duration timeout) {
    if (timeout == null || timeout.isZero() || timeout.isNegative() || timeout.toNanos() % 1_000_000L != 0) {
      throw new IllegalArgumentException(name + " must be a positive whole number of milliseconds, was " + timeout);
    }
    return timeout;
  }

  /** The Micrometer part, loaded only when Micrometer is on the classpath. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
  static class MetricsConfiguration {

    /**
     * Creates the Micrometer observer when exactly one (or one primary) {@code MeterRegistry} bean exists.
     * Without a registry the observer ignores every event.
     *
     * @param registries the registry beans
     * @return the observer
     */
    @Bean
    @ConditionalOnMissingBean(TierwiseObserver.class)
    TierwiseObserver tierwiseMicrometerObserver(ObjectProvider<MeterRegistry> registries) {
      MeterRegistry registry = registries.getIfUnique();
      return registry == null ? TierwiseObserver.NOOP : new MicrometerTierwiseObserver(registry);
    }
  }
}
