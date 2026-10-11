package io.github.visplabs.tierwise.server;

/**
 * The {@code code} values that the server puts in its JSON errors ({@code {code, message}}).
 *
 * <p>Errors that come from {@code tierwise-core} keep the core's stable code ({@code E_UNKNOWN_KEY},
 * {@code E_CONTEXT_INVALID}, {@code E_BUNDLE_INTEGRITY}, ...), taken from {@code docs/requirements.md} section
 * 6. The codes below are the ones that section does not define, because they concern HTTP and storage only.
 * They are marked (OPEN) in the README until a reviewer accepts them in an ADR.
 */
public final class ErrorCodes {

  /** The request is malformed: a missing part, a body that is not JSON, an unknown member (400). */
  public static final String REQUEST_INVALID = "E_REQUEST_INVALID";
  /** The request is larger than the server accepts (413). */
  public static final String REQUEST_TOO_LARGE = "E_REQUEST_TOO_LARGE";
  /** The request body has a media type the endpoint does not read (415). */
  public static final String UNSUPPORTED_MEDIA_TYPE = "E_UNSUPPORTED_MEDIA_TYPE";
  /** The endpoint cannot produce a media type the request accepts (406). */
  public static final String NOT_ACCEPTABLE = "E_NOT_ACCEPTABLE";
  /** The path is not an endpoint of the server (404). */
  public static final String NOT_FOUND = "E_NOT_FOUND";
  /** The path is an endpoint, but not for this HTTP method (405). */
  public static final String METHOD_NOT_ALLOWED = "E_METHOD_NOT_ALLOWED";
  /** The requested bundle version is not stored, or no bundle has been published yet (404). */
  public static final String BUNDLE_NOT_FOUND = "E_BUNDLE_NOT_FOUND";
  /** A different body is already stored for the version that is published (409). */
  public static final String BUNDLE_CONFLICT = "E_BUNDLE_CONFLICT";
  /** The request has no valid token (401). */
  public static final String UNAUTHENTICATED = "E_UNAUTHENTICATED";
  /** The token is valid but does not carry the role that the endpoint needs (403). */
  public static final String FORBIDDEN = "E_FORBIDDEN";
  /** The bundle store cannot be reached or written (503). */
  public static final String STORE_UNAVAILABLE = "E_STORE_UNAVAILABLE";
  /** Any other failure of the server (500). The response does not say more. */
  public static final String INTERNAL = "E_INTERNAL";

  private ErrorCodes() {
  }
}
