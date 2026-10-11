package io.github.visplabs.tierwise.server.security;

import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * The decoder that the server uses when the application configures none: it rejects every token. The server
 * then starts, serves open reads if they are enabled, and refuses every publish and promote, instead of
 * trusting tokens it cannot verify. Configure
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} or {@code jwk-set-uri} to accept tokens.
 */
public final class FailClosedJwtDecoder implements JwtDecoder {

  @Override
  public Jwt decode(String token) {
    throw new BadJwtException("the server has no JWT decoder; set"
        + " spring.security.oauth2.resourceserver.jwt.issuer-uri or jwk-set-uri");
  }
}
