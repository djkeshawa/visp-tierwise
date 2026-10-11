package io.github.visplabs.tierwise.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FindingTest {

  private static Finding finding(String message, Optional<String> suggestion) {
    return new Finding(Code.E_UNKNOWN_KEY, Severity.ERROR, "values/level_a/node_x.yaml",
        List.of("key_x", 0), 7, message, suggestion);
  }

  @Test
  void equalComponentsGiveEqualFindings() {
    assertEquals(finding("m", Optional.empty()), finding("m", Optional.empty()));
    assertEquals(finding("m", Optional.empty()).hashCode(), finding("m", Optional.empty()).hashCode());
    assertNotEquals(finding("m", Optional.empty()), finding("other", Optional.empty()));
    assertNotEquals(finding("m", Optional.empty()), finding("m", Optional.of("key_y")));
  }

  @Test
  void theKeyIgnoresSeverityMessageAndSuggestion() {
    Finding first = finding("m", Optional.empty());
    Finding second = new Finding(Code.E_UNKNOWN_KEY, Severity.WARN, "values/level_a/node_x.yaml",
        List.of("key_x", 0), 7, "different", Optional.of("key_y"));
    assertEquals(first.key(), second.key());
    assertEquals(Set.of(first.key()), Set.of(second.key()));
    assertNotEquals(first.key(), Finding.of(Code.E_UNKNOWN_KEY, Severity.ERROR, "a.yaml", List.of(), 7, "m").key());
  }

  @Test
  void thePathIsCopiedAndImmutable() {
    List<Object> path = new ArrayList<>(List.of("key_x"));
    Finding finding = Finding.of(Code.E_PARSE, Severity.ERROR, "tierwise.yaml", path, 1, "m");
    path.add("more");
    assertEquals(List.of("key_x"), finding.path());
    assertThrows(UnsupportedOperationException.class, () -> finding.path().add("x"));
  }

  @Test
  void aPathSegmentIsAStringOrAnInteger() {
    assertThrows(IllegalArgumentException.class,
        () -> Finding.of(Code.E_PARSE, Severity.ERROR, "f", List.of(1.5), 1, "m"));
    assertThrows(IllegalArgumentException.class,
        () -> Finding.of(Code.E_PARSE, Severity.ERROR, "f", List.of(1L), 1, "m"));
  }

  @Test
  void theLineIsOneBased() {
    assertThrows(IllegalArgumentException.class,
        () -> Finding.of(Code.E_PARSE, Severity.ERROR, "f", List.of(), 0, "m"));
  }

  @Test
  void severityParsesItsSpelling() {
    assertEquals(Optional.of(Severity.WARN), Severity.parse("warn"));
    assertEquals(Optional.empty(), Severity.parse("off"));
    assertEquals("error", Severity.ERROR.id());
  }
}
