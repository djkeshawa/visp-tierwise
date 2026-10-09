package io.github.tierwise.server.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the hand-written {@code openapi.yaml} (OpenAPI 3.1) of the server at {@code GET /openapi.yaml}. */
@RestController
public class OpenApiController {

  private static final MediaType YAML = MediaType.parseMediaType("application/yaml");

  private final byte[] document;

  /** Reads {@code openapi.yaml} from the class path. */
  public OpenApiController() {
    try (InputStream stream = OpenApiController.class.getResourceAsStream("/openapi.yaml")) {
      if (stream == null) {
        throw new IllegalStateException("openapi.yaml is missing from the class path");
      }
      this.document = stream.readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * {@code GET /openapi.yaml}.
   *
   * @return the document, exactly as it is packaged
   */
  @GetMapping("/openapi.yaml")
  public ResponseEntity<byte[]> openApi() {
    return ResponseEntity.ok().contentType(YAML).body(document);
  }
}
