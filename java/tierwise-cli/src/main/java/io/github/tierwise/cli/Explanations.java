package io.github.tierwise.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The explanations that {@code tierwise explain} prints (FIND-29): the entries of the error-code
 * catalogue {@code spec/error-codes.md}, which the build copies into the jar as a resource.
 *
 * <p>An entry is a third-level heading ({@code ### E_UNKNOWN_KEY}, {@code ### values/no-op}) and the text
 * up to the next heading. Only the entries between {@code ## Engine errors} and {@code ## Index} count.
 * The entry {@code naming/*} explains a naming rule {@code naming/<name>} when no project is given; with a
 * project, {@link ExplainCommand} uses the declared pattern (FIND-30).
 */
final class Explanations {

  /** The resource name of the catalogue, next to this class. */
  static final String RESOURCE = "error-codes.md";

  private static final String FIRST_SECTION = "## Engine errors";
  private static final String LAST_SECTION = "## Index";
  private static final String NAMING_FAMILY = "naming/*";
  private static final String NAMING_PREFIX = "naming/";

  private final Map<String, String> entries;

  private Explanations(Map<String, String> entries) {
    this.entries = entries;
  }

  /**
   * Loads the catalogue from the class path.
   *
   * @return the explanations
   * @throws UsageException if the resource is missing or cannot be read
   */
  static Explanations load() {
    try (InputStream in = Explanations.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new UsageException("the error-code catalogue is not part of this build");
      }
      return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UsageException("cannot read the error-code catalogue: " + e.getMessage(), e);
    }
  }

  /**
   * Parses the text of the catalogue.
   *
   * @param markdown the text of {@code error-codes.md}
   * @return the explanations
   */
  static Explanations parse(String markdown) {
    Map<String, String> entries = new LinkedHashMap<>();
    boolean inEntries = false;
    String name = null;
    List<String> body = new ArrayList<>();
    for (String line : markdown.split("\r?\n", -1)) {
      if (line.startsWith("## ")) {
        flush(entries, name, body);
        name = null;
        inEntries = line.equals(FIRST_SECTION) || (inEntries && !line.equals(LAST_SECTION));
        continue;
      }
      if (!inEntries) {
        continue;
      }
      if (line.startsWith("### ")) {
        flush(entries, name, body);
        name = line.substring(4).trim();
        body = new ArrayList<>();
      } else if (name != null) {
        body.add(line);
      }
    }
    flush(entries, name, body);
    return new Explanations(entries);
  }

  private static void flush(Map<String, String> entries, String name, List<String> body) {
    if (name == null) {
      return;
    }
    int end = body.size();
    while (end > 0 && body.get(end - 1).isBlank()) {
      end--;
    }
    entries.put(name, String.join("\n", body.subList(0, end)).strip());
  }

  /**
   * Returns the names of all entries in catalogue order: engine errors, evaluation errors, the tooling
   * error, then lint rules.
   *
   * @return the codes and rule identifiers
   */
  List<String> names() {
    return List.copyOf(entries.keySet());
  }

  /**
   * Looks an entry up by a code or a lint rule identifier. A code matches without regard to case, and
   * a rule {@code naming/<name>} is explained by the entry {@code naming/*} (FIND-30).
   *
   * @param query a code such as {@code E_UNKNOWN_KEY}, or a rule such as {@code values/no-op}
   * @return the entry text, starting with its heading, or empty when there is none
   */
  Optional<String> explain(String query) {
    String wanted = query.trim().toLowerCase(Locale.ROOT);
    for (Map.Entry<String, String> entry : entries.entrySet()) {
      if (entry.getKey().toLowerCase(Locale.ROOT).equals(wanted)) {
        return Optional.of(entry.getKey() + "\n" + entry.getValue());
      }
    }
    if (wanted.startsWith(NAMING_PREFIX) && entries.containsKey(NAMING_FAMILY)) {
      String text = NAMING_FAMILY + "\n" + entries.get(NAMING_FAMILY);
      if (!wanted.equals(NAMING_FAMILY)) {
        text += "\n\n`" + query.trim() + "` checks the naming pattern called `"
            + query.trim().substring(NAMING_PREFIX.length())
            + "`. The description declared for that pattern in your project or its presets is the"
            + " explanation of this rule (FIND-30); the text above covers the whole family. The pattern needs a"
            + " project: run `tierwise explain --project DIR " + query.trim() + "`, or run the command in the"
            + " project directory.";
      }
      return Optional.of(text);
    }
    return Optional.empty();
  }

  /**
   * Returns the text of the naming family entry {@code naming/*}, without its heading (FIND-30).
   *
   * @return the entry text
   * @throws UsageException if the catalogue has no such entry
   */
  String namingFamily() {
    String text = entries.get(NAMING_FAMILY);
    if (text == null) {
      throw new UsageException("the error-code catalogue has no entry " + NAMING_FAMILY);
    }
    return text;
  }

  /**
   * Finds the name closest to an unknown one, by edit distance.
   *
   * @param unknown the name that matched nothing
   * @param candidates the known names
   * @return the closest candidate within a small distance, or empty when none is close
   */
  static Optional<String> closest(String unknown, Collection<String> candidates) {
    String best = null;
    int bestDistance = Math.max(2, unknown.length() / 4) + 1;
    for (String candidate : candidates) {
      int distance = distance(unknown, candidate);
      if (distance < bestDistance) {
        best = candidate;
        bestDistance = distance;
      }
    }
    return Optional.ofNullable(best);
  }

  /**
   * Finds the entry names closest to a query that matched nothing.
   *
   * @param query what the user typed
   * @return up to three names, closest first, or an empty list when none is close
   */
  List<String> suggestions(String query) {
    String wanted = query.trim().toLowerCase(Locale.ROOT);
    List<String> names = new ArrayList<>(entries.keySet());
    names.sort((a, b) -> Integer.compare(distance(wanted, a.toLowerCase(Locale.ROOT)),
        distance(wanted, b.toLowerCase(Locale.ROOT))));
    List<String> close = new ArrayList<>();
    for (String name : names) {
      String lower = name.toLowerCase(Locale.ROOT);
      boolean near = distance(wanted, lower) <= Math.max(2, wanted.length() / 4)
          || (wanted.length() >= 3 && lower.contains(wanted));
      if (near && close.size() < 3) {
        close.add(name);
      }
    }
    return close;
  }

  private static int distance(String a, String b) {
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
  }
}
