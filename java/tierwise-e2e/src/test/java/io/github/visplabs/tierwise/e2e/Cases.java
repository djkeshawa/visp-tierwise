package io.github.visplabs.tierwise.e2e;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** The calls that every service answers: {@code cases.json}. */
final class Cases {

  private Cases() {
  }

  /** Reads the cases: objects with {@code id}, {@code call} ({@code resolve} or {@code decide}), {@code context} and the call's arguments. */
  static List<JsonNode> load() {
    try (InputStream in = Cases.class.getResourceAsStream("/cases.json")) {
      if (in == null) {
        throw new IllegalStateException("cases.json is missing from the test resources");
      }
      List<JsonNode> cases = new ArrayList<>();
      Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).forEach(cases::add);
      return cases;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
