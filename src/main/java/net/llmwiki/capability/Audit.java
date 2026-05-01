package net.llmwiki.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.llmwiki.core.*;
import net.llmwiki.fs.*;
import net.llmwiki.model.WikiContext;
import net.llmwiki.prelude.WikiResolver;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * /wiki:audit scan [--artifact path] [--project slug] [--wiki-only] [--outputs-only] [--quick] [--fresh]
 * /wiki:audit report
 *
 * Implements:
 *   - derive-audit-scope
 *   - reuse-or-rerun-librarian (always re-runs librarian for simplicity)
 *   - scan-output-drift (deterministic file dependency check)
 *   - classify-output-verdict
 *   - identify-claims-under-scrutiny (LLM, when escalated)
 *   - render-claim-verdict (LLM, support+attack agents are STUBBED)
 *   - classify-provenance-state
 *   - write-audit-reports
 */
public final class Audit {

  private static final Set<String> VALUED = Set.of("wiki", "artifact", "project");

  private final Context ctx;
  private final Librarian librarian;
  public Audit(Context ctx) {
    this.ctx = ctx;
    this.librarian = new Librarian(ctx);
  }

  public String run(String args) {
    ArgParse.Args a = ArgParse.parse(args, VALUED);
    String sub = (a.first() != null && !a.first().startsWith("--")) ? a.first() : "scan";

    WikiContext wiki = new WikiResolver().resolve(ctx.hub, a.get("wiki"), a.is("local"));
    if (!wiki.exists()) return "audit: no wiki found.";

    return switch (sub) {
      case "report" -> showReport(wiki);
      default -> scan(wiki, a);
    };
  }

  private String scan(WikiContext wiki, ArgParse.Args a) {
    Path auditDir = wiki.root().resolve(".audit");
    WikiFS.mkdirs(auditDir);
    String auditId = Instant.now().toString();

    Logs.appendSessionEvent(wiki.root(), Map.of(
        "ts", Instant.now().toString(),
        "command", "audit",
        "phase", "start",
        "event", "audit_started",
        "scope", scopeLabel(a)));

    // Pass 1: Wiki content (delegate to librarian unless --outputs-only)
    String wikiPassSummary = "(skipped)";
    if (!a.is("outputs-only")) {
      try { wikiPassSummary = librarian.run("scan" + (a.has("wiki") ? " --wiki " + a.get("wiki") : "")); }
      catch (Exception e) { wikiPassSummary = "librarian failed: " + e.getMessage(); }
    }

    // Pass 2: Output drift
    List<Map<String, Object>> outputFindings = a.is("wiki-only")
        ? List.of()
        : scanOutputDrift(wiki, a);

    Logs.appendSessionEvent(wiki.root(), Map.of(
        "ts", Instant.now().toString(),
        "command", "audit",
        "phase", "scan",
        "event", "audit_output_scan_completed",
        "outputs_scanned", outputFindings.size()));

    // Pass 3: Truth escalation (LLM-driven; AgentExecutor STUB means we render verdicts off local data only)
    List<Map<String, Object>> investigations = new ArrayList<>();
    if (!a.is("quick")) {
      for (var f : outputFindings) {
        if (shouldEscalate(f)) {
          var claims = identifyClaims(f);
          for (var claim : claims) {
            var verdict = renderClaimVerdict(claim, f);
            investigations.add(Map.of("claim", claim, "verdict", verdict, "artifact", f.get("path")));
          }
        }
      }
    }
    Logs.appendSessionEvent(wiki.root(), Map.of(
        "ts", Instant.now().toString(),
        "command", "audit",
        "phase", "research",
        "event", "audit_truth_escalation_completed",
        "investigations", investigations.size()));

    // Pass 4: Provenance
    String provenance = classifyProvenance(wiki);

    // Pass 5: Write reports
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("audit_id", auditId);
    doc.put("scope", scopeLabel(a));
    doc.put("summary", Map.of(
        "outputs_scanned", outputFindings.size(),
        "drifted_outputs", outputFindings.stream().filter(f -> "drifted".equals(f.get("verdict"))).count(),
        "research_escalations", investigations.size(),
        "provenance_state", provenance));
    doc.put("outputs", outputFindings);
    doc.put("investigations", investigations);
    doc.put("provenance", Map.of("state", provenance));
    WikiFS.writeAtomic(auditDir.resolve("scan-results.json"), Json.stringify(doc));
    String report = renderReport(doc);
    WikiFS.write(auditDir.resolve("REPORT.md"), report);
    Logs.appendActivity(auditDir, "scan",
        "scope=" + scopeLabel(a) + ", outputs=" + outputFindings.size()
            + ", escalations=" + investigations.size());
    Logs.appendActivity(wiki.root(), "audit",
        "scope=" + scopeLabel(a) + ", outputs=" + outputFindings.size()
            + ", escalations=" + investigations.size());
    Logs.appendSessionEvent(wiki.root(), Map.of(
        "ts", Instant.now().toString(),
        "command", "audit",
        "phase", "finish",
        "event", "audit_completed"));
    return report;
  }

