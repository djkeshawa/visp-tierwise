package io.github.visplabs.tierwise.cli;

import java.util.List;

/**
 * A minimal JSON writer for the reports of the command line. It writes compact JSON and has no
 * dependency; only the shapes the reports need are supported.
 */
final class Json {

  private final StringBuilder out = new StringBuilder();
  /** One flag per open container: whether it already has a member, so that a comma is due. */
  private final java.util.ArrayDeque<boolean[]> open = new java.util.ArrayDeque<>();
  private boolean afterName;

  /** Starts an object. */
  Json beginObject() {
    separator();
    out.append('{');
    open.push(new boolean[1]);
    return this;
  }

  /** Ends an object. */
  Json endObject() {
    open.pop();
    out.append('}');
    return this;
  }

  /** Starts an array. */
  Json beginArray() {
    separator();
    out.append('[');
    open.push(new boolean[1]);
    return this;
  }

  /** Ends an array. */
  Json endArray() {
    open.pop();
    out.append(']');
    return this;
  }

  /** Writes a member name; the next call writes its value. */
  Json name(String name) {
    separator();
    quote(name);
    out.append(':');
    afterName = true;
    return this;
  }

  /** Writes a string value. */
  Json value(String value) {
    separator();
    quote(value);
    return this;
  }

  /** Writes an integer value. */
  Json value(long value) {
    separator();
    out.append(value);
    return this;
  }

  /** Writes a boolean value. */
  Json value(boolean value) {
    separator();
    out.append(value);
    return this;
  }

  /** Writes a path of strings and integers as an array. */
  Json path(List<Object> path) {
    beginArray();
    for (Object segment : path) {
      if (segment instanceof Integer number) {
        value(number.longValue());
      } else {
        value(String.valueOf(segment));
      }
    }
    return endArray();
  }

  @Override
  public String toString() {
    return out.toString();
  }

  private void separator() {
    if (afterName) {
      afterName = false;
      return;
    }
    boolean[] top = open.peek();
    if (top != null) {
      if (top[0]) {
        out.append(',');
      }
      top[0] = true;
    }
  }

  private void quote(String text) {
    out.append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
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
