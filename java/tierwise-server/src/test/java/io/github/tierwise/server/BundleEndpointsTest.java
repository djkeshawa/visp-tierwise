package io.github.tierwise.server;

import static io.github.tierwise.server.Fixtures.PROMOTER;
import static io.github.tierwise.server.Fixtures.PUBLISHER;
import static io.github.tierwise.server.Fixtures.READER;
import static io.github.tierwise.server.Fixtures.bundle;
import static io.github.tierwise.server.Fixtures.publish;
import static io.github.tierwise.server.Fixtures.record;
import static io.github.tierwise.server.Fixtures.version;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** POST /v1/bundles, GET /v1/bundles/latest, GET /v1/bundles/{version}, GET /v1/bundles, promote. */
class BundleEndpointsTest extends ServerTest {

  private static final String CREATED = "2026-10-09T08:30:00Z";
  private final byte[] commerce = bundle("commerce.json");
  private final byte[] retail = bundle("retail-pos.json");
  private final String commerceVersion = version(commerce);
  private final String retailVersion = version(retail);

  @Test
  void publishStoresTheBundleAndMakesItTheLatest() throws Exception {
    mvc.perform(publish(commerce, record(CREATED, Fixtures.commit('a'), "ci")).with(PUBLISHER))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/v1/bundles/" + commerceVersion))
        .andExpect(jsonPath("$.bundleVersion").value(commerceVersion))
        .andExpect(jsonPath("$.newVersion").value(true))
        .andExpect(jsonPath("$.recordAdded").value(true))
        .andExpect(jsonPath("$.madeLatest").value(true))
        .andExpect(jsonPath("$.latest").value(commerceVersion))
        .andExpect(jsonPath("$.record.bundleVersion").value(commerceVersion))
        .andExpect(jsonPath("$.record.gitCommit").value(Fixtures.commit('a')))
        .andExpect(jsonPath("$.record.publisher").value("ci"));
  }

  @Test
  void theStoredBytesAreTheBytesThatWerePublished() throws Exception {
    publishOk(commerce, record(CREATED));
    Path file = Fixtures.STORE.resolve("bundles").resolve(commerceVersion.substring("sha256:".length()) + ".json");
    assertArrayEquals(commerce, Files.readAllBytes(file));
  }

