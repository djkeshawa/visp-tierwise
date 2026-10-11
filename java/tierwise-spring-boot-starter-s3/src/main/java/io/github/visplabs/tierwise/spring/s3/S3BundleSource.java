package io.github.visplabs.tierwise.spring.s3;

import io.github.visplabs.tierwise.core.LoadedBundle;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.core.TierwiseException;
import io.github.visplabs.tierwise.spring.BundleFetch;
import io.github.visplabs.tierwise.spring.BundleSource;
import java.io.IOException;
import java.util.Objects;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Reads one bundle object from S3 for the starter, through the {@link BundleSource} contract.
 *
 * <p>The caller names the {@code bundleVersion} it holds. The source sends a conditional {@code GetObject}
 * only when that version is the one it last loaded, and it sends the S3 ETag of that load as
 * {@code If-None-Match}. S3 answers {@code 304 Not Modified} for an unchanged object, and the source then
 * returns {@link BundleFetch#notModified()} with no body. In every other case the request is unconditional.
 *
 * <p>The S3 ETag is an opaque token. It is never a version or a digest. The source learns the version of
 * the bytes it reads by loading them with {@link Tierwise#loadBundle(byte[])}, which verifies the digest
 * (BUNDLE-5). A read whose bytes the core rejects throws an {@link IOException}, and the source keeps the
 * previous version and ETag. The next call is therefore unconditional, or conditional on the version the
 * caller really holds.
 *
 * <p>Calls are {@code synchronized}, so one source makes one request at a time. The S3 client is
 * thread-safe and the source may be shared between threads.
 *
 * <p>The source does not schedule polls and does not keep a last-known-good bundle. The starter's refresher
 * owns both: it keeps its current bundle when {@link #fetch(String)} throws.
 */
public final class S3BundleSource implements BundleSource {

  /**
   * The largest object that is read, in bytes. It equals the limit of the starter's HTTP source, so both
   * sources refuse the same inputs.
   */
  static final int MAX_BODY_BYTES = 64 * 1024 * 1024;

  private static final int NOT_MODIFIED = 304;

  private final S3Client client;
  private final S3Location location;
  private String loadedVersion;
  private String loadedEtag;

  /**
   * Creates a source for one object.
   *
   * @param client the S3 client; the caller owns its lifecycle
   * @param location the bucket and key
   */
  public S3BundleSource(S3Client client, S3Location location) {
    this.client = Objects.requireNonNull(client, "client");
    this.location = Objects.requireNonNull(location, "location");
  }

  /**
   * Returns the object that this source reads.
   *
   * @return the location
   */
  public S3Location location() {
    return location;
  }

  /**
   * Returns {@code s3://bucket/key}, which holds no credential.
   *
   * @return the description
   */
  @Override
  public String describe() {
    return location.toString();
  }

  /**
   * Reads the object unless the caller already holds the bundle that the last load produced.
   *
   * @param currentVersion the {@code bundleVersion} the caller holds, or {@code null}
   * @return the bytes of a bundle that the core has verified, or "not modified"
   * @throws IOException when S3 cannot give the object (a missing key, an access or server error, a network
   *     failure, or an object larger than {@link #MAX_BODY_BYTES}), or when the bytes are not an accepted
   *     bundle; the cause of the last two is a {@link TierwiseException} with its code; the previous version
   *     and ETag are kept
   */
  @Override
  public synchronized BundleFetch fetch(String currentVersion) throws IOException {
    boolean conditional = currentVersion != null && currentVersion.equals(loadedVersion) && loadedEtag != null;
    GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(location.bucket()).key(location.key());
    if (conditional) {
      request.ifNoneMatch(loadedEtag);
    }
    byte[] bytes;
    String etag;
    try (ResponseInputStream<GetObjectResponse> body =
             client.getObject(request.build(), ResponseTransformer.toInputStream())) {
      bytes = body.readNBytes(MAX_BODY_BYTES + 1);
      etag = body.response().eTag();
    } catch (S3Exception e) {
      if (e.statusCode() == NOT_MODIFIED && conditional) {
        return BundleFetch.notModified();
      }
      throw new IOException("Cannot read the bundle at " + location, e);
    } catch (SdkException e) {
      throw new IOException("Cannot read the bundle at " + location, e);
    }
    if (bytes.length > MAX_BODY_BYTES) {
      throw new IOException("The object at " + location + " is larger than " + MAX_BODY_BYTES + " bytes");
    }
    LoadedBundle bundle;
    try {
      bundle = Tierwise.loadBundle(bytes);
    } catch (TierwiseException e) {
      throw new IOException("The object at " + location + " is not an accepted bundle: " + e.code(), e);
    }
    loadedVersion = bundle.bundleVersion();
    loadedEtag = etag;
    return BundleFetch.of(bytes, unquote(etag));
  }

  private static String unquote(String etag) {
    if (etag != null && etag.length() >= 2 && etag.startsWith("\"") && etag.endsWith("\"")) {
      return etag.substring(1, etag.length() - 1);
    }
    return etag;
  }
}
