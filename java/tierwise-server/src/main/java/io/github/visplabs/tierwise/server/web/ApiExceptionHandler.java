package io.github.visplabs.tierwise.server.web;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.TierwiseException;
import io.github.visplabs.tierwise.server.ApiException;
import io.github.visplabs.tierwise.server.ErrorCodes;
import io.github.visplabs.tierwise.server.store.BundleStoreException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every failure into the JSON error {@code {code, message}}.
 *
 * <ul>
 *   <li>Errors of the core keep their code. {@code E_UNKNOWN_KEY} and {@code E_UNKNOWN_FEATURE} are 404; the
 *       errors of a bad request ({@code E_CONTEXT_INVALID}, {@code E_UNKNOWN_SCOPE}, {@code E_UNKNOWN_NODE},
 *       {@code E_FACTS_INVALID}) are 400; any other core error is a fault of the server and is 500.
 *   <li>{@link ApiException} carries its own status and code.
 *   <li>Malformed HTTP requests get 400, 404, 405, 406, 413 or 415 with a code of {@link ErrorCodes}.
 *   <li>Anything else is 500 with {@code E_INTERNAL} and no detail; the detail goes to the log.
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  private static final Set<String> NOT_FOUND = Set.of(Code.E_UNKNOWN_KEY, Code.E_UNKNOWN_FEATURE);
  private static final Set<String> BAD_REQUEST = Set.of(Code.E_CONTEXT_INVALID, Code.E_UNKNOWN_SCOPE,
      Code.E_UNKNOWN_NODE, Code.E_FACTS_INVALID);

  /**
   * Maps an error of the core.
   *
   * @param e the error
   * @return the JSON error
   */
  @ExceptionHandler(TierwiseException.class)
  public ResponseEntity<ErrorBody> core(TierwiseException e) {
    String message = e.getMessage();
    String prefix = e.code() + ": ";
    String text = message.startsWith(prefix) ? message.substring(prefix.length()) : message;
    if (NOT_FOUND.contains(e.code())) {
      return json(HttpStatus.NOT_FOUND, e.code(), text);
    }
    if (BAD_REQUEST.contains(e.code())) {
      return json(HttpStatus.BAD_REQUEST, e.code(), text);
    }
    LOG.error("The core failed with {}", e.code(), e);
    return json(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL, "the server failed to evaluate the request");
  }

  /**
   * Maps an {@link ApiException}.
   *
   * @param e the exception
   * @return the JSON error
   */
  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorBody> api(ApiException e) {
    return ResponseEntity.status(e.status()).contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorBody(e.code(), e.getMessage()));
  }

  /**
   * Maps a failure of the bundle store.
   *
   * @param e the failure
   * @return 503 with {@code E_STORE_UNAVAILABLE}
   */
  @ExceptionHandler(BundleStoreException.class)
  public ResponseEntity<ErrorBody> store(BundleStoreException e) {
    LOG.error("The bundle store failed", e);
    return json(HttpStatus.SERVICE_UNAVAILABLE, ErrorCodes.STORE_UNAVAILABLE, "the bundle store is unavailable");
  }

  /**
   * Maps a multipart request that cannot be read.
   *
   * @param e the failure
   * @return 400 with {@code E_REQUEST_INVALID}
   */
  @ExceptionHandler(MultipartException.class)
  public ResponseEntity<ErrorBody> multipart(MultipartException e) {
    return json(HttpStatus.BAD_REQUEST, ErrorCodes.REQUEST_INVALID, "the multipart request cannot be read");
  }

  /**
   * Maps every other failure.
   *
   * @param e the failure
   * @return 500 with {@code E_INTERNAL}
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorBody> other(Exception e) {
    LOG.error("Unexpected failure", e);
    return json(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCodes.INTERNAL, "the server failed to handle the request");
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
      HttpStatusCode statusCode, WebRequest request) {
    int status = statusCode.value();
    HttpHeaders out = new HttpHeaders();
    out.putAll(headers);
    out.setContentType(MediaType.APPLICATION_JSON);
    return new ResponseEntity<>(new ErrorBody(codeFor(status), messageFor(status, ex)), out, statusCode);
  }

  private static String codeFor(int status) {
    return switch (status) {
      case 400 -> ErrorCodes.REQUEST_INVALID;
      case 404 -> ErrorCodes.NOT_FOUND;
      case 405 -> ErrorCodes.METHOD_NOT_ALLOWED;
      case 406 -> ErrorCodes.NOT_ACCEPTABLE;
      case 413 -> ErrorCodes.REQUEST_TOO_LARGE;
      case 415 -> ErrorCodes.UNSUPPORTED_MEDIA_TYPE;
      default -> status >= 500 ? ErrorCodes.INTERNAL : ErrorCodes.REQUEST_INVALID;
    };
  }

  private static String messageFor(int status, Exception ex) {
    return switch (status) {
      case 400 -> "the request is malformed: " + firstLine(ex);
      case 404 -> "no such endpoint";
      case 405 -> "the method is not allowed for this endpoint";
      case 406 -> "the endpoint cannot produce an accepted media type";
      case 413 -> "the request is too large";
      case 415 -> "the endpoint does not accept this content type";
      default -> status >= 500 ? "the server failed to handle the request" : "the request cannot be handled";
    };
  }

  private static String firstLine(Exception ex) {
    String message = ex.getMessage();
    if (message == null) {
      return ex.getClass().getSimpleName();
    }
    int end = message.indexOf('\n');
    String line = end < 0 ? message : message.substring(0, end);
    return line.length() > 200 ? line.substring(0, 200) + "..." : line;
  }

  private static ResponseEntity<ErrorBody> json(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(new ErrorBody(code, message));
  }
}
