package io.github.visplabs.tierwise.server;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Configuration of the server, bound from the {@code tierwise.server} properties.
 *
 * @param store which bundle store the server uses: {@code filesystem} (the default) or the name that a store module
 *     documents, for example {@code s3}. The filesystem store is registered only when this is {@code filesystem}
 *     and the application defines no {@link io.github.visplabs.tierwise.server.store.BundleStore} bean
 * @param filesystem the settings of the filesystem store
 * @param security who may call which endpoint
 * @param maxBundleSize the largest bundle the server accepts at {@code POST /v1/bundles}
 * @param providerTimeout the timeout of one call to a {@link io.github.visplabs.tierwise.core.LevelValueProvider}
 *     (SVC-48); it must be a positive whole number of milliseconds
 * @param latestCacheTtl how long one instance remembers the latest pointer; zero reads the store on every
 *     request, so every instance sees a publish or a promote at once. A publish or promote on the same
 *     instance always takes effect on that instance at once
 * @param runtimeCacheSize how many loaded bundle versions one instance keeps in memory
 * @param maxKeysPerRequest the largest number of keys in one {@code /v1/resolve} request
 */
@ConfigurationProperties("tierwise.server")
public record TierwiseServerProperties(
    @DefaultValue("filesystem") String store,
    @DefaultValue Filesystem filesystem,
    @DefaultValue Security security,
    @DefaultValue("16MB") DataSize maxBundleSize,
    @DefaultValue("200ms") Duration providerTimeout,
    @DefaultValue("0s") Duration latestCacheTtl,
    @DefaultValue("8") int runtimeCacheSize,
    @DefaultValue("100") int maxKeysPerRequest) {

  /** Checks the values that no default can make safe. */
  public TierwiseServerProperties {
    Objects.requireNonNull(filesystem, "filesystem");
    Objects.requireNonNull(security, "security");
    if (store == null || store.isBlank()) {
      throw new IllegalArgumentException("tierwise.server.store must not be blank");
    }
    if (maxBundleSize.toBytes() <= 0) {
      throw new IllegalArgumentException("tierwise.server.max-bundle-size must be positive");
    }
    if (providerTimeout.isNegative() || providerTimeout.isZero() || providerTimeout.toNanos() % 1_000_000 != 0) {
      throw new IllegalArgumentException(
          "tierwise.server.provider-timeout must be a positive whole number of milliseconds");
    }
    if (latestCacheTtl.isNegative()) {
      throw new IllegalArgumentException("tierwise.server.latest-cache-ttl must not be negative");
    }
    if (runtimeCacheSize < 1) {
      throw new IllegalArgumentException("tierwise.server.runtime-cache-size must be at least 1");
    }
    if (maxKeysPerRequest < 1) {
      throw new IllegalArgumentException("tierwise.server.max-keys-per-request must be at least 1");
    }
  }

  /**
   * Settings of the filesystem store.
   *
   * @param root the directory that holds the bundles, the records and the latest pointer. It is created if
   *     it does not exist. Instances that share a store point at the same directory
   */
  public record Filesystem(@DefaultValue("./tierwise-data") Path root) {
  }

  /**
   * Who may call which endpoint. Every request needs a valid JWT, except reads when {@link #reads()} is
   * {@link Reads#OPEN}, and {@code GET /openapi.yaml}, which is always open.
   *
   * @param reads whether reads need a token
   * @param publishRole the role that may call {@code POST /v1/bundles}
   * @param promoteRole the role that may call {@code POST /v1/bundles/{version}/promote}
   * @param readRole a role that reads need, in addition to a valid token; empty means any valid token. It has
   *     no effect when reads are open
   * @param rolesClaim the JWT claim that holds the roles: a claim name, or a dotted path such as
   *     {@code realm_access.roles}. The claim is a list of strings or one space-separated string
   */
  public record Security(
      @DefaultValue("authenticated") Reads reads,
      @DefaultValue("tierwise-publisher") String publishRole,
      @DefaultValue("tierwise-promoter") String promoteRole,
      @DefaultValue("") String readRole,
      @DefaultValue("roles") String rolesClaim) {

    /** Checks that the role names and the claim are set. */
    public Security {
      requireText(publishRole, "publish-role");
      requireText(promoteRole, "promote-role");
      requireText(rolesClaim, "roles-claim");
    }

    private static void requireText(String text, String name) {
      if (text == null || text.isBlank()) {
        throw new IllegalArgumentException("tierwise.server.security." + name + " must not be blank");
      }
    }
  }

  /** Whether the read endpoints need a token. */
  public enum Reads {
    /** Reads need no token. Publishing and promoting still do. */
    OPEN,
    /** Reads need a valid token (and the read role, if one is configured). */
    AUTHENTICATED
  }
}
