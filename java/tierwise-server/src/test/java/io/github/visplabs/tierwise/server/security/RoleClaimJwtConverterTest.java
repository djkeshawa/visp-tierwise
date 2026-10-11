package io.github.visplabs.tierwise.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class RoleClaimJwtConverterTest {

  private static Jwt jwt(Map<String, Object> claims) {
    Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").subject("subject-1")
        .issuedAt(Instant.parse("2026-01-01T00:00:00Z")).expiresAt(Instant.parse("2026-01-02T00:00:00Z"));
    claims.forEach(builder::claim);
    return builder.build();
  }

  private static Set<String> authorities(String claim, Map<String, Object> claims) {
    return new RoleClaimJwtConverter(claim).convert(jwt(claims)).getAuthorities().stream()
        .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
  }

  @Test
  void aListClaimGivesOneAuthorityForEachRole() {
    assertEquals(Set.of("ROLE_a", "ROLE_b"), authorities("roles", Map.of("roles", List.of("a", "b"))));
  }

  @Test
  void aSpaceSeparatedClaimGivesOneAuthorityForEachRole() {
    assertEquals(Set.of("ROLE_a", "ROLE_b"), authorities("roles", Map.of("roles", "  a   b ")));
  }

  @Test
  void aDottedPathReadsNestedClaims() {
    Map<String, Object> claims = Map.of("realm_access", Map.of("roles", List.of("ci")));
    assertEquals(Set.of("ROLE_ci"), authorities("realm_access.roles", claims));
  }

  @Test
  void aClaimNameThatContainsDotsIsTriedAsAWhole() {
    assertEquals(Set.of("ROLE_ci"),
        authorities("https://example.test/roles", Map.of("https://example.test/roles", List.of("ci"))));
  }

  @Test
  void aMissingOrMistypedClaimGivesNoAuthority() {
    assertEquals(Set.of(), authorities("roles", Map.of()));
    assertEquals(Set.of(), authorities("roles", Map.of("roles", 5)));
    assertEquals(Set.of(), authorities("a.b", Map.of("a", "text")));
    assertEquals(Set.of(), authorities("roles", Map.of("roles", List.of(1, 2))));
  }

  @Test
  void scopesAndOtherClaimsGrantNothing() {
    assertEquals(Set.of(), authorities("roles", Map.of("scope", "tierwise-publisher", "scp", List.of("x"))));
  }

  @Test
  void theSubjectIsTheName() {
    assertEquals("subject-1", new RoleClaimJwtConverter("roles").convert(jwt(Map.of())).getName());
  }
}
