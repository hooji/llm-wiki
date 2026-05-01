package net.llmwiki.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.llmwiki.core.*;
import net.llmwiki.fs.*;
import net.llmwiki.model.WikiContext;
import net.llmwiki.prelude.WikiResolver;

import java.nio.file.Path;
import java.util.*;

/**
 * /wiki:query &lt;question&gt; [--quick] [--deep] [--list] [--resume]
 */
public final class Query {

  private static final Set<String> VALUED = Set.of("wiki", "tag", "category", "with");

  private final Context ctx;
  public Query(Context ctx) { this.ctx = ctx; }

  public String run(String args) {
    ArgParse.Args a = ArgParse.parse(args, VALUED);

    WikiContext wiki = new WikiResolver().resolve(ctx.hub, a.get("wiki"), a.is("local"));
    if (!wiki.exists()) return "query: no wiki found. Run /init <topic> first.";

    if (a.is("resume")) return runResume(wiki, a);

    String question = a.join();
    if (question.isBlank()) return "query: please provide a question.";

    if (a.is("list")) return runList(wiki, question, a);
    if (a.is("quick")) return runQuick(wiki, question, a);
    if (a.is("deep")) return runDeep(wiki, question, a);
    return runStandard(wiki, question, a);
  }

  // ----- modes -----

  private String runStandard(WikiContext wiki, String question, ArgParse.Args a) {
    Indexes.readFresh(wiki.wiki());

    JsonNode catsReply;
    try {
      String prompt = Templates.expand(Prompts.PICK_RELEVANT_CATEGORIES,
          "question", question, "master_index", readMaster(wiki));
      catsReply = Json.parse(ctx.llm.call(prompt));
    } catch (Exception e) {
      catsReply = Json.parse("{\"relevant_categories\":[\"concepts\",\"topics\",\"references\"]}");
    }
    List<String> categories = arrayToStringList(catsReply.path("relevant_categories"));
    if (categories.isEmpty()) categories = List.of("concepts", "topics", "references");

    // collect candidate index entries
    ArrayNode catalog = Json.obj().putArray("_");
    for (String cat : categories) {
      Path d = wiki.wiki().resolve(cat);
      if (!WikiFS.exists(d)) continue;
      for (var row : Indexes.articleRows(d)) {
        ObjectNode r = Json.obj();
        r.put("path", String.valueOf(row.get("path")));
        r.put("title", String.valueOf(row.get("title")));
        r.put("summary", String.valueOf(row.get("summary")));
        var tags = row.get("tags");
        if (tags instanceof List<?> list) {
          ArrayNode arr = r.putArray("tags");
          list.forEach(t -> arr.add(String.valueOf(t)));
        }
        catalog.add(r);
      }
    }
    if (catalog.size() == 0) {
      return "query: this wiki has no compiled articles yet. Run /compile.";
    }

    JsonNode picked;
    try {
      String prompt = Templates.expand(Prompts.PICK_CANDIDATE_ARTICLES,
          "question", question, "articles_json", Json.stringify(catalog));
      picked = Json.parse(ctx.llm.call(prompt));
    } catch (Exception e) {
      picked = Json.parse("{\"candidates\":[]}");
    }

    // read picked articles in full
    ArrayNode articles = Json.obj().putArray("_");
    if (picked.path("candidates").isArray()) {
      for (JsonNode c : picked.path("candidates")) {
        Path p = Path.of(c.path("path").asText());
        if (!WikiFS.exists(p)) continue;
        var fm = Frontmatter.parse(WikiFS.read(p));
        ObjectNode o = Json.obj();
        o.put("path", p.toString());
        o.put("title", Frontmatter.stringField(fm.fields, "title", ""));
        o.put("confidence", Frontmatter.stringField(fm.fields, "confidence", "medium"));
        o.put("content", truncate(fm.body, 12000));
        articles.add(o);
      }
    }

    String prompt = Templates.expand(Prompts.SYNTHESIZE_ANSWER,
        "question", question,
        "articles_json", Json.stringify(articles),
        "sibling_json", "[]",
        "with_json", "[]");
    JsonNode ans;
    try { ans = Json.parse(ctx.llm.call(prompt)); }
    catch (Exception e) { return "query: synthesis failed: " + e.getMessage(); }

    Logs.appendActivity(wiki.root(), "query",
        "\"" + question + "\" -> answered from " + articles.size() + " articles (standard)");
    return formatAnswer(ans);
  }

  private String runQuick(WikiContext wiki, String question, ArgParse.Args a) {
    String master = readMaster(wiki);
    String prompt = Templates.expand(Prompts.ANSWER_FROM_INDEXES,
        "question", question,
        "entries_json", master);
    JsonNode ans = Json.parse(ctx.llm.call(prompt));
    Logs.appendActivity(wiki.root(), "query", "\"" + question + "\" -> answered from indexes (quick)");
    return formatAnswer(ans);
  }

  private String runDeep(WikiContext wiki, String question, ArgParse.Args a) {
    // For brevity, fall back to standard but include all articles + grep hits.
    Indexes.readFresh(wiki.wiki());
    ArrayNode articles = Json.obj().putArray("_");
    for (Path p : WikiFS.listMdRecursive(wiki.wiki())) {
      var fm = Frontmatter.parse(WikiFS.read(p));
      ObjectNode o = Json.obj();
      o.put("path", p.toString());
      o.put("title", Frontmatter.stringField(fm.fields, "title", ""));
      o.put("confidence", Frontmatter.stringField(fm.fields, "confidence", "medium"));
      o.put("content", truncate(fm.body, 8000));
      articles.add(o);
    }
    String prompt = Templates.expand(Prompts.SYNTHESIZE_ANSWER,
        "question", question,
        "articles_json", Json.stringify(articles),
        "sibling_json", "[]",
        "with_json", "[]");
    JsonNode ans = Json.parse(ctx.llm.call(prompt));
    Logs.appendActivity(wiki.root(), "query", "\"" + question + "\" -> answered (deep)");
    return formatAnswer(ans);
  }

