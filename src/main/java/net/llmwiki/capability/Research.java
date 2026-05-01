package net.llmwiki.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.llmwiki.core.*;
import net.llmwiki.fs.*;
import net.llmwiki.model.WikiContext;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * /wiki:research &lt;topic|question&gt; [--mode thesis "claim"] [--new-topic] [--min-time 1h] [--plan]
 *
 * What's implemented here:
 *   - input-mode detection
 *   - scope-existing-knowledge (LLM)
 *   - dispatch-research-agent (STUBBED — calls AgentExecutor.dispatch)
 *   - dedup-source-list (deterministic)
 *   - score-source-credibility (LLM, per source)
 *   - select-top-sources (deterministic)
 *   - per-source ingest + a single compile sweep (delegates to Ingest + Compile)
 *   - generate-round-report (LLM)
 *   - basic --min-time loop with reflect-across-rounds
 *
 * Wire AgentExecutor.STUB by default — capability tells the user what to do
 * if no agent runtime is configured.
 */
public final class Research {

  private static final Set<String> VALUED = Set.of(
      "wiki", "new-topic", "sources", "min-time", "mode", "project");

  private final Context ctx;
  private final Init init;
  private final Ingest ingest;
  private final Compile compile;
  private final Thesis thesis;

  public Research(Context ctx) {
    this.ctx = ctx;
    this.init = new Init(ctx);
    this.ingest = new Ingest(ctx);
    this.compile = new Compile(ctx);
    this.thesis = new Thesis(ctx);
  }

  public String run(String args) {
    ArgParse.Args a = ArgParse.parse(args, VALUED);
    if (a.positionals.isEmpty()) return "research: please supply a topic, question, or --mode thesis \"claim\".";

    String input = a.join();
    String detectedMode = detectMode(input, a.get("mode"));
    if ("THESIS".equals(detectedMode)) {
      return thesis.run(args);
    }

    WikiContext wiki = init.resolveOrCreate(a.get("wiki"), a.is("local"), a.get("new-topic"), input);
    if (!wiki.exists()) {
      return "research: no wiki found. Use --wiki <name> or --new-topic.";
    }

    boolean question = "QUESTION".equals(detectedMode);
    String minTime = a.get("min-time");
    int totalRounds = 0, cumulativeSources = 0, cumulativeArticles = 0;

    if (minTime != null) writeSession(wiki, input, "single", minTime);

    long startNanos = System.nanoTime();
    long budgetMs = parseDuration(minTime);
    String currentTopic = input;
    List<String> roundReports = new ArrayList<>();

    while (true) {
      totalRounds++;
      JsonNode scope = scopeExistingKnowledge(wiki, currentTopic);
      JsonNode agentSwarm = dispatchAgents(currentTopic, scope, a);

      List<JsonNode> sources = arrayItems(agentSwarm.path("sources"));
      sources = dedup(sources);
      List<JsonNode> scored = scoreCredibility(sources);
      List<JsonNode> selected = selectTop(scored, parseInt(a.get("sources"), 5));

      // ingest + compile for each selected source's content_markdown
      int ingestedCount = 0;
      for (JsonNode src : selected) {
        try {
          String url = src.path("url").asText("");
          if (!url.isEmpty()) {
            ingest.run(quote(url) + " --type articles" + wikiArg(a));
            ingestedCount++;
          }
        } catch (Exception ignored) {}
      }
      String compileSummary = "";
      if (ingestedCount > 0) {
        try { compileSummary = compile.run(wikiArg(a).trim()); } catch (Exception ignored) {}
      }
      cumulativeSources += ingestedCount;

      // Round report
      Map<String, Object> details = Map.of(
          "agents_launched", agentSwarm.path("agent_roles"),
          "sources_found", sources.size(),
          "sources_ingested", ingestedCount,
          "compile_summary", compileSummary);
      JsonNode report;
      try {
        String prompt = Templates.expand(Prompts.GENERATE_ROUND_REPORT,
            "topic", currentTopic, "round", totalRounds,
            "details_json", Json.stringify(details));
        report = Json.parse(ctx.llm.call(prompt));
      } catch (Exception e) {
        report = Json.parse("{\"progress_score\":0,\"remaining_gaps\":[]}");
      }
      roundReports.add(report.toPrettyString());

      Logs.appendActivity(wiki.root(), "research",
          "\"" + currentTopic + "\" -> " + ingestedCount + " sources ingested (round " + totalRounds + ")");

      if (minTime == null) break;
      long elapsed = (System.nanoTime() - startNanos) / 1_000_000;
      if (elapsed >= budgetMs) break;
      String termination = report.path("termination_recommendation").asText("continue");
      if ("early_complete".equals(termination)) break;

      // Reflect, pick next round's gaps
      JsonNode gaps = report.path("remaining_gaps");
      if (gaps.size() == 0) break;
      currentTopic = gaps.get(0).path("gap").asText(currentTopic);
    }

    // Question mode: produce a playbook
    if (question) {
      try { generatePlaybook(wiki, input); } catch (Exception ignored) {}
    }

    if (minTime != null) deleteSession(wiki);

    return "Research complete. Rounds: " + totalRounds + ", sources ingested: " + cumulativeSources
        + ", articles compiled: " + cumulativeArticles
        + (roundReports.isEmpty() ? "" : "\n\n" + roundReports.get(roundReports.size() - 1));
  }