  private String showReport(WikiContext wiki) {
    Path r = wiki.root().resolve(".audit/REPORT.md");
    if (!WikiFS.exists(r)) return "No audit report found. Run /audit scan first.";
    return WikiFS.read(r);
  }

  // ----- Pass 2 -----

  private List<Map<String, Object>> scanOutputDrift(WikiContext wiki, ArgParse.Args a) {
    List<Path> outputs;
    if (a.has("artifact")) {
      Path p = wiki.root().resolve(a.get("artifact"));
      outputs = WikiFS.exists(p) ? List.of(p) : List.of();
    } else if (a.has("project")) {
      Path projDir = wiki.output().resolve("projects").resolve(a.get("project"));
      outputs = WikiFS.listMdRecursive(projDir).stream()
          .filter(p -> !p.getFileName().toString().equals("WHY.md"))
          .toList();
    } else {
      outputs = WikiFS.listMdRecursive(wiki.output()).stream()
          .filter(p -> !p.getFileName().toString().equals("_index.md"))
          .filter(p -> !p.getFileName().toString().equals("WHY.md"))
          .toList();
    }

    List<Map<String, Object>> findings = new ArrayList<>();
    for (Path p : outputs) {
      var fm = Frontmatter.parse(WikiFS.read(p)).fields;
      List<String> sources = Frontmatter.listField(fm, "sources");
      String generated = Frontmatter.stringField(fm, "generated", null);
      List<String> flags = new ArrayList<>();
      List<Map<String, Object>> drifted = new ArrayList<>();
      List<String> broken = new ArrayList<>();

      if (sources.isEmpty()) flags.add("missing-provenance");
      for (String s : sources) {
        Path dep = wiki.root().resolve(s);
        if (!WikiFS.exists(dep)) {
          flags.add("broken-source-ref");
          broken.add(s);
          continue;
        }
        var depFm = Frontmatter.parse(WikiFS.read(dep)).fields;
        String depUpdated = Frontmatter.stringField(depFm, "updated",
            Frontmatter.stringField(depFm, "ingested", null));
        if (generated != null && depUpdated != null && depUpdated.compareTo(generated) > 0) {
          flags.add("drifted-dependency");
          drifted.add(Map.of("path", s, "reason", "updated " + depUpdated + " > generated " + generated));
        }
      }

      String verdict = classifyVerdict(flags);
      Map<String, Object> finding = new LinkedHashMap<>();
      finding.put("path", wiki.root().relativize(p).toString());
      finding.put("verdict", verdict);
      finding.put("flags", flags.stream().distinct().toList());
      finding.put("drifted_deps", drifted);
      finding.put("broken_deps", broken);
      findings.add(finding);
    }
    return findings;
  }

  private static String classifyVerdict(List<String> flags) {
    if (flags.contains("broken-source-ref") || flags.contains("missing-provenance")) return "provenance-gap";
    if (flags.contains("drifted-dependency")) return "drifted";
    return "clean";
  }

  private static boolean shouldEscalate(Map<String, Object> f) {
    String v = (String) f.get("verdict");
    return "drifted".equals(v) || "provenance-gap".equals(v);
  }

  // ----- Pass 3 -----

