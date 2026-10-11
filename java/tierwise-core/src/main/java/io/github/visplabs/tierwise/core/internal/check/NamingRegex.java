package io.github.visplabs.tierwise.core.internal.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A naming pattern in the portable subset of regular expressions (LINT-17), with the search semantics of
 * LINT-18.
 *
 * <p>{@link #compile(String)} accepts exactly the subset that LINT-17 lists and gives a reason for every
 * other pattern. The subset is parsed here, not delegated to the platform engine, so that no construct
 * outside it is ever accepted. A valid pattern is then translated, construct by construct, to an
 * equivalent platform pattern: every literal is written as a code point escape, groups do not capture,
 * {@code .} matches every code point including line terminators, {@code ^} matches only at the start of
 * the input and {@code $} only at its true end (LINT-18, so a final line terminator is not skipped).
 */
public final class NamingRegex {

  /** The result of reading a pattern: the compiled pattern, or the reason it is invalid. */
  public record Outcome(Optional<NamingRegex> regex, String problem) {

    /**
     * Tells whether the pattern is in the subset and well formed.
     *
     * @return {@code true} for a valid pattern
     */
    public boolean valid() {
      return regex.isPresent();
    }
  }

  private static final String ESCAPABLE = "\\.[](){}|?*+^$-";
  private static final String CLASS_ESCAPABLE = "\\[]^-";
  private static final String CLASS_SPECIAL = "\\[]^-";

  private final String source;
  private final Pattern compiled;

  private NamingRegex(String source, Pattern compiled) {
    this.source = source;
    this.compiled = compiled;
  }

  /**
   * Reads a pattern.
   *
   * @param pattern the pattern text
   * @return the compiled pattern, or the reason that the text is not a valid pattern of the subset
   */
  public static Outcome compile(String pattern) {
    if (pattern.isEmpty()) {
      return invalid("the pattern is empty");
    }
    if (ValueText.hasUnpairedSurrogate(pattern)) {
      return invalid("the pattern holds an unpaired surrogate");
    }
    Reader reader = new Reader(pattern.codePoints().toArray());
    try {
      reader.alternation(0);
      if (reader.pos < reader.input.length) {
        throw new Fault("a closing parenthesis has no opening one");
      }
    } catch (Fault fault) {
      return invalid(fault.getMessage());
    }
    Pattern compiled = Pattern.compile(reader.out.toString(), Pattern.DOTALL);
    return new Outcome(Optional.of(new NamingRegex(pattern, compiled)), "");
  }

  private static Outcome invalid(String problem) {
    return new Outcome(Optional.empty(), problem);
  }

  /**
   * Tells whether a name matches: the pattern matches some part of the name (LINT-18).
   *
   * @param name the name to check
   * @return {@code true} when the pattern is found in the name
   */
  public boolean matches(String name) {
    return compiled.matcher(name).find();
  }

  /**
   * Returns the pattern as written.
   *
   * @return the source text
   */
  public String source() {
    return source;
  }

  @Override
  public String toString() {
    return source;
  }

  /** A fault in the pattern. */
  private static final class Fault extends Exception {
    private static final long serialVersionUID = 1L;

    Fault(String message) {
      super(message, null, false, false);
    }
  }

  /** Reads the code points of a pattern and writes the equivalent platform pattern. */
  private static final class Reader {

    private final int[] input;
    private final StringBuilder out = new StringBuilder();
    private int pos;

    Reader(int[] input) {
      this.input = input;
    }

    /** Reads alternatives up to the end of the input or an unconsumed closing parenthesis. */
    void alternation(int depth) throws Fault {
      boolean quantifiable = false;
      while (pos < input.length) {
        int c = input[pos];
        switch (c) {
          case ')' -> {
            if (depth == 0) {
              throw new Fault("a closing parenthesis has no opening one");
            }
            return;
          }
          case '(' -> {
            pos++;
            out.append("(?:");
            alternation(depth + 1);
            if (pos >= input.length) {
              throw new Fault("a group is not closed");
            }
            pos++;
            out.append(')');
            quantifiable = true;
          }
          case '|' -> {
            pos++;
            out.append('|');
            quantifiable = false;
          }
          case '[' -> {
            pos++;
            characterClass();
            quantifiable = true;
          }
          case '.' -> {
            pos++;
            out.append('.');
            quantifiable = true;
          }
          case '^' -> {
            pos++;
            out.append("\\A");
            quantifiable = false;
          }
          case '$' -> {
            pos++;
            out.append("\\z");
            quantifiable = false;
          }
          case '\\' -> {
            pos++;
            if (pos >= input.length || ESCAPABLE.indexOf(input[pos]) < 0) {
              throw new Fault("a backslash is followed by a character that it may not escape");
            }
            literal(input[pos++]);
            quantifiable = true;
          }
          case '?', '*', '+' -> {
            if (!quantifiable) {
              throw new Fault("a quantifier has nothing to repeat, or follows another quantifier");
            }
            pos++;
            out.appendCodePoint(c);
            quantifiable = false;
          }
          case '{' -> {
            if (!quantifiable) {
              throw new Fault("a quantifier has nothing to repeat, or follows another quantifier");
            }
            braces();
            quantifiable = false;
          }
          case '}', ']' -> throw new Fault("the character " + new String(Character.toChars(c))
              + " is a metacharacter and is literal only when escaped");
          default -> {
            pos++;
            literal(c);
            quantifiable = true;
          }
        }
      }
      if (depth > 0) {
        throw new Fault("a group is not closed");
      }
    }

    private void literal(int codePoint) {
      out.append("\\x{").append(Integer.toHexString(codePoint)).append('}');
    }

    /** Reads {@code {n}}, {@code {n,}} or {@code {n,m}}, the opening brace being the current code point. */
    private void braces() throws Fault {
      pos++;
      int lower = number();
      int upper = lower;
      boolean open = false;
      if (pos < input.length && input[pos] == ',') {
        pos++;
        if (pos < input.length && input[pos] == '}') {
          open = true;
        } else {
          upper = number();
        }
      }
      if (pos >= input.length || input[pos] != '}') {
        throw new Fault("a repeat is not closed");
      }
      pos++;
      if (!open && lower > upper) {
        throw new Fault("a repeat has a lower bound above its upper bound");
      }
      out.append('{').append(lower);
      if (open) {
        out.append(',');
      } else if (upper != lower) {
        out.append(',').append(upper);
      }
      out.append('}');
    }

    private int number() throws Fault {
      int start = pos;
      long value = 0;
      while (pos < input.length && input[pos] >= '0' && input[pos] <= '9') {
        value = value * 10 + (input[pos] - '0');
        if (value > 100_000) {
          throw new Fault("a repeat count is too large");
        }
        pos++;
      }
      if (pos == start) {
        throw new Fault("a repeat has no count");
      }
      return (int) value;
    }

    /** Reads a bracket class, the opening bracket being consumed. */
    private void characterClass() throws Fault {
      boolean negated = pos < input.length && input[pos] == '^';
      if (negated) {
        pos++;
      }
      List<int[]> ranges = new ArrayList<>();
      while (true) {
        if (pos >= input.length) {
          throw new Fault("a class is not closed");
        }
        int c = input[pos];
        if (c == ']') {
          pos++;
          break;
        }
        if (c == '&' && pos + 1 < input.length && input[pos + 1] == '&') {
          throw new Fault("an intersection in a class is outside the subset");
        }
        boolean plain = c != '\\';
        int low = classMember(c);
        int high = low;
        if (pos < input.length && input[pos] == '-') {
          if (pos + 1 < input.length && input[pos + 1] == ']') {
            throw new Fault("a hyphen at the end of a class must be escaped");
          }
          pos++;
          if (pos >= input.length) {
            throw new Fault("a class is not closed");
          }
          int next = input[pos];
          if (!plain || next == '\\') {
            throw new Fault("the ends of a range are plain literals");
          }
          high = classMember(next);
          if (high < low) {
            throw new Fault("a range ends below its start");
          }
        }
        ranges.add(new int[] {low, high});
      }
      if (ranges.isEmpty()) {
        throw new Fault("a class is empty");
      }
      out.append(negated ? "[^" : "[");
      for (int[] range : ranges) {
        out.append("\\x{").append(Integer.toHexString(range[0])).append('}');
        if (range[1] != range[0]) {
          out.append("-\\x{").append(Integer.toHexString(range[1])).append('}');
        }
      }
      out.append(']');
    }

    /** Reads one member of a class: a literal, or a backslash before one of the four escapable code points. */
    private int classMember(int c) throws Fault {
      pos++;
      if (c == '\\') {
        if (pos >= input.length || CLASS_ESCAPABLE.indexOf(input[pos]) < 0) {
          throw new Fault("a backslash in a class is followed by a character that it may not escape");
        }
        return input[pos++];
      }
      if (CLASS_SPECIAL.indexOf(c) >= 0) {
        throw new Fault("the character " + new String(Character.toChars(c))
            + " is literal in a class only when escaped");
      }
      return c;
    }
  }
}
