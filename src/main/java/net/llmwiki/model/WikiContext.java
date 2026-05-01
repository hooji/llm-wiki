package net.llmwiki.model;

import java.nio.file.Path;

/**
 * Resolved wiki context for the current operation.
 *
 * - root: absolute path to the wiki (HUB itself, HUB/topics/&lt;slug&gt;, or a local .wiki/)
 * - kind: hub | topic | local
 * - exists: false if --new-topic is in play and the wiki is being created on the fly
 */
public record WikiContext(Path hub, Path root, Kind kind, boolean exists) {
  public enum Kind { HUB, TOPIC, LOCAL }

  public Path raw()    { return root.resolve("raw"); }
  public Path wiki()   { return root.resolve("wiki"); }
  public Path output() { return root.resolve("output"); }
  public Path inbox()  { return root.resolve("inbox"); }
  public Path log()    { return root.resolve("log.md"); }
}
