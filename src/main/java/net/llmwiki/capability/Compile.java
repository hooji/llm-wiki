package net.llmwiki.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.llmwiki.core.*;
import net.llmwiki.fs.*;
import net.llmwiki.model.WikiContext;
import net.llmwiki.prelude.WikiResolver;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * /wiki:compile [--full]
 *
 * Implements:
 *   placement-precheck (skipped here — see TODO),
 *   survey-uncompiled-sources,
 *   per-source extract-source-signals (LLM),
 *   map-concepts-to-articles,
 *   per article: plan-article (LLM) -> write-article-skeleton -> write-article-section (LLM x sections)
 *                -> deterministic See Also + Sources,
 *   enforce-bidirectional-link,
 *   index updates,
 *   append-activity-log.
 */
public final class Compile {

  private static final Set<String> VALUED = Set.of("wiki", "source", "topic");

  private final Context ctx;
  public Compile(Context ctx) { this.ctx = ctx; }

  public String run(String args) {
    ArgParse.Args a = ArgParse.parse(args, VALUED);
    boolean full = a.is("full");

    WikiContext wiki = new WikiResolver().resolve(ctx.hub, a.get("wiki"), a.is("local"));
    if (!wiki.exists()) return "compile: no wiki found. Run /init <topic> first.";

    // Survey
    List<Path> sources = surveyUncompiledSources(wiki, full, a.get("source"));
    if (sources.isEmpty()) {
      return "compile: all sources already compiled. Use --full to recompile.";
    }

    // Per-source signals (sequential to keep it simple; parallelize later)
    Map<Path, JsonNode> signalsByPath = new LinkedHashMap<>();
    for (Path p : sources) {
      try {
        String fileContent = WikiFS.read(p);
        var parsed = Frontmatter.parse(fileContent);
        String prompt = Templates.expand(Prompts.EXTRACT_SOURCE_SIGNALS,
            "frontmatter", Frontmatter.stringify(parsed.fields),
            "body", truncate(parsed.body, 50_000));
        signalsByPath.put(p, Json.parse(ctx.llm.call(prompt)));
      } catch (Exception e) {
        signalsByPath.put(p, Json.parse("{\"key_concepts\":[],\"key_facts\":[],\"relationships\":[],\"tags\":[]}"));
      }
    }

    // Map concepts -> articles
    Map<String, ArticlePlanInput> map = new LinkedHashMap<>();
    for (var e : signalsByPath.entrySet()) {
      JsonNode sig = e.getValue();
      JsonNode concepts = sig.path("key_concepts");
      if (concepts.isArray()) {
        for (JsonNode c : concepts) {
          int salience = c.path("salience").asInt(1);
          if (salience < 3) continue;
          String slug = c.path("slug").asText("").trim();
          if (slug.isEmpty()) slug = Slugs.slugify(c.path("name").asText("untitled"));
          String kind = c.path("kind").asText("concept");
          map.computeIfAbsent(slug, k -> new ArticlePlanInput(k, kind))
             .sources.add(e.getKey());
        }
      }
    }

    if (map.isEmpty()) {
      return "compile: signals extracted from " + sources.size() + " sources but no salient concepts (>=3) found.";
    }

    int newArticles = 0, updated = 0;
    List<String> seeAlsoToEnforce = new ArrayList<>();
    for (var entry : map.entrySet()) {
      String slug = entry.getKey();
      ArticlePlanInput plan = entry.getValue();
      Path catDir = wiki.wiki().resolve(category(plan.kind));
      Path articlePath = catDir.resolve(slug + ".md");
      boolean isNew = !WikiFS.exists(articlePath);

      // Plan
      ArrayNode srcArray = Json.obj().putArray("_");
      for (Path sp : plan.sources) {
        var p = Frontmatter.parse(WikiFS.read(sp));
        ObjectNode o = Json.obj();
        o.put("path", wiki.root().relativize(sp).toString());
        o.put("title", Frontmatter.stringField(p.fields, "title", ""));
        o.put("summary", Frontmatter.stringField(p.fields, "summary", ""));
        srcArray.add(o);
      }

      String planPrompt = Templates.expand(Prompts.PLAN_ARTICLE,
          "slug", slug,
          "kind", plan.kind,
          "sources_json", Json.stringify(srcArray),
          "related_json", Json.stringify(relatedArticles(wiki, slug)));

      JsonNode planJson;
      try { planJson = Json.parse(ctx.llm.call(planPrompt)); }
      catch (Exception ex) { continue; }

      // Skeleton + sections
      writeArticle(wiki, articlePath, slug, plan, planJson, isNew);
      if (isNew) newArticles++; else updated++;

      // See Also references for bidirectional enforcement
      if (planJson.path("see_also").isArray()) {
        for (JsonNode sa : planJson.path("see_also")) {
          String to = sa.path("slug").asText("");
          if (!to.isEmpty()) seeAlsoToEnforce.add(slug + "|" + to);
        }
      }
    }

    // Enforce bidirectional links — best-effort
    for (String pair : seeAlsoToEnforce) {
      try {
        String[] parts = pair.split("\\|", 2);
        enforceBidirectional(wiki, parts[0], parts[1]);
      } catch (Exception ignored) {}
    }

    // Index updates
    safe(() -> Indexes.rebuild(wiki.wiki().resolve("concepts")));
    safe(() -> Indexes.rebuild(wiki.wiki().resolve("topics")));
    safe(() -> Indexes.rebuild(wiki.wiki().resolve("references")));
    safe(() -> Indexes.rebuild(wiki.wiki()));
    safe(() -> Indexes.writeMasterIndex(wiki.root(), wiki.root().getFileName().toString()));

    Logs.appendActivity(wiki.root(), "compile",
        sources.size() + " sources -> " + newArticles + " new articles, " + updated + " updated");

    return "Compile complete: " + sources.size() + " sources processed, "
        + newArticles + " new articles, " + updated + " updated.";
  }

