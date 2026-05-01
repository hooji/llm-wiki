package net.llmwiki.capability;

import com.fasterxml.jackson.databind.JsonNode;
import net.llmwiki.core.*;
import net.llmwiki.fs.*;
import net.llmwiki.model.WikiContext;
import net.llmwiki.prelude.WikiResolver;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * /wiki:librarian scan
 *
 * Tier 1 (deterministic): staleness + quality-tier-1 scoring per article.
 * Tier 2 (LLM): full quality scoring for articles that escalate.
 */
public final class Librarian {

  private static final Set<String> VALUED = Set.of("wiki", "article", "passes");

  private final Context ctx;
  public Librarian(Context ctx) { this.ctx = ctx; }

  public String run(String args) {
    ArgParse.Args a = ArgParse.parse(args, VALUED);
    String sub = a.first() != null ? a.first() : "scan";
    WikiContext wiki = new WikiResolver().resolve(ctx.hub, a.get("wiki"), a.is("local"));
    if (!wiki.exists()) return "librarian: no wiki found.";

    return switch (sub) {
      case "report" -> showReport(wiki);
      case "scan", "" -> scan(wiki, a);
      default -> scan(wiki, a);
    };
  }

  // ----- scan -----

  private String scan(WikiContext wiki, ArgParse.Args a) {
    Path libDir = wiki.root().resolve(".librarian");
    WikiFS.mkdirs(libDir);

    int threshold = readThreshold(wiki);

    List<Path> articles;
    if (a.has("article")) {
      Path p = wiki.root().resolve(a.get("article"));
      articles = WikiFS.exists(p) ? List.of(p) : List.of();
    } else {
      articles = WikiFS.listMdRecursive(wiki.wiki());
    }

    if (articles.isEmpty()) return "librarian: no articles to scan.";

    String scanId = Instant.now().toString();
    var summary = new ScanSummary();
    Map<String, Map<String, Object>> results = new LinkedHashMap<>();

    for (Path p : articles) {
      var fm = Frontmatter.parse(WikiFS.read(p)).fields;
      var staleness = computeStaleness(wiki, fm);
      var qualityT1 = computeQualityTier1(fm, p);
      Map<String, Object> quality;
      int tier = 1;
      boolean escalate = staleness.score < threshold
          || "hot".equals(Frontmatter.stringField(fm, "volatility", "warm"))
          || qualityT1.depth <= 2;
      if (escalate) {
        tier = 2;
        quality = qualityTier2(p, fm, qualityT1);
      } else {
        quality = Map.of(
            "score", computeQualityComposite(qualityT1.depth, qualityT1.sourceQuality, 3, 3),
            "dimensions", Map.of("depth", qualityT1.depth, "source_quality", qualityT1.sourceQuality, "coherence", 3, "utility", 3),
            "flags", qualityT1.flags
        );
      }

      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("staleness", Map.of("score", staleness.score, "factors", staleness.factors));
      entry.put("quality", quality);
      entry.put("tier", tier);
      results.put(wiki.root().relativize(p).toString(), entry);

      summary.scanned++;
      if (staleness.score < threshold) summary.stale++;
      Object qScoreObj = ((Map<?, ?>) entry.get("quality")).get("score");
      int qScore = (qScoreObj instanceof Number n) ? n.intValue() : 0;
      if (qScore < 50) summary.lowQuality++;
      summary.totalStale += staleness.score;
      summary.totalQuality += qScore;
    }

    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("scan_id", scanId);
    doc.put("wiki", wikiName(wiki));
    doc.put("completed_at", Instant.now().toString());
    doc.put("passes", List.of("staleness", "quality"));
    doc.put("threshold", threshold);
    doc.put("summary", Map.of(
        "articles_scanned", summary.scanned,
        "stale_count", summary.stale,
        "low_quality_count", summary.lowQuality,
        "avg_staleness", summary.scanned == 0 ? 0 : summary.totalStale / summary.scanned,
        "avg_quality", summary.scanned == 0 ? 0 : summary.totalQuality / summary.scanned));
    doc.put("articles", results);
    WikiFS.writeAtomic(libDir.resolve("scan-results.json"), Json.stringify(doc));

    String report = renderReport(wikiName(wiki), summary, threshold, results);
    WikiFS.write(libDir.resolve("REPORT.md"), report);
    Logs.appendActivity(libDir, "scan",
        summary.scanned + " articles, " + summary.stale + " stale, " + summary.lowQuality + " low-quality");
    Logs.appendActivity(wiki.root(), "librarian",
        "scanned " + summary.scanned + " articles, " + summary.stale + " stale, " + summary.lowQuality + " low-quality");
    return report;
  }

