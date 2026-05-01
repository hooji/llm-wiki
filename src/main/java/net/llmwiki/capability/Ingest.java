package net.llmwiki.capability;

import com.fasterxml.jackson.databind.JsonNode;
import net.llmwiki.core.*;
import net.llmwiki.fs.*;
import net.llmwiki.model.WikiContext;

import java.nio.file.Path;
import java.util.*;

/**
 * /wiki:ingest &lt;url|filepath|"text"&gt;
 *
 * Implements the single-source flow:
 *   detect-source-kind -> fetch-* -> extract-source-metadata ->
 *   write-raw-source -> rebuild raw/&lt;type&gt;/_index.md ->
 *   append-activity-log -> compilation-nudge
 */
public final class Ingest {

  private static final Set<String> VALUED = Set.of(
      "type", "title", "wiki", "new-topic", "project");

  private final Context ctx;
  private final Init init;
  public Ingest(Context ctx) {
    this.ctx = ctx;
    this.init = new Init(ctx);
  }

  public String run(String args) {
    ArgParse.Args a = ArgParse.parse(args, VALUED);
    if (a.positionals.isEmpty() && !a.has("inbox")) {
      return "ingest: supply a URL, file path, or quoted text. (Or --inbox.)";
    }

    boolean inbox = a.is("inbox");
    String wikiName = a.get("wiki");
    boolean local = a.is("local");
    String newTopic = a.get("new-topic");

    WikiContext wiki = init.resolveOrCreate(wikiName, local, newTopic, null);
    if (!wiki.exists()) {
      return "ingest: no wiki found. Use --wiki <name>, --local, or --new-topic <name>.";
    }

    if (inbox) {
      return runInbox(wiki, a);
    }

    String source = a.positionals.get(0);
    return ingestOne(wiki, source, a);
  }

  // ----- single source -----

  private String ingestOne(WikiContext wiki, String source, ArgParse.Args a) {
    var kind = detectSourceKind(source);
    String contentMd;
    String preknownTitle = a.get("title");
    String publishedDate = null;
    List<String> authors = new ArrayList<>();

    switch (kind.kind) {
      case "url" -> {
        var fetched = fetchUrl(source);
        contentMd = fetched.contentMarkdown;
        if (preknownTitle == null) preknownTitle = fetched.title;
        publishedDate = fetched.published;
        authors = fetched.authors;
      }
      case "github" -> {
        var fetched = fetchGithub(source);
        contentMd = fetched.contentMarkdown;
        if (preknownTitle == null) preknownTitle = fetched.title;
      }
      case "twitter" -> {
        // stub: best-effort plain fetch
        contentMd = "(twitter fallback chain not yet implemented)\n\n" + safeFetch(source);
      }
      case "file" -> {
        contentMd = WikiFS.read(Path.of(source));
        if (preknownTitle == null) preknownTitle = Path.of(source).getFileName().toString();
      }
      default /* text */ -> {
        contentMd = source;
      }
    }

    // extract-source-metadata
    Map<String, Object> meta = extractMetadata(contentMd);
    String title = preknownTitle != null ? preknownTitle : str(meta, "title", "untitled");
    String summary = str(meta, "summary", "");
    @SuppressWarnings("unchecked")
    List<String> tags = meta.get("tags") instanceof List<?> l
        ? l.stream().map(String::valueOf).toList()
        : List.of();
    String type = a.getOrDefault("type", str(meta, "type_suggestion", kind.defaultType));

    // write-raw-source
    String slug = Slugs.slugify(title);
    String filename = Slugs.datedFilename(slug);
    Path target = wiki.raw().resolve(type).resolve(filename);
    int bump = 1;
    while (WikiFS.exists(target)) {
      bump++;
      target = wiki.raw().resolve(type).resolve(Slugs.today() + "-" + slug + "-" + bump + ".md");
    }
    Map<String, Object> fm = new LinkedHashMap<>();
    fm.put("title", title);
    fm.put("source", kind.kind.equals("text") ? "MANUAL" : source);
    fm.put("type", type);
    fm.put("ingested", Slugs.today());
    fm.put("tags", tags);
    fm.put("summary", summary);
    if (publishedDate != null) fm.put("published", publishedDate);
    if (!authors.isEmpty()) fm.put("authors", authors);
    if (a.has("project")) fm.put("project", a.get("project"));

    String body = "# " + title + "\n\n" + (contentMd == null ? "" : contentMd);
    WikiFS.write(target, Frontmatter.render(fm, body));

    // best-effort index updates (full rebuild is cheap)
    safe(() -> Indexes.rebuild(wiki.raw().resolve(type)));
    safe(() -> Indexes.rebuild(wiki.raw()));
    safe(() -> Indexes.writeMasterIndex(wiki.root(), str(readConfigTitle(wiki), "title", wiki.root().getFileName().toString())));

    Logs.appendActivity(wiki.root(), "ingest", "\"" + title + "\" (" + wiki.root().relativize(target) + ")");

    String nudge = compilationNudge(wiki);
    return "Ingested: " + title + "\n  -> " + target + "\n  type=" + type + " tags=" + tags
        + (nudge.isEmpty() ? "" : "\n" + nudge);
  }

