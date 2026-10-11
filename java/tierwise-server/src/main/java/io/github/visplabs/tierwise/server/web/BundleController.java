package io.github.visplabs.tierwise.server.web;

import io.github.visplabs.tierwise.server.service.BundleService;
import io.github.visplabs.tierwise.server.service.PromoteOutcome;
import io.github.visplabs.tierwise.server.service.PublishOutcome;
import io.github.visplabs.tierwise.server.service.StoredBundle;
import io.github.visplabs.tierwise.server.store.PublicationRecord;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

/**
 * The bundle endpoints of requirements section 9: publish, read the latest or one version, list the
 * history, and promote.
 */
@RestController
@RequestMapping("/v1/bundles")
public class BundleController {

  private static final CacheControl REVALIDATE = CacheControl.noCache();
  private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePrivate()
      .immutable();

  private final BundleService bundles;

  /**
   * Creates the controller.
   *
   * @param bundles the bundle service
   */
  public BundleController(BundleService bundles) {
    this.bundles = bundles;
  }

  /**
   * {@code POST /v1/bundles}: publishes canonical bundle bytes and a publication record.
   *
   * @param bundle the part {@code bundle}: the exact bundle bytes
   * @param record the part {@code record}: the JSON {@code {gitCommit?, createdAt, publisher?}}
   * @return 201 when the version was stored, 200 when the same bytes were stored before
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> publish(@RequestPart("bundle") byte[] bundle,
      @RequestPart("record") byte[] record) {
    PublishOutcome outcome = bundles.publish(bundle, record);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("bundleVersion", outcome.bundleVersion());
    body.put("newVersion", outcome.newVersion());
    body.put("recordAdded", outcome.recordAdded());
    body.put("madeLatest", outcome.madeLatest());
    body.put("latest", outcome.latest());
    body.put("record", outcome.record().toJson());
    HttpStatus status = outcome.newVersion() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).location(URI.create("/v1/bundles/" + outcome.bundleVersion())).body(body);
  }

  /**
   * {@code GET /v1/bundles/latest}: the stored bytes of the latest bundle, unchanged. The ETag is the
   * {@code bundleVersion}, and {@code If-None-Match} with it gives 304.
   *
   * @param ifNoneMatch the {@code If-None-Match} header
   * @return the bundle, or 304
   */
  @GetMapping("/latest")
  public ResponseEntity<byte[]> latest(@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
    String version = bundles.requireLatestVersion();
    if (matches(ifNoneMatch, version)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(version).cacheControl(REVALIDATE).build();
    }
    return bundle(bundles.bundle(version), REVALIDATE);
  }

  /**
   * {@code GET /v1/bundles/{version}}: the stored bytes of one version, unchanged.
   *
   * @param version the {@code bundleVersion}
   * @param ifNoneMatch the {@code If-None-Match} header
   * @return the bundle, or 304
   */
  @GetMapping("/{version}")
  public ResponseEntity<byte[]> version(@PathVariable("version") String version,
      @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
    StoredBundle stored = bundles.bundle(version);
    if (matches(ifNoneMatch, version)) {
      return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(version).cacheControl(IMMUTABLE).build();
    }
    return bundle(stored, IMMUTABLE);
  }

  /**
   * {@code GET /v1/bundles}: the history from the publication records, newest first.
   *
   * @return {@code {latest?, bundles[]}}
   */
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> history() {
    List<Object> entries = new ArrayList<>();
    for (PublicationRecord record : bundles.history()) {
      entries.add(record.toJson());
    }
    Map<String, Object> body = new LinkedHashMap<>();
    bundles.latestVersion().ifPresent(version -> body.put("latest", version));
    body.put("bundles", entries);
    return body;
  }

  /**
   * {@code POST /v1/bundles/{version}/promote}: makes a stored version the latest.
   *
   * @param version the {@code bundleVersion}
   * @return {@code {latest, previous?}}
   */
  @PostMapping(path = "/{version}/promote", produces = MediaType.APPLICATION_JSON_VALUE)
  public Map<String, Object> promote(@PathVariable("version") String version) {
    PromoteOutcome outcome = bundles.promote(version);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("latest", outcome.latest());
    outcome.previous().ifPresent(previous -> body.put("previous", previous));
    return body;
  }

  private static ResponseEntity<byte[]> bundle(StoredBundle stored, CacheControl cache) {
    return ResponseEntity.ok().eTag(stored.version()).cacheControl(cache).contentType(MediaType.APPLICATION_JSON)
        .body(stored.bytes());
  }

  /** Tells whether an {@code If-None-Match} value names the version (RFC 9110 section 13.1.2, weak comparison). */
  static boolean matches(String ifNoneMatch, String version) {
    if (ifNoneMatch == null) {
      return false;
    }
    for (String candidate : ifNoneMatch.split(",")) {
      String tag = candidate.strip();
      if (tag.equals("*")) {
        return true;
      }
      if (tag.startsWith("W/")) {
        tag = tag.substring(2);
      }
      if (tag.length() >= 2 && tag.startsWith("\"") && tag.endsWith("\"")) {
        tag = tag.substring(1, tag.length() - 1);
      }
      if (tag.equals(version)) {
        return true;
      }
    }
    return false;
  }
}
