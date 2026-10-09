package io.github.tierwise.server.security;

import io.github.tierwise.server.ErrorCodes;
import io.github.tierwise.server.web.ErrorBody;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes 401 and 403 responses as the JSON error {@code {code, message}}, keeping the standard
 * {@code WWW-Authenticate} header of bearer authentication.
 */
final class JsonSecurityErrors {

  private final JsonMapper json;

  JsonSecurityErrors(JsonMapper json) {
    this.json = json;
  }

  AuthenticationEntryPoint entryPoint() {
    BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    return (HttpServletRequest request, HttpServletResponse response, AuthenticationException e) -> {
      bearer.commence(request, response, e);
      write(response, 401, new ErrorBody(ErrorCodes.UNAUTHENTICATED,
          "a valid bearer token is required"));
    };
  }

  AccessDeniedHandler accessDeniedHandler() {
    BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
    return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) -> {
      bearer.handle(request, response, e);
      write(response, 403, new ErrorBody(ErrorCodes.FORBIDDEN,
          "the token does not carry the role that this endpoint needs"));
    };
  }

  private void write(HttpServletResponse response, int status, ErrorBody body) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getOutputStream().write(json.writeValueAsBytes(body));
  }
}
