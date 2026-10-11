package io.github.visplabs.tierwise.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The JSON form of section 9.2 and 9.3, using the non-normative examples of section 9. */
class ResolvedTest {

  private static final String VERSION = "sha256:" + "0".repeat(64);

  private static Map<String, Object> map(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  @Test
  void anOverrideResultHasASourceAndNoOnSourceFailure() {
    Resolved resolved = new Resolved("key_x", "override", Optional.of("kind_x"), 8, VERSION, List.of(),
        List.of(
            new Resolved.Contribution(ScopeKind.LEVEL, "level_a", Optional.of("node_x"), Optional.of(6),
                Resolved.Contribution.Label.SET, Optional.empty()),
            new Resolved.Contribution(ScopeKind.AXIS, "axis_a", Optional.of("node_y"), Optional.of(8),
                Resolved.Contribution.Label.SET, Optional.empty())),
        Optional.of(Resolved.Source.of(ScopeKind.AXIS, "axis_a", "node_y")));
    Map<String, Object> expected = map(
        "key", "key_x", "strategy", "override", "kind", "kind_x", "value", 8L, "bundleVersion", VERSION,
        "skippedScopes", List.of(),
        "contributions", List.of(
            map("scope", "level", "name", "level_a", "node", "node_x", "value", 6L, "effect", "SET"),
            map("scope", "axis", "name", "axis_a", "node", "node_y", "value", 8L, "effect", "SET")),
        "source", map("scope", "axis", "name", "axis_a", "node", "node_y"));
    assertEquals(expected, resolved.toJson());
    assertEquals(List.of("key", "strategy", "kind", "value", "bundleVersion", "skippedScopes", "contributions",
        "source"), new ArrayList<>(resolved.toJson().keySet()));
  }

  @Test
  void aFailureUnderDenyKeepsItsLabelAndCarriesTheDenyValue() {
    Resolved resolved = new Resolved("key_x", "all", Optional.of("kind_x"), false, VERSION, List.of(),
        List.of(new Resolved.Contribution(ScopeKind.LEVEL, "level_a", Optional.of("node_x"), Optional.of(false),
            Resolved.Contribution.Label.UNAVAILABLE, Optional.of("deny"))),
        Optional.empty());
    Map<String, Object> json = resolved.toJson();
    assertFalse(json.containsKey("source"));
    assertEquals(List.of(map("scope", "level", "name", "level_a", "node", "node_x", "value", false,
        "effect", "UNAVAILABLE", "onSourceFailure", "deny")), json.get("contributions"));
  }

  @Test
  void anAbsentValueIsJsonNullAndAbsentMembersAreAbsent() {
    Resolved resolved = new Resolved("key_x", "min", Optional.empty(), null, VERSION,
        List.of(new SkippedScope(ScopeKind.LEVEL, "level_a", "node_z")),
        List.of(new Resolved.Contribution(ScopeKind.LEVEL, "level_b", Optional.of("node_w"), Optional.empty(),
            Resolved.Contribution.Label.INVALID, Optional.of("skip"))),
        Optional.empty());
    Map<String, Object> json = resolved.toJson();
    assertTrue(json.containsKey("value"));
    assertEquals(null, json.get("value"));
    assertFalse(resolved.hasValue());
    assertFalse(json.containsKey("kind"));
    assertEquals(List.of(map("scope", "level", "name", "level_a", "node", "node_z", "reason", "UNKNOWN_NODE")),
        json.get("skippedScopes"));
    assertEquals(List.of(map("scope", "level", "name", "level_b", "node", "node_w", "effect", "INVALID",
        "onSourceFailure", "skip")), json.get("contributions"));
  }

  @Test
  void aRootContributionHasNoNodeAndTheDefaultSourceIsAString() {
    Resolved resolved = new Resolved("key_x", "override", Optional.empty(), 4, VERSION, List.of(),
        List.of(), Optional.of(Resolved.Source.DEFAULT));
    assertEquals("DEFAULT", resolved.toJson().get("source"));
    Resolved.Contribution root = new Resolved.Contribution(ScopeKind.LEVEL, "level_root", Optional.empty(),
        Optional.of(1), Resolved.Contribution.Label.SET, Optional.empty());
    assertFalse(root.toJson().containsKey("node"));
    assertEquals(Map.of("scope", "level", "name", "level_root"),
        Resolved.Source.of(ScopeKind.LEVEL, "level_root", null).toJson());
  }

  @Test
  void contributionPresenceRulesAreEnforced() {
    assertThrows(IllegalArgumentException.class, () -> new Resolved.Contribution(ScopeKind.LEVEL, "a",
        Optional.of("n"), Optional.of(1), Resolved.Contribution.Label.UNAVAILABLE, Optional.of("skip")));
    assertThrows(IllegalArgumentException.class, () -> new Resolved.Contribution(ScopeKind.LEVEL, "a",
        Optional.of("n"), Optional.empty(), Resolved.Contribution.Label.UNAVAILABLE, Optional.of("deny")));
    assertThrows(IllegalArgumentException.class, () -> new Resolved.Contribution(ScopeKind.LEVEL, "a",
        Optional.of("n"), Optional.of(1), Resolved.Contribution.Label.SET, Optional.of("skip")));
    assertThrows(IllegalArgumentException.class, () -> new Resolved.Contribution(ScopeKind.LEVEL, "a",
        Optional.of("n"), Optional.empty(), Resolved.Contribution.Label.SET, Optional.empty()));
  }

  @Test
  void valuesAreCopiedDeeply() {
    List<Object> members = new ArrayList<>(List.of("A"));
    Resolved resolved = new Resolved("key_x", "union", Optional.empty(), members, VERSION, List.of(), List.of(),
        Optional.empty());
    members.add("B");
    assertEquals(List.of("A"), resolved.value());
    assertThrows(UnsupportedOperationException.class, () -> ((List<?>) resolved.value()).clear());
  }
}
