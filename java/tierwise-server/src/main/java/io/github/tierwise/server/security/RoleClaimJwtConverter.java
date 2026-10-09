package io.github.tierwise.server.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns a validated JWT into an authentication whose authorities are the roles in one claim.
 *
 * <p>The claim is named by {@code tierwise.server.security.roles-claim}: a claim name, or a dotted path into
 * nested objects such as {@code realm_access.roles}. A claim name that contains dots is tried as a whole
 * first. The claim is a list of strings or one string of space-separated roles. A role {@code r} becomes the
 * authority {@code ROLE_r}. No other claim, and in particular no scope, grants anything.
 */
public final class RoleClaimJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  /** The prefix of every authority that this converter creates. */
  public static final String ROLE_PREFIX = "ROLE_";

  private final String claim;

  /**
   * Creates a converter.
   *
   * @param claim the claim name or dotted path that holds the roles
   */
  public RoleClaimJwtConverter(String claim) {
    this.claim = Objects.requireNonNull(claim, "claim");
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (String role : roles(jwt.getClaims())) {
      authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role));
    }
    return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
  }

  private List<String> roles(Map<String, Object> claims) {
    Object value = claims.get(claim);
    if (value == null && claim.contains(".")) {
      value = path(claims, claim);
    }
    List<String> roles = new ArrayList<>();
    if (value instanceof String text) {
      for (String role : text.trim().split("\\s+")) {
        if (!role.isEmpty()) {
          roles.add(role);
        }
      }
    } else if (value instanceof Collection<?> collection) {
      for (Object element : collection) {
        if (element instanceof String role && !role.isBlank()) {
          roles.add(role);
        }
      }
    }
    return roles;
  }

  private static Object path(Map<String, Object> claims, String dotted) {
    Object current = claims;
    for (String segment : dotted.split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(segment);
    }
    return current;
  }
}
