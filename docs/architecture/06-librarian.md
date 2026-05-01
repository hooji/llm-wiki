# Capability: Librarian

> Score every article for staleness and quality. Two-tier scan: fast metadata
> check, then deep content read for flagged articles. Checkpoint recovery.
> Machine-readable JSON + human-readable report.

The librarian is the focused **wiki-layer maintenance** tool. It is purely
diagnostic: scores articles, flags issues, never modifies content during a
scan. Write operations (refresh, fix) are separate commands triggered by
explicit user confirmation. Audit ([07-audit.md](07-audit.md)) reuses the librarian's
scan as its first pass.

Two key design choices:

- **Score then act.** Conservative — false positives over false negatives.
- **Two-tier escalation.** Tier 1 reads only frontmatter (cheap). Tier 2
  reads the full body, but only for articles that are stale, hot-volatility,
  or already look thin. Token cost scales with problem density, not wiki
  size.

## 1. Inputs

| Flag | Meaning |
|------|---------|
| `scan` | (default) Run the full pass |
| `report` | Display the latest `.librarian/REPORT.md` |
| `fix <id>` | (Phase 3, not yet implemented) |
| `--article <path>` | Scan only one article |
| `--resume` | Resume from `checkpoint.json` |
| `--passes <list>` | Comma-separated subset (default `staleness,quality`; future: `verification,coherence,dedup`) |
| `--wiki <name>` / `--local` | Standard wiki resolution |

## 2. Workflow

### Step 0 (deterministic) — Init

1. Resolve hub + wiki.
2. `mkdir -p <wiki>/.librarian/`.
3. Read `config.md` `freshness_threshold` (default 70).
4. Build the article list:
   - `--article <path>` → just that file.
   - else `glob wiki/**/*.md` excluding `_index.md`.
5. Check for existing `checkpoint.json`:
   - exists + `--resume` (or no explicit flag) → read it, report progress,
     subtract `completed[]` from the list.
   - exists + user did not pass `--resume` → ask
     `Found checkpoint from <date> with N/M articles done. Resume? (y/n)`.
     On `n` delete and start fresh.

### Step 1 (deterministic) — Pass 1: Staleness, per article

For each pending article, **read only frontmatter** (Tier 1):

```
fm = readFrontmatter(article)
volatility = fm.volatility ?? "warm"          // C15 default
verified   = fm.verified ?? null
updated    = fm.updated ?? fm.created
sources    = fm.sources ?? []

# Resolved sources
resolved = 0
oldestIngestedDays = []
for srcPath in sources:
    if exists(srcPath):
        resolved++
        srcFm = readFrontmatter(srcPath)
        oldestIngestedDays.add( daysSince(srcFm.ingested) )

# Component scores (each 0-25)
half_life = {"hot": 30, "warm": 90, "cold": 365}[volatility]

source_freshness   = 25 * 0.5^(avg(oldestIngestedDays) / half_life)   // 0 if no resolved sources
verification_score = (verified == null) ? 0 : 25 * 0.5^(daysSince(verified) / half_life)
compilation_score  = 25 * 0.5^(daysSince(updated) / half_life)
integrity_score    = 25 * (resolved / sources.size())                  // 0 if no sources

staleness_score = source_freshness + verification_score + compilation_score + integrity_score
```

Write to `checkpoint.json` atomically (write to `.checkpoint.tmp`, rename):

```json
{
  "scan_id": "2026-04-22T10:30:00Z",
  "wiki": "<topic-slug>",
  "passes": ["staleness", "quality"],
  "scope": "full" | "single",
  "threshold": 70,
  "completed": ["wiki/concepts/article-a.md", ...],
  "pending": ["wiki/topics/article-c.md", ...],
  "results": {
    "wiki/concepts/article-a.md": {
      "staleness": {
        "score": 92,
        "factors": {
          "source_freshness": 23,
          "verification": 24,
          "compilation": 22,
          "integrity": 23
        }
      },
      "quality": null,                          // populated in Pass 2
      "tier": 1,
      "scanned_at": "2026-04-22T10:31:00Z"
    }
  }
}
```

This pass is **fully deterministic — no LLM call**. Frontmatter math only.

### Step 2 (mixed) — Pass 2: Quality, per article

Tier 1 (metadata-only, **deterministic**):

```
source_count = sources.size()
avg_source_confidence = average across sources of {high:5, medium:3, low:1}     // 0 if no sources

# Source quality (1-5)
source_quality_t1 = round(avg_source_confidence)        // capped 1-5
                  + (source_count >= 4 ? 1 : 0)         // bonus for plurality
                  - (source_count == 1 ? 1 : 0)         // penalty for single source
                  clamp 1-5

# Depth proxy (1-5) from word count + heading count
words    = wcWords(article)
headings = countMatch(article, /^## /)
if words < 200 or headings == 0:           depth_t1 = 1
elif words < 500 or headings <= 1:         depth_t1 = 2
elif words < 1000:                         depth_t1 = 3
elif headings >= 3 and words >= 1500:      depth_t1 = 4
else:                                      depth_t1 = 3

# See Also presence
has_see_also = article contains "## See Also"
flags = []
if not has_see_also: flags.add("no-see-also")
```