  // ----- helpers -----

  private static final class ArticlePlanInput {
    final String slug;
    String kind;
    final List<Path> sources = new ArrayList<>();
    ArticlePlanInput(String slug, String kind) { this.slug = slug; this.kind = kind; }
  }

  private static String category(String kind) {
    return switch (kind) {
      case "topic" -> "topics";
      case "reference" -> "references";
      default -> "concepts";
    };
  }

  private List<Path> surveyUncompiledSources(WikiContext wiki, boolean full, String specific) {
    if (specific != null && !specific.isBlank()) {
      Path p = wiki.root().resolve(specific);
      return WikiFS.exists(p) ? List.of(p) : List.of();
    }
    var all = WikiFS.listMdRecursive(wiki.raw());
    if (full) return all;
    String lastCompiled = readLastCompiled(wiki);
    if (lastCompiled == null) return all;
    return all.stream().filter(f -> {
      var fm = Frontmatter.parse(WikiFS.read(f)).fields;
      String ingested = Frontmatter.stringField(fm, "ingested", "");
      return ingested.compareTo(lastCompiled) > 0;
    }).toList();
  }

  private String readLastCompiled(WikiContext wiki) {
    Path master = wiki.root().resolve("_index.md");
    if (!WikiFS.exists(master)) return null;
    String body = WikiFS.read(master);
    for (String line : body.split("\\R", -1)) {
      if (line.startsWith("- Last compiled:")) {
        String v = line.substring("- Last compiled:".length()).trim();
        return v.equals("(n/a)") ? null : v;
      }
    }
    return null;
  }

