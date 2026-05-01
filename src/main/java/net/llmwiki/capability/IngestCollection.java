package net.llmwiki.capability;

import net.llmwiki.core.*;
import net.llmwiki.fs.*;
import net.llmwiki.model.WikiContext;
import net.llmwiki.prelude.WikiResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * /wiki:ingest-collection &lt;source&gt; [--adapter git|mediawiki-dump|mediawiki-api] [--limit N] [--dry-run]
 *
 * Implementation status:
 *   - Adapter detection: complete.
 *   - Git adapter: works if `git` CLI is on PATH.
 *   - mediawiki-dump / mediawiki-api: STUBBED.
 *
 * For a Git collection: shallow-clone, list text files, dedup against existing
 * raw, write a manifest plus one immutable raw child per file. No LLM step
 * here (summaries are deferred to compile).
 */
public final class IngestCollection {

  private static final Set<String> VALUED = Set.of(
      "wiki", "new-topic", "adapter", "limit", "namespace", "include", "exclude");

  private final Context ctx;
  private final Init init;
  public IngestCollection(Context ctx) {
    this.ctx = ctx;
    this.init = new Init(ctx);
  }

  public String run(String args) {
    ArgParse.Args a = ArgParse.parse(args, VALUED);
    if (a.positionals.isEmpty()) {
      return "ingest-collection: supply a source URL or path.";
    }
    String source = a.positionals.get(0);
    String adapter = detectAdapter(source, a.get("adapter"));

    WikiContext wiki = init.resolveOrCreate(a.get("wiki"), a.is("local"), a.get("new-topic"), null);
    if (!wiki.exists()) return "ingest-collection: no wiki found.";

    return switch (adapter) {
      case "git" -> ingestGit(wiki, source, a);
      case "mediawiki-dump" -> "ingest-collection: mediawiki-dump adapter not yet implemented.";
      case "mediawiki-api" -> "ingest-collection: mediawiki-api adapter not yet implemented.";
      default -> "ingest-collection: cannot detect adapter for " + source + ". Pass --adapter explicitly.";
    };
  }

  // ----- detection -----

  private String detectAdapter(String source, String explicit) {
    if (explicit != null && !explicit.isBlank() && !"auto".equals(explicit)) return explicit;
    String low = source.toLowerCase();
    if (low.endsWith(".xml") || low.endsWith(".xml.bz2") || low.endsWith(".xml.gz")) return "mediawiki-dump";
    if (low.contains("github.com/") || low.contains("gitlab.com/") || low.endsWith(".git")
        || Files.isDirectory(Path.of(source).resolve(".git"))) return "git";
    if (low.contains("/wiki/") || low.contains("/w/")) return "mediawiki-api";
    return "unknown";
  }

  // ----- git adapter -----

