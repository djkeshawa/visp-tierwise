package io.github.tierwise.server.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PublicationRecordTest {

  private static final String V = "sha256:" + "a".repeat(64);

  private static Map<String, Object> json(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  @Test
  void aRecordWithAllMembers() {
    PublicationRecord record = PublicationRecord.fromJson(json("createdAt", "2026-10-09T08:30:00Z",
        "gitCommit", "0123456789abcdef0123456789abcdef01234567", "publisher", "ci"), V);
    assertEquals(V, record.bundleVersion());
    assertEquals(Optional.of("ci"), record.publisher());
    assertEquals(List.of("bundleVersion", "createdAt", "gitCommit", "publisher"), List.copyOf(record.toJson().keySet()));
  }

  @Test
  void aSha256CommitOf64DigitsIsValid() {
    PublicationRecord.fromJson(json("createdAt", "2026-10-09T08:30:00Z", "gitCommit", "f".repeat(64)), V);
  }

  @Test
  void theOptionalMembersMayBeAbsent() {
    PublicationRecord record = PublicationRecord.fromJson(json("createdAt", "2026-10-09T08:30:00Z"), V);
    assertEquals(Optional.empty(), record.gitCommit());
    assertEquals(Optional.empty(), record.publisher());
    assertEquals(List.of("bundleVersion", "createdAt"), List.copyOf(record.toJson().keySet()));
  }

  @Test
  void aLeapYearDayIsARealDate() {
    PublicationRecord.fromJson(json("createdAt", "2028-02-29T00:00:00Z"), V);
    assertThrows(IllegalArgumentException.class,
        () -> PublicationRecord.fromJson(json("createdAt", "2026-02-29T00:00:00Z"), V));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-10-09T08:30:00z", "2026-10-09t08:30:00Z", "2026-10-09T08:30:00+00:00",
      "2026-10-09T08:30Z", "2026-10-09T08:30:00.000Z", "2026-13-09T08:30:00Z", "2026-10-32T08:30:00Z",
      "2026-10-09T24:00:00Z", "2026-10-09T08:60:00Z", "26-10-09T08:30:00Z", "", " 2026-10-09T08:30:00Z"})
  void aCreatedAtThatBreaksBundle150IsRejected(String createdAt) {
    assertThrows(IllegalArgumentException.class, () -> PublicationRecord.fromJson(json("createdAt", createdAt), V));
  }

  @Test
  void aMissingCreatedAtIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> PublicationRecord.fromJson(json("publisher", "ci"), V));
  }

  @Test
  void aMemberOfTheWrongTypeIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> PublicationRecord.fromJson(json("createdAt", 5), V));
    assertThrows(IllegalArgumentException.class,
        () -> PublicationRecord.fromJson(json("createdAt", "2026-10-09T08:30:00Z", "publisher", List.of()), V));
    assertThrows(IllegalArgumentException.class,
        () -> PublicationRecord.fromJson(json("createdAt", "2026-10-09T08:30:00Z", "gitCommit", 1), V));
    assertThrows(IllegalArgumentException.class,
        () -> PublicationRecord.fromJson(json("createdAt", "2026-10-09T08:30:00Z", "publisher", null), V));
  }

  static Stream<String> notCommits() {
    return Stream.of("", "abc", "ABCDEF0123456789ABCDEF0123456789ABCDEF01", "g".repeat(40), "a".repeat(41),
        "a".repeat(39), "a".repeat(63), "a".repeat(65));
  }

  @ParameterizedTest
  @MethodSource("notCommits")
  void aGitCommitThatIsNotFortyOrSixtyFourLowerCaseDigitsIsRejected(String commit) {
    assertThrows(IllegalArgumentException.class,
        () -> PublicationRecord.fromJson(json("createdAt", "2026-10-09T08:30:00Z", "gitCommit", commit), V));
  }

  @Test
  void anUnknownMemberIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> PublicationRecord.fromJson(json("createdAt", "2026-10-09T08:30:00Z", "branch", "main"), V));
  }

  @Test
  void aBundleVersionMemberMustEqualTheBundlesVersion() {
    PublicationRecord.fromJson(json("bundleVersion", V, "createdAt", "2026-10-09T08:30:00Z"), V);
    assertThrows(IllegalArgumentException.class, () -> PublicationRecord.fromJson(
        json("bundleVersion", "sha256:" + "b".repeat(64), "createdAt", "2026-10-09T08:30:00Z"), V));
    assertThrows(IllegalArgumentException.class,
        () -> PublicationRecord.fromJson(json("bundleVersion", 1, "createdAt", "2026-10-09T08:30:00Z"), V));
  }

  @Test
  void aRecordForAMalformedVersionIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new PublicationRecord("sha256:abc", "2026-10-09T08:30:00Z",
        Optional.empty(), Optional.empty()));
  }
}
