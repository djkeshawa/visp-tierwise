package io.github.tierwise.server.web;

/**
 * The JSON body of every error response.
 *
 * @param code a stable error code: a code of {@code docs/requirements.md} section 6 for errors of the core, or a
 *     code of {@link io.github.tierwise.server.ErrorCodes}
 * @param message a human-readable message; it is not stable
 */
public record ErrorBody(String code, String message) {
}