  // ----- transitions -----

  private String detectMode(String input, String explicitMode) {
    if (explicitMode != null && !explicitMode.isBlank()) return "THESIS";
    String s = input.toLowerCase().trim();
    if (s.matches(".*\\b(prove that|is it true that|verify|test the claim|test the hypothesis)\\b.*")) {
      return "THESIS";
    }
    if (s.contains("?") || s.startsWith("what") || s.startsWith("why") || s.startsWith("how")
        || s.startsWith("when") || s.startsWith("where") || s.startsWith("who")) return "QUESTION";
    return "TOPIC";
  }

  private JsonNode scopeExistingKnowledge(WikiContext wiki, String topic) {
    String master = readMaster(wiki);
    String prompt = Templates.expand(Prompts.SCOPE_EXISTING_KNOWLEDGE,
        "master_index", master,
        "existing_json", "[]",
        "topic", topic);
    try { return Json.parse(ctx.llm.call(prompt)); }
    catch (Exception e) { return Json.parse("{\"existing_coverage_summary\":\"\",\"gaps\":[],\"search_angles\":[]}"); }
  }

  /** Stubbed agent swarm: each role is dispatched via AgentExecutor.STUB until wired in. */
  private JsonNode dispatchAgents(String topic, JsonNode scope, ArgParse.Args a) {
    boolean deep = a.is("deep");
    boolean retardmax = a.is("retardmax");
    List<String> roles = new ArrayList<>(List.of("Academic", "Technical", "Applied", "News/Trends", "Contrarian"));
    if (deep || retardmax) roles.addAll(List.of("Historical", "Adjacent", "Data/Stats"));
    if (retardmax) roles.addAll(List.of("Rabbit Hole 1", "Rabbit Hole 2"));

    ObjectNode aggregate = Json.obj();
    ArrayNode aggregatedSources = aggregate.putArray("sources");
    ArrayNode rolesArr = aggregate.putArray("agent_roles");

    for (String role : roles) {
      rolesArr.add(role);
      String contextJson = Json.stringify(Map.of(
          "topic", topic,
          "role", role,
          "scope", scope));
      try {
        String reply = ctx.agents.dispatch("research:" + role.toLowerCase(), contextJson);
        JsonNode r = Json.parse(reply);
        if (r.path("sources").isArray()) for (JsonNode s : r.path("sources")) aggregatedSources.add(s);
      } catch (AgentExecutor.NotImplementedYet ex) {
        // no-op: agentic dispatch unavailable. Continue with empty source list.
      } catch (Exception ignored) {}
    }
    return aggregate;
  }

  private List<JsonNode> dedup(List<JsonNode> sources) {
    Map<String, JsonNode> byUrl = new LinkedHashMap<>();
    for (JsonNode s : sources) {
      String url = s.path("url").asText("");
      if (url.isEmpty()) continue;
      byUrl.merge(url, s, (a, b) -> b.path("quality_score").asInt(0) > a.path("quality_score").asInt(0) ? b : a);
    }
    return new ArrayList<>(byUrl.values());
  }

