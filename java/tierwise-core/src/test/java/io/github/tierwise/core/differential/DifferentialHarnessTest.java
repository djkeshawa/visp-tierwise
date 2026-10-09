package io.github.tierwise.core.differential;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Tierwise;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.bundle.BundleWriter;
import io.github.tierwise.core.internal.model.Model;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.RandomGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Writes the Java side of the cross-language differential run (requirements section 12, implementation plan 4.4).
 *
 * <p>The run is started by {@code scripts/differential.sh}, which sets {@code tierwise.differential.out} (the directory
 * for {@code cases.jsonl}), {@code tierwise.differential.seed} and {@code tierwise.differential.cases}. Without the
 * first property the test is skipped, so a normal build does not run it.
 *
 * <p>Each case is one line: the bundle as base64, the call, its inputs, and the result that the core returned, or the
 * code of the error it raised. The JavaScript runner in {@code js/evaluator/test/differential.test.mjs} replays the
 * lines and compares the results. Models come from jqwik: {@link Arbitraries#longs()} gives the seed of each model, and
 * the seed drives every choice that the model and its inputs make, so one run seed gives the same file every time.
 */
@Tag("differential")
@EnabledIfSystemProperty(named = "tierwise.differential.out", matches = ".+")
class DifferentialHarnessTest {

  /** The number of input draws that share one model, so that the bundle cost is spread over several cases. */
  private static final int CASES_PER_MODEL = 3;

  /** The size at which the jqwik generator of model seeds runs. */
  private static final int GENERATOR_SIZE = 50;

  @Test
  void writesDifferentialCases() throws IOException {
    Path dir = Path.of(System.getProperty("tierwise.differential.out"));
    long seed = Long.parseLong(System.getProperty("tierwise.differential.seed", "1"));
    int target = Integer.parseInt(System.getProperty("tierwise.differential.cases", "2000"));
    Files.createDirectories(dir);
    Path file = dir.resolve("cases.jsonl");
    RandomGenerator<Long> modelSeeds = Arbitraries.longs().generator(GENERATOR_SIZE);
    Random driver = new Random(seed);
    Tally tally = new Tally();
    try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      while (tally.cases < target) {
        long modelSeed = modelSeeds.next(driver).value();
        Random random = new Random(modelSeed);
        Model model = DiffModelBuilder.build(random);
        byte[] bundle = loadableBundle(model);
        if (bundle == null) {
          tally.rejectedModels++;
          continue;
        }
        for (int draw = 0; draw < CASES_PER_MODEL && tally.cases < target; draw++) {
          Map<String, Object> input = DiffInputs.draw(model, bundle, random);
          Map<String, Object> record = DiffRunner.record("case-" + tally.cases, modelSeed, input);
          out.write(DiffRunner.canonicalText(record));
          out.write('\n');
          tally.add(record);
        }
      }
    }
    System.out.println(tally.summary(seed, file));
    int allowedRejections = Math.max(10, target / 10);
    assertTrue(tally.rejectedModels <= allowedRejections,
        "the generator made " + tally.rejectedModels + " models that the bundle reader refuses");
  }

  /**
   * Writes a model as a bundle and loads it back, which is the check of EVAL-14.
   *
   * @param model the model
   * @return the bundle bytes, or {@code null} when the bundle reader refuses them
   */
  private static byte[] loadableBundle(Model model) {
    try {
      byte[] bytes = BundleWriter.write(model).bytes();
      Tierwise.loadBundle(bytes);
      return bytes;
    } catch (TierwiseException | IllegalArgumentException e) {
      return null;
    }
  }

  /** The counts of one run, printed as the summary. */
  private static final class Tally {

    private int cases;
    private int rejectedModels;
    private int results;
    private int events;
    private final Map<String, Integer> errors = new TreeMap<>();

    void add(Map<String, Object> record) {
      cases++;
      Map<String, Object> expect = DiffRunner.map(record.get("expect"));
      if (expect.containsKey("error")) {
        errors.merge((String) expect.get("error"), 1, Integer::sum);
      } else {
        results++;
      }
      events += ((List<?>) record.get("events")).size();
    }

    String summary(long seed, Path file) {
      return "differential: " + cases + " cases written to " + file + " (seed " + seed + "), "
          + rejectedModels + " generated models rejected by the bundle reader, " + results + " results, "
          + events + " listener events, errors by code " + errors;
    }
  }
}