  private String showReport(WikiContext wiki) {
    Path r = wiki.root().resolve(".librarian/REPORT.md");
    if (!WikiFS.exists(r)) return "No librarian report found. Run /librarian scan first.";
    return WikiFS.read(r);
  }

  // ----- per-article scoring -----

  private static final class StalenessResult {
    int score;
    Map<String, Integer> factors = new LinkedHashMap<>();
  }

  private StalenessResult computeStaleness(WikiContext wiki, Map<String, Object> fm) {
    String volatility = Frontmatter.stringField(fm, "volatility", "warm");
    int halfLife = switch (volatility) {
      case "hot" -> 30;
      case "cold" -> 365;
      default -> 90;
    };
    long verifiedDays = daysSince(Frontmatter.stringField(fm, "verified", null));
    long updatedDays = daysSince(Frontmatter.stringField(fm, "updated",
        Frontmatter.stringField(fm, "created", null)));

    List<String> sources = Frontmatter.listField(fm, "sources");
    int resolved = 0;
    long totalSourceAge = 0;
    int counted = 0;
    for (String s : sources) {
      Path sp = wiki.root().resolve(s);
      if (WikiFS.exists(sp)) {
        resolved++;
        var sfm = Frontmatter.parse(WikiFS.read(sp)).fields;
        long age = daysSince(Frontmatter.stringField(sfm, "ingested", null));
        if (age >= 0) { totalSourceAge += age; counted++; }
      }
    }
    long avgSourceAge = counted == 0 ? 0 : totalSourceAge / counted;

    int sourceFreshness = counted == 0 ? 0 : (int) Math.round(25 * Math.pow(0.5, (double) avgSourceAge / halfLife));
    int verification = verifiedDays < 0 ? 0 : (int) Math.round(25 * Math.pow(0.5, (double) verifiedDays / halfLife));
    int compilation = updatedDays < 0 ? 0 : (int) Math.round(25 * Math.pow(0.5, (double) updatedDays / halfLife));
    int integrity = sources.isEmpty() ? 0 : (int) Math.round(25.0 * resolved / sources.size());

    StalenessResult r = new StalenessResult();
    r.factors.put("source_freshness", sourceFreshness);
    r.factors.put("verification", verification);
    r.factors.put("compilation", compilation);
    r.factors.put("integrity", integrity);
    r.score = sourceFreshness + verification + compilation + integrity;
    return r;
  }

  private static final class QualityT1 {
    int depth;
    int sourceQuality;
    List<String> flags = new ArrayList<>();
  }

  private QualityT1 computeQualityTier1(Map<String, Object> fm, Path p) {
    QualityT1 q = new QualityT1();
    List<String> sources = Frontmatter.listField(fm, "sources");
    String body = WikiFS.read(p);
    int words = body.split("\\s+").length;
    int headings = (int) Arrays.stream(body.split("\\R")).filter(l -> l.startsWith("## ")).count();
    if (words < 200 || headings == 0) q.depth = 1;
    else if (words < 500 || headings <= 1) q.depth = 2;
    else if (words < 1000) q.depth = 3;
    else if (headings >= 3 && words >= 1500) q.depth = 4;
    else q.depth = 3;

    int sq = sources.size() == 0 ? 1 : (sources.size() >= 4 ? 4 : 3);
    q.sourceQuality = sq;

    if (q.depth <= 2) q.flags.add("thin-coverage");
    if (sources.size() == 1) q.flags.add("single-source");
    if (!body.contains("## See Also")) q.flags.add("no-see-also");
    if (Frontmatter.stringField(fm, "verified", null) == null) q.flags.add("unverified");
    return q;
  }

