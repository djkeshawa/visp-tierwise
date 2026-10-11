package io.github.visplabs.tierwise.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * The runnable Tierwise server.
 *
 * <p>Every bean of the server comes from an auto-configuration ({@link TierwiseServerAutoConfiguration},
 * {@link io.github.visplabs.tierwise.server.security.TierwiseSecurityAutoConfiguration}), so the class does not scan
 * packages. An application that embeds the server adds {@code tierwise-server} to its class path and
 * defines the beans it wants to replace: a {@link io.github.visplabs.tierwise.server.store.BundleStore}, one
 * {@link io.github.visplabs.tierwise.core.LevelValueProvider} per service-sourced level (the bean name is the level
 * name), or a {@code SecurityFilterChain}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TierwiseServerApplication {

  /**
   * Starts the server.
   *
   * @param args the command-line arguments, which Spring Boot reads as configuration
   */
  public static void main(String[] args) {
    SpringApplication.run(TierwiseServerApplication.class, args);
  }
}