  // ----- inbox stub -----

  private String runInbox(WikiContext wiki, ArgParse.Args a) {
    Path inbox = wiki.inbox();
    if (!WikiFS.exists(inbox)) return "ingest --inbox: no inbox/ directory in this wiki.";
    var files = WikiFS.listMd(inbox);
    int processed = 0;
    StringBuilder out = new StringBuilder();
    for (Path f : files) {
      if (f.getFileName().toString().startsWith(".")) continue;
      out.append(ingestOne(wiki, f.toString(), a)).append("\n");
      processed++;
    }
    if (processed == 0) return "Inbox is empty. Drop files into " + inbox + " and run again.";
    return out.toString();
  }

  // ----- transitions -----

  record SourceKind(String kind, String defaultType) {}

  private SourceKind detectSourceKind(String s) {
    if (s.matches("(?i)https?://(www\\.)?(x|twitter)\\.com/.*/status/.*")) return new SourceKind("twitter", "notes");
    if (s.matches("(?i)https?://(www\\.)?github\\.com/[^/]+/[^/]+/?$")) return new SourceKind("github", "repos");
    if (s.startsWith("http://") || s.startsWith("https://")) {
      String t = s.contains("arxiv") || s.contains("doi.org") || s.endsWith(".pdf") ? "papers" : "articles";
      return new SourceKind("url", t);
    }
    if (s.contains("/") || s.startsWith(".") || s.startsWith("~") || java.nio.file.Files.exists(Path.of(s))) {
      return new SourceKind("file", "notes");
    }
    return new SourceKind("text", "notes");
  }

  record Fetched(String title, String contentMarkdown, String published, List<String> authors) {}

  private Fetched fetchUrl(String url) {
    String raw = safeFetch(url);
    String prompt = Templates.expand(Prompts.FETCH_URL_EXTRACTION, "content", truncate(raw, 60_000));
    JsonNode reply = Json.parse(ctx.llm.call(prompt));
    List<String> authors = new ArrayList<>();
    if (reply.has("authors") && reply.get("authors").isArray()) {
      reply.get("authors").forEach(n -> authors.add(n.asText()));
    }
    String pub = reply.path("published").isNull() ? null : reply.path("published").asText(null);
    return new Fetched(
        reply.path("title").asText(""),
        reply.path("content_markdown").asText(""),
        pub,
        authors);
  }

  private Fetched fetchGithub(String url) {
    // Same shape as URL but a different extraction prompt would be ideal;
    // basic extraction is good enough as a starting point.
    return fetchUrl(url);
  }

  private String safeFetch(String url) {
    try { return ctx.fetcher.fetch(url); }
    catch (Exception e) { return "(fetch failed: " + e.getMessage() + ")"; }
  }

  private Map<String, Object> extractMetadata(String contentMd) {
    String prompt = Templates.expand(Prompts.EXTRACT_SOURCE_METADATA,
        "content_markdown", truncate(contentMd, 60_000));
    JsonNode reply = Json.parse(ctx.llm.call(prompt));
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("title", reply.path("title").asText(""));
    m.put("summary", reply.path("summary").asText(""));
    List<String> tags = new ArrayList<>();
    if (reply.path("tags").isArray()) reply.get("tags").forEach(n -> tags.add(n.asText()));
    m.put("tags", tags);
    m.put("type_suggestion", reply.path("type_suggestion").asText("notes"));
    return m;
  }

  private String compilationNudge(WikiContext wiki) {
    var rawIndex = wiki.root().resolve("_index.md");
    if (!WikiFS.exists(rawIndex)) return "";
    int count = WikiFS.listMdRecursive(wiki.raw()).size();
    if (count >= 5) {
      return "You have " + count + " raw sources. Run /compile to integrate them.";
    }
    return "";
  }

  // ----- helpers -----

  private static Map<String, Object> readConfigTitle(WikiContext wiki) {
    Path cfg = wiki.root().resolve("config.md");
    if (!WikiFS.exists(cfg)) return Map.of();
    return Frontmatter.parse(WikiFS.read(cfg)).fields;
  }

  private static String str(Map<String, Object> m, String k, String def) {
    Object v = m.get(k);
    return v == null ? def : String.valueOf(v);
  }

  private static String truncate(String s, int max) {
    return (s == null || s.length() <= max) ? s : s.substring(0, max) + "\n...[truncated]";
  }

  private static void safe(Runnable r) { try { r.run(); } catch (Exception ignored) {} }
}