  private String runList(WikiContext wiki, String query, ArgParse.Args a) {
    ArrayNode results = Json.obj().putArray("_");
    for (Path p : WikiFS.listMdRecursive(wiki.wiki())) {
      var fm = Frontmatter.parse(WikiFS.read(p)).fields;
      String body = WikiFS.read(p).toLowerCase();
      String title = Frontmatter.stringField(fm, "title", "");
      String summary = Frontmatter.stringField(fm, "summary", "");
      int matchCount = 0;
      String[] terms = query.toLowerCase().split("\\s+");
      String matchKind = "body";
      if (title.toLowerCase().contains(query.toLowerCase())) matchKind = "title";
      else if (summary.toLowerCase().contains(query.toLowerCase())) matchKind = "summary";
      for (String t : terms) if (body.contains(t)) matchCount++;
      if (matchCount == 0) continue;
      ObjectNode r = Json.obj();
      r.put("path", p.toString());
      r.put("title", title);
      r.put("summary", summary);
      r.put("match_kind", matchKind);
      r.put("match_count", matchCount);
      r.put("updated", Frontmatter.stringField(fm, "updated", ""));
      results.add(r);
    }
    if (results.size() == 0) return "list: no matches.";
    String prompt = Templates.expand(Prompts.RANK_SEARCH_RESULTS,
        "query", query, "results_json", Json.stringify(results));
    JsonNode ranked = Json.parse(ctx.llm.call(prompt));
    StringBuilder out = new StringBuilder("Search results for \"" + query + "\":\n\n");
    if (ranked.path("ranked").isArray()) {
      for (JsonNode r : ranked.path("ranked")) {
        out.append(r.path("rank").asInt()).append(". ")
           .append(r.path("path").asText()).append("\n   ")
           .append(r.path("reason").asText()).append("\n");
      }
    }
    Logs.appendActivity(wiki.root(), "query", "\"" + query + "\" -> list");
    return out.toString();
  }

  private String runResume(WikiContext wiki, ArgParse.Args a) {
    String name = wikiName(wiki);
    StringBuilder out = new StringBuilder();
    out.append("## Resume: ").append(name).append("\n\n");
    out.append("`").append(name).append("` booted from `").append(wiki.root()).append("`.\n\n");

    Path researchSession = wiki.root().resolve(".research-session.json");
    Path thesisSession = wiki.root().resolve(".thesis-session.json");
    if (WikiFS.exists(researchSession)) {
      out.append("**Interrupted research session detected:**\n");
      out.append(truncate(WikiFS.read(researchSession), 1500)).append("\n\n");
    } else if (WikiFS.exists(thesisSession)) {
      out.append("**Interrupted thesis session detected:**\n");
      out.append(truncate(WikiFS.read(thesisSession), 1500)).append("\n\n");
    } else {
      Path checkpoint = wiki.root().resolve(".session-checkpoint.json");
      if (WikiFS.exists(checkpoint)) {
        out.append("**Recent durable provenance:**\n");
        out.append(truncate(WikiFS.read(checkpoint), 1500)).append("\n\n");
      } else {
        out.append("No interrupted sessions.\n\n");
      }
    }

    out.append("**Recent activity** (last 10):\n");
    for (String entry : Logs.tailActivity(wiki.root(), 10)) out.append(entry).append("\n");

    Logs.appendActivity(wiki.root(), "query", "--resume briefing");
    return out.toString();
  }

  // ----- helpers -----

  private String readMaster(WikiContext wiki) {
    Path master = wiki.root().resolve("_index.md");
    if (!WikiFS.exists(master)) return "";
    return truncate(WikiFS.read(master), 8000);
  }

  private static String wikiName(WikiContext wiki) {
    Path cfg = wiki.root().resolve("config.md");
    if (WikiFS.exists(cfg)) {
      var fm = Frontmatter.parse(WikiFS.read(cfg)).fields;
      String t = Frontmatter.stringField(fm, "title", null);
      if (t != null && !t.isBlank()) return t;
    }
    return wiki.root().getFileName().toString();
  }

  private static List<String> arrayToStringList(JsonNode n) {
    List<String> out = new ArrayList<>();
    if (n != null && n.isArray()) n.forEach(x -> out.add(x.asText()));
    return out;
  }

  private static String formatAnswer(JsonNode ans) {
    StringBuilder out = new StringBuilder();
    out.append(ans.path("answer_markdown").asText("")).append("\n\n---\n");
    if (ans.path("sources_used").isArray() && ans.path("sources_used").size() > 0) {
      out.append("**Sources used:**\n");
      for (JsonNode s : ans.path("sources_used")) {
        out.append("- ").append(s.path("path").asText())
           .append(" (confidence: ").append(s.path("confidence").asText("medium")).append(")")
           .append(" — ").append(s.path("what_drawn").asText("")).append("\n");
      }
    }
    if (ans.path("knowledge_gaps").isArray() && ans.path("knowledge_gaps").size() > 0) {
      out.append("\n**Knowledge gaps:**\n");
      for (JsonNode g : ans.path("knowledge_gaps")) out.append("- ").append(g.asText()).append("\n");
    }
    return out.toString();
  }

  private static String truncate(String s, int max) {
    return (s == null || s.length() <= max) ? s : s.substring(0, max) + "\n...[truncated]";
  }
}
