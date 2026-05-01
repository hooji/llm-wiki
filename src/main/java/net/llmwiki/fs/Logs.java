package net.llmwiki.fs;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Activity log + session event log.
 *
 * - log.md: append-only, one operation per line:
 *     ## [YYYY-MM-DD] operation | description
 * - .session-events.jsonl: append-only, one JSON object per line.
 */
public final class Logs {

  public static void appendActivity(Path wikiRoot, String operation, String description) {
    String line = "## [" + LocalDate.now() + "] " + operation + " | " + description;
    WikiFS.append(wikiRoot.resolve("log.md"), line);
  }

  public static void appendSessionEvent(Path wikiRoot, Map<String, Object> event) {
    WikiFS.append(wikiRoot.resolve(".session-events.jsonl"), net.llmwiki.core.Json.stringify(event));
  }

  /** Read the last N "## [...]" lines from log.md. */
  public static List<String> tailActivity(Path wikiRoot, int n) {
    Path p = wikiRoot.resolve("log.md");
    if (!WikiFS.exists(p)) return List.of();
    String[] lines = WikiFS.read(p).split("\\R", -1);
    List<String> out = new ArrayList<>();
    for (String l : lines) if (l.startsWith("## [")) out.add(l);
    if (out.size() <= n) return out;
    return out.subList(out.size() - n, out.size());
  }

  private Logs() {}
}
