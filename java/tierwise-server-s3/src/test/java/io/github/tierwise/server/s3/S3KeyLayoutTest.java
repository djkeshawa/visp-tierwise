package io.github.tierwise.server.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class S3KeyLayoutTest {

  static final String VERSION = "sha256:" + "a".repeat(64);
  static final String CREATED = "2026-03-04T05:06:07Z";

  @Test
  void bundleKeyWithoutPrefixIsUnderBundles() {
    assertEquals("bundles/" + VERSION + ".json", new S3KeyLayout(null).bundleKey(VERSION));
    assertEquals("bundles/" + VERSION + ".json", new S3KeyLayout("").bundleKey(VERSION));
  }

  @Test
  void prefixSlashesAreTrimmedAndEveryKeyStartsWithThePrefix() {
    S3KeyLayout layout = new S3KeyLayout("/tierwise/prod/");
    assertEquals("tierwise/prod/bundles/" + VERSION + ".json", layout.bundleKey(VERSION));
    assertEquals("tierwise/prod/latest", layout.latestKey());
    assertEquals("tierwise/prod/publications/", layout.publicationsRoot());
  }

  @Test
  void slashOnlyPrefixMeansNoPrefix() {
    assertEquals("latest", new S3KeyLayout("/").latestKey());
    assertEquals("latest", new S3KeyLayout("///").latestKey());
  }

  @Test
  void prefixWithEmptyMiddleSegmentIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new S3KeyLayout("a//b"));
  }

  @Test
  void publicationKeyUsesVersionDirectoryAndCreatedAtSlot() {
    S3KeyLayout layout = new S3KeyLayout("tw");
    assertEquals("tw/publications/" + VERSION + "/" + CREATED + "-1.json", layout.publicationKey(VERSION, CREATED, 1));
    assertEquals("tw/publications/" + VERSION + "/", layout.publicationsPrefix(VERSION));
  }

  @Test
  void publicationKeyRejectsSlotZeroAndNegativeSlots() {
    assertThrows(IllegalArgumentException.class, () -> new S3KeyLayout(null).publicationKey(VERSION, CREATED, 0));
    assertThrows(IllegalArgumentException.class, () -> new S3KeyLayout(null).publicationKey(VERSION, CREATED, -3));
  }

  @Test
  void versionsAreCheckedWithTheSpiRuleBeforeAnyKeyIsBuilt() {
    S3KeyLayout layout = new S3KeyLayout(null);
    List<String> bad = List.of(
        "sha256:" + "A".repeat(64),
        "sha256:" + "a".repeat(63),
        "sha256:" + "a".repeat(65),
        "sha512:" + "a".repeat(64),
        "sha256:" + "a".repeat(63) + "/",
        "../" + VERSION,
        " " + VERSION,
        VERSION + "\n",
        "");
    for (String version : bad) {
      assertThrows(IllegalArgumentException.class, () -> layout.bundleKey(version), version);
      assertThrows(IllegalArgumentException.class, () -> layout.publicationsPrefix(version), version);
    }
  }

  @Test
  void nullVersionIsRejectedAsAnIllegalArgumentLikeTheSpi() {
    assertThrows(IllegalArgumentException.class, () -> new S3KeyLayout(null).bundleKey(null));
  }

  @Test
  void createdAtFollowsThePublicationRecordRule() {
    assertEquals(CREATED, S3KeyLayout.requireCreatedAt(CREATED));
    assertTrue(S3KeyLayout.isCreatedAt("2026-02-28T23:59:59Z"));
    List<String> bad = List.of(
        "2026-03-04T05:06:07.000Z",
        "2026-03-04T05:06:07+00:00",
        "2026-03-04 05:06:07Z",
        "2026-03-04T05:06Z",
        "2026-03-04T05:06:07z",
        "2026-03-04T05:06:07Z-1",
        "2026-13-04T05:06:07Z",
        "2026-03-32T05:06:07Z",
        "2026-02-30T05:06:07Z",
        "2026-03-04T24:00:00Z",
        "2026-03-04T05:06:61Z");
    for (String value : bad) {
      assertFalse(S3KeyLayout.isCreatedAt(value), value);
      assertThrows(IllegalArgumentException.class, () -> S3KeyLayout.requireCreatedAt(value), value);
    }
    assertFalse(S3KeyLayout.isCreatedAt(null));
  }

  @Test
  void parsesCanonicalPublicationKeyBack() {
    S3KeyLayout layout = new S3KeyLayout("tw");
    String key = layout.publicationKey(VERSION, CREATED, 12);
    S3KeyLayout.PublicationSlot slot = layout.parsePublicationKey(VERSION, key).orElseThrow();
    assertEquals(new S3KeyLayout.PublicationSlot(CREATED, 12), slot);
  }

  @Test
  void rejectsNonCanonicalOrForeignPublicationKeys() {
    S3KeyLayout layout = new S3KeyLayout("tw");
    String base = layout.publicationsPrefix(VERSION);
    List<String> keys = List.of(
        base + CREATED + "-01.json",
        base + CREATED + "-0.json",
        base + CREATED + "-.json",
        base + CREATED + ".json",
        base + CREATED + "-1",
        base + CREATED + "-1.json.bak",
        base + "2026-03-04T05:06:07.000Z-1.json",
        base + "2026-02-30T05:06:07Z-1.json",
        base + "notes.txt",
        base + CREATED + "-1.json/",
        base + CREATED + "-1000000000.json",
        "tw/publications/sha256:" + "b".repeat(64) + "/" + CREATED + "-1.json",
        "other/" + CREATED + "-1.json");
    for (String key : keys) {
      assertTrue(layout.parsePublicationKey(VERSION, key).isEmpty(), key);
    }
  }

  @Test
  void versionOfAPublicationKeyIsItsFirstSegmentWhenThatIsAVersion() {
    S3KeyLayout layout = new S3KeyLayout("tw");
    String key = layout.publicationKey(VERSION, CREATED, 1);

    assertEquals(Optional.of(VERSION), layout.publicationVersionOf(key));
    assertEquals(Optional.empty(), layout.publicationVersionOf("tw/publications/notes/x.json"));
    assertEquals(Optional.empty(), layout.publicationVersionOf("tw/publications/loose.json"));
    assertEquals(Optional.empty(), layout.publicationVersionOf("tw/bundles/" + VERSION + ".json"));
  }

  @Test
  void publicationSlotsOrderByCreatedAtThenNumber() {
    List<S3KeyLayout.PublicationSlot> slots = new ArrayList<>(List.of(
        new S3KeyLayout.PublicationSlot("2026-03-04T05:06:08Z", 1),
        new S3KeyLayout.PublicationSlot("2026-03-04T05:06:07Z", 10),
        new S3KeyLayout.PublicationSlot("2026-03-04T05:06:07Z", 2)));
    slots.sort(S3KeyLayout.PublicationSlot.ORDER);
    assertEquals(List.of(
        new S3KeyLayout.PublicationSlot("2026-03-04T05:06:07Z", 2),
        new S3KeyLayout.PublicationSlot("2026-03-04T05:06:07Z", 10),
        new S3KeyLayout.PublicationSlot("2026-03-04T05:06:08Z", 1)), slots);
  }
}