Tier 2 escalation (read full body) when **any** is true:

- `staleness_score < threshold`
- `volatility == "hot"`
- `depth_t1 in {1, 2}` (suspected stub)

#### Tier 2 (LLM)

**Prompt:**

```
You are scoring the quality of a wiki article.

Article (between fences):
```
{full_article_markdown}
```

Article frontmatter:
{frontmatter_yaml}

Source confidences (from raw frontmatter): {[high, medium, ...]}

Score on four dimensions (1-5 each):

1. Depth
   1 = single paragraph, no structure
   3 = multiple sections, covers key aspects
   5 = comprehensive treatment with nuance, examples, edge cases

2. Source quality
   1 = no sources or single low-confidence source
   3 = 2-3 sources, mixed confidence
   5 = 4+ high-confidence sources that corroborate

3. Coherence
   1 = disjointed, no logical flow
   3 = readable structure, minor gaps
   5 = clear narrative arc, smooth transitions, no logical gaps

4. Utility
   1 = trivial or obvious information
   3 = useful for understanding the topic
   5 = actionable for decision-making, includes tradeoffs and recommendations

Return JSON ONLY:

{
  "depth": 1-5,
  "source_quality": 1-5,
  "coherence": 1-5,
  "utility": 1-5,
  "flags": [
    "thin-coverage",                  // depth 1-2
    "single-source",                  // 1 source in sources[]
    "low-confidence-sources",         // avg below medium
    "no-see-also",                    // already determined in Tier 1, kept here
    "stale",                          // staleness < threshold
    "unverified"                      // missing verified:
  ],
  "rationale": "<one sentence>"
}
```

Non-escalated articles get `coherence: 3, utility: 3` (adequate default).
This avoids reading every body on large wikis.

#### Composite

```
quality_score = ((depth + source_quality + coherence + utility) / 4) * 20      // 20-100
```

Update `checkpoint.json.results[<path>].quality`.

### Step 3 (deterministic) — Stale article triage

After all articles are scored, sort by staleness ascending. For each below
threshold, recommend an action:

| Recommendation | When |
|----------------|------|
| `refresh` | Sources are old (`source_freshness` is the worst dimension). Delegate to `/wiki:refresh`. |
| `verify` | Article lacks `verified:` or it's been too long (`verification` is the worst dimension). User reads + confirms. |
| `expand` | Thin article with few sources (`integrity` low or quality low). Suggest `/wiki:research`. |

Present the triage list and prompt the user. For chosen `refresh`,
hand off to refresh protocol per article. For chosen `verify`, simply
update `verified:` to today.

### Step 4 (deterministic) — Generate reports

1. Compose `scan-results.json` (the source of truth for other skills, e.g.
   `/wiki:audit`):

   ```json
   {
     "scan_id": "...",
     "wiki": "<slug>",
     "completed_at": "...",
     "passes": ["staleness", "quality"],
     "threshold": 70,
     "summary": {
       "articles_scanned": 29,
       "stale_count": 4,
       "low_quality_count": 2,
       "avg_staleness": 78,
       "avg_quality": 72,
       "worst_staleness": {"article": "...", "score": 31},
       "worst_quality": {"article": "...", "score": 42}
     },
     "articles": {
       "wiki/concepts/article-a.md": {
         "staleness": {...},
         "quality": {...},
         "tier": 2
       }
     }
   }
   ```

2. Render `REPORT.md` from the JSON (deterministic templating; no LLM call):

   ```markdown
   # Librarian Report — YYYY-MM-DD

   > Scanned N articles in <wiki-name>. Passes: staleness, quality.

   ## Summary
   | Metric | Value |
   |--------|-------|
   | Articles scanned | N |
   | Below staleness threshold | N |
   | Low quality (< 50) | N |
   | Average staleness | N/100 |
   | Average quality | N/100 |

   ## Stale Articles (staleness < threshold)
   | Article | Score | Top Factor | Recommendation |
   |---------|-------|------------|----------------|
   | [Title](path) | 31/100 | sources 180d old | refresh |

   ## Low Quality Articles (quality < 50)
   | Article | Score | Flags | Recommendation |
   |---------|-------|-------|----------------|
   | [Title](path) | 42/100 | thin-coverage, single-source | expand and add sources |

   ## All Articles (sorted by combined score)
   | Article | Staleness | Quality | Flags |
   |---------|-----------|---------|-------|
   | ... | ... | ... | ... |
   ```

3. Delete `checkpoint.json` (scan complete).

### Step 5 (deterministic) — Log

