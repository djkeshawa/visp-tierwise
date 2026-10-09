package io.github.tierwise.server.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * A {@link BundleStore} on a local directory, or on any filesystem that the server instances share.
 *
 * <p>Layout under the root directory:
 *
 * <pre>
 * bundles/&lt;64 hex digits&gt;.json                           the exact bundle bytes
 * records/&lt;64 hex digits&gt;/&lt;millis&gt;-&lt;seq&gt;-&lt;uuid&gt;.json  one publication record per file
 * latest                                                 "sha256:..." and a newline
 * tmp/                                                   files being written
 * </pre>
 *
 * <p>Every file is written completely to {@code tmp/}, flushed to disk, and then put in place with one atomic
 * step. A bundle or a record is put in place with a hard link (or, where links are unsupported, a move)
 * that fails if the target exists, so an existing file is never overwritten or modified. Only the {@code latest}
 * pointer is replaced, with an atomic move. A reader therefore sees a complete file or none.
 *
 * <p>The directory must be on one filesystem so that the atomic steps stay atomic. Network filesystems must
 * give atomic renames and exclusive creation; on object storage use an object-store implementation.
 */
public final class FilesystemBundleStore implements BundleStore {

  private static final String EXTENSION = ".json";

  private final Path bundles;
  private final Path records;
  private final Path latest;
  private final Path tmp;
  private final Clock clock;
  private final JsonMapper json = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
  private long lastMillis = -1;
  private long sequence;

  /**
   * Opens a store, creating the directories it needs.
   *
   * @param root the root directory
   * @throws BundleStoreException when the directories cannot be created
   */
  public FilesystemBundleStore(Path root) {
    this(root, Clock.systemUTC());
  }

  FilesystemBundleStore(Path root, Clock clock) {
    Objects.requireNonNull(root, "root");
    this.clock = Objects.requireNonNull(clock, "clock");
    Path absolute = root.toAbsolutePath().normalize();
    this.bundles = absolute.resolve("bundles");
    this.records = absolute.resolve("records");
    this.latest = absolute.resolve("latest");
    this.tmp = absolute.resolve("tmp");
    try {
      Files.createDirectories(bundles);
      Files.createDirectories(records);
      Files.createDirectories(tmp);
    } catch (IOException e) {
      throw new BundleStoreException("cannot create the store directories under " + absolute, e);
    }
  }

  @Override
  public boolean putBundle(String version, byte[] bytes) {
    Path target = bundles.resolve(BundleVersions.digest(version) + EXTENSION);
    Objects.requireNonNull(bytes, "bytes");
    try {
      if (createExclusively(target, bytes)) {
        return true;
      }
      byte[] existing = Files.readAllBytes(target);
      if (Arrays.equals(existing, bytes)) {
        return false;
      }
      throw new BundleConflictException(version);
    } catch (IOException e) {
      throw new BundleStoreException("cannot store the bundle " + version, e);
    }
  }

  @Override
  public Optional<byte[]> getBundle(String version) {
    Path source = bundles.resolve(BundleVersions.digest(version) + EXTENSION);
    try {
      return Optional.of(Files.readAllBytes(source));
    } catch (NoSuchFileException e) {
      return Optional.empty();
    } catch (IOException e) {
      throw new BundleStoreException("cannot read the bundle " + version, e);
    }
  }

  @Override
  public void addRecord(PublicationRecord record) {
    Objects.requireNonNull(record, "record");
    Path directory = records.resolve(BundleVersions.digest(record.bundleVersion()));
    byte[] bytes = json.writeValueAsBytes(record.toJson());
    try {
      Files.createDirectories(directory);
      String name = String.format("%s-%s%s", nextStamp(), UUID.randomUUID(), EXTENSION);
      if (!createExclusively(directory.resolve(name), bytes)) {
        throw new BundleStoreException("a record file exists already: " + name);
      }
    } catch (IOException e) {
      throw new BundleStoreException("cannot store the record of " + record.bundleVersion(), e);
    }
  }

  @Override
  public List<PublicationRecord> records(String version) {
    Path directory = records.resolve(BundleVersions.digest(version));
    try {
      return readRecords(directory);
    } catch (NoSuchFileException e) {
      return List.of();
    } catch (IOException e) {
      throw new BundleStoreException("cannot read the records of " + version, e);
    }
  }

