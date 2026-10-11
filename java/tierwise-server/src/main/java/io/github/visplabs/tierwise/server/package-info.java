/**
 * The Tierwise server, the optional control plane of requirements section 9.
 *
 * <p>It stores every published bundle version and every publication record, serves the latest bundle with an
 * ETag, promotes a stored version to latest (roll back or forward), and answers {@code /v1/resolve} and
 * {@code /v1/decide} with {@code tierwise-core}. It is stateless, so any number of instances can share one
 * {@linkplain io.github.visplabs.tierwise.server.store.BundleStore store}, and it is never the source of truth: the
 * configuration repository is.
 *
 * <p>The server is a Spring Boot application ({@link io.github.visplabs.tierwise.server.TierwiseServerApplication}).
 * Its beans are registered by {@link io.github.visplabs.tierwise.server.TierwiseServerAutoConfiguration} and
 * {@link io.github.visplabs.tierwise.server.security.TierwiseSecurityAutoConfiguration}, so an application that
 * embeds the server can replace any of them, in particular the {@code BundleStore}.
 */
package io.github.visplabs.tierwise.server;
