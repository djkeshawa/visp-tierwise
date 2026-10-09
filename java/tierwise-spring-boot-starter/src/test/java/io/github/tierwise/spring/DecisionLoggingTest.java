package io.github.tierwise.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Context;
import io.github.tierwise.core.Decision;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.FactSet;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.LogLevel;

class DecisionLoggingTest {

  private static final class RecordingSink implements DecisionLogger.Sink {
    final List<String> lines = new ArrayList<>();
    final List<LogLevel> levels = new ArrayList<>();
    private final LogLevel enabledAtOrAbove;

    RecordingSink(LogLevel enabledAtOrAbove) {
      this.enabledAtOrAbove = enabledAtOrAbove;
    }

    @Override
    public boolean isEnabled(LogLevel level) {
      return level.ordinal() >= enabledAtOrAbove.ordinal();
    }

    @Override
    public void write(LogLevel level, String message) {
      levels.add(level);
      lines.add(message);
    }
  }

  private TierwiseEvaluators evaluators;
  private String version;

  @BeforeEach
  void setUp() {
    EvaluatorOptions options = EvaluatorOptions.builder()
        .provider("tenant", nodeId -> Map.of(), Duration.ofMillis(200))
        .provider("partner", nodeId -> Map.of(), Duration.ofMillis(200))
        .provider("seat", nodeId -> Map.of(), Duration.ofMillis(200))
        .build();
    evaluators = TierwiseEvaluators.start(
        new FileBundleSource(TestBundles.specBundle("edge-providers.json")), null, options, Duration.ZERO,
        Duration.ofMinutes(5), TierwiseObserver.NOOP, Clock.systemUTC());
    version = evaluators.bundleVersion();
  }

  @AfterEach
  void tearDown() {
    evaluators.close();
  }

  private DefaultDecisionEngine engine(LogLevel level, RecordingSink sink, RecordingObserver observer) {
    return new DefaultDecisionEngine(evaluators, observer, new DecisionLogger(level, sink));
  }

  private static FactSet express(boolean value) {
    return FactSet.builder().fact("order.express", value).build();
  }

  @Test
  void everyDecisionIsLoggedWithTheBundleVersionAtTheConfiguredLevel() {
    RecordingSink sink = new RecordingSink(LogLevel.TRACE);
    DefaultDecisionEngine engine = engine(LogLevel.INFO, sink, new RecordingObserver());

    Decision allow = engine.decide(Context.empty(), "order.express_allowed", express(true));
    engine.decide(Context.empty(), "order.express_allowed", express(false));
    engine.decide(Context.empty(), "order.express_allowed", FactSet.empty());

    assertEquals(version, allow.bundleVersion());
    assertEquals(List.of(LogLevel.INFO, LogLevel.INFO, LogLevel.INFO), sink.levels);
    assertEquals("tierwise decision feature=order.express_allowed reason=RULE rule=express-ok effect=ALLOW bundleVersion=" + version,
        sink.lines.get(0));
    assertEquals("tierwise decision feature=order.express_allowed reason=DEFAULT effect=DENY bundleVersion=" + version,
        sink.lines.get(1));
    assertEquals("tierwise decision feature=order.express_allowed reason=MISSING_FACT effect=DENY bundleVersion=" + version,
        sink.lines.get(2));
  }

  @Test
  void aDisabledLevelLogsNothingButStillReturnsAndCountsTheDecision() {
    RecordingSink sink = new RecordingSink(LogLevel.ERROR);
    RecordingObserver observer = new RecordingObserver();
    DefaultDecisionEngine engine = engine(LogLevel.DEBUG, sink, observer);

    Decision decision = engine.decide(Context.empty(), "order.express_allowed", express(true));

    assertEquals("ALLOW", decision.effect());
    assertTrue(sink.lines.isEmpty());
    assertEquals(List.of("order.express_allowed/RULE"), observer.decisions);
  }

  @Test
  void offLogsNothing() {
    RecordingSink sink = new RecordingSink(LogLevel.TRACE);

    engine(LogLevel.OFF, sink, new RecordingObserver()).decide(Context.empty(), "order.express_allowed", express(true));

    assertTrue(sink.lines.isEmpty());
  }

  @Test
  void aLineHoldsNeitherTheContextNorTheFacts() {
    RecordingSink sink = new RecordingSink(LogLevel.TRACE);

    engine(LogLevel.DEBUG, sink, new RecordingObserver())
        .decide(Context.builder().level("tenant", "customer-secret-42").build(), "order.express_allowed", express(true));

    assertFalse(sink.lines.get(0).contains("customer-secret-42"));
    assertFalse(sink.lines.get(0).contains("order.express"
        + "=true"));
  }

  @Test
  void anObserverThatThrowsDoesNotLoseTheDecision() {
    TierwiseObserver failing = new TierwiseObserver() {
      @Override
      public void onDecision(String feature, String reason) {
        throw new IllegalStateException("observer failure");
      }
    };
    DefaultDecisionEngine engine = new DefaultDecisionEngine(evaluators, failing,
        new DecisionLogger(LogLevel.OFF, new RecordingSink(LogLevel.TRACE)));

    assertEquals("ALLOW", engine.decide(Context.empty(), "order.express_allowed", express(true)).effect());
  }

  @Test
  void theCommonsLoggingSinkMapsEveryLevel() {
    List<String> calls = new ArrayList<>();
    org.apache.commons.logging.Log log = new org.apache.commons.logging.Log() {
      public boolean isFatalEnabled() { return true; }
      public boolean isErrorEnabled() { return true; }
      public boolean isWarnEnabled() { return true; }
      public boolean isInfoEnabled() { return true; }
      public boolean isDebugEnabled() { return false; }
      public boolean isTraceEnabled() { return false; }
      public void fatal(Object message) { calls.add("fatal"); }
      public void fatal(Object message, Throwable t) { calls.add("fatal"); }
      public void error(Object message) { calls.add("error:" + message); }
      public void error(Object message, Throwable t) { calls.add("error"); }
      public void warn(Object message) { calls.add("warn:" + message); }
      public void warn(Object message, Throwable t) { calls.add("warn"); }
      public void info(Object message) { calls.add("info:" + message); }
      public void info(Object message, Throwable t) { calls.add("info"); }
      public void debug(Object message) { calls.add("debug:" + message); }
      public void debug(Object message, Throwable t) { calls.add("debug"); }
      public void trace(Object message) { calls.add("trace:" + message); }
      public void trace(Object message, Throwable t) { calls.add("trace"); }
    };
    DecisionLogger.Sink sink = DecisionLogger.sink(log);

    assertTrue(sink.isEnabled(LogLevel.INFO));
    assertTrue(sink.isEnabled(LogLevel.WARN));
    assertTrue(sink.isEnabled(LogLevel.ERROR));
    assertTrue(sink.isEnabled(LogLevel.FATAL));
    assertFalse(sink.isEnabled(LogLevel.DEBUG));
    assertFalse(sink.isEnabled(LogLevel.TRACE));
    assertFalse(sink.isEnabled(LogLevel.OFF));
    for (LogLevel level : LogLevel.values()) {
      sink.write(level, "m");
    }
    assertEquals(List.of("trace:m", "debug:m", "info:m", "warn:m", "error:m", "error:m"), calls);
  }
}
