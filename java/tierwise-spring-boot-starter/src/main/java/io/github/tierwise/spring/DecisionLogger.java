package io.github.tierwise.spring;

import io.github.tierwise.core.Decision;
import java.util.Objects;
import org.apache.commons.logging.Log;
import org.springframework.boot.logging.LogLevel;

/**
 * Logs one line per decision with the bundle version, at the configured level. The line holds the
 * feature, the reason, the rule that won and the effect when it is a plain string. It never holds the
 * context or the facts.
 */
final class DecisionLogger {

  /** Where the lines go. */
  interface Sink {

    boolean isEnabled(LogLevel level);

    void write(LogLevel level, String message);
  }

  private final LogLevel level;
  private final Sink sink;

  DecisionLogger(LogLevel level, Sink sink) {
    this.level = Objects.requireNonNull(level, "level");
    this.sink = Objects.requireNonNull(sink, "sink");
  }

  static Sink sink(Log log) {
    return new Sink() {
      @Override
      public boolean isEnabled(LogLevel logLevel) {
        return switch (logLevel) {
          case TRACE -> log.isTraceEnabled();
          case DEBUG -> log.isDebugEnabled();
          case INFO -> log.isInfoEnabled();
          case WARN -> log.isWarnEnabled();
          case ERROR, FATAL -> log.isErrorEnabled();
          case OFF -> false;
        };
      }

      @Override
      public void write(LogLevel logLevel, String message) {
        switch (logLevel) {
          case TRACE -> log.trace(message);
          case DEBUG -> log.debug(message);
          case INFO -> log.info(message);
          case WARN -> log.warn(message);
          case ERROR, FATAL -> log.error(message);
          case OFF -> {
          }
        }
      }
    };
  }

  void log(Decision decision) {
    if (level == LogLevel.OFF || !sink.isEnabled(level)) {
      return;
    }
    StringBuilder line = new StringBuilder("tierwise decision feature=").append(decision.feature());
    line.append(" reason=").append(decision.reason().type().name());
    decision.reason().id().ifPresent(id -> line.append(" rule=").append(id));
    if (decision.effect() instanceof String effect) {
      line.append(" effect=").append(effect);
    }
    line.append(" bundleVersion=").append(decision.bundleVersion());
    sink.write(level, line.toString());
  }
}
