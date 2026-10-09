package io.github.tierwise.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CodeTest {

  @Test
  void ofFindsEveryKindOfIdentifier() {
    assertEquals(Optional.of("E_PARSE"), Code.of("E_PARSE"));
    assertEquals(Optional.of("E_PROVENANCE_INVALID"), Code.of("E_PROVENANCE_INVALID"));
    assertEquals(Optional.of("values/no-op"), Code.of("values/no-op"));
    assertEquals(Optional.of("naming/key"), Code.of("naming/key"));
  }

  @Test
  void ofRejectsUnknownNames() {
    assertEquals(Optional.empty(), Code.of("E_NOT_A_CODE"));
    assertEquals(Optional.empty(), Code.of("e_parse"));
    assertEquals(Optional.empty(), Code.of("naming/"));
    assertEquals(Optional.empty(), Code.of("naming/*"));
    assertEquals(Optional.empty(), Code.of("naming/a b"));
    assertEquals(Optional.empty(), Code.of("naming/a/b"));
    assertEquals(Optional.empty(), Code.of(null));
  }

  @Test
  void namingBuildsTheRuleIdentifier() {
    assertEquals("naming/key", Code.naming("key"));
    assertThrows(IllegalArgumentException.class, () -> Code.naming(""));
    assertThrows(IllegalArgumentException.class, () -> Code.naming("*"));
  }

  @Test
  void theEvaluationCodesAreTheElevenOfEval38() {
    assertEquals(Set.of("E_BUNDLE_INTEGRITY", "E_BUNDLE_UNSUPPORTED", "E_PROVIDER_MISSING", "E_PROVIDER_REQUIRED",
        "E_CONTEXT_INVALID", "E_UNKNOWN_SCOPE", "E_UNKNOWN_NODE", "E_UNKNOWN_KEY", "E_UNKNOWN_FEATURE",
        "E_FACTS_INVALID", "E_EVALUATOR_OPTIONS"), Code.evaluation());
  }

  @Test
  void theSharedCodesAreBothEngineAndEvaluation() {
    for (String code : Set.of(Code.E_UNKNOWN_SCOPE, Code.E_UNKNOWN_NODE, Code.E_UNKNOWN_KEY, Code.E_UNKNOWN_FEATURE)) {
      assertTrue(Code.engine().contains(code), code);
      assertTrue(Code.evaluation().contains(code), code);
    }
    assertFalse(Code.engine().contains(Code.E_BUNDLE_INTEGRITY));
    assertFalse(Code.engine().contains(Code.E_PROVENANCE_INVALID));
  }

  @Test
  void theCatalogueAndTheConstantsAgree() throws IOException {
    Path catalogue = Paths.get(System.getProperty("tierwise.spec.dir", "../../spec")).resolve("error-codes.md");
    Set<String> documented = new LinkedHashSet<>();
    Matcher matcher = Pattern.compile("(?m)^### (E_[A-Z_]+)$").matcher(Files.readString(catalogue));
    while (matcher.find()) {
      documented.add(matcher.group(1));
    }
    Matcher lint = Pattern.compile("(?m)^### ((?:examples|presets|rules|schema|values)/[a-z-]+)$")
        .matcher(Files.readString(catalogue));
    while (lint.find()) {
      documented.add(lint.group(1));
    }
    Set<String> declared = new LinkedHashSet<>(Code.all());
    assertEquals(documented, declared);
  }
}
