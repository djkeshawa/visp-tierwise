package io.github.tierwise.server.s3;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.server.store.BundleConflictException;
import io.github.tierwise.server.store.BundleStoreException;
import io.github.tierwise.server.store.PublicationRecord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class S3BundleStoreTest {

  static final String BUCKET = "tierwise-test";
  static final String A = S3KeyLayoutTest.VERSION;
  static final String B = "sha256:" + "b".repeat(64);
  static final String C = "sha256:" + "c".repeat(64);
  static final String T1 = "2026-03-04T05:06:07Z";
  static final String T2 = "2026-03-04T05:06:08Z";
  static final String GIT_SHA1 = "0123456789abcdef0123456789abcdef01234567";
  static final String GIT_SHA256 = "f".repeat(64);
  static final byte[] BYTES_A = "bundle-a".getBytes(UTF_8);

  FakeS3Client s3;
  S3BundleStore store;

  @BeforeEach
  void setUp() {
    s3 = new FakeS3Client(BUCKET);
    store = new S3BundleStore(s3, BUCKET, "tw");
  }

  static byte[] bytes(String text) {
    return text.getBytes(UTF_8);
  }

  static PublicationRecord record(String version, String createdAt, String publisher) {
    return new PublicationRecord(version, createdAt, Optional.empty(), Optional.ofNullable(publisher));
  }

  static List<String> publishers(List<PublicationRecord> records) {
    List<String> out = new ArrayList<>();
    for (PublicationRecord record : records) {
      out.add(record.publisher().orElse("<none>"));
    }
    return out;
  }

  static Map<String, Object> parseJson(byte[] body) {
    return JsonMapper.builder().build().readValue(body, new TypeReference<Map<String, Object>>() { });
  }

  String pointerEtag() {
    return s3.stored("tw/latest").etag();
  }

  // Construction

  @Test
  void constructorRejectsNullClientAndBlankBucket() {
    assertThrows(NullPointerException.class, () -> new S3BundleStore(null, BUCKET, null));
    assertThrows(IllegalArgumentException.class, () -> new S3BundleStore(s3, " ", null));
    assertThrows(IllegalArgumentException.class, () -> new S3BundleStore(s3, null, null));
  }

  @Test
  void constructorRejectsAMalformedPrefix() {
    assertThrows(IllegalArgumentException.class, () -> new S3BundleStore(s3, BUCKET, "a//b"));
  }

  // Bundles

  @Test
  void putBundleCreatesTheVersionedKeyWithAConditionalCreateAndReturnsTrue() {
    assertTrue(store.putBundle(A, BYTES_A));

    assertArrayEquals(BYTES_A, s3.bytesOf("tw/bundles/" + A + ".json").orElseThrow());
    PutObjectRequest request = s3.putRequests().get(0);
    assertEquals("*", request.ifNoneMatch());
    assertNull(request.ifMatch());
    assertEquals("application/json", request.contentType());
  }

  @Test
  void identicalBytesReturnFalseAndTheStoredObjectIsNotRewritten() {
    store.putBundle(A, BYTES_A);
    String etag = s3.stored("tw/bundles/" + A + ".json").etag();

    assertFalse(store.putBundle(A, BYTES_A.clone()));

    assertEquals(etag, s3.stored("tw/bundles/" + A + ".json").etag());
    assertEquals(2, s3.putRequests().size(), "the second write was sent and rejected by the precondition");
  }

  @Test
  void differentBytesAreRejectedWithTheSpiConflictAndTheStoredBytesAreKept() {
    store.putBundle(A, BYTES_A);

    BundleConflictException error = assertThrows(BundleConflictException.class,
        () -> store.putBundle(A, bytes("other body")));

    assertTrue(error.getMessage().contains(A));
    assertArrayEquals(BYTES_A, store.getBundle(A).orElseThrow());
  }

  @Test
  void aConflictingWriteThatLeavesNoObjectIsRetried() {
    s3.failNext("putObject", 409, 1);

    assertTrue(store.putBundle(A, BYTES_A));
    assertEquals(2, s3.putRequests().size());
  }

  @Test
  void aRacingWriterThatFinishesWithOtherBytesIsAConflict() {
    String key = "tw/bundles/" + A + ".json";
    s3.beforeNextPut(() -> s3.seed(key, bytes("written first by another publisher")));
    s3.failNext("putObject", 409, 1);

    assertThrows(BundleConflictException.class, () -> store.putBundle(A, BYTES_A));
    assertArrayEquals(bytes("written first by another publisher"), s3.bytesOf(key).orElseThrow());
  }

  @Test
  void aRacingWriterThatFinishesWithIdenticalBytesReturnsFalse() {
    String key = "tw/bundles/" + A + ".json";
    s3.beforeNextPut(() -> s3.seed(key, BYTES_A));

    assertFalse(store.putBundle(A, BYTES_A));
  }

  @Test
  void repeatedConflictingWritesWithNoVisibleObjectEndAsAStoreError() {
    s3.failNext("putObject", 409, S3BundleStore.MAX_ATTEMPTS);

    BundleStoreException error = assertThrows(BundleStoreException.class, () -> store.putBundle(A, BYTES_A));

    assertTrue(error.getMessage().contains("kept conflicting"), error.getMessage());
    assertTrue(s3.bytesOf("tw/bundles/" + A + ".json").isEmpty());
    assertEquals(S3BundleStore.MAX_ATTEMPTS, s3.putRequests().size());
  }

  @Test
  void otherWriteFailuresAreStoreErrorsThatCarryTheHttpStatus() {
    s3.failNext("putObject", 500, 1);

    BundleStoreException error = assertThrows(BundleStoreException.class, () -> store.putBundle(A, BYTES_A));

    assertTrue(error.getMessage().contains("HTTP 500"), error.getMessage());
    assertTrue(error.getCause() instanceof S3Exception);
    assertTrue(s3.bytesOf("tw/bundles/" + A + ".json").isEmpty());
  }

  @Test
  void putBundleKeepsItsOwnCopyOfTheCallerBytes() {
    byte[] input = BYTES_A.clone();
    store.putBundle(A, input);
    input[0] = 'X';

    assertArrayEquals(BYTES_A, store.getBundle(A).orElseThrow());
  }

  @Test
  void malformedVersionsAreIllegalArgumentsBeforeAnyRequest() {
    List<String> versions = List.of("sha256:xyz", "../" + A, "sha256:" + "A".repeat(64));
    for (String version : versions) {
      assertThrows(IllegalArgumentException.class, () -> store.putBundle(version, BYTES_A), version);
    }
    assertThrows(IllegalArgumentException.class, () -> store.putBundle(null, BYTES_A));
    assertTrue(s3.putRequests().isEmpty());
  }

  @Test
  void nullBytesAreANullPointerBeforeAnyRequest() {
    assertThrows(NullPointerException.class, () -> store.putBundle(A, null));
    assertTrue(s3.putRequests().isEmpty());
  }

  @Test
  void getBundleReturnsStoredBytesOrEmpty() {
    assertEquals(Optional.empty(), store.getBundle(A));
    store.putBundle(A, BYTES_A);
    assertArrayEquals(BYTES_A, store.getBundle(A).orElseThrow());
  }

  @Test
  void getBundleReportsOtherFailures() {
    store.putBundle(A, BYTES_A);
    s3.failNext("getObject", 503, 1);

    BundleStoreException error = assertThrows(BundleStoreException.class, () -> store.getBundle(A));
    assertTrue(error.getMessage().contains("HTTP 503"));
  }

  // Publication records: writing and the JSON form

  @Test
  void addRecordStoresTheJsonFormOfTheRecordInTheFirstFreeSlot() {
    PublicationRecord record = new PublicationRecord(A, T1, Optional.of(GIT_SHA1), Optional.of("ci"));

    store.addRecord(record);

    String key = "tw/publications/" + A + "/" + T1 + "-1.json";
    assertEquals(record.toJson(), parseJson(s3.bytesOf(key).orElseThrow()));
    PutObjectRequest request = s3.putRequests().get(0);
    assertEquals("*", request.ifNoneMatch());
    assertEquals("application/json", request.contentType());
  }

  @Test
  void aRecordReadBackIsEqualToTheRecordThatWasAdded() {
    PublicationRecord withSha1 = new PublicationRecord(A, T1, Optional.of(GIT_SHA1), Optional.of("ci-runner"));
    PublicationRecord withSha256 = new PublicationRecord(A, T2, Optional.of(GIT_SHA256), Optional.empty());
    PublicationRecord bare = new PublicationRecord(A, T2, Optional.empty(), Optional.empty());
    store.addRecord(withSha1);
    store.addRecord(withSha256);
    store.addRecord(bare);

    assertEquals(List.of(withSha1, withSha256, bare), store.records(A));
  }

  @Test
  void addRecordRejectsANullRecord() {
    assertThrows(NullPointerException.class, () -> store.addRecord(null));
    assertTrue(s3.putRequests().isEmpty());
  }

  @Test
  void publicationRecordsTakeFreeSlotsAndNeverReplaceEachOther() {
    store.addRecord(record(A, T1, "p1"));
    store.addRecord(record(A, T1, "p2"));
    store.addRecord(record(A, T1, "p3"));

    assertEquals(List.of(
        "tw/publications/" + A + "/" + T1 + "-1.json",
        "tw/publications/" + A + "/" + T1 + "-2.json",
        "tw/publications/" + A + "/" + T1 + "-3.json"), s3.keys());
    assertEquals(List.of("p1", "p2", "p3"), publishers(store.records(A)));
  }

  @Test
  void slotCollisionsCostOneRequestEach() {
    store.addRecord(record(A, T1, "1"));
    store.addRecord(record(A, T1, "2"));
    store.addRecord(record(A, T1, "3"));

    assertEquals(1 + 2 + 3, s3.putRequests().size());
  }

  @Test
  void aConflictingWriteOnASlotMovesThePublicationToTheNextSlot() {
    s3.failNext("putObject", 409, 1);

    store.addRecord(record(A, T1, "only"));

    assertEquals(List.of("only"), publishers(store.records(A)));
    assertTrue(s3.bytesOf("tw/publications/" + A + "/" + T1 + "-2.json").isPresent());
  }

  @Test
  void slotsTenAndElevenSortAfterSlotNineInTheOrderTheyWereAdded() {
    for (int i = 1; i <= 12; i++) {
      store.addRecord(record(A, T1, "record-" + i));
    }

    assertTrue(s3.bytesOf("tw/publications/" + A + "/" + T1 + "-10.json").isPresent());
    List<String> expected = new ArrayList<>();
    for (int i = 1; i <= 12; i++) {
      expected.add("record-" + i);
    }
    assertEquals(expected, publishers(store.records(A)));
  }

  @Test
  void recordsOfOtherVersionsAreNotListed() {
    store.addRecord(record(A, T1, "a"));
    store.addRecord(record(B, T1, "b"));

    assertEquals(List.of("a"), publishers(store.records(A)));
    assertEquals(List.of("b"), publishers(store.records(B)));
    assertEquals(List.of(), store.records(C));
  }

  // Publication records: ordering

  @Test
  void recordsAreOrderedByCreatedAtBeforeAddOrder() {
    store.addRecord(record(A, T2, "later"));
    store.addRecord(record(A, T1, "earlier"));

    assertEquals(List.of("earlier", "later"), publishers(store.records(A)));
  }

  // Publication records: listing and damage

  @Test
  void folderMarkersUnderAVersionAreSkipped() {
    store.addRecord(record(A, T1, "real"));
    s3.seed("tw/publications/" + A + "/", new byte[0]);
    s3.seed("tw/publications/" + A + "/nested/", new byte[0]);

    assertEquals(List.of("real"), publishers(store.records(A)));
    assertEquals(List.of("real"), publishers(store.allRecords()));
  }

  @Test
  void foreignObjectsAmongPublicationRecordsAreAnError() {
    store.addRecord(record(A, T1, "real"));
    s3.seed("tw/publications/" + A + "/notes.txt", bytes("not a record"));

    assertThrows(BundleStoreException.class, () -> store.records(A));
    assertThrows(BundleStoreException.class, () -> store.allRecords());
  }

  @Test
  void aRecordWhoseBundleVersionDisagreesWithItsKeyPrefixIsDamaged() {
    s3.seed("tw/publications/" + A + "/" + T1 + "-1.json",
        bytes("{\"bundleVersion\":\"" + B + "\",\"createdAt\":\"" + T1 + "\"}"));

    BundleStoreException error = assertThrows(BundleStoreException.class, () -> store.records(A));
    assertTrue(error.getMessage().contains("damaged"), error.getMessage());
    assertThrows(BundleStoreException.class, () -> store.allRecords());
  }

  @Test
  void damagedRecordObjectsAreStoreErrorsAndAreNeverSkipped() {
    assertDamaged("not json");
    assertDamaged("null");
    assertDamaged("[1, 2]");
    assertDamaged("{\"createdAt\":\"" + T1 + "\",\"createdAt\":\"" + T1 + "\"}");
    assertDamaged("{\"createdAt\":\"" + T1 + "\",\"extra\":true}");
    assertDamaged("{\"bundleVersion\":\"" + A + "\"}");
    assertDamaged("{\"createdAt\":\"" + T1 + "\",\"gitCommit\":\"XYZ\"}");
    assertDamaged("{\"createdAt\":\"2026-02-30T00:00:00Z\"}");
    assertDamaged("{\"createdAt\":\"" + T1 + "\",\"publisher\":5}");
  }

  private void assertDamaged(String body) {
    FakeS3Client fake = new FakeS3Client(BUCKET);
    S3BundleStore damaged = new S3BundleStore(fake, BUCKET, "tw");
    fake.seed("tw/publications/" + A + "/" + T1 + "-1.json", bytes(body));

    assertThrows(BundleStoreException.class, () -> damaged.records(A), body);
    assertThrows(BundleStoreException.class, () -> damaged.allRecords(), body);
  }

  @Test
  void recordsAreCollectedAcrossListingPages() {
    S3BundleStore paged = new S3BundleStore(new FakeS3Client(BUCKET, 2), BUCKET, "tw");
    for (int i = 1; i <= 5; i++) {
      paged.addRecord(record(A, T1, "p" + i));
    }

    assertEquals(List.of("p1", "p2", "p3", "p4", "p5"), publishers(paged.records(A)));
  }

  @Test
  void aTruncatedListingWithoutAContinuationTokenIsAnError() {
    FakeS3Client broken = new FakeS3Client(BUCKET) {
      @Override
      public ListObjectsV2Response listObjectsV2(ListObjectsV2Request request) {
        return ListObjectsV2Response.builder().isTruncated(true).build();
      }
    };
    S3BundleStore truncated = new S3BundleStore(broken, BUCKET, "tw");

    assertThrows(BundleStoreException.class, () -> truncated.records(A));
    assertThrows(BundleStoreException.class, () -> truncated.allRecords());
  }

  @Test
  void recordsOfAVersionRejectMalformedVersionsAsIllegalArguments() {
    assertThrows(IllegalArgumentException.class, () -> store.records("sha256:nope"));
  }

  @Test
  void allRecordsReturnsEveryVersionsRecordsFromOneListing() {
    store.addRecord(record(B, T1, "b"));
    store.addRecord(record(A, T1, "a1"));
    store.addRecord(record(A, T2, "a2"));
    store.putBundle(C, bytes("bundle without a record"));

    List<PublicationRecord> all = store.allRecords();

    assertEquals(3, all.size());
    assertEquals(Set.of(record(A, T1, "a1"), record(A, T2, "a2"), record(B, T1, "b")), new HashSet<>(all));
  }

  @Test
  void allRecordsOfAnEmptyStoreIsEmpty() {
    assertEquals(List.of(), store.allRecords());
  }

  @Test
  void allRecordsIsCollectedAcrossListingPages() {
    S3BundleStore paged = new S3BundleStore(new FakeS3Client(BUCKET, 1), BUCKET, "tw");
    paged.addRecord(record(C, T1, "c"));
    paged.addRecord(record(A, T1, "a"));
    paged.addRecord(record(B, T1, "b"));

    assertEquals(Set.of(record(A, T1, "a"), record(B, T1, "b"), record(C, T1, "c")),
        new HashSet<>(paged.allRecords()));
  }

  @Test
  void allRecordsRejectsForeignPrefixesAndLooseObjects() {
    s3.seed("tw/publications/notes/x.json", bytes("x"));
    assertThrows(BundleStoreException.class, () -> store.allRecords());

    FakeS3Client loose = new FakeS3Client(BUCKET);
    loose.seed("tw/publications/loose.json", bytes("x"));
    assertThrows(BundleStoreException.class, () -> new S3BundleStore(loose, BUCKET, "tw").allRecords());
  }

  // Latest pointer

  @Test
  void latestIsEmptyBeforeAnyPointerIsSet() {
    store.putBundle(A, BYTES_A);

    assertEquals(Optional.empty(), store.latest());
  }

  @Test
  void setLatestFirstCreatesThePointerWithAConditionalCreate() {
    store.putBundle(A, BYTES_A);

    store.setLatest(A);

    assertEquals(Optional.of(A), store.latest());
    assertEquals(A, new String(s3.bytesOf("tw/latest").orElseThrow(), UTF_8));
    PutObjectRequest pointerWrite = s3.putRequests().get(1);
    assertEquals("*", pointerWrite.ifNoneMatch());
    assertNull(pointerWrite.ifMatch());
  }

  @Test
  void setLatestToAnotherVersionIsConditionalOnTheEtagThatWasRead() {
    store.putBundle(A, BYTES_A);
    store.putBundle(B, bytes("bundle-b"));
    store.setLatest(A);
    String etagOfA = pointerEtag();

    store.setLatest(B);

    PutObjectRequest last = s3.putRequests().get(s3.putRequests().size() - 1);
    assertEquals("tw/latest", last.key());
    assertEquals(etagOfA, last.ifMatch());
    assertEquals(Optional.of(B), store.latest());
  }

  @Test
  void setLatestToTheCurrentVersionWritesNothing() {
    store.putBundle(A, BYTES_A);
    store.setLatest(A);
    int puts = s3.putRequests().size();

    store.setLatest(A);

    assertEquals(puts, s3.putRequests().size());
    assertEquals(Optional.of(A), store.latest());
  }

  @Test
  void setLatestOfAnUnstoredVersionIsAStoreErrorAndLeavesThePointerAlone() {
    store.putBundle(A, BYTES_A);
    store.setLatest(A);
    String etag = pointerEtag();
    int puts = s3.putRequests().size();

    BundleStoreException error = assertThrows(BundleStoreException.class, () -> store.setLatest(C));

    assertTrue(error.getMessage().contains(C));
    assertEquals(Optional.of(A), store.latest());
    assertEquals(etag, pointerEtag());
    assertEquals(puts, s3.putRequests().size(), "no pointer write was attempted");
  }

  @Test
  void setLatestRetriesWhenThePointerMovesBetweenTheReadAndTheWrite() {
    store.putBundle(A, BYTES_A);
    store.putBundle(B, bytes("bundle-b"));
    store.putBundle(C, bytes("bundle-c"));
    store.setLatest(A);
    s3.beforeNextPut(() -> store.setLatest(B));

    store.setLatest(C);

    assertEquals(Optional.of(C), store.latest());
    long pointerWrites = s3.putRequests().stream().filter(request -> request.key().equals("tw/latest")).count();
    assertEquals(4, pointerWrites, "set A, the racing set B, the rejected set C, then the retried set C");
  }

  @Test
  void setLatestRetriesAsAFirstPointerWhenThePointerIsDeletedBetweenTheReadAndTheWrite() {
    store.putBundle(A, BYTES_A);
    store.putBundle(B, bytes("bundle-b"));
    store.setLatest(A);
    s3.beforeNextPut(() -> s3.remove("tw/latest"));

    store.setLatest(B);

    assertEquals(Optional.of(B), store.latest());
    PutObjectRequest retry = s3.putRequests().get(s3.putRequests().size() - 1);
    assertEquals("*", retry.ifNoneMatch());
  }

  @Test
  void setLatestGivesUpWhenEveryAttemptIsRejected() {
    store.putBundle(A, BYTES_A);
    store.putBundle(B, bytes("bundle-b"));
    store.setLatest(A);
    s3.failNext("putObject", 412, S3BundleStore.MAX_ATTEMPTS);

    BundleStoreException error = assertThrows(BundleStoreException.class, () -> store.setLatest(B));

    assertTrue(error.getMessage().contains("kept changing"), error.getMessage());
    assertEquals(Optional.of(A), store.latest());
  }

  @Test
  void setLatestRefusesAPointerWithoutAnEtagInsteadOfWritingUnconditionally() {
    store.putBundle(A, BYTES_A);
    store.putBundle(B, bytes("bundle-b"));
    store.setLatest(A);
    int puts = s3.putRequests().size();
    s3.omitEtags();

    BundleStoreException error = assertThrows(BundleStoreException.class, () -> store.setLatest(B));

    assertTrue(error.getMessage().contains("ETag"));
    assertEquals(puts, s3.putRequests().size());
  }

  @Test
  void latestDoesNotNeedAnEtag() {
    store.putBundle(A, BYTES_A);
    store.setLatest(A);
    s3.omitEtags();

    assertEquals(Optional.of(A), store.latest());
  }

  @Test
  void setLatestRejectsAMalformedVersionBeforeAnyRequest() {
    assertThrows(IllegalArgumentException.class, () -> store.setLatest("sha256:nope"));
    assertThrows(IllegalArgumentException.class, () -> store.setLatest(null));
    assertTrue(s3.putRequests().isEmpty());
  }

  @Test
  void aPointerThatIsNotAVersionIsAnError() {
    s3.seed("tw/latest", bytes("hello"));

    assertThrows(BundleStoreException.class, () -> store.latest());
  }

  @Test
  void aPointerWithATrailingNewlineIsReadAsTheVersionForCrossStoreCompatibility() {
    s3.seed("tw/latest", bytes(A + "\n"));

    assertEquals(Optional.of(A), store.latest());
  }

  @Test
  void theLayoutWithoutAPrefixPutsEverythingAtTheTopOfTheBucket() {
    FakeS3Client bare = new FakeS3Client(BUCKET);
    S3BundleStore unprefixed = new S3BundleStore(bare, BUCKET, null);
    unprefixed.putBundle(A, BYTES_A);
    unprefixed.setLatest(A);
    unprefixed.addRecord(record(A, T1, "r"));

    assertEquals(List.of("bundles/" + A + ".json", "latest", "publications/" + A + "/" + T1 + "-1.json"), bare.keys());
  }
}
