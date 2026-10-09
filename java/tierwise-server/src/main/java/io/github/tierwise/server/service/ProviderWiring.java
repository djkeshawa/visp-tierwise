package io.github.tierwise.server.service;

import io.github.tierwise.core.EvaluationEvent;
import io.github.tierwise.core.EvaluationListener;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.LevelValueProvider;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builds the {@link EvaluatorOptions} of the server from the {@link LevelValueProvider} beans of the
 * application (requirements section 9).
 *
 * <p>The bean name is the level name: a bean named {@code account} serves the service-sourced level
 * {@code account}. A service-sourced level without a bean gets a provider that always fails, so its keys
 * come back as {@code UNAVAILABLE} contributions with the {@code onSourceFailure} behaviour of the bundle,
 * which is what requirements section 9 states. A bean whose name is not a service-sourced level of the
 * bundle is ignored with a warning.
 */
final class ProviderWiring {

  private static final Logger LOG = LoggerFactory.getLogger(ProviderWiring.class);

  private final Map<String, LevelValueProvider> beans;
  private final Duration timeout;
  private final EvaluationListener listener;

  ProviderWiring(Map<String, LevelValueProvider> beans, Duration timeout, EvaluationListener listener) {
    this.beans = Map.copyOf(beans);
    this.timeout = Objects.requireNonNull(timeout, "timeout");
    this.listener = listener;
  }

  /**
   * Options for an evaluator that is shared by many requests. Its providers are called as they are.
   *
   * @param serviceLevels the service-sourced levels of the bundle
   */
  EvaluatorOptions shared(Set<String> serviceLevels) {
    EvaluatorOptions.Builder options = EvaluatorOptions.builder().listener(listener);
    for (String level : serviceLevels) {
      options.provider(level, providerFor(level), timeout);
    }
    return options.build();
  }

  /**
   * Options for the evaluator of one request. Each provider is called at most once for a node during the
   * request, however many keys the request resolves (SVC-19, SVC-20), and the "evaluator created" event is
   * not reported again.
   *
   * @param serviceLevels the service-sourced levels of the bundle
   * @param request the memory of this request
   */
  EvaluatorOptions forRequest(Set<String> serviceLevels, RequestCalls request) {
    EvaluatorOptions.Builder options = EvaluatorOptions.builder();
    if (listener != null) {
      options.listener(event -> {
        if (event.type() != EvaluationEvent.Type.BUNDLE_LOADED) {
          listener.onEvent(event);
        }
      });
    }
    for (String level : serviceLevels) {
      options.provider(level, request.remember(level, providerFor(level), timeout), timeout);
    }
    return options.build();
  }

  /** Warns about beans that name no service-sourced level of a bundle that is being loaded. */
  void warnAboutUnusedBeans(String bundleVersion, Set<String> serviceLevels) {
    for (String name : beans.keySet()) {
      if (!serviceLevels.contains(name)) {
        LOG.warn("The provider bean '{}' is ignored: {} has no service-sourced level of that name", name,
            bundleVersion);
      }
    }
  }

  private LevelValueProvider providerFor(String level) {
    LevelValueProvider bean = beans.get(level);
    if (bean != null) {
      return bean;
    }
    return node -> {
      throw new IllegalStateException("this server has no provider for the level " + level);
    };
  }

  /**
   * Remembers the outcome of every provider call made while one request is evaluated.
   *
   * <p>The whole request is one evaluation call (SVC-20), so it has one deadline per provider call: the
   * timeout counts from the start of the first call for a node (SVC-41), and a result that arrives after
   * it is not used for any key of the request (SVC-40). A key that is resolved later therefore sees the same
   * outcome as the key that made the call, a value or a failure, and never a late value.
   */
  static final class RequestCalls {

    /** A provider result and the time it became available. */
    private record Timed(Map<String, Object> values, long finishedNanos) {
    }

    /** The first call for a node: when it started and what it gave. */
    private record Call(CompletableFuture<Timed> outcome, long startNanos) {
    }

    private final Map<String, Call> calls = new ConcurrentHashMap<>();

    LevelValueProvider remember(String level, LevelValueProvider delegate, Duration timeout) {
      long timeoutNanos = timeout.toNanos();
      return node -> {
        String key = level + '\u0000' + node;
        Call mine = new Call(new CompletableFuture<>(), System.nanoTime());
        Call earlier = calls.putIfAbsent(key, mine);
        if (earlier == null) {
          try {
            Map<String, Object> values = delegate.valuesFor(node);
            mine.outcome().complete(new Timed(values, System.nanoTime()));
            return values;
          } catch (Exception e) {
            mine.outcome().completeExceptionally(e);
            throw e;
          } finally {
            if (!mine.outcome().isDone()) {
              mine.outcome().cancel(false);
            }
          }
        }
        long remaining = Math.max(timeoutNanos - (System.nanoTime() - earlier.startNanos()), 0L);
        Timed timed;
        try {
          timed = earlier.outcome().get(remaining, TimeUnit.NANOSECONDS);
        } catch (ExecutionException e) {
          Throwable cause = e.getCause();
          if (cause instanceof Exception exception) {
            throw exception;
          }
          throw e;
        }
        if (timed.finishedNanos() - earlier.startNanos() > timeoutNanos) {
          throw new TimeoutException("the provider for " + level + " answered after its timeout");
        }
        return timed.values();
      };
    }
  }
}
