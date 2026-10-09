package io.github.tierwise.server.security;

import io.github.tierwise.server.TierwiseServerProperties;
import io.github.tierwise.server.TierwiseServerProperties.Reads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.json.JsonMapper;

/**
 * Secures the endpoints as an OAuth2 resource server that validates JWTs.
 *
 * <ul>
 *   <li>{@code POST /v1/bundles} needs the publish role, and {@code POST /v1/bundles/{version}/promote} the
 *       promote role ({@code tierwise.server.security.publish-role}, {@code promote-role}).
 *   <li>The reads ({@code GET /v1/bundles}, {@code /latest}, {@code /{version}}, {@code GET /v1/schema},
 *       {@code POST /v1/resolve}, {@code POST /v1/decide}) are open or need a valid token
 *       ({@code reads}), and the read role if one is configured.
 *   <li>{@code GET /openapi.yaml} is always open. Every other request is denied.
 *   <li>The server keeps no session and has no user store, no password and no default credentials. Without
 *       JWT configuration it rejects every token ({@link FailClosedJwtDecoder}).
 * </ul>
 *
 * <p>An application can replace the filter chain by defining its own {@code SecurityFilterChain} bean.
 */
@AutoConfiguration(
    beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"},
    afterName = "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(TierwiseServerProperties.class)
@EnableWebSecurity
public class TierwiseSecurityAutoConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(TierwiseSecurityAutoConfiguration.class);

  /**
   * The method and path pattern of every read endpoint. {@code POST /v1/resolve} and {@code /decide} only read, and
   * {@code HEAD} is the read-only twin of {@code GET} that clients use to check an ETag.
   */
  private static final String[][] READS = {
      {"GET", "/v1/bundles"},
      {"GET", "/v1/bundles/*"},
      {"GET", "/v1/schema"},
      {"HEAD", "/v1/bundles"},
      {"HEAD", "/v1/bundles/*"},
      {"HEAD", "/v1/schema"},
      {"POST", "/v1/resolve"},
      {"POST", "/v1/decide"}};

  /**
   * The decoder used when the application configures none. It rejects every token.
   *
   * @return the decoder
   */
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  public JwtDecoder tierwiseFailClosedJwtDecoder() {
    LOG.warn("No JWT decoder is configured: every token is rejected, so publishing and promoting are impossible."
        + " Set spring.security.oauth2.resourceserver.jwt.issuer-uri or jwk-set-uri.");
    return new FailClosedJwtDecoder();
  }

  /**
   * The filter chain of the server.
   *
   * @param http the builder
   * @param properties the server configuration
   * @param json the application's JSON mapper
   * @return the chain
   * @throws Exception when the chain cannot be built
   */
  @Bean
  @ConditionalOnMissingBean(SecurityFilterChain.class)
  public SecurityFilterChain tierwiseSecurityFilterChain(HttpSecurity http, TierwiseServerProperties properties,
      JsonMapper json) throws Exception {
    TierwiseServerProperties.Security security = properties.security();
    JsonSecurityErrors errors = new JsonSecurityErrors(json);
    String publish = authority(security.publishRole());
    String promote = authority(security.promoteRole());
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests -> {
          requests.requestMatchers(HttpMethod.GET, "/openapi.yaml").permitAll();
          requests.requestMatchers(HttpMethod.HEAD, "/openapi.yaml").permitAll();
          requests.requestMatchers(HttpMethod.POST, "/v1/bundles").hasAuthority(publish);
          requests.requestMatchers(HttpMethod.POST, "/v1/bundles/*/promote").hasAuthority(promote);
          for (String[] read : READS) {
            var url = requests.requestMatchers(HttpMethod.valueOf(read[0]), read[1]);
            if (security.reads() == Reads.OPEN) {
              url.permitAll();
            } else if (security.readRole().isBlank()) {
              url.authenticated();
            } else {
              url.hasAuthority(authority(security.readRole()));
            }
          }
          requests.anyRequest().denyAll();
        })
        .exceptionHandling(handling -> handling
            .authenticationEntryPoint(errors.entryPoint())
            .accessDeniedHandler(errors.accessDeniedHandler()))
        .oauth2ResourceServer(resource -> resource
            .authenticationEntryPoint(errors.entryPoint())
            .accessDeniedHandler(errors.accessDeniedHandler())
            .jwt(jwt -> jwt.jwtAuthenticationConverter(new RoleClaimJwtConverter(security.rolesClaim()))));
    return http.build();
  }

  private static String authority(String role) {
    return RoleClaimJwtConverter.ROLE_PREFIX + role;
  }
}