  @Override
  public List<PublicationRecord> allRecords() {
    List<PublicationRecord> all = new ArrayList<>();
    try (DirectoryStream<Path> directories = Files.newDirectoryStream(records)) {
      for (Path directory : directories) {
        if (Files.isDirectory(directory)) {
          all.addAll(readRecords(directory));
        }
      }
    } catch (IOException e) {
      throw new BundleStoreException("cannot read the records", e);
    }
    return Collections.unmodifiableList(all);
  }

  @Override
  public Optional<String> latest() {
    try {
      String text = Files.readString(latest, StandardCharsets.UTF_8).strip();
      return Optional.of(BundleVersions.require(text));
    } catch (NoSuchFileException e) {
      return Optional.empty();
    } catch (IOException e) {
      throw new BundleStoreException("cannot read the latest pointer", e);
    } catch (IllegalArgumentException e) {
      throw new BundleStoreException("the latest pointer file is damaged: " + e.getMessage());
    }
  }

  @Override
  public void setLatest(String version) {
    BundleVersions.require(version);
    Path temporary = temporaryFile();
    try {
      writeFlushed(temporary, (version + "\n").getBytes(StandardCharsets.UTF_8));
      try {
        Files.move(temporary, latest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, latest, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      throw new BundleStoreException("cannot move the latest pointer to " + version, e);
    } finally {
      deleteQuietly(temporary);
    }
  }

  /**
   * Returns a stamp that sorts after every stamp this instance returned before: the time in milliseconds and a
   * sequence number within that millisecond. A clock that steps back keeps the last millisecond, so the order
   * of the records this instance adds is the order of their file names.
   */
  private synchronized String nextStamp() {
    long now = clock.millis();
    if (now > lastMillis) {
      lastMillis = now;
      sequence = 0;
    } else {
      sequence++;
    }
    return String.format("%013d-%06d", lastMillis, sequence);
  }

  private List<PublicationRecord> readRecords(Path directory) throws IOException {
    List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + EXTENSION)) {
      for (Path file : stream) {
        files.add(file);
      }
    } catch (DirectoryIteratorException e) {
      throw e.getCause();
    }
    Collections.sort(files);
    List<PublicationRecord> result = new ArrayList<>(files.size());
    for (Path file : files) {
      result.add(parseRecord(file));
    }
    return Collections.unmodifiableList(result);
  }

  private PublicationRecord parseRecord(Path file) throws IOException {
    try {
      Map<String, Object> raw = json.readValue(Files.readAllBytes(file), new TypeReference<Map<String, Object>>() { });
      Object version = raw.get("bundleVersion");
      if (!(version instanceof String text)) {
        throw new IllegalArgumentException("no bundleVersion");
      }
      return PublicationRecord.fromJson(raw, text);
    } catch (JacksonException | IllegalArgumentException e) {
      throw new BundleStoreException("the record file " + file.getFileName() + " is damaged: " + e.getMessage(), e);
    }
  }

  /** Writes a complete temporary file and puts it at {@code target} unless {@code target} exists. */
  private boolean createExclusively(Path target, byte[] bytes) throws IOException {
    Path temporary = temporaryFile();
    try {
      writeFlushed(temporary, bytes);
      try {
        Files.createLink(target, temporary);
        return true;
      } catch (FileAlreadyExistsException e) {
        return false;
      } catch (UnsupportedOperationException | IOException e) {
        // Hard links are not available here. A move without REPLACE_EXISTING still refuses an existing target.
        try {
          Files.move(temporary, target);
          return true;
        } catch (FileAlreadyExistsException exists) {
          return false;
        }
      }
    } finally {
      deleteQuietly(temporary);
    }
  }

  private Path temporaryFile() {
    return tmp.resolve(UUID.randomUUID() + ".tmp");
  }

  private static void writeFlushed(Path file, byte[] bytes) throws IOException {
    try (FileChannel out = FileChannel.open(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      ByteBuffer buffer = ByteBuffer.wrap(bytes);
      while (buffer.hasRemaining()) {
        out.write(buffer);
      }
      out.force(true);
    }
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException e) {
      // A leftover file in tmp/ is harmless; it is never read.
    }
  }
}
