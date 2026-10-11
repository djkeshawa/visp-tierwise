package io.github.visplabs.tierwise.core.internal.bundle;

import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.bundle;
import static io.github.visplabs.tierwise.core.internal.bundle.BundleFixtures.richContent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.FactSet;
import io.github.visplabs.tierwise.core.LoadedBundle;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.core.TierwiseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Determinism rules that concern the environment, not the bytes (DET-7, DET-8). A conformance vector cannot run under
 * another locale, time zone or clock, so these tests are their enforcers (spec/conformance/enforcers.d/E7.yaml). Each
 * one loads the same bundle bytes again and compares the text of every result and error code.
 */
class LoadDeterminismTest {

  private static final byte[] BYTES = bundle(richContent());
  private static final List<String> KEYS = List.of("checkout.allowed", "checkout.lead_hours", "checkout.limit",
      "checkout.methods", "checkout.rate", "checkout.window", "checkout.unknown");

  /** Builds a map from pairs, in the given order or the reverse. */
  private static Map<String, Object> map(boolean reverse, Object... pairs) {
    List<Object[]> entries = new ArrayList<>();
    for (int i = 0; i < pairs.length; i += 2) {
      entries.add(new Object[] {pairs[i], pairs[i + 1]});
    }
    if (reverse) {
      Collections.reverse(entries);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    entries.forEach(entry -> out.put((String) entry[0], entry[1]));
    return out;
  }

  /** Loads the bytes, runs resolve and decide over fixed inputs, and returns every result and code as text. */
  private static String probe(boolean reverse) {
    LoadedBundle loaded = Tierwise.loadBundle(BYTES.clone());
    Evaluator evaluator = Tierwise.evaluator(loaded, EvaluatorOptions.builder()
        .provider("account", node -> map(reverse, "checkout.allowed", node.startsWith("a"),
            "checkout.lead_hours", 3L, "checkout.unknown", "x"), Duration.ofSeconds(5)).build());
    List<Object> contexts = List.of(Map.of(),
        map(reverse, "levels", map(reverse, "division", "south", "account", "a1"), "axes", Map.of("seller", "acme")),
        map(reverse, "levels", map(reverse, "account", "b2", "division", "north")),
        Map.of("levels", Map.of("division", "west")),
        Map.of("levels", Map.of("global", "x")));
    List<Object> factSets = List.of(Map.of("facts", map(reverse, "customer.active", true)),
        map(reverse, "facts", map(reverse, "customer.active", false), "children",
            Map.of("order", List.of(Map.of("order.status", "PAID"), Map.of("order.status", "PENDING")))),
        Map.of("facts", Map.of("customer.active", "yes")), Map.of());
    StringBuilder out = new StringBuilder(loaded.bundleVersion()).append('\n');
    for (Object raw : contexts) {
      Context context = Context.fromJson(raw);
      for (String key : KEYS) {
        out.append(run(() -> evaluator.resolve(context, key).toJson())).append('\n');
        out.append(run(() -> evaluator.resolveLocal(context, key).toJson())).append('\n');
      }
      for (Object facts : factSets) {
        out.append(run(() -> evaluator.decide(context, "checkout.submission", FactSet.fromJson(facts)).toJson()))
            .append('\n');
      }
    }
    return out.toString();
  }

  private interface Call {
    Map<String, Object> run();
  }

  private static String run(Call call) {
    try {
      return sorted(call.run()).toString();
    } catch (TierwiseException e) {
      return "error " + e.code();
    }
  }

  /** A copy with every map sorted by key, so the text does not depend on the map class of a result. */
  @SuppressWarnings("unchecked")
  private static Object sorted(Object value) {
    if (value instanceof Map<?, ?> m) {
      Map<String, Object> out = new java.util.TreeMap<>();
      ((Map<String, Object>) m).forEach((k, v) -> out.put(k, sorted(v)));
      return out;
    }
    if (value instanceof List<?> l) {
      return l.stream().map(LoadDeterminismTest::sorted).toList();
    }
    return value;
  }

  @Test
  void DET_7_loadingAndEvaluatingUnderANonEnglishLocaleAndANonUtcTimeZoneGivesTheSameResults() {
    Locale locale = Locale.getDefault();
    TimeZone zone = TimeZone.getDefault();
    try {
      Locale.setDefault(Locale.ROOT);
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      String reference = probe(false);
      assertTrue(reference.contains("ALLOW") || reference.contains("DENY"), reference);
      for (String[] pair : List.of(new String[] {"tr-TR", "Pacific/Kiritimati"},
          new String[] {"ar-EG", "America/St_Johns"}, new String[] {"th-TH-u-nu-thai", "Asia/Kathmandu"})) {
        Locale.setDefault(Locale.forLanguageTag(pair[0]));
        TimeZone.setDefault(TimeZone.getTimeZone(pair[1]));
        assertEquals(reference, probe(false), pair[0] + " " + pair[1]);
      }
    } finally {
      Locale.setDefault(locale);
      TimeZone.setDefault(zone);
    }
  }

  @Test
  void DET_8_theResultsDoNotDependOnTheIterationOrderOfTheInputMaps() {
    assertEquals(probe(false), probe(true));
  }

  @Test
  void DET_8_theResultsDoNotDependOnTheWallClockOrTheHostEnvironment() throws InterruptedException {
    String reference = probe(false);
    Thread.sleep(1100); // a later second, and a later millisecond
    assertEquals(reference, probe(false));
    Map<String, String> saved = new HashMap<>();
    Map<String, String> changed = Map.of("line.separator", "\r\n", "file.separator", "\\", "user.dir", "/nonexistent",
        "user.home", "/nonexistent", "user.language", "tr", "java.io.tmpdir", "/nonexistent");
    try {
      changed.forEach((name, value) -> saved.put(name, System.setProperty(name, value)));
      assertEquals(reference, probe(false), "other host properties");
    } finally {
      saved.forEach((name, value) -> {
        if (value == null) {
          System.clearProperty(name);
        } else {
          System.setProperty(name, value);
        }
      });
    }
  }

  /** The load and evaluation code reads no clock, environment, default locale or default time zone. */
  @Test
  void DET_7_DET_8_theLoadAndEvaluationSourcesReadNoClockEnvironmentOrDefaultLocale() throws IOException {
    Path core = mainSources();
    List<String> forbidden = List.of("System.currentTimeMillis", "Instant.now", "LocalDate", "LocalTime",
        "ZonedDateTime", "OffsetDateTime", "Clock.", "new Date(", "System.getenv", "System.getProperty",
        "Locale.getDefault", "TimeZone.getDefault", "ZoneId.systemDefault", "Math.random", "new Random",
        "Collator", "toLowerCase()", "toUpperCase()", "String.format(", ".formatted(");
    List<Path> files = new ArrayList<>();
    try (Stream<Path> top = Files.list(core)) {
      top.filter(f -> f.toString().endsWith(".java")).forEach(files::add);
    }
    for (String dir : List.of("bundle", "eval", "json", "model")) {
      try (Stream<Path> walk = Files.walk(core.resolve("internal").resolve(dir))) {
        walk.filter(f -> f.toString().endsWith(".java")).forEach(files::add);
      }
    }
    assertTrue(files.size() > 20, "found the sources: " + files.size());
    for (Path file : files) {
      String text = Files.readString(file);
      for (String call : forbidden) {
        assertTrue(!text.contains(call), file.getFileName() + " uses " + call);
      }
      // A provider timeout is measured with the monotonic clock; the outcome of a call is an input (EVAL-51, SVC-39).
      if (!file.getFileName().toString().equals("ProviderCalls.java")) {
        assertTrue(!text.contains("nanoTime"), file.getFileName() + " reads the clock");
      }
    }
  }

  private static Path mainSources() {
    for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
      for (Path candidate : List.of(dir.resolve("src/main/java/io/github/visplabs/tierwise/core"),
          dir.resolve("tierwise-core/src/main/java/io/github/visplabs/tierwise/core"),
          dir.resolve("java/tierwise-core/src/main/java/io/github/visplabs/tierwise/core"))) {
        if (Files.isDirectory(candidate)) {
          return candidate;
        }
      }
    }
    throw new IllegalStateException("cannot locate the tierwise-core sources");
  }
}
