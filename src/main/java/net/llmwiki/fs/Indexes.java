package net.llmwiki.fs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Derived Index Protocol implementation.
 *
 * - Indexes are caches; frontmatter is truth.
 * - Before reading any _index.md, count *.md vs rows; rebuild on mismatch.
 * - Writes are best-effort; staleness heals on next read.
 */
public final class Indexes {

  /** Read an index, rebuilding inline if file count != row count. */
  public static String readFresh(Path dir) {
    Path indexPath = dir.resolve("_index.md");
    if (!WikiFS.exists(indexPath)) {
      String built = build(dir);
      WikiFS.write(indexPath, built);
      return built;
    }
    String existing = WikiFS.read(indexPath);
    int rows = countContentsRows(existing);
    int files = WikiFS.listMd(dir).size();
    if (rows != files) {
      String built = build(dir);
      WikiFS.write(indexPath, built);
      return built;
    }
    return existing;
  }

  /** Force rebuild from frontmatter. */
  public static void rebuild(Path dir) {
    Path indexPath = dir.resolve("_index.md");
    WikiFS.write(indexPath, build(dir));
  }

  /** Build a fresh index from the directory's *.md files (frontmatter-driven). */
  public static String build(Path dir) {
    if (!Files.isDirectory(dir)) {
      try { Files.createDirectories(dir); }
      catch (Exception e) { throw new RuntimeException(e); }
    }
    String dirName = dir.getFileName().toString();
    List<Path> files = WikiFS.listMd(dir);

    StringBuilder out = new StringBuilder();
    out.append("# ").append(dirName).append(" Index\n\n");
    out.append("> Auto-generated index. Indexes are caches; frontmatter is truth.\n\n");
    out.append("Last updated: ").append(Slugs.today()).append("\n\n");
    out.append("## Contents\n\n");
    out.append("| File | Summary | Tags | Updated |\n");
    out.append("|------|---------|------|---------|\n");

    for (Path f : files) {
      var parsed = Frontmatter.parse(WikiFS.read(f));
      String name = f.getFileName().toString();
      String summary = Frontmatter.stringField(parsed.fields, "summary", "");
      String updated = Frontmatter.stringField(parsed.fields, "updated", Frontmatter.stringField(parsed.fields, "ingested", ""));
      String tags = String.join(", ", Frontmatter.listField(parsed.fields, "tags"));
      out.append("| [").append(name).append("](").append(name).append(") | ")
         .append(escapeRow(summary)).append(" | ")
         .append(escapeRow(tags)).append(" | ")
         .append(escapeRow(updated)).append(" |\n");
    }

    out.append("\n## Recent Changes\n\n");
    out.append("- ").append(Slugs.today()).append(": Index rebuilt from frontmatter\n");
    return out.toString();
  }

  /** Best-effort: count rows in the Contents table (excluding header). */
  static int countContentsRows(String index) {
    String[] lines = index.split("\\R", -1);
    int rows = 0;
    boolean inContents = false;
    boolean afterHeader = false;
    for (String l : lines) {
      String t = l.trim();
      if (t.startsWith("## ")) {
        inContents = t.startsWith("## Contents");
        afterHeader = false;
        continue;
      }
      if (!inContents) continue;
      if (t.startsWith("|------")) { afterHeader = true; continue; }
      if (afterHeader && t.startsWith("|") && !t.startsWith("|------")) {
        rows++;
      }
    }
    return rows;
  }

  /** Build the master index for a wiki root with stats and quick navigation. */
  public static void writeMasterIndex(Path wikiRoot, String wikiTitle) {
    int sources = countMdRecursive(wikiRoot.resolve("raw"));
    int articles = countMdRecursive(wikiRoot.resolve("wiki"));
    int outputs = countMdRecursive(wikiRoot.resolve("output"));

    String body = """
        # %s

        > Master index for this topic wiki.

        Last updated: %s

        ## Statistics

        - Sources: %d raw documents
        - Articles: %d compiled wiki articles
        - Outputs: %d generated artifacts
        - Last compiled: (n/a)
        - Last lint: (n/a)

        ## Quick Navigation

        - [All Sources](raw/_index.md)
        - [Concepts](wiki/concepts/_index.md)
        - [Topics](wiki/topics/_index.md)
        - [References](wiki/references/_index.md)
        - [Outputs](output/_index.md)

        ## Recent Changes

        - %s: Index updated
        """.formatted(wikiTitle, Slugs.today(), sources, articles, outputs, Slugs.today());

    WikiFS.write(wikiRoot.resolve("_index.md"), body);
  }

  public static int countMdRecursive(Path root) {
    return WikiFS.listMdRecursive(root).size();
  }

  private static String escapeRow(String s) {
    if (s == null) return "";
    return s.replace("|", "\\|").replace("\n", " ").trim();
  }

  /** Read frontmatter table for a directory's articles into in-memory rows. */
  public static List<Map<String, Object>> articleRows(Path dir) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Path f : WikiFS.listMd(dir)) {
      var fm = Frontmatter.parse(WikiFS.read(f)).fields;
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("path", f.toString());
      row.put("name", f.getFileName().toString());
      row.put("title", fm.getOrDefault("title", ""));
      row.put("summary", fm.getOrDefault("summary", ""));
      row.put("tags", fm.getOrDefault("tags", List.of()));
      row.put("updated", fm.getOrDefault("updated", fm.getOrDefault("ingested", "")));
      out.add(row);
    }
    return out;
  }

  private Indexes() {}
}