  private List<JsonNode> scoreCredibility(List<JsonNode> sources) {
    List<JsonNode> out = new ArrayList<>();
    for (JsonNode s : sources) {
      try {
        String prompt = Templates.expand(Prompts.SCORE_SOURCE_CREDIBILITY,
            "source_json", Json.stringify(s));
        JsonNode score = Json.parse(ctx.llm.call(prompt));
        ObjectNode merged = (ObjectNode) s.deepCopy();
        merged.set("credibility", score);
        out.add(merged);
      } catch (Exception e) {
        ObjectNode merged = (ObjectNode) s.deepCopy();
        merged.putObject("credibility").put("tier", "medium").put("credibility_score", 2);
        out.add(merged);
      }
    }
    return out;
  }

  private List<JsonNode> selectTop(List<JsonNode> scored, int n) {
    return scored.stream()
        .filter(s -> !"reject".equals(s.path("credibility").path("tier").asText("medium")))
        .sorted(Comparator.<JsonNode>comparingInt(s ->
            s.path("credibility").path("credibility_score").asInt(0)
            * Math.max(1, s.path("quality_score").asInt(1))).reversed())
        .limit(n)
        .toList();
  }

  private void generatePlaybook(WikiContext wiki, String question) {
    String prompt = Templates.expand(Prompts.GENERATE_QUESTION_PLAYBOOK,
        "question", question, "subq_json", "[]", "articles_json", "[]");
    JsonNode reply = Json.parse(ctx.llm.call(prompt));
    Path out = wiki.output().resolve("playbook-" + Slugs.slugify(question) + "-" + Slugs.today() + ".md");
    Map<String, Object> fm = new LinkedHashMap<>();
    fm.put("title", reply.path("title").asText(question));
    fm.put("type", "playbook");
    fm.put("generated", LocalDate.now().toString());
    StringBuilder body = new StringBuilder("# ").append(reply.path("title").asText(question)).append("\n\n");
    if (reply.path("sections").isArray()) {
      for (JsonNode s : reply.path("sections")) {
        body.append(s.path("heading").asText("##")).append("\n\n")
            .append(s.path("markdown").asText("")).append("\n\n");
      }
    }
    WikiFS.write(out, Frontmatter.render(fm, body.toString()));
  }

  // ----- session helpers -----

  private void writeSession(WikiContext wiki, String topic, String mode, String budget) {
    Map<String, Object> session = new LinkedHashMap<>();
    session.put("session_id", Instant.now().toString());
    session.put("topic", topic);
    session.put("mode", mode);
    session.put("start_time", Instant.now().toString());
    session.put("min_time_budget", budget);
    session.put("current_round", 1);
    session.put("status", "in_progress");
    WikiFS.writeAtomic(wiki.root().resolve(".research-session.json"), Json.stringify(session));
    Logs.appendSessionEvent(wiki.root(), Map.of(
        "ts", Instant.now().toString(),
        "command", "research",
        "phase", "start",
        "event", "research_started",
        "topic", topic,
        "min_time_budget", budget));
  }

  private void deleteSession(WikiContext wiki) {
    Logs.appendSessionEvent(wiki.root(), Map.of(
        "ts", Instant.now().toString(),
        "command", "research",
        "phase", "finish",
        "event", "research_completed"));
    WikiFS.delete(wiki.root().resolve(".research-session.json"));
  }

  private static long parseDuration(String s) {
    if (s == null) return 0;
    if (s.endsWith("h")) return Long.parseLong(s.substring(0, s.length() - 1)) * 3600_000L;
    if (s.endsWith("m")) return Long.parseLong(s.substring(0, s.length() - 1)) * 60_000L;
    return Long.parseLong(s) * 1000L;
  }

  private static int parseInt(String s, int def) {
    if (s == null) return def;
    try { return Integer.parseInt(s); } catch (Exception e) { return def; }
  }

  private static List<JsonNode> arrayItems(JsonNode arr) {
    List<JsonNode> out = new ArrayList<>();
    if (arr != null && arr.isArray()) arr.forEach(out::add);
    return out;
  }

  private static String quote(String s) { return "\"" + s.replace("\"", "\\\"") + "\""; }

  private static String wikiArg(ArgParse.Args a) {
    StringBuilder s = new StringBuilder();
    if (a.has("wiki")) s.append(" --wiki ").append(a.get("wiki"));
    if (a.is("local")) s.append(" --local");
    return s.toString();
  }

  private String readMaster(WikiContext wiki) {
    Path master = wiki.root().resolve("_index.md");
    if (!WikiFS.exists(master)) return "";
    String body = WikiFS.read(master);
    return body.length() > 8000 ? body.substring(0, 8000) : body;
  }
}
