package io.github.tierwise.e2e;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** HTTP calls to the server under test, with test tokens. The decoder of the test accepts {@code r.<role>}. */
final class ServerClient {

  static final String PUBLISHER = "Bearer r.tierwise-publisher";
  static final String PROMOTER = "Bearer r.tierwise-promoter";

  private static final String BOUNDARY = "----tierwise-e2e-boundary";

  private final String baseUrl;
  private final HttpClient http = HttpClient.newHttpClient();

  ServerClient(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  String baseUrl() {
    return baseUrl;
  }

  /** {@code POST /v1/bundles}: the bundle bytes and a publication record. A {@code null} token sends none. */
  HttpResponse<byte[]> publish(byte[] bundle, String token) {
    String record = "{\"createdAt\":\"" + Instant.now().truncatedTo(ChronoUnit.SECONDS) + "\",\"publisher\":\"e2e\"}";
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    part(body, "bundle", "bundle.json", "application/json", bundle);
    part(body, "record", null, null, record.getBytes(StandardCharsets.UTF_8));
    body.writeBytes(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/bundles"))
        .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
        .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
    return send(request, token);
  }

  /** {@code POST /v1/bundles/{version}/promote}. */
  HttpResponse<byte[]> promote(String version, String token) {
    return send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/bundles/" + version + "/promote"))
        .POST(HttpRequest.BodyPublishers.noBody()), token);
  }

  /** {@code GET /v1/bundles/latest}; reads are open in this test. */
  HttpResponse<byte[]> latest() {
    return send(HttpRequest.newBuilder(URI.create(baseUrl + "/v1/bundles/latest")).GET(), null);
  }

  /** The {@code bundleVersion} that the server serves as latest, from the ETag. */
  String latestVersion() {
    String etag = latest().headers().firstValue("ETag").orElseThrow(() -> new AssertionError("no ETag"));
    return etag.replaceAll("^\"|\"$", "");
  }

  /** Runs the cases through {@code /v1/resolve} and {@code /v1/decide}, in the shape the other clients use. */
  Map<String, JsonNode> evaluate(List<JsonNode> cases) {
    Map<String, JsonNode> outcomes = new LinkedHashMap<>();
    for (JsonNode c : cases) {
      ObjectNode request = Json.MAPPER.createObjectNode();
      request.set("context", c.get("context"));
      String path;
      if (c.get("call").asString().equals("resolve")) {
        path = "/v1/resolve";
        request.putArray("keys").add(c.get("key").asString());
      } else {
        path = "/v1/decide";
        request.put("feature", c.get("feature").asString());
        if (c.has("input")) {
          request.set("input", c.get("input"));
        }
      }
      HttpResponse<byte[]> response = send(HttpRequest.newBuilder(URI.create(baseUrl + path))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(Json.MAPPER.writeValueAsString(request))), null);
      JsonNode body = Json.parse(new String(response.body(), StandardCharsets.UTF_8));
      ObjectNode outcome = Json.MAPPER.createObjectNode();
      outcome.put("id", c.get("id").asString());
      if (response.statusCode() == 200) {
        outcome.set("result", path.equals("/v1/resolve") ? body.get("results").get(0) : body);
      } else {
        outcome.set("error", Json.MAPPER.createObjectNode().put("code", body.path("code").asString()));
      }
      outcomes.put(c.get("id").asString(), outcome);
    }
    return outcomes;
  }

  private HttpResponse<byte[]> send(HttpRequest.Builder request, String token) {
    if (token != null) {
      request.header("Authorization", token);
    }
    try {
      return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("interrupted", e);
    }
  }

  private static void part(ByteArrayOutputStream out, String name, String filename, String contentType, byte[] content) {
    String disposition = "form-data; name=\"" + name + "\"" + (filename == null ? "" : "; filename=\"" + filename + "\"");
    String type = contentType == null ? "" : "Content-Type: " + contentType + "\r\n";
    out.writeBytes(("--" + BOUNDARY + "\r\nContent-Disposition: " + disposition + "\r\n" + type + "\r\n")
        .getBytes(StandardCharsets.UTF_8));
    out.writeBytes(content);
    out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
  }
}
