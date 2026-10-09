package io.github.tierwise.server.s3;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.server.store.BundleConflictException;
import io.github.tierwise.server.store.BundleStoreException;
import io.github.tierwise.server.store.PublicationRecord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Races the store against itself on the fake S3 client. The fake is linearizable, so each test checks the outcome
 * that S3's conditional writes guarantee, whatever the interleaving.
 */
class S3BundleStoreConcurrencyTest {

  static final String BUCKET = "tierwise-test";
  static final int THREADS = 8;
  static final String T1 = "2026-03-04T05:06:07Z";

  FakeS3Client s3;
  S3BundleStore store;
  ExecutorService pool;

  @BeforeEach
  void setUp() {
    s3 = new FakeS3Client(BUCKET);
    store = new S3BundleStore(s3, BUCKET, "tw");
    pool = Executors.newFixedThreadPool(THREADS);
  }

  @AfterEach
  void tearDown() {
    pool.shutdownNow();
  }

  static String version(int i) {
    return "sha256:" + String.format("%064x", i);
  }

  /** Runs every task at the same moment and returns each outcome, a value or the thrown exception. */
  List<Object> race(List<Callable<Object>> tasks) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Object>> futures = new ArrayList<>();
    for (Callable<Object> task : tasks) {
      futures.add(pool.submit(() -> {
        start.await();
        try {
          return task.call();
        } catch (RuntimeException e) {
          return e;
        }
      }));
    }
    start.countDown();
    List<Object> outcomes = new ArrayList<>();
    for (Future<Object> future : futures) {
      outcomes.add(future.get());
    }
    return outcomes;
  }

  @Test
  void identicalConcurrentBundlePutsStoreOnceAndTheRestReturnFalse() throws Exception {
    byte[] bytes = "same".getBytes(UTF_8);
    List<Callable<Object>> tasks = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      tasks.add(() -> store.putBundle(version(1), bytes));
    }

    List<Object> outcomes = race(tasks);

    long stored = outcomes.stream().filter(o -> Boolean.TRUE.equals(o)).count();
    long unchanged = outcomes.stream().filter(o -> Boolean.FALSE.equals(o)).count();
    assertEquals(1, stored);
    assertEquals(THREADS - 1, unchanged);
    assertEquals(Optional.of("same"), store.getBundle(version(1)).map(b -> new String(b, UTF_8)));
  }

  @Test
  void differentConcurrentBundlePutsLeaveOneWinnerAndTheRestConflict() throws Exception {
    List<Callable<Object>> tasks = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      byte[] bytes = ("body-" + i).getBytes(UTF_8);
      tasks.add(() -> store.putBundle(version(2), bytes));
    }

    List<Object> outcomes = race(tasks);

    long stored = outcomes.stream().filter(o -> Boolean.TRUE.equals(o)).count();
    long conflicts = outcomes.stream().filter(o -> o instanceof BundleConflictException).count();
    assertEquals(1, stored);
    assertEquals(THREADS - 1, conflicts);
    String kept = new String(store.getBundle(version(2)).orElseThrow(), UTF_8);
    int winner = outcomes.indexOf(Boolean.TRUE);
    assertEquals("body-" + winner, kept);
  }

  @Test
  void concurrentSetLatestCallsEachSucceedOrFailCleanlyAndEndOnARequestedVersion() throws Exception {
    List<Callable<Object>> tasks = new ArrayList<>();
    Set<String> requested = new HashSet<>();
    for (int i = 0; i < THREADS; i++) {
      String target = version(10 + i);
      requested.add(target);
      store.putBundle(target, ("bundle-" + i).getBytes(UTF_8));
      tasks.add(() -> {
        store.setLatest(target);
        return target;
      });
    }

    List<Object> outcomes = race(tasks);

    Set<String> succeeded = new HashSet<>();
    for (Object outcome : outcomes) {
      if (outcome instanceof String target) {
        succeeded.add(target);
      } else {
        assertTrue(outcome instanceof BundleStoreException, String.valueOf(outcome));
      }
    }
    assertFalse(succeeded.isEmpty(), "at least one call must complete");
    Optional<String> latest = store.latest();
    assertTrue(latest.isPresent() && succeeded.contains(latest.get()),
        "the latest pointer must name a call that completed: " + latest);
    assertTrue(requested.contains(latest.get()));
  }

  @Test
  void concurrentRecordsTakeDistinctSlotsAndNoRecordIsLost() throws Exception {
    List<Callable<Object>> tasks = new ArrayList<>();
    for (int i = 0; i < THREADS; i++) {
      PublicationRecord record = new PublicationRecord(version(3), T1, Optional.empty(), Optional.of("p" + i));
      tasks.add(() -> {
        store.addRecord(record);
        return null;
      });
    }

    List<Object> outcomes = race(tasks);

    assertTrue(outcomes.stream().allMatch(o -> o == null), outcomes.toString());
    List<PublicationRecord> records = store.records(version(3));
    assertEquals(THREADS, records.size());
    assertEquals(THREADS, new HashSet<>(records).size());
    assertEquals(THREADS, s3.keys().stream().filter(k -> k.startsWith("tw/publications/")).count());
  }
}
