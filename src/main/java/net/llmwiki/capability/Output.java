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
 * /wiki:output &lt;type&gt; [--topic] [--retardmax] [--with]
 *
 * Type-aware artifact generation. The implementation here covers `summary`
 * and `report` end-to-end; other types (slides/study-guide/timeline/
 * glossary/comparison) reuse the same pattern with type-specific prompts —
 * those are wired with placeholder calls and clear TODOs.
 */
public final class Output {

  private static final Set<String> VALUED = Set.of("wiki", "topic", "with", "project");
  private static final Set<String> KNOWN_TYPES = Set.of(
      "summary", "report", "study-guide", "slides", "timeline", "glossary", "comparison");

  private final Context ctx;
  public Output(Context ctx) { this.ctx = ctx; }

  public String run(String args) {
    ArgParse.Args a = ArgParse.parse(args, VALUED);
    if (a.positionals.isEmpty()) {
      return "output: supply a type. One of: " + String.join(", ", KNOWN_TYPES);
    }
    String type = a.positionals.get(0);
    if (!KNOWN_TYPES.contains(type)) {
      return "output: unknown type '" + type + "'. Allowed: " + String.join(", ", KNOWN_TYPES);
    }

    WikiContext wiki = new WikiResolver().resolve(ctx.hub, a.get("wiki"), a.is("local"));
    if (!wiki.exists()) return "output: no wiki found.";

    boolean retardmax = a.is("retardmax");
    String topic = a.get("topic");

    // Gather sources
    ArrayNode subject = Json.obj().putArray("_");
    List<Path> articles = retardmax || topic == null
        ? WikiFS.listMdRecursive(wiki.wiki())
        : WikiFS.grep(wiki.wiki(), topic);
    for (Path p : articles) {
      var fm = Frontmatter.parse(WikiFS.read(p));
      ObjectNode o = Json.obj();
      o.put("path", wiki.root().relativize(p).toString());
      o.put("title", Frontmatter.stringField(fm.fields, "title", ""));
      o.put("confidence", Frontmatter.stringField(fm.fields, "confidence", "medium"));
      o.put("content", truncate(fm.body, 6000));
      subject.add(o);
    }
    if (subject.size() == 0) return "output: no matching articles to draw from.";

    // Generate
    String prompt = switch (type) {
      case "summary" -> Templates.expand(Prompts.GENERATE_OUTPUT_SUMMARY,
          "subject_json", Json.stringify(subject),
          "craft_json", "[]",
          "mode", retardmax ? "retardmax" : "standard");
      case "report" -> Templates.expand(Prompts.GENERATE_OUTPUT_REPORT,
          "subject_json", Json.stringify(subject),
          "craft_json", "[]",
          "mode", retardmax ? "retardmax" : "standard");
      default -> Templates.expand(Prompts.GENERATE_OUTPUT_REPORT,    // fallback shape
          "subject_json", Json.stringify(subject),
          "craft_json", "[]",
          "mode", retardmax ? "retardmax" : "standard");
    };
    JsonNode reply = Json.parse(ctx.llm.call(prompt));

    // Render markdown
    String body = switch (type) {
      case "summary" -> renderSummary(reply);
      default -> renderReport(reply);
    };

    // Save
    String slug = Slugs.slugify(reply.path("title").asText(topic == null ? type : topic));
    Path target;
    if (a.has("project")) {
      target = wiki.output().resolve("projects").resolve(a.get("project"))
          .resolve(type + "-" + slug + "-" + Slugs.today() + ".md");
    } else {
      target = wiki.output().resolve(type + "-" + slug + "-" + Slugs.today() + ".md");
    }

    Map<String, Object> fm = new LinkedHashMap<>();
    fm.put("title", reply.path("title").asText(""));
    fm.put("type", type);
    List<String> sources = new ArrayList<>();
    if (reply.path("wiki_articles_used").isArray()) {
      reply.path("wiki_articles_used").forEach(n -> sources.add(n.asText()));
    } else {
      subject.forEach(o -> sources.add(o.path("path").asText()));
    }
    fm.put("sources", sources);
    fm.put("generated", LocalDate.now().toString());
    if (a.has("project")) fm.put("project", a.get("project"));

    WikiFS.write(target, Frontmatter.render(fm, body));
    safe(() -> Indexes.rebuild(wiki.output()));
    safe(() -> Indexes.writeMasterIndex(wiki.root(), wiki.root().getFileName().toString()));
    Logs.appendActivity(wiki.root(), "output",
        type + " -> " + wiki.root().relativize(target));
    return "Wrote " + type + " to " + target;
  }

  private String renderSummary(JsonNode reply) {
    StringBuilder b = new StringBuilder();
    b.append("# ").append(reply.path("title").asText("")).append("\n\n");
    if (!reply.path("subtitle").isMissingNode() && !reply.path("subtitle").isNull()) {
      b.append("> ").append(reply.path("subtitle").asText("")).append("\n\n");
    }
    if (!reply.path("tldr").isMissingNode()) {
      b.append("> ").append(reply.path("tldr").asText("")).append("\n\n");
    }
    appendSections(b, reply.path("sections"));
    return b.toString();
  }

  private String renderReport(JsonNode reply) {
    StringBuilder b = new StringBuilder();
    b.append("# ").append(reply.path("title").asText("")).append("\n\n");
    if (!reply.path("executive_summary").isMissingNode()) {
      b.append("## Executive Summary\n\n").append(reply.path("executive_summary").asText("")).append("\n\n");
    }
    appendSections(b, reply.path("sections"));
    return b.toString();
  }

  private static void appendSections(StringBuilder b, JsonNode sections) {
    if (!sections.isArray()) return;
    for (JsonNode s : sections) {
      String h = s.path("heading").asText("##");
      if (!h.startsWith("#")) h = "## " + h;
      b.append(h).append("\n\n").append(s.path("markdown").asText("")).append("\n\n");
    }
  }

  private static String truncate(String s, int max) {
    return (s == null || s.length() <= max) ? s : s.substring(0, max) + "\n...[truncated]";
  }

  private static void safe(Runnable r) { try { r.run(); } catch (Exception ignored) {} }
}
