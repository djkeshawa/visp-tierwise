package io.github.visplabs.tierwise.server;

import static io.github.visplabs.tierwise.server.Fixtures.bundle;
import static io.github.visplabs.tierwise.server.Fixtures.record;
import static io.github.visplabs.tierwise.server.Fixtures.version;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The server on a real Tomcat with real multipart parsing and real bearer tokens (decoded by a test decoder),
 * for what the mock servlet environment does not exercise: multipart limits, HEAD, and the wire format.
 */
@SpringBootTest(classes = TierwiseServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "tierwise.server.max-bundle-size=100KB")
@ActiveProfiles("test")
@Import(RealHttpTest.Decoder.class)
class RealHttpTest {

  /** The token {@code r.a.b} is a JWT with the roles a and b. */
  @TestConfiguration
  static class Decoder {
    @Bean
    JwtDecoder testDecoder() {
      return token -> {
        if (!token.equals("r") && !token.startsWith("r.")) {
          throw new BadJwtException("unknown token");
        }
        List<String> roles = token.length() == 1 ? List.of() : List.of(token.substring(2).split("\\."));
        return Jwt.withTokenValue(token).header("alg", "none").subject("tester")
            .issuedAt(Instant.parse("2026-01-01T00:00:00Z")).expiresAt(Instant.parse("2099-01-01T00:00:00Z"))
            .claim("roles", roles).build();
      };
    }
  }

  private static final String BOUNDARY = "----tierwise-test-boundary";
  private static final String PUBLISHER = "Bearer r.tierwise-publisher";
  private static final String PROMOTER = "Bearer r.tierwise-promoter";
  private static final String READER = "Bearer r";

  @DynamicPropertySource
  static void store(DynamicPropertyRegistry registry) {
    registry.add("tierwise.server.filesystem.root", () -> Fixtures.STORE.toString());
  }

  @Value("${local.server.port}")
  private int port;

  private final HttpClient http = HttpClient.newHttpClient();
  private final JsonMapper json = JsonMapper.builder().build();
  private final byte[] commerce = bundle("commerce.json");

  @BeforeEach
  void emptyStore() {
    Fixtures.resetStore();
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  private HttpResponse<byte[]> send(HttpRequest.Builder request) throws Exception {
    return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
  }

  private static HttpRequest.BodyPublisher multipart(byte[] bundle, String record) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    part(out, "bundle", "bundle.json", "application/json", bundle);
    part(out, "record", null, null, record.getBytes(StandardCharsets.UTF_8));
    out.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return HttpRequest.BodyPublishers.ofByteArray(out.toByteArray());
  }

