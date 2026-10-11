package io.github.visplabs.tierwise.core.internal.eval;

import io.github.visplabs.tierwise.core.EvaluationEvent;
import io.github.visplabs.tierwise.core.LevelValueProvider;
import io.github.visplabs.tierwise.core.Resolved.Contribution.Label;
import io.github.visplabs.tierwise.core.internal.model.KeyDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The provider calls of one evaluation call (SVC-13 to SVC-37, SVC-38 to SVC-41, SVC-43 to SVC-47).
 *
 * <p>For each admitted service-sourced level at which at least one key being resolved is settable, the
 * provider is called exactly once, whoever wins the merge (SVC-17 to SVC-19). No other level is called.
 * The calls of one evaluation run concurrently and each is bounded by its registered timeout, which is
 * measured from the start of the call until its result is available (SVC-41). A result that arrives after
 * the timeout is never used (SVC-40). Provider code runs on a pool thread, not on the calling thread.
 *
 * <p>Each entry of a provider result is classified in the order of SVC-26: an undeclared key is reported
 * as unknown (SVC-27); a declared key that is not being resolved is neither validated nor reported
 * (SVC-28); a key being resolved that is not settable at the level is ignored and reported (SVC-29); any
 * other entry is validated, and an invalid value, {@code null} included, is an {@code INVALID} source
 * failure for that key only (SVC-34, SVC-35, SVC-37). A throw, a timeout or a result that is not a
 * mapping is an {@code UNAVAILABLE} source failure for every key being resolved at the level (SVC-23,
 * SVC-25). A key that the result omits has no outcome (SVC-36). The result does not depend on the order or
 * the concurrency of the calls (SVC-21).
 */
final class ProviderCalls {

  /**
   * A provider and its timeout.
   *
   * @param provider the provider of one level
   * @param timeoutMillis the timeout in milliseconds, positive
   */
  record Registration(LevelValueProvider provider, long timeoutMillis) {
  }

  /** A provider result with the time at which it became available. */
  private record Timed(Map<String, Object> result, long finishedNanos) {
  }

  /** A running provider call; {@code future} is {@code null} when the pool rejected the call. */
  private record Pending(PathScope level, Future<Timed> future, long startNanos, long timeoutNanos) {
  }

  /** A listener event for one key, delivered after the level's result is classified. */
  private record KeyEvent(EvaluationEvent.Type type, String key) {
  }

  private final Model model;
  private final Map<String, Registration> registrations;
  private final EventSink events;

  /**
   * Creates the call logic of an evaluator.
   *
   * @param model the model of the bundle
   * @param registrations the provider of each service-sourced level, by level name
   * @param events the sink for listener events
   */
  ProviderCalls(Model model, Map<String, Registration> registrations, EventSink events) {
    this.model = model;
    this.registrations = Map.copyOf(registrations);
    this.events = events;
  }

  /**
   * Finds the levels that need a provider call (SVC-17, SVC-42): admitted, service-sourced, and with at
   * least one key being resolved that is settable at the level.
   *
   * @param admitted the validated context
   * @param keys the declared keys being resolved
   * @return the levels to call, in evaluation-path order
   */
  List<PathScope> neededLevels(AdmittedContext admitted, Set<String> keys) {
    List<PathScope> needed = new ArrayList<>();
    for (PathScope scope : admitted.path()) {
      if (!scope.isRoot() && isServiceSourced(scope.name()) && !keysAt(scope.name(), keys).isEmpty()) {
        needed.add(scope);
      }
    }
    return needed;
  }

  /**
   * Calls the providers that are needed and classifies their results.
   *
   * @param admitted the validated context
   * @param keys the declared keys being resolved; the whole request is one evaluation call (SVC-20)
   * @return the outcomes; listener events have been delivered when this returns (EVAL-44)
   */
  ProviderOutcomes call(AdmittedContext admitted, Set<String> keys) {
    List<PathScope> needed = neededLevels(admitted, keys);
    if (needed.isEmpty()) {
      return ProviderOutcomes.NONE;
    }
    List<Pending> running = new ArrayList<>();
    for (PathScope level : needed) {
      running.add(start(level));
    }
    Map<String, Map<String, SourceOutcome>> byLevel = new LinkedHashMap<>();
    for (Pending call : running) {
      Map<?, ?> result = await(call);
      byLevel.put(call.level().name(), settle(call.level(), keysAt(call.level().name(), keys), keys, result));
    }
    return new ProviderOutcomes(byLevel);
  }

  private boolean isServiceSourced(String level) {
    var scope = model.hierarchy().scope(level);
    return scope != null && scope.isServiceSourced();
  }

