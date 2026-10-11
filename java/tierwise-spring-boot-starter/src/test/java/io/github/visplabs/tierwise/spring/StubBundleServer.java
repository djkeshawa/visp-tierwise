package io.github.visplabs.tierwise.spring;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A stub of {@code GET /v1/bundles/latest} on the JDK's {@code com.sun.net.httpserver}. It serves one
 * bundle with {@code ETag: "<bundleVersion>"}, honours {@code If-None-Match}, records the requests and
 * can be told to fail, to stop and to start again on the same port.
 */
final class StubBundleServer implements AutoCloseable {

  /** One request as the stub saw it. */
  record Request(String path, String ifNoneMatch, String authorization) {
  }

  private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
  private final AtomicInteger notModified = new AtomicInteger();
  private volatile byte[] body;
  private volatile String etag;
  private volatile int failWith;
  private HttpServer server;
  private int port;

  StubBundleServer(byte[] bundle, String bundleVersion) throws IOException {
    publish(bundle, bundleVersion);
    start(0);
  }

  void publish(byte[] bundle, String bundleVersion) {
    this.body = bundle.clone();
    this.etag = "\"" + bundleVersion + "\"";
  }

  /** Answers every request with this status until {@link #recover()}; 0 means answer normally. */
  void failWith(int status) {
    this.failWith = status;
  }

  void recover() {
    this.failWith = 0;
  }

  /** The base URL of the server, as {@code tierwise.server.url} takes it. */
  String baseUrl() {
    return "http://127.0.0.1:" + port;
  }

  String latestUrl() {
    return baseUrl() + "/v1/bundles/latest";
  }

  List<Request> requests() {
    synchronized (requests) {
      return List.copyOf(requests);
    }
  }

  int notModifiedCount() {
    return notModified.get();
  }

  /** Stops the server: later connections are refused. */
  void stop() {
    if (server != null) {
      server.stop(0);
      server = null;
    }
  }

  /** Starts the server again on the port it had. */
  void restart() throws IOException {
    stop();
    start(port);
  }

  private void start(int wantedPort) throws IOException {
    HttpServer created = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), wantedPort), 0);
    created.createContext("/", this::handle);
    created.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(task -> {
      Thread thread = new Thread(task, "stub-bundle-server");
      thread.setDaemon(true);
      return thread;
    }));
    created.start();
    this.server = created;
    this.port = created.getAddress().getPort();
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
      requests.add(new Request(
          exchange.getRequestURI().getPath(), ifNoneMatch, exchange.getRequestHeaders().getFirst("Authorization")));
      int forced = failWith;
      if (forced != 0) {
        exchange.sendResponseHeaders(forced, -1);
        return;
      }
      if (!exchange.getRequestURI().getPath().equals("/v1/bundles/latest")) {
        exchange.sendResponseHeaders(404, -1);
        return;
      }
      exchange.getResponseHeaders().set("ETag", etag);
      if (etag.equals(ifNoneMatch)) {
        notModified.incrementAndGet();
        exchange.sendResponseHeaders(304, -1);
        return;
      }
      byte[] content = body;
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, content.length);
      exchange.getResponseBody().write(content);
    }
  }

  @Override
  public void close() {
    stop();
  }
}