  private String ingestGit(WikiContext wiki, String source, ArgParse.Args a) {
    Path tmp;
    try { tmp = Files.createTempDirectory("llm-wiki-collection-"); }
    catch (IOException e) { return "git adapter: cannot create temp dir: " + e.getMessage(); }

    try {
      runProcess(List.of("git", "clone", "--depth", "1", source, tmp.toString()));
      String revision = runProcess(List.of("git", "-C", tmp.toString(), "rev-parse", "HEAD")).trim();

      String slug = collectionSlug(source);
      List<Path> textFiles = new ArrayList<>();
      try (var stream = Files.walk(tmp)) {
        stream.filter(Files::isRegularFile)
              .filter(p -> !p.toString().contains("/.git/"))
              .filter(IngestCollection::isTextLike)
              .forEach(textFiles::add);
      }
      int limit = parseInt(a.get("limit"), Integer.MAX_VALUE);
      if (textFiles.size() > limit) textFiles = textFiles.subList(0, limit);

      // dedup via existing collection-tagged raw
      Set<String> existingKeys = new HashSet<>();
      for (Path raw : WikiFS.listMdRecursive(wiki.raw())) {
        var fm = Frontmatter.parse(WikiFS.read(raw)).fields;
        if (slug.equals(Frontmatter.stringField(fm, "collection", ""))) {
          existingKeys.add(Frontmatter.stringField(fm, "upstream_id", "") + "|"
              + Frontmatter.stringField(fm, "revision", ""));
        }
      }

      int newCount = 0, skipped = 0;
      for (Path f : textFiles) {
        String upstreamId = tmp.relativize(f).toString();
        String key = upstreamId + "|" + revision;
        if (existingKeys.contains(key)) { skipped++; continue; }

        String content = WikiFS.read(f);
        Map<String, Object> fm = new LinkedHashMap<>();
        fm.put("title", upstreamId);
        fm.put("source", source + "/blob/" + revision + "/" + upstreamId);
        fm.put("type", "articles");
        fm.put("ingested", LocalDate.now().toString());
        fm.put("tags", List.of("collection", slug));
        fm.put("summary", "Imported from " + slug + " at " + revision);
        fm.put("collection", slug);
        fm.put("adapter", "git");
        fm.put("upstream_id", upstreamId);
        fm.put("upstream_type", "git-file");
        fm.put("revision", revision);
        fm.put("canonical_url", source + "/blob/" + revision + "/" + upstreamId);
        fm.put("content_format", contentFormat(upstreamId));
        fm.put("license", "unknown");

        String childSlug = Slugs.slugify(slug + "-" + upstreamId);
        Path target = wiki.raw().resolve("articles").resolve(LocalDate.now() + "-" + childSlug + ".md");
        if (a.is("dry-run")) { newCount++; continue; }
        WikiFS.write(target, Frontmatter.render(fm, content));
        newCount++;
      }

      // Manifest
      if (!a.is("dry-run")) {
        Map<String, Object> manifestFm = new LinkedHashMap<>();
        manifestFm.put("title", "Collection: " + slug);
        manifestFm.put("source", source);
        manifestFm.put("type", "repos");
        manifestFm.put("ingested", LocalDate.now().toString());
        manifestFm.put("tags", List.of("collection", "collection-manifest", "git"));
        manifestFm.put("summary", "Manifest for " + slug + ": " + newCount + " new, "
            + skipped + " skipped, revision " + revision);
        manifestFm.put("collection", slug);
        manifestFm.put("adapter", "git");
        manifestFm.put("revision", revision);
        manifestFm.put("canonical_url", source);
        manifestFm.put("license", "unknown");
        Path mPath = wiki.raw().resolve("repos").resolve(LocalDate.now() + "-" + slug + "-manifest.md");
        WikiFS.write(mPath, Frontmatter.render(manifestFm, "# Collection manifest: " + slug));

        safe(() -> Indexes.rebuild(wiki.raw().resolve("articles")));
        safe(() -> Indexes.rebuild(wiki.raw().resolve("repos")));
        safe(() -> Indexes.rebuild(wiki.raw()));
        Logs.appendActivity(wiki.root(), "ingest-collection",
            slug + " via git: " + newCount + " new, " + skipped + " skipped, " + textFiles.size() + " total");
      }
      return "git collection " + slug + " (revision " + revision + "): "
          + newCount + " new, " + skipped + " skipped"
          + (a.is("dry-run") ? " (dry-run)" : "");
    } catch (Exception e) {
      return "git adapter failed: " + e.getMessage();
    }
  }

  // ----- helpers -----

  private static String contentFormat(String path) {
    String l = path.toLowerCase();
    if (l.endsWith(".md")) return "markdown";
    if (l.endsWith(".mediawiki") || l.endsWith(".wiki")) return "mediawiki";
    if (l.endsWith(".rst")) return "text";
    if (l.endsWith(".adoc")) return "text";
    return "text";
  }

  private static boolean isTextLike(Path p) {
    String n = p.getFileName().toString().toLowerCase();
    return n.endsWith(".md") || n.endsWith(".mediawiki") || n.endsWith(".wiki")
        || n.endsWith(".rst") || n.endsWith(".txt") || n.endsWith(".adoc");
  }

  private static String collectionSlug(String source) {
    String s = source.replaceAll("\\.git$", "");
    String[] parts = s.split("/");
    return Slugs.slugify(parts[parts.length - 1]);
  }

  private static int parseInt(String s, int def) {
    if (s == null) return def;
    try { return Integer.parseInt(s); } catch (Exception e) { return def; }
  }

  private static String runProcess(List<String> cmd) throws IOException, InterruptedException {
    var pb = new ProcessBuilder(cmd).redirectErrorStream(true);
    var p = pb.start();
    String out = new String(p.getInputStream().readAllBytes());
    int code = p.waitFor();
    if (code != 0) throw new IOException("command failed: " + String.join(" ", cmd) + "\n" + out);
    return out;
  }

  private static void safe(Runnable r) { try { r.run(); } catch (Exception ignored) {} }
}
