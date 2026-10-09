package io.github.tierwise.server;

import static io.github.tierwise.server.Fixtures.PUBLISHER;
import static io.github.tierwise.server.Fixtures.READER;
import static io.github.tierwise.server.Fixtures.bundle;
import static io.github.tierwise.server.Fixtures.publish;
import static io.github.tierwise.server.Fixtures.record;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Many CI jobs may publish the same version at once: it is stored once and every call succeeds. */
class ConcurrentPublishTest extends ServerTest {

  @Test
  void parallelPublishesOfOneVersionStoreItOnce() throws Exception {
    byte[] commerce = bundle("commerce.json");
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<Integer>> calls = new ArrayList<>();
      for (int i = 0; i < 16; i++) {
        String createdAt = "2026-10-09T08:00:%02dZ".formatted(i);
        calls.add(() -> mvc.perform(publish(commerce, record(createdAt)).with(PUBLISHER)).andReturn().getResponse().getStatus());
      }
      int created = 0;
      int ok = 0;
      for (Future<Integer> result : pool.invokeAll(calls)) {
        int status = result.get();
        if (status == 201) {
          created++;
        } else if (status == 200) {
          ok++;
        }
      }
      assertEquals(1, created);
      assertEquals(15, ok);
    } finally {
      pool.shutdownNow();
    }
    assertArrayEquals(commerce, mvc.perform(get("/v1/bundles/latest").with(READER)).andReturn().getResponse()
        .getContentAsByteArray());
    var history = object(get("/v1/bundles"), READER, 200);
    assertEquals(16, ((List<?>) history.get("bundles")).size());
  }
}