  private List<String> keysAt(String level, Set<String> keys) {
    List<String> settable = new ArrayList<>();
    for (String key : keys) {
      if (model.keys().get(key).settableAt().contains(level)) {
        settable.add(key);
      }
    }
    return settable;
  }

  private Pending start(PathScope level) {
    Registration registration = registrations.get(level.name());
    if (registration == null) {
      throw new IllegalStateException("no provider registered for the service-sourced level " + level.name());
    }
    long timeoutNanos = registration.timeoutMillis() > Long.MAX_VALUE / 1_000_000L
        ? Long.MAX_VALUE : registration.timeoutMillis() * 1_000_000L;
    LevelValueProvider provider = registration.provider();
    String node = level.node();
    Callable<Timed> task = () -> new Timed(provider.valuesFor(node), System.nanoTime());
    long start = System.nanoTime();
    try {
      return new Pending(level, ProviderExecutor.submit(task), start, timeoutNanos);
    } catch (RejectedExecutionException e) {
      return new Pending(level, null, start, timeoutNanos);
    }
  }

  /** Waits for a call; returns {@code null} for any failure, including a timeout (SVC-23, SVC-39). */
  private static Map<?, ?> await(Pending call) {
    if (call.future() == null) {
      return null;
    }
    try {
      long remaining = call.timeoutNanos() - (System.nanoTime() - call.startNanos());
      Timed timed = call.future().get(Math.max(remaining, 0L), TimeUnit.NANOSECONDS);
      boolean late = timed.finishedNanos() - call.startNanos() > call.timeoutNanos();
      return late ? null : timed.result();
    } catch (TimeoutException e) {
      call.future().cancel(true);
      return null;
    } catch (ExecutionException | CancellationException e) {
      return null;
    } catch (InterruptedException e) {
      call.future().cancel(true);
      Thread.currentThread().interrupt();
      return null;
    }
  }

  private Map<String, SourceOutcome> settle(
      PathScope level, List<String> keysAtLevel, Set<String> requested, Map<?, ?> result) {
    Map<String, SourceOutcome> outcomes = new LinkedHashMap<>();
    List<KeyEvent> pending = new ArrayList<>();
    boolean usable = result != null;
    if (usable) {
      try {
        classify(level, keysAtLevel, requested, result, outcomes, pending);
      } catch (RuntimeException e) {
        // A result that cannot be read as a mapping of entries counts as no mapping (SVC-25).
        usable = false;
      }
    }
    if (!usable) {
      outcomes.clear();
      pending.clear();
      for (String key : keysAtLevel) {
        outcomes.put(key, new SourceOutcome.Failed(Label.UNAVAILABLE));
        pending.add(new KeyEvent(EvaluationEvent.Type.SOURCE_UNAVAILABLE, key));
      }
    }
    for (KeyEvent event : pending) {
      events.forKey(event.type(), level.name(), level.node(), event.key());
    }
    return outcomes;
  }

  private void classify(
      PathScope level,
      List<String> keysAtLevel,
      Set<String> requested,
      Map<?, ?> result,
      Map<String, SourceOutcome> outcomes,
      List<KeyEvent> pending) {
    List<String> names = new ArrayList<>();
    for (Object name : result.keySet()) {
      if (name instanceof String text) {
        names.add(text);
      }
    }
    names.sort(CodePoints.ORDER);
    for (String name : names) {
      KeyDef declared = model.keys().get(name);
      if (declared == null) {
        pending.add(new KeyEvent(EvaluationEvent.Type.UNKNOWN_KEY, name));
      } else if (requested.contains(name) && !declared.settableAt().contains(level.name())) {
        pending.add(new KeyEvent(EvaluationEvent.Type.IGNORED_ENTRY, name));
      }
    }
    for (String key : keysAtLevel) {
      if (result.containsKey(key)) {
        Optional<Object> valid = validate(key, model.keys().get(key), result.get(key));
        if (valid.isPresent()) {
          outcomes.put(key, new SourceOutcome.Supplied(valid.get()));
        } else {
          outcomes.put(key, new SourceOutcome.Failed(Label.INVALID));
          pending.add(new KeyEvent(EvaluationEvent.Type.SOURCE_INVALID, key));
        }
      }
    }
  }

  private Optional<Object> validate(String key, KeyDef definition, Object value) {
    boolean deepMerge = Strategy.parse(definition.combine()).orElse(Strategy.OVERRIDE) == Strategy.DEEP_MERGE;
    if (!deepMerge) {
      return RuntimeValues.validate(definition.type(), value);
    }
    Object starting = definition.defaultValue()
        .orElseGet(() -> model.values().get(model.hierarchy().rootLevel(), "", key));
    return RuntimeValues.validatePartial(definition.type(), value, starting);
  }
}
