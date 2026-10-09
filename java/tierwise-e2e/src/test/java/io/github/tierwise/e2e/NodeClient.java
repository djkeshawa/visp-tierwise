package io.github.tierwise.e2e;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The Node service: {@code node-client.mjs} running {@code js/node}'s built output in a child process. The test sends
 * one JSON command per line on standard input and reads one JSON reply per line on standard output.
 */
final class NodeClient implements AutoCloseable {

  private static final String END_OF_OUTPUT = "\u0000end";

  private final Process process;
  private final BufferedWriter commands;
  private final BlockingQueue<String> replies = new LinkedBlockingQueue<>();

  private NodeClient(Process process) {
    this.process = process;
    this.commands = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    Thread reader = new Thread(this::readReplies, "e2e-node-output");
    reader.setDaemon(true);
    reader.start();
  }

  /**
   * Tells why the Node part cannot run, or returns empty when it can: {@code node} must run and answer with version
   * 20 or later, and {@code js/node} and {@code js/evaluator} must be built.
   */
  static Optional<String> unavailableReason(Path root) {
    Path nodeDist = distIndex(root);
    Path evaluatorDist = root.resolve("js/evaluator/dist/esm/index.js");
    if (!Files.isRegularFile(nodeDist) || !Files.isRegularFile(evaluatorDist)) {
      return Optional.of("js/node/dist (or js/evaluator/dist) is missing; build it with: pnpm --dir js run build");
    }
    try {
      Process version = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
      String output = new String(version.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      if (!version.waitFor(10, TimeUnit.SECONDS) || version.exitValue() != 0) {
        return Optional.of("`node --version` did not succeed");
      }
      int major = Integer.parseInt(output.replaceFirst("^v", "").split("\\.")[0]);
      return major >= 20 ? Optional.empty() : Optional.of("node " + output + " is older than 20");
    } catch (IOException e) {
      return Optional.of("`node` is not on the PATH (" + e.getMessage() + ")");
    } catch (NumberFormatException e) {
      return Optional.of("cannot read the version of node");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.of("interrupted while checking node");
    }
  }

  private static Path distIndex(Path root) {
    return root.resolve("js/node/dist/esm/index.js");
  }

  /** Starts the process and waits until its loader has loaded the first bundle. */
  static NodeClient start(Path root, String bundleUrl, Duration refreshInterval) {
    Path script = root.resolve("java/tierwise-e2e/src/test/resources/node-client.mjs");
    try {
      Process process = new ProcessBuilder("node", script.toString(), distIndex(root).toString(), bundleUrl,
          String.valueOf(refreshInterval.toMillis()))
          .redirectError(ProcessBuilder.Redirect.INHERIT)
          .start();
      NodeClient client = new NodeClient(process);
      JsonNode ready = client.receive(Duration.ofSeconds(30));
      if (!ready.path("ready").asBoolean()) {
        client.close();
        throw new AssertionError("the node client did not start: " + ready);
      }
      return client;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The {@code bundleVersion} the loader holds. */
  String bundleVersion() {
    send(Json.MAPPER.createObjectNode().put("op", "status"));
    return receive(Duration.ofSeconds(10)).path("bundleVersion").asString();
  }

  /** Starts waiting for a version; read the answer with {@link #receive}. The loader polls on its own timer. */
  void sendAwait(String version, Duration timeout) {
    ObjectNode command = Json.MAPPER.createObjectNode().put("op", "await").put("version", version);
    command.put("timeoutMs", timeout.toMillis());
    send(command);
  }

  /** Runs the cases and returns the outcome of each by id, in the order of the cases. */
  Map<String, JsonNode> evaluate(List<JsonNode> cases) {
    ObjectNode command = Json.MAPPER.createObjectNode().put("op", "evaluate");
    command.putArray("cases").addAll(cases);
    send(command);
    JsonNode reply = receive(Duration.ofSeconds(30));
    if (!reply.path("ok").asBoolean()) {
      throw new AssertionError("the node client failed to evaluate: " + reply);
    }
    Map<String, JsonNode> outcomes = new LinkedHashMap<>();
    for (JsonNode outcome : reply.get("results")) {
      outcomes.put(outcome.get("id").asString(), outcome);
    }
    return outcomes;
  }

  void send(ObjectNode command) {
    try {
      commands.write(Json.MAPPER.writeValueAsString(command));
      commands.newLine();
      commands.flush();
    } catch (IOException e) {
      throw new UncheckedIOException("cannot write to the node client", e);
    }
  }

  /** Reads the next reply. */
  JsonNode receive(Duration timeout) {
    try {
      String line = replies.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (line == null) {
        throw new AssertionError("the node client sent no reply within " + timeout);
      }
      if (line.equals(END_OF_OUTPUT)) {
        throw new AssertionError("the node client ended (exit " + exitValueOrNegative() + ")");
      }
      return Json.parse(line);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("interrupted while waiting for the node client", e);
    }
  }

  private int exitValueOrNegative() {
    return process.isAlive() ? -1 : process.exitValue();
  }

  private void readReplies() {
    try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = out.readLine()) != null) {
        replies.add(line);
      }
    } catch (IOException e) {
      // The process ended; the end marker below tells the waiting test.
    } finally {
      replies.add(END_OF_OUTPUT);
    }
  }

  @Override
  public void close() {
    try {
      if (process.isAlive()) {
        send(Json.MAPPER.createObjectNode().put("op", "stop"));
      }
    } catch (RuntimeException e) {
      // The process is already gone.
    }
    try {
      commands.close();
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
    } catch (IOException e) {
      process.destroyForcibly();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }
}