  @Test
  void latestServesTheStoredBytesUnchangedWithTheVersionAsETag() throws Exception {
    publishOk(commerce, record(CREATED));
    MvcResult result = mvc.perform(get("/v1/bundles/latest").with(READER))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"" + commerceVersion + "\""))
        .andExpect(header().string("Cache-Control", "no-cache"))
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_JSON_VALUE))
        .andReturn();
    assertArrayEquals(commerce, result.getResponse().getContentAsByteArray());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"%s\"", "%s", "W/\"%s\"", "\"other\", \"%s\"", "*"})
  void latestAnswers304WhenIfNoneMatchNamesTheVersion(String template) throws Exception {
    publishOk(commerce, record(CREATED));
    String header = template.formatted(commerceVersion);
    MvcResult result = mvc.perform(get("/v1/bundles/latest").header("If-None-Match", header).with(READER))
        .andExpect(status().isNotModified())
        .andExpect(header().string("ETag", "\"" + commerceVersion + "\""))
        .andReturn();
    assertEquals(0, result.getResponse().getContentAsByteArray().length);
  }

  @Test
  void latestAnswers200WhenIfNoneMatchNamesAnotherVersion() throws Exception {
    publishOk(commerce, record(CREATED));
    mvc.perform(get("/v1/bundles/latest").header("If-None-Match", "\"" + retailVersion + "\"").with(READER))
        .andExpect(status().isOk());
  }

  @Test
  void latestChangesItsETagWhenANewVersionIsPublished() throws Exception {
    publishOk(commerce, record(CREATED));
    publishOk(retail, record("2026-10-09T09:00:00Z"));
    mvc.perform(get("/v1/bundles/latest").header("If-None-Match", "\"" + commerceVersion + "\"").with(READER))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"" + retailVersion + "\""));
  }

  @Test
  void headIsAllowedForReads() throws Exception {
    publishOk(commerce, record(CREATED));
    mvc.perform(MockMvcRequestBuilders.head("/v1/bundles/latest").with(READER))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"" + commerceVersion + "\""));
    mvc.perform(MockMvcRequestBuilders.head("/v1/bundles/latest").header("If-None-Match", "\"" + commerceVersion + "\"")
        .with(READER)).andExpect(status().isNotModified());
  }

  @Test
  void latestIs404WhenNothingWasPublished() throws Exception {
    mvc.perform(get("/v1/bundles/latest").with(READER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_BUNDLE_NOT_FOUND"))
        .andExpect(jsonPath("$.message").isString());
  }

  @Test
  void aVersionIsServedUnchangedAndMayBeCached() throws Exception {
    publishOk(commerce, record(CREATED));
    publishOk(retail, record("2026-10-09T09:00:00Z"));
    MvcResult result = mvc.perform(get("/v1/bundles/" + commerceVersion).with(READER))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"" + commerceVersion + "\""))
        .andReturn();
    assertArrayEquals(commerce, result.getResponse().getContentAsByteArray());
    assertTrue(result.getResponse().getHeader("Cache-Control").contains("immutable"));
    mvc.perform(get("/v1/bundles/" + commerceVersion).header("If-None-Match", "\"" + commerceVersion + "\"").with(READER))
        .andExpect(status().isNotModified());
  }

  @Test
  void anUnknownVersionIs404() throws Exception {
    mvc.perform(get("/v1/bundles/sha256:" + "0".repeat(64)).with(READER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_BUNDLE_NOT_FOUND"));
  }

  @ParameterizedTest
  @MethodSource("malformedVersions")
  void aMalformedVersionIs400(String version) throws Exception {
    mvc.perform(get("/v1/bundles/" + version).with(READER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_REQUEST_INVALID"));
  }

  static Stream<String> malformedVersions() {
    return Stream.of("sha256:abc", "md5:00", "sha256:" + "A".repeat(64), "x");
  }

  @Test
  void aBundleThatAlreadyExistsIsAcceptedAgainWithoutMovingTheLatestPointer() throws Exception {
    publishOk(commerce, record(CREATED));
    publishOk(retail, record("2026-10-09T09:00:00Z"));
    mvc.perform(publish(commerce, record("2026-10-09T10:00:00Z", Fixtures.commit('b'), "rerun")).with(PUBLISHER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.newVersion").value(false))
        .andExpect(jsonPath("$.recordAdded").value(true))
        .andExpect(jsonPath("$.madeLatest").value(false))
        .andExpect(jsonPath("$.latest").value(retailVersion));
    mvc.perform(get("/v1/bundles/latest").with(READER))
        .andExpect(header().string("ETag", "\"" + retailVersion + "\""));
  }

  @Test
  void anIdenticalRecordIsStoredOnce() throws Exception {
    publishOk(commerce, record(CREATED));
    mvc.perform(publish(commerce, record(CREATED)).with(PUBLISHER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.recordAdded").value(false));
    Map<String, Object> history = object(get("/v1/bundles"), READER, 200);
    assertEquals(1, ((List<?>) history.get("bundles")).size());
  }

  @Test
  void differentBytesForAnExistingVersionAreRejectedWith409AndTheStoredBytesStay() throws Exception {
    Path file = Fixtures.STORE.resolve("bundles").resolve(commerceVersion.substring("sha256:".length()) + ".json");
    byte[] other = "{\"another\":\"body\"}".getBytes(StandardCharsets.UTF_8);
    Files.write(file, other);
    mvc.perform(publish(commerce, record(CREATED)).with(PUBLISHER))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("E_BUNDLE_CONFLICT"));
    assertArrayEquals(other, Files.readAllBytes(file));
    assertTrue(Files.notExists(Fixtures.STORE.resolve("latest")));
    Map<String, Object> history = object(get("/v1/bundles"), READER, 200);
    assertEquals(List.of(), history.get("bundles"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"digest-mismatch.json", "extra-envelope-member.json", "duplicate-member.json",
      "non-integer-number.json", "integer-out-of-range.json", "missing-spec-version.json"})
  void aBundleThatTheCoreRejectsIsNotStored(String name) throws Exception {
    mvc.perform(publish(Fixtures.invalidBundle(name), record(CREATED)).with(PUBLISHER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("E_BUNDLE_")));
    mvc.perform(get("/v1/bundles/latest").with(READER)).andExpect(status().isNotFound());
  }

  @Test
  void anUnsupportedSpecVersionIsReportedAsUnsupported() throws Exception {
    mvc.perform(publish(Fixtures.invalidBundle("unsupported-spec-version.json"), record(CREATED)).with(PUBLISHER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_BUNDLE_UNSUPPORTED"));
  }

  @Test
  void aDamagedDigestIsReportedAsIntegrityError() throws Exception {
    mvc.perform(publish(Fixtures.invalidBundle("digest-mismatch.json"), record(CREATED)).with(PUBLISHER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_BUNDLE_INTEGRITY"));
  }

  @Test
  void aBundleWithATrailingNewlineIsNotCanonicalAndIsRejected() throws Exception {
    byte[] withNewline = new byte[commerce.length + 1];
    System.arraycopy(commerce, 0, withNewline, 0, commerce.length);
    withNewline[commerce.length] = '\n';
    mvc.perform(publish(withNewline, record(CREATED)).with(PUBLISHER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_BUNDLE_INTEGRITY"));
  }

  @Test
  void textThatIsNotJsonIsRejected() throws Exception {
    mvc.perform(publish("not json".getBytes(StandardCharsets.UTF_8), record(CREATED)).with(PUBLISHER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_BUNDLE_INTEGRITY"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "{}",
      "[]",
      "null",
      "\"text\"",
      "not json",
      "{\"createdAt\":\"2026-10-09T08:30:00.5Z\"}",
      "{\"createdAt\":\"2026-10-09 08:30:00\"}",
      "{\"createdAt\":\"2026-10-09T08:30:00+00:00\"}",
      "{\"createdAt\":\"2026-10-09t08:30:00z\"}",
      "{\"createdAt\":\"2026-02-30T08:30:00Z\"}",
      "{\"createdAt\":1}",
      "{\"createdAt\":\"2026-10-09T08:30:00Z\",\"gitCommit\":\"ABCDEFABCDEFABCDEFABCDEFABCDEFABCDEFABCD\"}",
      "{\"createdAt\":\"2026-10-09T08:30:00Z\",\"gitCommit\":\"abc123\"}",
      "{\"createdAt\":\"2026-10-09T08:30:00Z\",\"publisher\":7}",
      "{\"createdAt\":\"2026-10-09T08:30:00Z\",\"extra\":\"x\"}",
      "{\"createdAt\":\"2026-10-09T08:30:00Z\",\"bundleVersion\":\"sha256:0000000000000000000000000000000000000000000000000000000000000000\"}",
      "{\"createdAt\":\"2026-10-09T08:30:00Z\",\"createdAt\":\"2026-10-09T08:30:01Z\"}"})
  void anInvalidPublicationRecordIsRejectedAndNothingIsStored(String record) throws Exception {
    mvc.perform(publish(commerce, record).with(PUBLISHER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_PROVENANCE_INVALID"));
    mvc.perform(get("/v1/bundles/" + commerceVersion).with(READER)).andExpect(status().isNotFound());
    mvc.perform(get("/v1/bundles/latest").with(READER)).andExpect(status().isNotFound());
  }

  @Test
  void aRecordMayRepeatTheBundleVersionWhenItMatches() throws Exception {
    mvc.perform(publish(commerce, "{\"bundleVersion\":\"" + commerceVersion + "\",\"createdAt\":\"" + CREATED + "\"}")
        .with(PUBLISHER)).andExpect(status().isCreated());
  }

  @Test
  void aSixtyFirstSecondIsAValidRfc3339Time() throws Exception {
    mvc.perform(publish(commerce, record("2016-12-31T23:59:60Z")).with(PUBLISHER)).andExpect(status().isCreated());
  }

  @Test
  void aMissingPartIs400() throws Exception {
    mvc.perform(MockMvcRequestBuilders.multipart("/v1/bundles")
        .file(new MockMultipartFile("bundle", "b.json", "application/json", commerce)).with(PUBLISHER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_REQUEST_INVALID"));
    mvc.perform(MockMvcRequestBuilders.multipart("/v1/bundles")
        .file(new MockMultipartFile("record", "r.json", "application/json", record(CREATED).getBytes(StandardCharsets.UTF_8)))
        .with(PUBLISHER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_REQUEST_INVALID"));
  }

  @Test
  void aBodyThatIsNotMultipartIs415() throws Exception {
    mvc.perform(post("/v1/bundles").contentType(MediaType.APPLICATION_JSON).content(commerce).with(PUBLISHER))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("E_UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  void historyListsEveryPublicationRecordNewestFirst() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z", Fixtures.commit('a'), "ci-1"));
    publishOk(retail, record("2026-10-09T09:00:00Z", Fixtures.commit('b'), "ci-2"));
    publishOk(commerce, record("2026-10-09T10:00:00Z", Fixtures.commit('c'), "ci-3"));
    Map<String, Object> history = object(get("/v1/bundles"), READER, 200);
    assertEquals(retailVersion, history.get("latest"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> entries = (List<Map<String, Object>>) history.get("bundles");
    assertEquals(List.of("ci-3", "ci-2", "ci-1"), entries.stream().map(entry -> entry.get("publisher")).toList());
    assertEquals(List.of(commerceVersion, retailVersion, commerceVersion),
        entries.stream().map(entry -> entry.get("bundleVersion")).toList());
    assertEquals(Fixtures.commit('b'), entries.get(1).get("gitCommit"));
    assertEquals("2026-10-09T09:00:00Z", entries.get(1).get("createdAt"));
  }

  @Test
  void historyOmitsMembersThatTheRecordDoesNotHave() throws Exception {
    publishOk(commerce, record(CREATED));
    Map<String, Object> history = object(get("/v1/bundles"), READER, 200);
    @SuppressWarnings("unchecked")
    Map<String, Object> entry = ((List<Map<String, Object>>) history.get("bundles")).get(0);
    assertEquals(List.of("bundleVersion", "createdAt"), List.copyOf(entry.keySet()));
  }

  @Test
  void historyIsEmptyBeforeTheFirstPublication() throws Exception {
    Map<String, Object> history = object(get("/v1/bundles"), READER, 200);
    assertEquals(List.of(), history.get("bundles"));
    assertFalse(history.containsKey("latest"));
  }

  @Test
  void promoteRollsBackAndForward() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    publishOk(retail, record("2026-10-09T09:00:00Z"));

    mvc.perform(post("/v1/bundles/" + commerceVersion + "/promote").with(PROMOTER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.latest").value(commerceVersion))
        .andExpect(jsonPath("$.previous").value(retailVersion));
    MvcResult rolledBack = mvc.perform(get("/v1/bundles/latest").with(READER))
        .andExpect(header().string("ETag", "\"" + commerceVersion + "\"")).andReturn();
    assertArrayEquals(commerce, rolledBack.getResponse().getContentAsByteArray());

    mvc.perform(post("/v1/bundles/" + retailVersion + "/promote").with(PROMOTER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.latest").value(retailVersion))
        .andExpect(jsonPath("$.previous").value(commerceVersion));
    MvcResult forward = mvc.perform(get("/v1/bundles/latest").with(READER)).andReturn();
    assertArrayEquals(retail, forward.getResponse().getContentAsByteArray());
  }

  @Test
  void aClientThatHoldsTheRolledBackVersionGets304AfterPromote() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    publishOk(retail, record("2026-10-09T09:00:00Z"));
    mvc.perform(post("/v1/bundles/" + commerceVersion + "/promote").with(PROMOTER)).andExpect(status().isOk());
    mvc.perform(get("/v1/bundles/latest").header("If-None-Match", "\"" + commerceVersion + "\"").with(READER))
        .andExpect(status().isNotModified());
    mvc.perform(get("/v1/bundles/latest").header("If-None-Match", "\"" + retailVersion + "\"").with(READER))
        .andExpect(status().isOk());
  }

  @Test
  void promoteDoesNotChangeStoredBundlesOrRecords() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    publishOk(retail, record("2026-10-09T09:00:00Z"));
    mvc.perform(post("/v1/bundles/" + commerceVersion + "/promote").with(PROMOTER)).andExpect(status().isOk());
    Map<String, Object> history = object(get("/v1/bundles"), READER, 200);
    assertEquals(2, ((List<?>) history.get("bundles")).size());
    assertEquals(commerceVersion, history.get("latest"));
  }

  @Test
  void promoteOfAnUnknownVersionIs404() throws Exception {
    mvc.perform(post("/v1/bundles/sha256:" + "1".repeat(64) + "/promote").with(PROMOTER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_BUNDLE_NOT_FOUND"));
  }

  @Test
  void promoteOfAMalformedVersionIs400() throws Exception {
    mvc.perform(post("/v1/bundles/latest/promote").with(PROMOTER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_REQUEST_INVALID"));
  }

  @Test
  void aVersionPublishedAfterAPromoteBecomesTheLatest() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    publishOk(retail, record("2026-10-09T09:00:00Z"));
    mvc.perform(post("/v1/bundles/" + commerceVersion + "/promote").with(PROMOTER)).andExpect(status().isOk());
    byte[] third = bundle("saas-entitlements.json");
    publishOk(third, record("2026-10-09T11:00:00Z"));
    mvc.perform(get("/v1/bundles/latest").with(READER))
        .andExpect(header().string("ETag", "\"" + version(third) + "\""));
  }

  @Test
  void everythingThatIsNotAnEndpointIsDenied() throws Exception {
    mvc.perform(get("/v1/nothing").with(READER)).andExpect(status().isForbidden());
    mvc.perform(MockMvcRequestBuilders.put("/v1/bundles").with(PUBLISHER)).andExpect(status().isForbidden());
  }
}
