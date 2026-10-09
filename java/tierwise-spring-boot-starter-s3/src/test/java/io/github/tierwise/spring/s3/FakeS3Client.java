package io.github.tierwise.spring.s3;

import java.io.ByteArrayInputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * An in-memory {@link S3Client} for unit tests. It answers {@code GetObject} from a queue of replies, records
 * every request, and passes the body through the caller's {@link ResponseTransformer}, as the SDK does. It
 * opens no connection.
 */
final class FakeS3Client implements S3Client {

  /** One scripted answer: a body with an ETag, or an error. */
  record Reply(String etag, byte[] body, RuntimeException error) {
  }

  private final Deque<Reply> replies = new ArrayDeque<>();
  private final List<GetObjectRequest> requests = new ArrayList<>();
  private int inFlight;
  private int maxInFlight;
  private long delayMillis;

  /**
   * Queues a successful answer.
   *
   * @param etag the ETag, or {@code null} for a response without one
   * @param body the object bytes
   * @return this client
   */
  FakeS3Client reply(String etag, byte[] body) {
    replies.add(new Reply(etag, body, null));
    return this;
  }

  /**
   * Queues the answer of S3 when the ETag matches: {@code 304 Not Modified}.
   *
   * @return this client
   */
  FakeS3Client notModified() {
    replies.add(new Reply(null, null, S3Exception.builder().statusCode(304).message("Not Modified").build()));
    return this;
  }

  /**
   * Queues an error.
   *
   * @param error the exception to throw
   * @return this client
   */
  FakeS3Client fail(RuntimeException error) {
    replies.add(new Reply(null, null, error));
    return this;
  }

  /**
   * Makes each call sleep, so that concurrent callers overlap in the fake.
   *
   * @param millis the sleep in milliseconds
   * @return this client
   */
  FakeS3Client delay(long millis) {
    this.delayMillis = millis;
    return this;
  }

  /**
   * Returns the requests received so far, in order.
   *
   * @return an unmodifiable copy
   */
  synchronized List<GetObjectRequest> requests() {
    return List.copyOf(requests);
  }

  /**
   * Returns the largest number of calls that were in progress at the same time.
   *
   * @return the maximum concurrency seen
   */
  synchronized int maxInFlight() {
    return maxInFlight;
  }

  @Override
  public String serviceName() {
    return "S3";
  }

  @Override
  public void close() {
    // Nothing to release.
  }

  @Override
  public <ReturnT> ReturnT getObject(
      GetObjectRequest getObjectRequest, ResponseTransformer<GetObjectResponse, ReturnT> responseTransformer) {
    Reply reply = begin(getObjectRequest);
    try {
      if (delayMillis > 0) {
        Thread.sleep(delayMillis);
      }
      if (reply.error() != null) {
        throw reply.error();
      }
      GetObjectResponse response = GetObjectResponse.builder().eTag(reply.etag()).build();
      return responseTransformer.transform(
          response, AbortableInputStream.create(new ByteArrayInputStream(reply.body())));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    } finally {
      end();
    }
  }

  private synchronized Reply begin(GetObjectRequest request) {
    requests.add(request);
    inFlight++;
    maxInFlight = Math.max(maxInFlight, inFlight);
    Reply reply = replies.poll();
    if (reply == null) {
      inFlight--;
      throw new IllegalStateException("The fake S3 client has no reply left for " + request);
    }
    return reply;
  }

  private synchronized void end() {
    inFlight--;
  }
}