  private Map<String, Object> qualityTier2(Path p, Map<String, Object> fm, QualityT1 t1) {
    String body = WikiFS.read(p);
    String prompt = Templates.expand(Prompts.SCORE_QUALITY_TIER_2,
        "article", truncate(body, 30_000),
        "frontmatter", Frontmatter.stringify(fm),
        "confidences", Json.stringify(Frontmatter.listField(fm, "sources")));
    JsonNode reply;
    try { reply = Json.parse(ctx.llm.call(prompt)); }
    catch (Exception e) {
      return Map.of("score", computeQualityComposite(t1.depth, t1.sourceQuality, 3, 3),
          "dimensions", Map.of("depth", t1.depth, "source_quality", t1.sourceQuality, "coherence", 3, "utility", 3),
          "flags", t1.flags);
    }
    int depth = reply.path("depth").asInt(t1.depth);
    int sq = reply.path("source_quality").asInt(t1.sourceQuality);
    int co = reply.path("coherence").asInt(3);
    int ut = reply.path("utility").asInt(3);
    List<String> flags = new ArrayList<>(t1.flags);
    if (reply.path("flags").isArray()) reply.path("flags").forEach(n -> flags.add(n.asText()));
    return Map.of(
        "score", computeQualityComposite(depth, sq, co, ut),
        "dimensions", Map.of("depth", depth, "source_quality", sq, "coherence", co, "utility", ut),
        "flags", flags.stream().distinct().toList());
  }

  private static int computeQualityComposite(int depth, int sq, int co, int ut) {
    return (int) Math.round(((depth + sq + co + ut) / 4.0) * 20);
  }

  // ----- report -----

  private String renderReport(String wikiName, ScanSummary s, int threshold,
                              Map<String, Map<String, Object>> articles) {
    StringBuilder b = new StringBuilder();
    b.append("# Librarian Report — ").append(java.time.LocalDate.now()).append("\n\n");
    b.append("> Scanned ").append(s.scanned).append(" articles in ").append(wikiName).append(".\n\n");
    b.append("## Summary\n\n");
    b.append("| Metric | Value |\n|--------|-------|\n");
    b.append("| Articles scanned | ").append(s.scanned).append(" |\n");
    b.append("| Below staleness threshold (").append(threshold).append(") | ").append(s.stale).append(" |\n");
    b.append("| Low quality (< 50) | ").append(s.lowQuality).append(" |\n");
    b.append("| Average staleness | ").append(s.scanned == 0 ? 0 : s.totalStale / s.scanned).append("/100 |\n");
    b.append("| Average quality | ").append(s.scanned == 0 ? 0 : s.totalQuality / s.scanned).append("/100 |\n\n");

    b.append("## All Articles\n\n");
    b.append("| Article | Staleness | Quality | Flags |\n|---------|-----------|---------|-------|\n");
    for (var e : articles.entrySet()) {
      var stal = (Map<?, ?>) e.getValue().get("staleness");
      var qual = (Map<?, ?>) e.getValue().get("quality");
      b.append("| ").append(e.getKey()).append(" | ")
          .append(stal.get("score")).append("/100 | ")
          .append(qual.get("score")).append("/100 | ")
          .append(String.join(", ", castStrings(qual.get("flags"))))
          .append(" |\n");
    }
    return b.toString();
  }

  // ----- helpers -----

  private static final class ScanSummary {
    int scanned, stale, lowQuality;
    int totalStale, totalQuality;
  }

  private static int readThreshold(WikiContext wiki) {
    Path cfg = wiki.root().resolve("config.md");
    if (!WikiFS.exists(cfg)) return 70;
    var fm = Frontmatter.parse(WikiFS.read(cfg)).fields;
    String v = Frontmatter.stringField(fm, "freshness_threshold", "70");
    try { return Integer.parseInt(v); } catch (Exception e) { return 70; }
  }

  private static String wikiName(WikiContext wiki) {
    Path cfg = wiki.root().resolve("config.md");
    if (!WikiFS.exists(cfg)) return wiki.root().getFileName().toString();
    var fm = Frontmatter.parse(WikiFS.read(cfg)).fields;
    return Frontmatter.stringField(fm, "title", wiki.root().getFileName().toString());
  }

  private static long daysSince(String isoDate) {
    if (isoDate == null || isoDate.isBlank()) return -1;
    try {
      return java.time.temporal.ChronoUnit.DAYS.between(
          java.time.LocalDate.parse(isoDate), java.time.LocalDate.now());
    } catch (Exception e) { return -1; }
  }

  private static List<String> castStrings(Object o) {
    if (o instanceof List<?> l) return l.stream().map(String::valueOf).toList();
    return List.of();
  }

  private static String truncate(String s, int max) {
    return (s == null || s.length() <= max) ? s : s.substring(0, max) + "\n...[truncated]";
  }
}
