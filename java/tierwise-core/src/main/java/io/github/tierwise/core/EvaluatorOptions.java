package io.github.tierwise.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Options for {@link Tierwise#evaluator(LoadedBundle, EvaluatorOptions)}: one provider with a timeout for
 * each service-sourced level, and an optional listener (EVAL-4).
 *
 * <p>The builder records registrations as given and does not judge them. Creating the evaluator checks
 * them and raises {@code E_EVALUATOR_OPTIONS} for a timeout that is missing or not positive, for a level
 * registered twice, and for a level that the bundle does not declare or that is not service-sourced
 * (EVAL-15, EVAL-27, EVAL-28, SVC-48). It raises {@code E_PROVIDER_MISSING} when a service-sourced level
 * has no provider.
 */
public final class EvaluatorOptions {

  private static final EvaluatorOptions NONE = new EvaluatorOptions(List.of(), null);

  private final List<ProviderRegistration> providers;
  private final EvaluationListener listener;

  private EvaluatorOptions(List<ProviderRegistration> providers, EvaluationListener listener) {
    this.providers = providers;
    this.listener = listener;
  }

  /**
   * One provider registration, exactly as the caller made it.
   *
   * @param level the level name
   * @param provider the provider
   * @param timeout the timeout for one provider call; {@code null} when none was given, which creating the
   *     evaluator rejects (SVC-48)
   */
  public record ProviderRegistration(String level, LevelValueProvider provider, Duration timeout) {

    /** Validates the level and the provider. */
    public ProviderRegistration {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(provider, "provider");
    }
  }

  /**
   * Returns empty options: no provider and no listener (EVAL-28).
   *
   * @return the empty options
   */
  public static EvaluatorOptions none() {
    return NONE;
  }

  /**
   * Starts a builder.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the registrations in the order they were made.
   *
   * @return an unmodifiable list; it may hold two registrations for one level
   */
  public List<ProviderRegistration> providers() {
    return providers;
  }

  /**
   * Returns the listener.
   *
   * @return the listener, or empty when none is configured
   */
  public Optional<EvaluationListener> listener() {
    return Optional.ofNullable(listener);
  }

  /** Builds {@link EvaluatorOptions}. */
  public static final class Builder {

    private final List<ProviderRegistration> providers = new ArrayList<>();
    private EvaluationListener listener;

    private Builder() {
    }

    /**
     * Registers the provider of a service-sourced level.
     *
     * @param level the level name
     * @param provider the provider
     * @param timeout the timeout of one provider call; it must be positive for the evaluator to accept it
     * @return this builder
     */
    public Builder provider(String level, LevelValueProvider provider, Duration timeout) {
      providers.add(new ProviderRegistration(level, provider, timeout));
      return this;
    }

    /**
     * Sets the listener.
     *
     * @param eventListener the listener, or {@code null} for none
     * @return this builder
     */
    public Builder listener(EvaluationListener eventListener) {
      this.listener = eventListener;
      return this;
    }

    /**
     * Builds the options.
     *
     * @return the options
     */
    public EvaluatorOptions build() {
      return new EvaluatorOptions(Collections.unmodifiableList(new ArrayList<>(providers)), listener);
    }
  }
}