  private List<Map<String, Object>> identifyClaims(Map<String, Object> finding) {
    String prompt = Templates.expand(Prompts.IDENTIFY_CLAIMS_UNDER_SCRUTINY,
        "artifact_path", finding.get("path"),
        "excerpt", "(excerpt unavailable in stub)",
        "triggers_json", Json.stringify(finding.get("flags")));
    try {
      JsonNode reply = Json.parse(ctx.llm.call(prompt));
      List<Map<String, Object>> claims = new ArrayList<>();
      if (reply.path("claims").isArray()) {
        for (JsonNode c : reply.path("claims")) {
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("text", c.path("text").asText(""));
          m.put("stake", c.path("stake").asText("medium"));
          claims.add(m);
        }
      }
      return claims;
    } catch (Exception e) {
      return List.of();
    }
  }

  private Map<String, Object> renderClaimVerdict(Map<String, Object> claim, Map<String, Object> finding) {
    // Without a real AgentExecutor, "support" and "attack" branches are empty.
    // We still ask the LLM to render an honest verdict from local-only evidence.
    ObjectNode findings = Json.obj();
    findings.putArray("support");
    findings.putArray("attack");
    findings.putArray("primary");

    String prompt = Templates.expand(Prompts.RENDER_CLAIM_VERDICT,
        "claim", claim.get("text"),
        "findings_json", Json.stringify(findings),
        "local_json", Json.stringify(finding));
    try {
      JsonNode v = Json.parse(ctx.llm.call(prompt));
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("verdict", v.path("verdict").asText("unresolved"));
      m.put("confidence", v.path("confidence").asText("low"));
      m.put("rationale", v.path("rationale_2_3_sentences").asText(""));
      return m;
    } catch (Exception e) {
      return Map.of("verdict", "unresolved", "confidence", "low", "rationale", "verdict rendering failed");
    }
  }

  // ----- Pass 4 -----

  private String classifyProvenance(WikiContext wiki) {
    boolean events = WikiFS.exists(wiki.root().resolve(".session-events.jsonl"));
    boolean checkpoint = WikiFS.exists(wiki.root().resolve(".session-checkpoint.json"));
    if (events) return "replayable";
    if (checkpoint) return "partial";
    return "missing";
  }

  // ----- report -----

  private String renderReport(Map<String, Object> doc) {
    StringBuilder b = new StringBuilder();
    b.append("# Audit Report — ").append(java.time.LocalDate.now()).append("\n\n");
    b.append("> Scope: ").append(doc.get("scope")).append("\n\n");
    b.append("## Summary\n");
    @SuppressWarnings("unchecked")
    Map<String, Object> s = (Map<String, Object>) doc.get("summary");
    s.forEach((k, v) -> b.append("- ").append(k).append(": ").append(v).append("\n"));
    b.append("\n## Output verdicts\n\n");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> outs = (List<Map<String, Object>>) doc.get("outputs");
    if (outs.isEmpty()) b.append("(none)\n");
    else for (var f : outs) {
      b.append("- **").append(f.get("path")).append("** -> ").append(f.get("verdict"));
      Object flags = f.get("flags");
      if (flags instanceof List<?> l && !l.isEmpty()) {
        b.append(" (").append(String.join(", ", l.stream().map(String::valueOf).toList())).append(")");
      }
      b.append("\n");
    }
    b.append("\n## Truth investigations\n\n");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> invs = (List<Map<String, Object>>) doc.get("investigations");
    if (invs.isEmpty()) b.append("(none escalated)\n");
    else for (var i : invs) {
      Map<?, ?> claim = (Map<?, ?>) i.get("claim");
      Map<?, ?> verdict = (Map<?, ?>) i.get("verdict");
      b.append("- **claim:** ").append(claim.get("text")).append("\n  -> ")
          .append(verdict.get("verdict")).append(" (").append(verdict.get("confidence")).append(")\n");
    }
    return b.toString();
  }

  private static String scopeLabel(ArgParse.Args a) {
    if (a.has("artifact")) return "artifact:" + a.get("artifact");
    if (a.has("project")) return "project:" + a.get("project");
    if (a.is("wiki-only")) return "wiki-only";
    if (a.is("outputs-only")) return "outputs-only";
    return "full";
  }
}
