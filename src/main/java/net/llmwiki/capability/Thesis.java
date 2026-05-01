package net.llmwiki.capability;

import com.fasterxml.jackson.databind.JsonNode;
import net.llmwiki.core.*;
import net.llmwiki.fs.*;
import net.llmwiki.model.WikiContext;
import net.llmwiki.prelude.WikiResolver;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * /wiki:research --mode thesis "claim"
 *
 * Implements the thesis-specific deltas:
 *   - decompose-thesis (LLM)
 *   - create-thesis-file
 *   - dispatch-thesis-agent (STUB)
 *   - update-thesis-evidence-tables (LLM)
 *   - render-thesis-verdict (LLM)
 *   - apply-verdict-edit
 *
 * Most of the agent swarm + scoring is shared with Research; here we focus
 * on the verdict-rendering path. Wire a real AgentExecutor to enable the
 * full multi-round flow.
 */
public final class Thesis {

  private static final Set<String> VALUED = Set.of("wiki", "new-topic", "min-time", "mode");

  private final Context ctx;
  public Thesis(Context ctx) { this.ctx = ctx; }

  public String run(String args) {
    ArgParse.Args a = ArgParse.parse(args, VALUED);
    String thesisStatement = a.get("mode") != null
        ? extractClaim(a.raw)
        : a.join();
    if (thesisStatement == null || thesisStatement.isBlank()) {
      return "thesis: please supply a claim. Example: /thesis \"X causes Y\"";
    }

    WikiContext wiki = new WikiResolver().resolve(ctx.hub, a.get("wiki"), a.is("local"));
    if (!wiki.exists()) return "thesis: no wiki found. Use --wiki <name> or run /init first.";

    JsonNode decomp;
    try {
      String prompt = Templates.expand(Prompts.DECOMPOSE_THESIS, "thesis", thesisStatement);
      decomp = Json.parse(ctx.llm.call(prompt));
    } catch (Exception e) {
      return "thesis decomposition failed: " + e.getMessage();
    }

    String falsification = decomp.path("falsification_criteria").asText("");
    if (falsification.isBlank()) {
      return "thesis: the claim has no falsification criteria. Refine the thesis and try again.";
    }

    Path thesisFile = createThesisFile(wiki, thesisStatement, decomp);

    // Multi-round loop is stubbed here — wire AgentExecutor + ingest + compile
    // following the same pattern as Research to do the full flow.
    if (ctx.agents == AgentExecutor.STUB) {
      return "Thesis file created: " + thesisFile + "\n"
          + "Multi-agent dispatch is not yet wired (AgentExecutor stub).\n"
          + "The decomposition has been saved; provide an AgentExecutor to run the for/against swarm.";
    }

    // Render a verdict from whatever evidence is already in the file.
    JsonNode verdict;
    try {
      String prompt = Templates.expand(Prompts.RENDER_THESIS_VERDICT,
          "thesis", thesisStatement,
          "evidence_json", "{}",
          "rounds_json", "[]");
      verdict = Json.parse(ctx.llm.call(prompt));
    } catch (Exception e) {
      return "Thesis file created: " + thesisFile + " (verdict rendering failed)";
    }
    applyVerdict(thesisFile, verdict);
    Logs.appendActivity(wiki.root(), "thesis", "verdict for \"" + thesisStatement + "\" -> "
        + verdict.path("verdict").asText("pending"));
    return "Thesis: " + thesisStatement + "\nVerdict: " + verdict.path("verdict").asText("pending")
        + " (confidence " + verdict.path("confidence").asText("low") + ")\n"
        + "File: " + thesisFile;
  }

  private Path createThesisFile(WikiContext wiki, String thesis, JsonNode decomp) {
    String slug = Slugs.slugify(thesis);
    Path target = wiki.wiki().resolve("theses").resolve(slug + ".md");
    if (WikiFS.exists(target)) return target;

    Map<String, Object> fm = new LinkedHashMap<>();
    fm.put("title", "Thesis: " + thesis);
    fm.put("type", "thesis");
    fm.put("status", "investigating");
    fm.put("created", LocalDate.now().toString());
    fm.put("updated", LocalDate.now().toString());
    fm.put("verdict", "pending");
    fm.put("confidence", "pending");
    fm.put("core_claim", decomp.path("core_claim").asText(""));
    fm.put("key_variables", varsList(decomp));
    fm.put("falsification", decomp.path("falsification_criteria").asText(""));

    String body = """
        # Thesis: %s

        ## Core Claim
        %s

        ## Key Variables
        %s

        ## Testable Prediction
        %s

        ## Falsification Criteria
        %s

        ## Evidence For
        (populated during research)

        ## Evidence Against
        (populated during research)

        ## Nuances & Caveats
        (populated during research)

        ## Verdict
        **Status**: Investigating
        """.formatted(
            thesis,
            decomp.path("core_claim").asText(""),
            renderList(varsList(decomp)),
            decomp.path("testable_prediction").asText(""),
            decomp.path("falsification_criteria").asText(""));
    WikiFS.write(target, Frontmatter.render(fm, body));
    return target;
  }

  private void applyVerdict(Path thesisFile, JsonNode verdict) {
    String body = WikiFS.read(thesisFile);
    String newSection = """
        ## Verdict
        **Status**: %s
        **Confidence**: %s
        **Summary**: %s
        **Strongest supporting evidence**:
        %s
        **Strongest opposing evidence**:
        %s
        **Key caveats**:
        %s
        **What would change this verdict**:
        %s
        **Suggested follow-up theses**:
        %s
        """.formatted(
            verdict.path("verdict").asText("pending"),
            verdict.path("confidence").asText("low"),
            verdict.path("summary_2_3_sentences").asText(""),
            renderJsonList(verdict.path("strongest_supporting_evidence")),
            renderJsonList(verdict.path("strongest_opposing_evidence")),
            renderJsonList(verdict.path("key_caveats")),
            renderJsonList(verdict.path("what_would_change_this_verdict")),
            renderJsonList(verdict.path("suggested_followup_theses")));
    String updated = body.replaceAll("(?s)## Verdict.*", newSection);
    WikiFS.write(thesisFile, updated);
  }

  // ----- helpers -----

  private static java.util.List<String> varsList(JsonNode decomp) {
    java.util.List<String> out = new java.util.ArrayList<>();
    if (decomp.path("key_variables").isArray()) {
      decomp.path("key_variables").forEach(n -> out.add(n.asText()));
    }
    return out;
  }

  private static String renderList(java.util.List<String> items) {
    StringBuilder b = new StringBuilder();
    for (String i : items) b.append("- ").append(i).append("\n");
    return b.toString();
  }

  private static String renderJsonList(JsonNode arr) {
    if (!arr.isArray()) return "";
    StringBuilder b = new StringBuilder();
    arr.forEach(n -> b.append("- ").append(n.asText()).append("\n"));
    return b.toString();
  }

  private static String extractClaim(String raw) {
    // Pull the value of "--mode thesis <claim>" (claim may be quoted)
    int idx = raw.indexOf("--mode thesis");
    if (idx < 0) return null;
    String tail = raw.substring(idx + "--mode thesis".length()).trim();
    if (tail.startsWith("\"")) {
      int close = tail.indexOf('"', 1);
      if (close > 0) return tail.substring(1, close);
    }
    return tail;
  }
}
