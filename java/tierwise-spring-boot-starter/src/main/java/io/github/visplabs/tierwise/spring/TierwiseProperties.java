package io.github.visplabs.tierwise.spring;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.logging.LogLevel;

/**
 * The {@code tierwise.*} configuration properties.
 *
 * <p>Exactly one primary bundle source is used: a {@link BundleSource} bean if the application has one,
 * otherwise {@link #getSource()}, otherwise {@link Server#getUrl()}. A {@link #getSnapshot() snapshot} is a
 * fallback for startup only.
 */
@ConfigurationProperties(prefix = "tierwise")
public class TierwiseProperties {

  /** Turns the auto-configuration off when {@code false}. */
  private boolean enabled = true;

  /**
   * The bundle location: {@code classpath:path}, {@code file:/path/to/bundle.json}, or an
   * {@code http://} or {@code https://} URL of the bundle bytes. Ignored when the application defines a
   * {@link BundleSource} bean.
   */
  private String source;

  /**
   * The location of a snapshot bundle ({@code classpath:} or {@code file:}). It is read only at
   * startup, and only when the primary source cannot supply a loadable bundle. Without a snapshot,
   * startup fails when no bundle loads.
   */
  private String snapshot;

  /** How often the starter checks the source for a new bundle. {@code 0} turns refreshing off. */
  private Duration refreshInterval = Duration.ofSeconds(30);

  /**
   * How long the bundle in use may go without being confirmed current before it counts as stale. It
   * drives {@link TierwiseEvaluators#isStale()} and the escalated log message.
   */
  private Duration stalenessThreshold = Duration.ofMinutes(5);

  /** The timeout of one provider call, for a provider with no more specific timeout. */
  private Duration providerTimeout = Duration.ofMillis(200);

  /**
   * Provider timeouts by level name. An entry here wins over {@link TierwiseProvider#timeout()} and over
   * {@link #getProviderTimeout()}. A level name with dots or other special characters needs the bracket
   * form, {@code tierwise.provider-timeouts[my.level]}.
   */
  private Map<String, Duration> providerTimeouts = new LinkedHashMap<>();

  /**
   * The level at which each decision is logged with its bundle version. {@code OFF} disables the
   * message. {@code FATAL} logs at error level.
   */
  private LogLevel decisionLogLevel = LogLevel.DEBUG;

  private final Server server = new Server();
  private final Http http = new Http();

  /**
   * Tells whether the auto-configuration is on.
   *
   * @return {@code true} unless {@code tierwise.enabled} is {@code false}
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets {@code tierwise.enabled}.
   *
   * @param enabled {@code false} turns the auto-configuration off
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns {@code tierwise.source}.
   *
   * @return the bundle location, or {@code null}
   */
  public String getSource() {
    return source;
  }

  /**
   * Sets {@code tierwise.source}.
   *
   * @param source the bundle location
   */
  public void setSource(String source) {
    this.source = source;
  }

  /**
   * Returns {@code tierwise.snapshot}.
   *
   * @return the snapshot location, or {@code null}
   */
  public String getSnapshot() {
    return snapshot;
  }

  /**
   * Sets {@code tierwise.snapshot}.
   *
   * @param snapshot the snapshot location
   */
  public void setSnapshot(String snapshot) {
    this.snapshot = snapshot;
  }

  /**
   * Returns {@code tierwise.refresh-interval}.
   *
   * @return the polling interval
   */
  public Duration getRefreshInterval() {
    return refreshInterval;
  }

  /**
   * Sets {@code tierwise.refresh-interval}.
   *
   * @param refreshInterval the polling interval; zero turns refreshing off, negative is rejected at startup
   */
  public void setRefreshInterval(Duration refreshInterval) {
    this.refreshInterval = refreshInterval;
  }

  /**
   * Returns {@code tierwise.staleness-threshold}.
   *
   * @return the staleness threshold
   */
  public Duration getStalenessThreshold() {
    return stalenessThreshold;
  }

