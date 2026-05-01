package net.llmwiki.fs;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Filesystem helpers. Atomic writes, glob, append. */
public final class WikiFS {

  public static String read(Path p) {
    try { return Files.readString(p, StandardCharsets.UTF_8); }
    catch (Exception e) { throw new RuntimeException("read failed: " + p + " — " + e.getMessage(), e); }
  }

  public static boolean exists(Path p) { return Files.exists(p); }

  public static void write(Path p, String content) {
    try {
      Files.createDirectories(p.getParent());
      Files.writeString(p, content, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("write failed: " + p + " — " + e.getMessage(), e);
    }
  }

  /** Atomic write via tmp + rename. Used for checkpoints. */
  public static void writeAtomic(Path p, String content) {
    try {
      Files.createDirectories(p.getParent());
      Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
      Files.writeString(tmp, content, StandardCharsets.UTF_8);
      Files.move(tmp, p, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception e) {
      throw new RuntimeException("atomic write failed: " + p + " — " + e.getMessage(), e);
    }
  }

  /** Append a line + newline. Open-append-close per call so concurrent writers interleave safely. */
  public static void append(Path p, String line) {
    try {
      Files.createDirectories(p.getParent());
      Files.write(p, (line + "\n").getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    } catch (Exception e) {
      throw new RuntimeException("append failed: " + p + " — " + e.getMessage(), e);
    }
  }

  public static void mkdirs(Path p) {
    try { Files.createDirectories(p); }
    catch (Exception e) { throw new RuntimeException("mkdirs failed: " + p, e); }
  }

  public static void delete(Path p) {
    try { Files.deleteIfExists(p); }
    catch (Exception e) { throw new RuntimeException("delete failed: " + p, e); }
  }

  /** List all *.md files in a directory (non-recursive), excluding _index.md. */
  public static List<Path> listMd(Path dir) {
    if (!Files.isDirectory(dir)) return List.of();
    try (Stream<Path> s = Files.list(dir)) {
      return s.filter(p -> p.getFileName().toString().endsWith(".md"))
              .filter(p -> !p.getFileName().toString().equals("_index.md"))
              .sorted()
              .toList();
    } catch (Exception e) {
      throw new RuntimeException("list failed: " + dir, e);
    }
  }

  /** Recursive list of *.md under root (excluding _index.md). */
  public static List<Path> listMdRecursive(Path root) {
    if (!Files.isDirectory(root)) return List.of();
    try (Stream<Path> s = Files.walk(root)) {
      return s.filter(Files::isRegularFile)
              .filter(p -> p.getFileName().toString().endsWith(".md"))
              .filter(p -> !p.getFileName().toString().equals("_index.md"))
              .sorted()
              .toList();
    } catch (Exception e) {
      throw new RuntimeException("walk failed: " + root, e);
    }
  }

  /** Naive grep for a literal substring across files; returns matching paths. Case-insensitive. */
  public static List<Path> grep(Path root, String needle) {
    List<Path> out = new ArrayList<>();
    String n = needle.toLowerCase();
    for (Path p : listMdRecursive(root)) {
      try {
        String body = Files.readString(p, StandardCharsets.UTF_8).toLowerCase();
        if (body.contains(n)) out.add(p);
      } catch (Exception ignored) {}
    }
    return out;
  }

  private WikiFS() {}
}
