package io.github.visplabs.tierwise.core.bench;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.FactSet;
import io.github.visplabs.tierwise.core.LoadedBundle;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.core.internal.bundle.BundleWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Measures {@code resolve} and {@code decide} in process on the 1,000-key, 50-feature model (requirements section 12,
 * implementation plan 3.9).
 *
 * <p>Each operation is timed on its own with {@link System#nanoTime()}, after a warm-up of the same operation, and the
 * percentiles are the nearest-rank values of every sample. The model has no service-sourced level, so {@code resolve}
 * never calls a provider and the numbers exclude provider cost. The test is skipped unless
 * {@code tierwise.bench.out} names the file for the report, which is also printed.
 */
@Tag("bench")
@EnabledIfSystemProperty(named = "tierwise.bench.out", matches = ".+")
class ResolveDecideBenchmarkTest {

  private static final int WARMUP = 50_000;
  private static final int MEASURED = 200_000;
  private static final int CONTEXTS = 64;

  /** Keeps the results alive, so that the JIT cannot remove the work being timed. */
  private static int sink;

  @Test
  void measuresResolveAndDecideInProcess() throws IOException {
    LoadedBundle loaded = Tierwise.loadBundle(BundleWriter.write(BenchModel.build()).bytes());
    Evaluator evaluator = Tierwise.evaluator(loaded, EvaluatorOptions.none());
    List<Context> contexts = BenchModel.contexts(CONTEXTS);
    List<FactSet> facts = BenchModel.factSets(CONTEXTS);
    List<String> keys = BenchModel.keyNames();
    List<String> features = BenchModel.featureNames();

    IntFunction<Object> resolve = i -> evaluator.resolve(contexts.get(i % CONTEXTS), keys.get(i % keys.size()));
    IntFunction<Object> decide = i -> evaluator.decide(
        contexts.get(i % CONTEXTS), features.get(i % features.size()), facts.get(i % CONTEXTS));

    LatencySummary resolveSummary = measure(resolve);
    LatencySummary decideSummary = measure(decide);
    String report = report(resolveSummary, decideSummary);
    System.out.print(report);
    Path out = Path.of(System.getProperty("tierwise.bench.out"));
    Files.createDirectories(out.toAbsolutePath().getParent());
    Files.writeString(out, report, StandardCharsets.UTF_8);
  }

  private static LatencySummary measure(IntFunction<Object> operation) {
    for (int i = 0; i < WARMUP; i++) {
      sink ^= operation.apply(i).hashCode();
    }
    long[] nanos = new long[MEASURED];
    for (int i = 0; i < MEASURED; i++) {
      long start = System.nanoTime();
      Object result = operation.apply(i);
      nanos[i] = System.nanoTime() - start;
      sink ^= result.hashCode();
    }
    return LatencySummary.of(nanos);
  }

  private static String report(LatencySummary resolve, LatencySummary decide) {
    StringBuilder out = new StringBuilder();
    out.append("Java in-process benchmark: ").append(BenchModel.KEYS).append(" keys, ")
        .append(BenchModel.FEATURES).append(" features, ").append(WARMUP).append(" warm-up operations, ")
        .append(MEASURED).append(" measured operations per call\n");
    out.append("java.version=").append(System.getProperty("java.version"))
        .append(" vm=").append(System.getProperty("java.vm.name"))
        .append(" os=").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch"))
        .append(" processors=").append(Runtime.getRuntime().availableProcessors()).append('\n');
    out.append("operation,count,p50_ns,p90_ns,p99_ns,p999_ns,max_ns,mean_ns\n");
    row(out, "resolve", resolve);
    row(out, "decide", decide);
    out.append("checksum=").append(sink).append('\n');
    return out.toString();
  }

  private static void row(StringBuilder out, String name, LatencySummary summary) {
    out.append(name).append(',').append(summary.count()).append(',').append(summary.p50()).append(',')
        .append(summary.p90()).append(',').append(summary.p99()).append(',').append(summary.p999()).append(',')
        .append(summary.max()).append(',').append(Math.round(summary.mean())).append('\n');
  }
}