  /**
   * Sets {@code tierwise.staleness-threshold}.
   *
   * @param stalenessThreshold the staleness threshold; it must be positive
   */
  public void setStalenessThreshold(Duration stalenessThreshold) {
    this.stalenessThreshold = stalenessThreshold;
  }

  /**
   * Returns {@code tierwise.provider-timeout}.
   *
   * @return the default provider timeout
   */
  public Duration getProviderTimeout() {
    return providerTimeout;
  }

  /**
   * Sets {@code tierwise.provider-timeout}.
   *
   * @param providerTimeout the default provider timeout; a positive whole number of milliseconds
   */
  public void setProviderTimeout(Duration providerTimeout) {
    this.providerTimeout = providerTimeout;
  }

  /**
   * Returns {@code tierwise.provider-timeouts}.
   *
   * @return the live map of level name to timeout
   */
  public Map<String, Duration> getProviderTimeouts() {
    return providerTimeouts;
  }

  /**
   * Sets {@code tierwise.provider-timeouts}.
   *
   * @param providerTimeouts level name to timeout
   */
  public void setProviderTimeouts(Map<String, Duration> providerTimeouts) {
    this.providerTimeouts = providerTimeouts;
  }

  /**
   * Returns {@code tierwise.decision-log-level}.
   *
   * @return the level decisions are logged at
   */
  public LogLevel getDecisionLogLevel() {
    return decisionLogLevel;
  }

  /**
   * Sets {@code tierwise.decision-log-level}.
   *
   * @param decisionLogLevel the level, or {@code OFF}
   */
  public void setDecisionLogLevel(LogLevel decisionLogLevel) {
    this.decisionLogLevel = decisionLogLevel;
  }

  /**
   * Returns the {@code tierwise.server.*} properties.
   *
   * @return the server properties
   */
  public Server getServer() {
    return server;
  }

  /**
   * Returns the {@code tierwise.http.*} properties.
   *
   * @return the HTTP properties
   */
  public Http getHttp() {
    return http;
  }

  /** The {@code tierwise.server.*} properties: a {@code tierwise-server} to poll. */
  public static class Server {

    /**
     * The base URL of a {@code tierwise-server}. The starter reads {@code GET /v1/bundles/latest} below
     * it, so {@code https://tierwise.internal} reads {@code https://tierwise.internal/v1/bundles/latest}.
     */
    private String url;

    /**
     * Returns {@code tierwise.server.url}.
     *
     * @return the base URL, or {@code null}
     */
    public String getUrl() {
      return url;
    }

    /**
     * Sets {@code tierwise.server.url}.
     *
     * @param url the base URL
     */
    public void setUrl(String url) {
      this.url = url;
    }
  }

  /** The {@code tierwise.http.*} properties: how HTTP and server sources are read. */
  public static class Http {

    /** The time allowed to open a connection. */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /** The time allowed for one request, including reading the body. */
    private Duration requestTimeout = Duration.ofSeconds(10);

    /** Extra request headers, such as {@code Authorization}. Their values are never logged. */
    private Map<String, String> headers = new LinkedHashMap<>();

    /**
     * Returns {@code tierwise.http.connect-timeout}.
     *
     * @return the connect timeout
     */
    public Duration getConnectTimeout() {
      return connectTimeout;
    }

    /**
     * Sets {@code tierwise.http.connect-timeout}.
     *
     * @param connectTimeout the connect timeout
     */
    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    /**
     * Returns {@code tierwise.http.request-timeout}.
     *
     * @return the request timeout
     */
    public Duration getRequestTimeout() {
      return requestTimeout;
    }

    /**
     * Sets {@code tierwise.http.request-timeout}.
     *
     * @param requestTimeout the request timeout
     */
    public void setRequestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
    }

    /**
     * Returns {@code tierwise.http.headers}.
     *
     * @return the live map of header name to value
     */
    public Map<String, String> getHeaders() {
      return headers;
    }

    /**
     * Sets {@code tierwise.http.headers}.
     *
     * @param headers header name to value
     */
    public void setHeaders(Map<String, String> headers) {
      this.headers = headers;
    }
  }
}