Append to `.librarian/log.md`:
```
## [YYYY-MM-DD] scan | N articles, M stale, K low-quality (passes: staleness, quality)
```

Append to wiki root `log.md`:
```
## [YYYY-MM-DD] librarian | scanned N articles, M stale, K low-quality
```

### Step 6 (deterministic) — Summary block

Render to user:

```
## Librarian Scan Complete

Scanned N articles in <wiki-name>.

| Metric | Value |
|--------|-------|
| Below staleness threshold | M |
| Low quality (< 50) | K |
| Average staleness | X/100 |
| Average quality | Y/100 |

Full report: .librarian/REPORT.md
Scan data: .librarian/scan-results.json
```

If stale articles were found and the user hasn't triaged yet, flow into
Step 3.

## 3. Boundary with other commands

| Command | Owns | Librarian does NOT |
|---------|------|--------------------|
| `lint` | Structure: missing indexes, broken links, frontmatter schema, file placement | Lint's territory — librarian skips |
| `lint --deep` (C7) | Quick spot-check: a few web searches for obvious staleness | Lightweight — librarian goes deeper, but never web-fetches itself |
| `refresh` | Re-fetch sources, compare changes, offer recompile | Librarian flags, then delegates |
| `compile` | Transform raw → wiki | Librarian reviews, never compiles |
| `audit` | Umbrella trust audit | Librarian is the wiki-layer pass *inside* audit |

## 4. `report` subcommand (deterministic)

```
if not exists(.librarian/REPORT.md):
    "No librarian report found. Run /wiki:librarian scan first."
else:
    display REPORT.md
    note when scan ran (from scan-results.completed_at)
    if completed_at older than 30 days: suggest re-scanning
```

## 5. Persistence summary

| File | Lifecycle |
|------|-----------|
| `.librarian/checkpoint.json` | Created at scan start, updated per article, deleted on completion |
| `.librarian/scan-results.json` | Final, machine-readable, source of truth |
| `.librarian/REPORT.md` | Final, human-readable, rendered from scan-results |
| `.librarian/log.md` | Append-only |
| `<wiki>/log.md` | Append-only librarian entry |

## 6. Edge cases

- **Mid-scan crash**: `checkpoint.json` survives. `--resume` (or implicit
  resume on detection) skips already-completed articles. The Tier-2 result
  for the in-flight article is rescanned (the `.checkpoint.tmp` rename is
  atomic, so a partially written checkpoint is impossible).
- **Article missing `volatility`**: treat as `warm`. C15 lint-fix will add
  the field on the next lint pass.
- **Article missing `verified`**: `verification_score = 0` (never verified).
  The article scores low on this dimension regardless of its other
  dimensions, which is the intended signal — please verify.
- **Article missing `sources`**: `integrity = 0`. The article also gets
  `single-source` or worse flags. Strong signal to expand.
- **Concurrent scan**: not supported on the same wiki; the second
  invocation finds the existing checkpoint and offers to resume or
  abandon. Guard with the wiki's `log.md` if needed.
- **Only one pass requested**: `--passes staleness` skips Step 2 entirely;
  quality results are absent from the output. The audit pass tolerates
  missing-quality data.

## 7. Java reimplementation outline

```java
public class LibrarianService {
  public ScanResult scan(ScanRequest req) {
    WikiContext wiki = wikis.resolve(req);
    Path libDir = wiki.root.resolve(".librarian");
    fs.mkdirs(libDir);

    int threshold = wiki.config().freshnessThreshold(70);
    List<Path> articles = req.singleArticle != null
        ? List.of(req.singleArticle)
        : globExcluding(wiki.root.resolve("wiki"), "**/*.md", "_index.md");

    Checkpoint cp = Checkpoint.loadOrCreate(libDir.resolve("checkpoint.json"), req);
    articles = articles.stream().filter(a -> !cp.completed.contains(a)).toList();

    for (Path a : articles) {
      Frontmatter fm = readFrontmatter(a);
      Staleness s = computeStaleness(fm, threshold);                 // deterministic

      QualityT1 q1 = computeQualityT1(a, fm);                        // deterministic
      Quality q;
      if (s.score < threshold || "hot".equals(fm.volatility) || q1.depth <= 2) {
        q = qualityTier2LLM(a, fm, q1);                              // LLM call
      } else {
        q = q1.toAdequateDefault();                                  // coherence=3, utility=3
      }

      cp.record(a, new ArticleResult(s, q));
      cp.writeAtomic();
    }

    ScanResult result = ScanResult.from(cp);
    fs.writeAtomic(libDir.resolve("scan-results.json"), result.toJson());
    fs.writeAtomic(libDir.resolve("REPORT.md"), renderReportMd(result));
    fs.delete(libDir.resolve("checkpoint.json"));

    appendLog(libDir.resolve("log.md"),  "scan",      result.summary());
    appendLog(wiki.root.resolve("log.md"),"librarian", result.summary());
    return result;
  }
}
```
