package io.github.tierwise.openfeature;

import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.SkippedScope;
import java.util.List;

/**
 * Writes the compact JSON strings of the structured flag metadata entries. Member order is fixed, so the
 * Java and JavaScript providers write identical strings for identical results.
 */
final class Json {

  private Json() {
  }

  /** Writes the contribution summary: {@code scope, name, node?, effect, onSourceFailure?} per entry. */
  static String contributions(List<Resolved.Contribution> entries) {
    StringBuilder out = new StringBuilder("[");
    for (int i = 0; i < entries.size(); i++) {
      Resolved.Contribution entry = entries.get(i);
      if (i > 0) {
        out.append(',');
      }
      out.append("{\"scope\":");
      quote(out, entry.scope().json());
      out.append(",\"name\":");
      quote(out, entry.name());
      if (entry.node().isPresent()) {
        out.append(",\"node\":");
        quote(out, entry.node().get());
      }
      out.append(",\"effect\":");
      quote(out, entry.effect().name());
      if (entry.onSourceFailure().isPresent()) {
        out.append(",\"onSourceFailure\":");
        quote(out, entry.onSourceFailure().get());
      }
      out.append('}');
    }
    return out.append(']').toString();
  }

  /** Writes the skipped scopes: {@code scope, name, node, reason} per entry. */
  static String skippedScopes(List<SkippedScope> scopes) {
    StringBuilder out = new StringBuilder("[");
    for (int i = 0; i < scopes.size(); i++) {
      SkippedScope scope = scopes.get(i);
      if (i > 0) {
        out.append(',');
      }
      out.append("{\"scope\":");
      quote(out, scope.scope().json());
      out.append(",\"name\":");
      quote(out, scope.name());
      out.append(",\"node\":");
      quote(out, scope.node());
      out.append(",\"reason\":");
      quote(out, SkippedScope.REASON);
      out.append('}');
    }
    return out.append(']').toString();
  }

  /** Writes an array of strings. */
  static String strings(List<String> values) {
    StringBuilder out = new StringBuilder("[");
    for (int i = 0; i < values.size(); i++) {
      if (i > 0) {
        out.append(',');
      }
      quote(out, values.get(i));
    }
    return out.append(']').toString();
  }

  /** Appends a JSON string literal. Escapes as {@code JSON.stringify} does for well-formed text. */
  private static void quote(StringBuilder out, String text) {
    out.append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }
}
