package io.github.tierwise.core.conformance;

import io.github.tierwise.core.Context;
import io.github.tierwise.core.Decision;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.LoadedBundle;
import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.Tierwise;
import io.github.tierwise.core.TierwiseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.opentest4j.AssertionFailedError;

/**
 * Runs one evaluate vector (VEC-24): load the bundle, create an evaluator with stub providers, call the
 * operation, and compare the outcome with {@code expect}.
 *
 * <p>An error raised by any of the three steps is the outcome of the vector. A timeout in a stub is
 * produced at once and without waiting (VEC-45), so the runner registers every provider with a generous
 * timeout.
 */
final class EvaluateRunner {

  /** The timeout registered for every stub provider. The stubs never wait, so it is never reached. */
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private EvaluateRunner() {
  }

  /**
   * Runs the vector in a file.
   *
   * @param file the vector file under {@code evaluate/}
   * @throws RunnerFailure if the vector cannot be executed as written
   * @throws AssertionFailedError if the outcome differs from {@code expect}
   */
  static void run(Path file) {
    Path conformance = VectorFiles.conformanceDir();
    EvaluateVector vector = EvaluateVector.parse(VectorFiles.loadMapping(file), file, conformance.resolve("bundles"));
    byte[] bundleBytes;
    try {
      bundleBytes = Files.readAllBytes(vector.bundle());
    } catch (IOException e) {
      throw new RunnerFailure("cannot read the bundle " + vector.bundle(), e);
    }
    BundleLevels levels = BundleLevels.read(bundleBytes);
    boolean loadErrorExpected = levels == BundleLevels.UNKNOWN;
    checkLevelNames(vector, levels, loadErrorExpected);

    StubProviders stubs = new StubProviders();
    EvaluatorOptions.Builder options = EvaluatorOptions.builder();
    Set<String> registered = new TreeSet<>(levels.serviceSourced());
    registered.addAll(vector.providers().keySet());
    for (String level : registered) {
      Object stub = vector.providers().getOrDefault(level, Map.of());
      if ("none".equals(stub)) {
        continue;
      }
      options.provider(level, stubs.provider(level, stub), TIMEOUT);
    }

    String errorCode = null;
    Object result = null;
    try {
      LoadedBundle bundle = Tierwise.loadBundle(bundleBytes);
      Evaluator evaluator = Tierwise.evaluator(bundle, options.build());
      Context context = Context.fromJson(vector.context());
      if (vector.call().equals("resolve")) {
        Resolved resolved = vector.sync() ? evaluator.resolveLocal(context, vector.key())
            : evaluator.resolve(context, vector.key());
        result = resolved.toJson();
      } else {
        Decision decision = evaluator.decide(context, vector.feature(), FactSet.fromJson(vector.input()));
        result = decision.toJson();
      }
    } catch (TierwiseException e) {
      errorCode = e.code();
    }

    if (!stubs.unlistedCalls().isEmpty()) {
      throw new RunnerFailure("a provider was invoked for a node that the stub does not list: "
          + stubs.unlistedCalls() + " (VEC-46)");
    }
    if (vector.sync() && vector.call().equals("resolve") && !stubs.calls().isEmpty()) {
      throw new RunnerFailure("the synchronous variant invoked a provider: " + stubs.calls() + " (VEC-26)");
    }

    List<String> problems = new ArrayList<>();
    if (vector.expectError() != null) {
      if (errorCode == null) {
        problems.add("expected error " + vector.expectError() + ", but the operation returned a result");
      } else if (!errorCode.equals(vector.expectError())) {
        problems.add("expected error " + vector.expectError() + ", got " + errorCode);
      }
    } else if (errorCode != null) {
      problems.add("expected a result, got error " + errorCode);
    } else {
      problems.addAll(Expect.mismatches(vector.expectResult(), result, vector.exact()));
    }
    if (vector.providerCalls() != null || vector.exact()) {
      problems.addAll(providerCallMismatches(vector.providerCalls(), stubs.calls(), vector.exact()));
    }
    if (!problems.isEmpty()) {
      throw new AssertionFailedError(vector.id() + ":\n  " + String.join("\n  ", problems));
    }
  }

  private static void checkLevelNames(EvaluateVector vector, BundleLevels levels, boolean loadErrorExpected) {
    if (loadErrorExpected) {
      return;
    }
    boolean optionsErrorExpected = "E_EVALUATOR_OPTIONS".equals(vector.expectError());
    for (String level : vector.providers().keySet()) {
      if (!levels.serviceSourced().contains(level) && !optionsErrorExpected) {
        throw new RunnerFailure("providers names " + level + ", which is not a service-sourced level (VEC-38)");
      }
    }
    if (vector.providerCalls() != null) {
      for (String level : vector.providerCalls().keySet()) {
        if (!levels.serviceSourced().contains(level)) {
          throw new RunnerFailure("providerCalls names " + level + ", which is not a service-sourced level (VEC-38)");
        }
      }
    }
  }

  /**
   * Compares the expected invocation counts with the actual ones (VEC-65 to VEC-67).
   *
   * <p>Unless {@code exact}, only the level and node pairs that {@code expected} names are compared. When
   * {@code exact}, every invocation of a pair that {@code expected} does not name is a mismatch. A
   * vector with no {@code providerCalls} is read as naming no pair.
   *
   * @param expected the expected counts (level, node, count), or {@code null} when the vector has none
   * @param actual the actual counts (level, node, count)
   * @param exact whether the vector sets {@code exact: true}
   * @return the mismatches; empty when the counts agree
   */
  static List<String> providerCallMismatches(
      Map<String, Map<String, Integer>> expected,
      Map<String, Map<String, Integer>> actual,
      boolean exact) {
    List<String> problems = new ArrayList<>();
    Map<String, Map<String, Integer>> named = expected == null ? Map.of() : expected;
    named.forEach((level, nodes) -> nodes.forEach((node, count) -> {
      int got = actual.getOrDefault(level, Map.of()).getOrDefault(node, 0);
      if (got != count) {
        problems.add("providerCalls " + level + "/" + node + ": expected " + count + ", got " + got);
      }
    }));
    if (exact) {
      actual.forEach((level, nodes) -> nodes.forEach((node, count) -> {
        if (!named.getOrDefault(level, Map.of()).containsKey(node)) {
          problems.add("providerCalls " + level + "/" + node + ": invoked " + count
              + " time(s) but not named by exact expect (VEC-67)");
        }
      }));
    }
    return problems;
  }
}