  private List<Map<String, Object>> relatedArticles(WikiContext wiki, String slug) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (String cat : List.of("concepts", "topics", "references")) {
      var dir = wiki.wiki().resolve(cat);
      for (var p : WikiFS.listMd(dir)) {
        var fm = Frontmatter.parse(WikiFS.read(p)).fields;
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("slug", p.getFileName().toString().replace(".md", ""));
        r.put("title", Frontmatter.stringField(fm, "title", ""));
        r.put("summary", Frontmatter.stringField(fm, "summary", ""));
        out.add(r);
      }
    }
    return out;
  }

  private void writeArticle(WikiContext wiki, Path articlePath, String slug,
                            ArticlePlanInput plan, JsonNode planJson, boolean isNew) {
    Map<String, Object> fm = new LinkedHashMap<>();
    fm.put("title", planJson.path("title").asText(slug));
    fm.put("category", switch (plan.kind) {
      case "topic" -> "topic";
      case "reference" -> "reference";
      default -> "concept";
    });
    List<String> sourcePaths = new ArrayList<>();
    for (Path sp : plan.sources) sourcePaths.add(wiki.root().relativize(sp).toString());
    fm.put("sources", sourcePaths);
    fm.put("created", isNew ? LocalDate.now().toString() : readCreated(articlePath));
    fm.put("updated", LocalDate.now().toString());
    List<String> tags = new ArrayList<>();
    if (planJson.path("tags").isArray()) planJson.path("tags").forEach(n -> tags.add(n.asText()));
    fm.put("tags", tags);
    List<String> aliases = new ArrayList<>();
    if (planJson.path("aliases").isArray()) planJson.path("aliases").forEach(n -> aliases.add(n.asText()));
    fm.put("aliases", aliases);
    fm.put("confidence", planJson.path("confidence").asText("medium"));
    fm.put("volatility", planJson.path("volatility").asText("warm"));
    fm.put("verified", LocalDate.now().toString());
    fm.put("summary", planJson.path("summary").asText(""));

    StringBuilder body = new StringBuilder();
    body.append("# ").append(planJson.path("title").asText(slug)).append("\n\n");
    body.append("> ").append(planJson.path("abstract_paragraph").asText("")).append("\n\n");

    // Skeleton: write file with first heading only
    String firstHeading = "## " + (planJson.path("sections").isArray() && planJson.path("sections").size() > 0
        ? stripHeadingPrefix(planJson.path("sections").get(0).path("heading").asText("Background"))
        : "Background");
    WikiFS.write(articlePath, Frontmatter.render(fm, body.toString() + firstHeading + "\n"));

    // For each section, call LLM and Edit-append
    if (planJson.path("sections").isArray()) {
      for (int i = 0; i < planJson.path("sections").size(); i++) {
        JsonNode sec = planJson.path("sections").get(i);
        String heading = stripHeadingPrefix(sec.path("heading").asText("Section"));
        String intent = sec.path("intent").asText("");

        ArrayNode extracts = Json.obj().putArray("_");
        for (Path sp : plan.sources) {
          var p = Frontmatter.parse(WikiFS.read(sp));
          ObjectNode o = Json.obj();
          o.put("path", wiki.root().relativize(sp).toString());
          o.put("title", Frontmatter.stringField(p.fields, "title", ""));
          o.put("excerpt", truncate(p.body, 8000));
          extracts.add(o);
        }
        String prompt = Templates.expand(Prompts.WRITE_ARTICLE_SECTION,
            "title", planJson.path("title").asText(slug),
            "heading", heading,
            "intent", intent,
            "extracts_json", Json.stringify(extracts));
        JsonNode reply;
        try { reply = Json.parse(ctx.llm.call(prompt)); }
        catch (Exception ex) { reply = Json.parse("{\"section_markdown\":\"(generation failed)\"}"); }
        // For section 0 we already wrote the heading; just append body. Otherwise append heading + body.
        String md = reply.path("section_markdown").asText("");
        if (i == 0) {
          WikiFS.append(articlePath, "\n" + md + "\n");
        } else {
          WikiFS.append(articlePath, "\n## " + heading + "\n\n" + md + "\n");
        }
      }
    }

    // See Also section
    StringBuilder sa = new StringBuilder("\n## See Also\n\n");
    if (planJson.path("see_also").isArray()) {
      for (JsonNode link : planJson.path("see_also")) {
        String to = link.path("slug").asText("");
        String rel = link.path("relationship").asText("");
        if (!to.isEmpty()) {
          sa.append("- [[").append(to).append("|").append(to).append("]] ([")
            .append(to).append("](../").append(category(plan.kind)).append("/").append(to).append(".md)) — ")
            .append(rel).append("\n");
        }
      }
    }
    WikiFS.append(articlePath, sa.toString());

    // Sources section
    StringBuilder src = new StringBuilder("\n## Sources\n\n");
    if (planJson.path("source_attributions").isArray()) {
      for (JsonNode attr : planJson.path("source_attributions")) {
        String path = attr.path("path").asText("");
        String contributed = attr.path("what_it_contributed").asText("");
        src.append("- [")
            .append(path.substring(path.lastIndexOf('/') + 1).replace(".md", ""))
            .append("](../../").append(path).append(") — ").append(contributed).append("\n");
      }
    }
    WikiFS.append(articlePath, src.toString());
  }

  private static String stripHeadingPrefix(String h) {
    return h == null ? "Section" : h.replaceFirst("^#+\\s*", "");
  }

  private static String readCreated(Path p) {
    if (!WikiFS.exists(p)) return LocalDate.now().toString();
    var fm = Frontmatter.parse(WikiFS.read(p)).fields;
    return Frontmatter.stringField(fm, "created", LocalDate.now().toString());
  }

  private void enforceBidirectional(WikiContext wiki, String fromSlug, String toSlug) {
    // Find target file in any wiki/<cat>/ directory
    Path target = null;
    for (String cat : List.of("concepts", "topics", "references")) {
      Path candidate = wiki.wiki().resolve(cat).resolve(toSlug + ".md");
      if (WikiFS.exists(candidate)) { target = candidate; break; }
    }
    if (target == null) return;
    String body = WikiFS.read(target);
    if (body.contains("[[" + fromSlug + "|") || body.contains("(" + fromSlug + ".md)")) return;
    String backref = "- [[" + fromSlug + "|" + fromSlug + "]] ([" + fromSlug + "](../"
        + (target.getParent().getFileName()) + "/" + fromSlug + ".md))\n";
    if (body.contains("## See Also")) {
      String updated = body.replaceFirst("## See Also\\s*\\n", "## See Also\n\n" + backref);
      WikiFS.write(target, updated);
    } else {
      WikiFS.append(target, "\n## See Also\n\n" + backref);
    }
  }

  private static String truncate(String s, int max) {
    return (s == null || s.length() <= max) ? s : s.substring(0, max) + "\n...[truncated]";
  }

  private static void safe(Runnable r) { try { r.run(); } catch (Exception ignored) {} }
}
