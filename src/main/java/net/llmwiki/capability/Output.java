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
 * Type-aware artifact generation. Each type has its own prompt
 * (Prompts.GENERATE_OUTPUT_*) and its own renderer.
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

    // Gather subject articles
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

    // Optional --with craft wikis
    ArrayNode craft = loadCraftWikis(wiki, a);

    // Type-specific generation
    String prompt = promptFor(type, subject, craft, retardmax);
    JsonNode reply;
    try { reply = Json.parse(ctx.llm.call(prompt)); }
    catch (Exception e) { return "output: generation failed: " + e.getMessage(); }

    // Type-specific render
    String body = switch (type) {
      case "summary"     -> renderSummary(reply);
      case "report"      -> renderReport(reply);
      case "study-guide" -> renderStudyGuide(reply);
      case "slides"      -> renderSlides(reply);
      case "timeline"    -> renderTimeline(reply);
      case "glossary"    -> renderGlossary(reply);
      case "comparison"  -> renderComparison(reply);
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
    List<String> sources = collectSourcePaths(reply, subject);
    fm.put("sources", sources);
    fm.put("generated", LocalDate.now().toString());
    if (a.has("project")) fm.put("project", a.get("project"));
    if (a.has("with")) fm.put("with_wikis", List.of(a.get("with").split(",")));

    WikiFS.write(target, Frontmatter.render(fm, body));
    safe(() -> Indexes.rebuild(wiki.output()));
    safe(() -> Indexes.writeMasterIndex(wiki.root(), wiki.root().getFileName().toString()));
    Logs.appendActivity(wiki.root(), "output",
        type + " -> " + wiki.root().relativize(target));
    return "Wrote " + type + " to " + target;
  }

  // ---- prompt selection ----

  private String promptFor(String type, ArrayNode subject, ArrayNode craft, boolean retardmax) {
    String mode = retardmax ? "retardmax" : "standard";
    String tmpl = switch (type) {
      case "summary"     -> Prompts.GENERATE_OUTPUT_SUMMARY;
      case "report"      -> Prompts.GENERATE_OUTPUT_REPORT;
      case "study-guide" -> Prompts.GENERATE_OUTPUT_STUDY_GUIDE;
      case "slides"      -> Prompts.GENERATE_OUTPUT_SLIDES;
      case "timeline"    -> Prompts.GENERATE_OUTPUT_TIMELINE;
      case "glossary"    -> Prompts.GENERATE_OUTPUT_GLOSSARY;
      case "comparison"  -> Prompts.GENERATE_OUTPUT_COMPARISON;
      default            -> Prompts.GENERATE_OUTPUT_REPORT;
    };
    return Templates.expand(tmpl,
        "subject_json", Json.stringify(subject),
        "craft_json",   Json.stringify(craft),
        "mode",         mode);
  }

  // ---- renderers ----

  private String renderSummary(JsonNode r) {
    StringBuilder b = new StringBuilder();
    b.append("# ").append(r.path("title").asText("")).append("\n\n");
    if (r.hasNonNull("subtitle")) b.append("> ").append(r.path("subtitle").asText("")).append("\n\n");
    if (!r.path("tldr").isMissingNode()) b.append("> ").append(r.path("tldr").asText("")).append("\n\n");
    appendSections(b, r.path("sections"));
    return b.toString();
  }

  private String renderReport(JsonNode r) {
    StringBuilder b = new StringBuilder();
    b.append("# ").append(r.path("title").asText("")).append("\n\n");
    if (!r.path("executive_summary").isMissingNode()) {
      b.append("## Executive Summary\n\n").append(r.path("executive_summary").asText("")).append("\n\n");
    }
    appendSections(b, r.path("sections"));
    return b.toString();
  }

  private String renderStudyGuide(JsonNode r) {
    StringBuilder b = new StringBuilder();
    b.append("# ").append(r.path("title").asText("Study Guide")).append("\n\n");

    if (r.path("concepts").isArray() && r.path("concepts").size() > 0) {
      b.append("## Concepts\n\n");
      for (JsonNode c : r.path("concepts")) {
        b.append("### ").append(c.path("name").asText("")).append("\n");
        b.append(c.path("definition").asText("")).append("\n\n");
        if (c.path("key_relationships").isArray() && c.path("key_relationships").size() > 0) {
          b.append("**Relationships**:\n");
          for (JsonNode rel : c.path("key_relationships")) {
            b.append("- ").append(rel.path("kind").asText("related"))
                .append(" → ").append(rel.path("to").asText(""))
                .append(": ").append(rel.path("description").asText("")).append("\n");
          }
          b.append("\n");
        }
        String wikiLink = c.path("wiki_link").asText("");
        if (!wikiLink.isBlank()) b.append("Source: [").append(wikiLink).append("](../").append(wikiLink).append(")\n\n");
      }
    }

    if (r.path("questions").isArray() && r.path("questions").size() > 0) {
      b.append("## Questions & Answers\n\n");
      Map<String, List<JsonNode>> byDifficulty = new LinkedHashMap<>();
      byDifficulty.put("easy", new ArrayList<>());
      byDifficulty.put("medium", new ArrayList<>());
      byDifficulty.put("hard", new ArrayList<>());
      for (JsonNode q : r.path("questions")) {
        String d = q.path("difficulty").asText("medium");
        byDifficulty.computeIfAbsent(d, k -> new ArrayList<>()).add(q);
      }
      for (var e : byDifficulty.entrySet()) {
        if (e.getValue().isEmpty()) continue;
        b.append("### ").append(capitalize(e.getKey())).append("\n\n");
        int n = 1;
        for (JsonNode q : e.getValue()) {
          b.append(n++).append(". **").append(q.path("q").asText("")).append("**\n");
          b.append("   ").append(q.path("a").asText("")).append("\n\n");
        }
      }
    }
    return b.toString();
  }

  private String renderSlides(JsonNode r) {
    StringBuilder b = new StringBuilder();
    // Title slide
    b.append("---\nmarp: true\n---\n\n");
    b.append("# ").append(r.path("title").asText("")).append("\n");
    if (r.hasNonNull("subtitle")) b.append("\n").append(r.path("subtitle").asText("")).append("\n");
    b.append("\n---\n\n");

    if (r.path("slides").isArray()) {
      int idx = 0;
      int total = r.path("slides").size();
      for (JsonNode s : r.path("slides")) {
        idx++;
        b.append("## ").append(s.path("title").asText("")).append("\n\n");
        if (s.path("bullets").isArray()) {
          for (JsonNode bullet : s.path("bullets")) b.append("- ").append(bullet.asText("")).append("\n");
        }
        if (s.hasNonNull("speaker_notes")) {
          b.append("\n<!--\n").append(s.path("speaker_notes").asText("")).append("\n-->\n");
        }
        if (s.path("wiki_citations").isArray() && s.path("wiki_citations").size() > 0) {
          b.append("\n<small>");
          List<String> cites = new ArrayList<>();
          s.path("wiki_citations").forEach(c -> cites.add(c.asText()));
          b.append("Sources: ").append(String.join(", ", cites));
          b.append("</small>\n");
        }
        if (idx < total) b.append("\n---\n\n");
      }
    }
    return b.toString();
  }

  private String renderTimeline(JsonNode r) {
    StringBuilder b = new StringBuilder();
    b.append("# ").append(r.path("title").asText("Timeline")).append("\n\n");
    if (!r.path("scope").isMissingNode()) {
      b.append("> ").append(r.path("scope").asText("")).append("\n\n");
    }
    if (r.path("entries").isArray()) {
      List<JsonNode> entries = new ArrayList<>();
      r.path("entries").forEach(entries::add);
      entries.sort(Comparator.comparing(n -> n.path("date").asText("")));
      for (JsonNode e : entries) {
        String date = e.path("date").asText("");
        String event = e.path("event").asText("");
        String sig = e.path("significance").asText("");
        String src = e.path("source_article").asText("");
        b.append("## ").append(date).append(" — ").append(event).append("\n\n");
        if (!sig.isBlank()) b.append("> ").append(sig).append("\n\n");
        if (!src.isBlank()) b.append("Source: [").append(src).append("](../").append(src).append(")\n\n");
      }
    }
    return b.toString();
  }

  private String renderGlossary(JsonNode r) {
    StringBuilder b = new StringBuilder();
    b.append("# ").append(r.path("title").asText("Glossary")).append("\n\n");
    if (!r.path("scope").isMissingNode()) {
      b.append("> ").append(r.path("scope").asText("")).append("\n\n");
    }
    if (r.path("entries").isArray()) {
      List<JsonNode> entries = new ArrayList<>();
      r.path("entries").forEach(entries::add);
      entries.sort(Comparator.comparing(n -> n.path("term").asText("").toLowerCase()));
      for (JsonNode e : entries) {
        String term = e.path("term").asText("");
        b.append("### ").append(term).append("\n");
        if (e.path("aliases").isArray() && e.path("aliases").size() > 0) {
          List<String> a = new ArrayList<>();
          e.path("aliases").forEach(x -> a.add(x.asText()));
          b.append("*Also known as: ").append(String.join(", ", a)).append("*\n\n");
        }
        b.append(e.path("definition").asText("")).append("\n\n");
        if (e.path("see_also").isArray() && e.path("see_also").size() > 0) {
          List<String> a = new ArrayList<>();
          e.path("see_also").forEach(x -> a.add(x.asText()));
          b.append("**See also**: ").append(String.join(", ", a)).append("\n\n");
        }
        String src = e.path("source_article").asText("");
        if (!src.isBlank()) b.append("Source: [").append(src).append("](../").append(src).append(")\n\n");
      }
    }
    return b.toString();
  }

  private String renderComparison(JsonNode r) {
    StringBuilder b = new StringBuilder();
    b.append("# ").append(r.path("title").asText("Comparison")).append("\n\n");

    List<String> subjectNames = new ArrayList<>();
    List<String> subjectLinks = new ArrayList<>();
    if (r.path("subjects").isArray()) {
      for (JsonNode s : r.path("subjects")) {
        subjectNames.add(s.path("name").asText(""));
        String link = s.path("wiki_article").asText("");
        subjectLinks.add(link.isBlank() ? "" : "[" + s.path("name").asText("") + "](../" + link + ")");
      }
    }
    if (subjectNames.isEmpty()) {
      b.append(r.path("summary").asText("")).append("\n");
      return b.toString();
    }

    b.append("| Dimension | ").append(String.join(" | ", subjectNames)).append(" |\n");
    b.append("|-----------|");
    for (int i = 0; i < subjectNames.size(); i++) b.append("---|");
    b.append("\n");

    if (r.path("dimensions").isArray()) {
      for (JsonNode d : r.path("dimensions")) {
        String dim = d.path("dimension").asText("");
        b.append("| **").append(dim).append("** |");
        if (d.path("values_per_subject").isArray()) {
          for (JsonNode v : d.path("values_per_subject")) {
            b.append(" ").append(v.asText("").replace("|", "\\|").replace("\n", " ")).append(" |");
          }
        }
        b.append("\n");
        if (!d.path("notes").isMissingNode() && !d.path("notes").asText("").isBlank()) {
          b.append("|   *notes*  | ").append(d.path("notes").asText("")).append(" ");
          for (int i = 1; i < subjectNames.size(); i++) b.append("| ");
          b.append("|\n");
        }
      }
    }

    String summary = r.path("summary").asText("");
    if (!summary.isBlank()) b.append("\n## Summary\n\n").append(summary).append("\n");

    if (subjectLinks.stream().anyMatch(s -> !s.isBlank())) {
      b.append("\n## Sources\n\n");
      for (String l : subjectLinks) if (!l.isBlank()) b.append("- ").append(l).append("\n");
    }
    return b.toString();
  }

  // ---- helpers ----

  private static void appendSections(StringBuilder b, JsonNode sections) {
    if (!sections.isArray()) return;
    for (JsonNode s : sections) {
      String h = s.path("heading").asText("##");
      if (!h.startsWith("#")) h = "## " + h;
      b.append(h).append("\n\n").append(s.path("markdown").asText("")).append("\n\n");
    }
  }

  /** Load --with wiki articles (subject articles whose wiki name was passed) for craft context. */
  private ArrayNode loadCraftWikis(WikiContext primary, ArgParse.Args a) {
    ArrayNode out = Json.obj().putArray("_");
    if (!a.has("with") || ctx.hub == null) return out;
    for (String name : a.get("with").split(",")) {
      var sibling = new WikiResolver().resolve(ctx.hub, name.trim(), false);
      if (!sibling.exists()) continue;
      ArrayNode arts = Json.obj().putArray("_");
      for (Path p : WikiFS.listMdRecursive(sibling.wiki())) {
        var fm = Frontmatter.parse(WikiFS.read(p));
        ObjectNode o = Json.obj();
        o.put("path", sibling.root().relativize(p).toString());
        o.put("title", Frontmatter.stringField(fm.fields, "title", ""));
        o.put("content", truncate(fm.body, 4000));
        arts.add(o);
      }
      ObjectNode wrapper = Json.obj();
      wrapper.put("wiki", name.trim());
      wrapper.set("articles", arts);
      out.add(wrapper);
    }
    return out;
  }

  private static List<String> collectSourcePaths(JsonNode reply, ArrayNode subject) {
    List<String> sources = new ArrayList<>();
    if (reply.path("wiki_articles_used").isArray()) {
      reply.path("wiki_articles_used").forEach(n -> sources.add(n.asText()));
    } else {
      // Pull from type-specific shapes
      if (reply.path("concepts").isArray()) reply.path("concepts").forEach(c -> {
        String w = c.path("wiki_link").asText(""); if (!w.isBlank()) sources.add(w);
      });
      if (reply.path("entries").isArray()) reply.path("entries").forEach(e -> {
        String w = e.path("source_article").asText(""); if (!w.isBlank()) sources.add(w);
      });
      if (reply.path("subjects").isArray()) reply.path("subjects").forEach(s -> {
        String w = s.path("wiki_article").asText(""); if (!w.isBlank()) sources.add(w);
      });
      if (reply.path("slides").isArray()) reply.path("slides").forEach(s -> {
        if (s.path("wiki_citations").isArray())
          s.path("wiki_citations").forEach(c -> sources.add(c.asText()));
      });
      if (sources.isEmpty()) {
        subject.forEach(o -> sources.add(o.path("path").asText()));
      }
    }
    return sources.stream().distinct().toList();
  }

  private static String capitalize(String s) {
    return (s == null || s.isEmpty()) ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String truncate(String s, int max) {
    return (s == null || s.length() <= max) ? s : s.substring(0, max) + "\n...[truncated]";
  }

  private static void safe(Runnable r) { try { r.run(); } catch (Exception ignored) {} }
}
