package io.github.tierwise.core.internal.project;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * The files of a project directory: path resolution with the containment rule of MANIFEST-12, reading,
 * and listing for glob patterns (SRC-18).
 *
 * <p>Symbolic links are followed. A path counts as inside the project only when its resolved location
 * lies below the resolved project root.
 */
final class ProjectFiles {

  /** The result of resolving a project-relative path. */
  enum Status {
    /** A regular file inside the project root. */
    FILE,
    /** The path exists nowhere, or an I/O error stopped the lookup. */
    MISSING,
    /** The path names a directory, or something that is not a regular file. */
    NOT_A_FILE,
    /** The path, after symbolic links are resolved, lies outside the project root, or is not relative. */
    OUTSIDE
  }

  /**
   * A resolved path.
   *
   * @param status the outcome
   * @param name the normalised project-relative path with {@code /} separators; meaningful for every
   *     status except {@link Status#OUTSIDE} of an absolute path
   * @param path the file system path, for {@link Status#FILE}
   */
  record Resolved(Status status, String name, Path path) {
  }

  private final Path root;
  private final Path realRoot;

  /**
   * Creates the view of a project directory.
   *
   * @param projectRoot the directory that contains the manifest
   */
  ProjectFiles(Path projectRoot) {
    this.root = projectRoot.toAbsolutePath().normalize();
    Path real;
    try {
      real = projectRoot.toRealPath();
    } catch (IOException e) {
      real = root;
    }
    this.realRoot = real;
  }

  /**
   * Resolves a path that the manifest names (MANIFEST-12).
   *
   * @param relative the path as written
   * @return the outcome
   */
  Resolved resolve(String relative) {
    if (relative.isEmpty() || relative.startsWith("/") || relative.startsWith("\\")) {
      return new Resolved(Status.OUTSIDE, relative, null);
    }
    Path candidate;
    try {
      candidate = root.resolve(relative).normalize();
    } catch (RuntimeException e) {
      return new Resolved(Status.MISSING, relative, null);
    }
    if (!candidate.startsWith(root) || candidate.equals(root)) {
      return new Resolved(Status.OUTSIDE, relative, null);
    }
    String name = relativeName(candidate);
    if (!Files.exists(candidate)) {
      return new Resolved(Status.MISSING, name, null);
    }
    try {
      Path real = candidate.toRealPath();
      if (!real.startsWith(realRoot)) {
        return new Resolved(Status.OUTSIDE, name, null);
      }
    } catch (IOException e) {
      return new Resolved(Status.MISSING, name, null);
    }
    if (!Files.isRegularFile(candidate)) {
      return new Resolved(Status.NOT_A_FILE, name, null);
    }
    return new Resolved(Status.FILE, name, candidate);
  }

  /**
   * Reads a file.
   *
   * @param path a path from {@link #resolve(String)}
   * @return the bytes, or empty when the file cannot be read
   */
  Optional<byte[]> read(Path path) {
    try {
      return Optional.of(Files.readAllBytes(path));
    } catch (IOException | RuntimeException e) {
      return Optional.empty();
    }
  }

  /**
   * Lists the regular files below a directory, following symbolic links, hidden files included.
   *
   * @param directory a project-relative directory with {@code /} separators; empty for the project root
   * @return the root-relative paths in code point order. A path that leaves the root begins with
   *     {@code ..}, and {@link #resolve(String)} then reports it as outside.
   */
  List<String> list(String directory) {
    List<String> found = new ArrayList<>();
    if (directory.startsWith("/") || directory.startsWith("\\")) {
      return found;
    }
    Path start = directory.isEmpty() ? root : root.resolve(directory).normalize();
    if (!Files.isDirectory(start)) {
      return found;
    }
    try {
      Files.walkFileTree(start, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
              if (attrs.isRegularFile()) {
                found.add(relativeName(file));
              }
              return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
              return FileVisitResult.CONTINUE;
            }
          });
    } catch (IOException | RuntimeException e) {
      // A directory that cannot be walked contributes the files that were found so far.
    }
    found.sort(CodePointOrder.ORDER);
    return found;
  }

  private String relativeName(Path file) {
    return root.relativize(file.normalize()).toString().replace('\\', '/');
  }
}