  private static void part(ByteArrayOutputStream out, String name, String filename, String contentType, byte[] content) {
    String disposition = "form-data; name=\"" + name + "\"" + (filename == null ? "" : "; filename=\"" + filename + "\"");
    String type = contentType == null ? "" : "Content-Type: " + contentType + "\r\n";
    out.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: " + disposition + "\r\n" + type + "\r\n")
        .getBytes(StandardCharsets.UTF_8));
    out.writeBytes(content);
    out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
  }

  private HttpRequest.Builder publish(byte[] bundle, String record, String token) {
    return HttpRequest.newBuilder(uri("/v1/bundles"))
        .header("Authorization", token)
        .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
        .POST(multipart(bundle, record));
  }

  private Map<String, Object> parse(HttpResponse<byte[]> response) {
    return json.readValue(response.body(), new TypeReference<Map<String, Object>>() { });
  }

  @Test
  void aBundleIsPublishedAndServedOverRealHttp() throws Exception {
    String version = version(commerce);
    HttpResponse<byte[]> published = send(publish(commerce, record("2026-10-09T08:00:00Z"), PUBLISHER));
    assertEquals(201, published.statusCode());
    assertEquals(version, parse(published).get("bundleVersion"));

    HttpResponse<byte[]> latest = send(HttpRequest.newBuilder(uri("/v1/bundles/latest")).header("Authorization", READER));
    assertEquals(200, latest.statusCode());
    assertArrayEquals(commerce, latest.body());
    assertEquals("\"" + version + "\"", latest.headers().firstValue("ETag").orElseThrow());
    assertEquals("application/json", latest.headers().firstValue("Content-Type").orElseThrow());

    HttpResponse<byte[]> notModified = send(HttpRequest.newBuilder(uri("/v1/bundles/latest"))
        .header("Authorization", READER).header("If-None-Match", latest.headers().firstValue("ETag").orElseThrow()));
    assertEquals(304, notModified.statusCode());
    assertEquals(0, notModified.body().length);
  }

  @Test
  void headHasNoBody() throws Exception {
    send(publish(commerce, record("2026-10-09T08:00:00Z"), PUBLISHER));
    HttpResponse<byte[]> head = send(HttpRequest.newBuilder(uri("/v1/bundles/latest")).header("Authorization", READER)
        .method("HEAD", HttpRequest.BodyPublishers.noBody()));
    assertEquals(200, head.statusCode());
    assertEquals(0, head.body().length);
    assertEquals("\"" + version(commerce) + "\"", head.headers().firstValue("ETag").orElseThrow());
  }

  @Test
  void aPlainFormFieldWithoutAContentTypeWorksAsTheRecordPart() throws Exception {
    // curl -F 'record={...}' sends a field with no file name and no content type; -F bundle=@file sends a file.
    HttpResponse<byte[]> response = send(publish(commerce, record("2026-10-09T08:00:00Z"), PUBLISHER));
    assertEquals(201, response.statusCode());
  }

  @Test
  void resolveAndDecideWorkOverHttp() throws Exception {
    send(publish(commerce, record("2026-10-09T08:00:00Z"), PUBLISHER));
    HttpResponse<byte[]> resolved = send(HttpRequest.newBuilder(uri("/v1/resolve")).header("Authorization", READER)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"context\":{\"levels\":{\"division\":\"south\"}},"
            + "\"keys\":[\"checkout.pickup.lead_hours\"]}")));
    assertEquals(200, resolved.statusCode());
    assertEquals(6, ((Map<?, ?>) ((List<?>) parse(resolved).get("results")).get(0)).get("value"));

    HttpResponse<byte[]> missing = send(HttpRequest.newBuilder(uri("/v1/decide")).header("Authorization", READER)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"feature\":\"nope\"}")));
    assertEquals(404, missing.statusCode());
    assertEquals("E_UNKNOWN_FEATURE", parse(missing).get("code"));
  }

  @Test
  void tokensAreChecked() throws Exception {
    assertEquals(401, send(HttpRequest.newBuilder(uri("/v1/bundles/latest"))).statusCode());
    HttpResponse<byte[]> bad = send(HttpRequest.newBuilder(uri("/v1/bundles/latest")).header("Authorization", "Bearer nonsense"));
    assertEquals(401, bad.statusCode());
    assertTrue(bad.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Bearer"));
    assertEquals("E_UNAUTHENTICATED", parse(bad).get("code"));
    HttpResponse<byte[]> forbidden = send(publish(commerce, record("2026-10-09T08:00:00Z"), READER));
    assertEquals(403, forbidden.statusCode());
    assertEquals("E_FORBIDDEN", parse(forbidden).get("code"));
    assertEquals(403, send(HttpRequest.newBuilder(uri("/v1/bundles/" + version(commerce) + "/promote"))
        .header("Authorization", PUBLISHER).POST(HttpRequest.BodyPublishers.noBody())).statusCode());
    send(publish(commerce, record("2026-10-09T08:00:00Z"), PUBLISHER));
    assertEquals(200, send(HttpRequest.newBuilder(uri("/v1/bundles/" + version(commerce) + "/promote"))
        .header("Authorization", PROMOTER).POST(HttpRequest.BodyPublishers.noBody())).statusCode());
  }

  @Test
  void aRequestOverTheMultipartLimitIs413WithAJsonError() throws Exception {
    byte[] big = new byte[400 * 1024];
    java.util.Arrays.fill(big, (byte) ' ');
    HttpResponse<byte[]> response = send(publish(big, record("2026-10-09T08:00:00Z"), PUBLISHER));
    assertEquals(413, response.statusCode());
    assertEquals("E_REQUEST_TOO_LARGE", parse(response).get("code"));
    assertEquals(404, send(HttpRequest.newBuilder(uri("/v1/bundles/latest")).header("Authorization", READER)).statusCode());
  }

  @Test
  void aBundleOverTheLimitButInsideTheMultipartAllowanceIs413() throws Exception {
    byte[] big = new byte[110 * 1024];
    java.util.Arrays.fill(big, (byte) ' ');
    HttpResponse<byte[]> response = send(publish(big, record("2026-10-09T08:00:00Z"), PUBLISHER));
    assertEquals(413, response.statusCode());
    assertEquals("E_REQUEST_TOO_LARGE", parse(response).get("code"));
  }

  @Test
  void theOpenApiDocumentIsServedWithoutAToken() throws Exception {
    HttpResponse<byte[]> response = send(HttpRequest.newBuilder(uri("/openapi.yaml")));
    assertEquals(200, response.statusCode());
    assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/yaml"));
    assertTrue(new String(response.body(), StandardCharsets.UTF_8).startsWith("openapi: 3.1.0"));
  }
}
