package io.github.visplabs.tierwise.core.internal.check;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * The zone and link names of the pinned IANA tz database release (TYPE-34, TYPE-36, TYPE-80). The names
 * are the lines of {@code spec/data/tz-names.txt}, which the build copies onto the classpath next to
 * this class. A compiler accepts an {@code iana_tz} value only when it equals a listed name code point
 * for code point.
 */
final class TimeZoneNames {

  private static final String RESOURCE = "tz-names.txt";

  private TimeZoneNames() {
  }

  /** Loads the names on first use. */
  private static final class Holder {
    static final Set<String> NAMES = load();
  }

  /**
   * Tells whether a name is a listed zone or link name.
   *
   * @param name the name, compared code point for code point
   * @return {@code true} when the release lists it
   */
  static boolean contains(String name) {
    return Holder.NAMES.contains(name);
  }

  private static Set<String> load() {
    try (InputStream in = TimeZoneNames.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(RESOURCE + " is missing from the classpath");
      }
      Set<String> names = new HashSet<>();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
          if (!line.isEmpty()) {
            names.add(line);
          }
        }
      }
      return Set.copyOf(names);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
