package io.github.visplabs.tierwise.spring;

import io.github.visplabs.tierwise.core.LevelValueProvider;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link LevelValueProvider} that names the service-sourced level it serves, so that it can be a Spring
 * bean. The auto-configuration registers every {@code TierwiseProvider} bean with the evaluator, with a
 * timeout chosen in this order: the property {@code tierwise.provider-timeouts.<level>}, then {@link
 * #timeout()}, then {@code tierwise.provider-timeout} (default 200 ms).
 *
 * <p>The bundle must declare exactly the levels that have a provider bean among its service-sourced
 * levels; the core evaluator rejects a missing provider ({@code E_PROVIDER_MISSING}) and an extra or
 * duplicate one ({@code E_EVALUATOR_OPTIONS}). At startup that fails the application. At refresh it fails
 * the refresh, and the current bundle stays in use.
 */
public interface TierwiseProvider extends LevelValueProvider {

  /**
   * Names the level this provider serves.
   *
   * @return the level name as the bundle declares it
   */
  String level();

  /**
   * Suggests a timeout for one call of this provider. A property overrides it.
   *
   * @return the timeout, or empty to use {@code tierwise.provider-timeout}
   */
  default Optional<Duration> timeout() {
    return Optional.empty();
  }

  /**
   * Wraps a provider function for a level.
   *
   * @param level the level name
   * @param provider the function that returns the values of a node
   * @return a provider bean with the default timeout
   */
  static TierwiseProvider of(String level, LevelValueProvider provider) {
    return of(level, provider, null);
  }

  /**
   * Wraps a provider function for a level, with a timeout.
   *
   * @param level the level name
   * @param provider the function that returns the values of a node
   * @param timeout the suggested timeout, or {@code null} for the default
   * @return a provider bean
   */
  static TierwiseProvider of(String level, LevelValueProvider provider, Duration timeout) {
    Objects.requireNonNull(level, "level");
    Objects.requireNonNull(provider, "provider");
    Optional<Duration> suggested = Optional.ofNullable(timeout);
    return new TierwiseProvider() {
      @Override
      public String level() {
        return level;
      }

      @Override
      public Optional<Duration> timeout() {
        return suggested;
      }

      @Override
      public java.util.Map<String, Object> valuesFor(String nodeId) throws Exception {
        return provider.valuesFor(nodeId);
      }
    };
  }
}
