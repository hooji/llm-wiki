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
 * /wiki:plan &lt;goal&gt; [--format roadmap|rfc|adr|spec] [--quick] [--no-interview] [--no-research]
 *
 * Implementation status: Stage 1 (assemble-wiki-context), Stage 2 (interview
 * QUESTIONS — answers must be supplied via --answers), Stage 5 (roadmap
 * generation), Stage 6 (write file).
 *
 * Stage 3 (gap research) is stubbed because it requires WebSearch.
 */
public final class Plan {

  private static final Set<String> VALUED = Set.of("wiki", "with", "format", "answers", "project");

  private final Context ctx;
  public Plan(Context ctx) { this.ctx = ctx; }

  public String run(String args) {
    ArgParse.Args a = ArgParse.parse(args, VALUED);
    if (a.positionals.isEmpty()) return "plan: please supply a goal.";
    String goal = a.join();
    String format = a.getOrDefault("format", "roadmap");

    WikiContext wiki = new WikiResolver().resolve(ctx.hub, a.get("wiki"), a.is("local"));
    if (!wiki.exists()) return "plan: no wiki found.";

    // Stage 1: assemble-wiki-context
    ArrayNode articles = Json.obj().putArray("_");
    for (Path p : WikiFS.listMdRecursive(wiki.wiki())) {
      var fm = Frontmatter.parse(WikiFS.read(p));
      ObjectNode o = Json.obj();
      o.put("path", p.toString());
      o.put("title", Frontmatter.stringField(fm.fields, "title", ""));
      o.put("confidence", Frontmatter.stringField(fm.fields, "confidence", "medium"));
      o.put("content", truncate(fm.body, 6000));
      articles.add(o);
    }
    String ctxPrompt = Templates.expand(Prompts.ASSEMBLE_WIKI_CONTEXT,
        "goal", goal,
        "articles_json", Json.stringify(articles),
        "siblings_json", "[]",
        "with_json", "[]");
    JsonNode context;
    try { context = Json.parse(ctx.llm.call(ctxPrompt)); }
    catch (Exception e) { return "plan: context assembly failed: " + e.getMessage(); }

    // Stage 2: interview questions (printed; answers passed via --answers JSON)
    if (!a.is("no-interview") && !a.is("quick") && !a.has("answers")) {
      String iPrompt = Templates.expand(Prompts.GENERATE_INTERVIEW_QUESTIONS,
          "goal", goal, "summary", context.path("summary_for_user").asText(""));
      JsonNode questions = Json.parse(ctx.llm.call(iPrompt));
      StringBuilder out = new StringBuilder();
      out.append("Plan interview — answer with --answers <json>:\n\n");
      if (questions.path("questions").isArray()) {
        for (JsonNode q : questions.path("questions")) {
          out.append(q.path("id").asInt()).append(". ").append(q.path("question").asText())
              .append(" (").append(q.path("expected_answer_shape").asText("free-text")).append(")\n");
        }
      }
      out.append("\nThen rerun with: /plan \"" + goal + "\" --answers '<json>' --no-interview");
      return out.toString();
    }

    // Stage 5: generate plan
    Map<String, Object> userReqs = Map.of();
    if (a.has("answers")) {
      try { userReqs = Json.MAPPER.readValue(a.get("answers"), Map.class); }
      catch (Exception ignored) {}
    }
    Map<String, Object> synth = new LinkedHashMap<>();
    synth.put("goal", goal);
    synth.put("wiki_evidence", Map.of("from_context", context));
    synth.put("user_requirements", userReqs);
    synth.put("gap_fills", List.of());
    synth.put("constraints", List.of());
    synth.put("risks", List.of());

    String genPrompt = Templates.expand(Prompts.GENERATE_PLAN_DOCUMENT_ROADMAP,
        "goal", goal, "context_json", Json.stringify(synth));
    JsonNode plan = Json.parse(ctx.llm.call(genPrompt));

    // Stage 6: write
    Path target;
    if (a.has("project")) {
      Path projectDir = wiki.output().resolve("projects").resolve(a.get("project"));
      target = projectDir.resolve("plan-" + Slugs.slugify(goal) + "-" + Slugs.today() + ".md");
    } else {
      target = wiki.output().resolve("plan-" + Slugs.slugify(goal) + "-" + Slugs.today() + ".md");
    }
    Map<String, Object> fm = new LinkedHashMap<>();
    fm.put("title", "Plan: " + goal);
    fm.put("type", "plan");
    fm.put("format", format);
    fm.put("generated", LocalDate.now().toString());
    if (a.has("project")) fm.put("project", a.get("project"));

    String body = renderRoadmap(goal, plan);
    WikiFS.write(target, Frontmatter.render(fm, body));
    Logs.appendActivity(wiki.root(), "plan", "\"" + goal + "\" -> " + wiki.root().relativize(target));
    return "Plan written: " + target;
  }

  private String renderRoadmap(String goal, JsonNode plan) {
    StringBuilder b = new StringBuilder();
    b.append("# Plan: ").append(goal).append("\n\n");
    b.append("## Executive Summary\n\n").append(plan.path("executive_summary").asText("")).append("\n\n");

    if (plan.path("architecture_decisions").isArray() && plan.path("architecture_decisions").size() > 0) {
      b.append("## Architecture Decisions\n\n");
      int n = 1;
      for (JsonNode d : plan.path("architecture_decisions")) {
        b.append("### Decision ").append(n++).append(": ").append(d.path("title").asText("")).append("\n");
        b.append("**Decision**: ").append(d.path("decision").asText("")).append("\n");
        b.append("**Rationale**: ").append(d.path("rationale").asText("")).append("\n");
        b.append("**Consequences**: ").append(d.path("consequences").asText("")).append("\n\n");
      }
    }

    if (plan.path("phases").isArray() && plan.path("phases").size() > 0) {
      b.append("## Implementation Phases\n\n");
      for (JsonNode ph : plan.path("phases")) {
        b.append("### Phase ").append(ph.path("n").asInt()).append(": ")
            .append(ph.path("title").asText("")).append(" (effort: ")
            .append(ph.path("estimated_effort").asText("")).append(")\n");
        b.append("**Goal**: ").append(ph.path("goal").asText("")).append("\n\n");
        b.append("**Tasks**:\n");
        if (ph.path("tasks").isArray()) for (JsonNode t : ph.path("tasks")) b.append("- [ ] ").append(t.asText()).append("\n");
        b.append("\n**Validation**: ").append(ph.path("validation").asText("")).append("\n\n");
      }
    }

    if (plan.path("risks_table").isArray() && plan.path("risks_table").size() > 0) {
      b.append("## Risks & Mitigations\n\n| Risk | Source | Mitigation |\n|------|--------|------------|\n");
      for (JsonNode r : plan.path("risks_table")) {
        b.append("| ").append(r.path("risk").asText("")).append(" | ")
            .append(r.path("source").asText("")).append(" | ")
            .append(r.path("mitigation").asText("")).append(" |\n");
      }
    }

    if (plan.path("open_questions").isArray() && plan.path("open_questions").size() > 0) {
      b.append("\n## Open Questions\n\n");
      for (JsonNode q : plan.path("open_questions")) b.append("- ").append(q.asText()).append("\n");
    }
    return b.toString();
  }

  private static String truncate(String s, int max) {
    return (s == null || s.length() <= max) ? s : s.substring(0, max) + "\n...[truncated]";
  }
}
