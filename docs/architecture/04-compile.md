# Capability: Compile

> Raw sources become synthesized articles with cross-references and confidence
> scores. Every directory has an `_index.md` — nothing is scanned blindly.

Compile is the **LLM-as-compiler** step. Given a set of immutable raw
sources, it produces a consistent body of synthesized wiki articles in
`wiki/concepts/`, `wiki/topics/`, `wiki/references/`, with bidirectional
"See Also" cross-links, dual-link Obsidian compatibility, confidence scoring,
and volatility classification.

Two modes:

- **Incremental** (default): only sources ingested after the last compile date.
- **Full** (`--full`): re-read every raw source, rewrite every article.

## 1. Inputs

| Flag | Meaning |
|------|---------|
| `--full` | Re-process all sources |
| `--source <path>` | Compile only that one raw file |
| `--topic <name>` | Force a topic article for that name |
| `--wiki <name>` / `--local` | Standard wiki resolution |

## 2. Workflow

### Step 0 (deterministic) — Hub + wiki resolution

Standard prelude.

### Step 1 (deterministic) — Placement pre-check (lint C13 + C11)

Walk `raw/`. For every `.md` file:

1. Read frontmatter.
2. Apply C13 alias rewrites (legacy keys → canonical, legacy enum values →
   canonical). Currently empty in v0.6 but the framework runs.
3. Compute the expected directory from the placement map:
   - `type: thesis` → `wiki/theses/`
   - `type ∈ {articles, papers, repos, notes, data}` → `raw/<type>/`
   - `category ∈ {concept, topic, reference}` → `wiki/<plural>/`
4. If actual directory ≠ expected, `mv` the file. On slug collision at the
   destination, skip and warn.

This is the same mechanical lint pass run inline because the compiler is
already reading every frontmatter. The principle: lint-is-the-migration —
there is no separate `/wiki:migrate`.

### Step 2 (deterministic) — Survey

```
allRaw      = listFiles("raw/**/*.md", excluding _index.md)
allArticles = listFiles("wiki/**/*.md", excluding _index.md)
lastCompile = readMasterIndex().lastCompiled                // or epoch if absent

if --full:
    target = allRaw
elif --source:
    target = [readFile(--source)]
else:
    target = [s for s in allRaw if frontmatter(s).ingested > lastCompile]

if target.empty and not --full:
    report("All sources are already compiled. Use --full to recompile.")
    return
```

### Step 3 (LLM) — Extract per-source signals

For each raw source in `target`, call the LLM once to extract structured
signals. The body of the source is bounded (typically <50KB), so this is one
call per source.

**Prompt:**

```
You are extracting structured signals from a raw source for a knowledge
wiki compiler.

Source frontmatter:
{frontmatter_yaml}

Source body (between fences):
```
{body_markdown}
```

Return JSON ONLY:

{
  "key_concepts": [
    {
      "name": "<canonical name, Title Case>",
      "slug": "<lowercase-hyphenated>",
      "kind": "concept" | "topic" | "reference",
      "salience": 1 | 2 | 3 | 4 | 5,         // 5 = central to this source
      "summary": "<one sentence>"
    }, ...
  ],
  "key_facts": [
    "<short factual claim with no editorializing>", ...
  ],
  "relationships": [
    {"from": "<slug>", "kind": "is-a" | "part-of" | "contrasts-with" | "depends-on" | "created-by", "to": "<slug>"}
  ],
  "tags": ["<lowercase-hyphenated>", ...],   // 3-7
  "evidence_strength": "meta-analysis" | "rct" | "cohort" | "case" | "expert-opinion" | "anecdotal" | "documentation" | "primary-data",
  "volatility_suggestion": "hot" | "warm" | "cold",
  "rationale": "<one sentence on why this volatility>"
}

Concept classification rules:
- concept: a specific bounded idea explainable in 1-3 pages (e.g. "Self-Attention", "Docker Container")
- topic: a broader theme tying concepts together (e.g. "Deep Learning", "DevOps")
- reference: a curated list of resources/tools/links (e.g. "Python ML Libraries")

Volatility rules:
- hot: product specs, pricing, current events, competitive landscape
- warm: best practices, framework comparisons, market analysis (default)
- cold: foundational concepts, historical events, mathematical proofs
```

Persist results in memory keyed by source path.

### Step 4 (deterministic) — Map concepts to existing articles

Build a global map `Map<slug, ArticleStatus>` where status is
`{EXISTING_UPDATE, NEW_CREATE, MENTION_ONLY}`:

```
for each (source, concepts) in extractedSignals:
    for c in concepts:
        if c.salience >= 3:
            if existsArticle(c.slug):
                map[c.slug] = (EXISTING_UPDATE, c.kind, sources_to_add)
            else:
                map[c.slug] = (NEW_CREATE, c.kind, sources_to_add)
        else:
            map[c.slug] = MENTION_ONLY
```

Salience filter (≥3) prevents one-line mentions from spawning stub articles.

### Step 5 (LLM, per article) — Plan article structure

For each `NEW_CREATE` slug, ask the LLM to plan the article skeleton from the
collected source signals.

**Prompt:**

```
You are planning a new wiki article for an LLM-compiled knowledge base.

Article slug: {slug}
Article kind: {concept|topic|reference}

Sources contributing to this article:
[
  {"path": "raw/papers/2026-...md", "title": "...", "summary": "...",
   "extracts_about_this_concept": [
      "<the relevant key_facts and key_concepts entries from Step 3>"
   ],
   "credibility_score": 4,                       // from research Phase 2b if available
   "evidence_strength": "rct"
  }, ...
]

Existing related articles in the wiki:
[{"slug": "self-attention", "title": "Self-Attention", "summary": "..."}, ...]

Return JSON ONLY:

{
  "title": "<title case display name>",
  "aliases": ["alternate name", ...],            // up to 5
  "summary": "<2-3 sentence summary for index>",
  "tags": ["..."],
  "confidence": "high" | "medium" | "low",
  "confidence_rationale": "<one sentence>",
  "volatility": "hot" | "warm" | "cold",
  "abstract_paragraph": "<single paragraph: what is this and why does it matter>",
  "sections": [
    {"heading": "## Background", "intent": "what to cover in this section, 1-3 sentences"},
    {"heading": "## Mechanism", "intent": "..."},
    ...
  ],
  "see_also": [
    {"slug": "<existing or to-be-created>", "relationship": "<one phrase>"}
  ],
  "source_attributions": [
    {"path": "raw/papers/...md", "what_it_contributed": "<one phrase>"}
  ]
}

Confidence rules:
- high: multiple sources with credibility >= 4 agree, OR single peer-reviewed meta-analysis/systematic review
- medium: single credible source, OR multiple sources partially agree, OR recent findings not yet replicated
- low: single non-peer-reviewed source, OR sources disagree, OR anecdotal only
```

For each `EXISTING_UPDATE` slug, the prompt is the same but includes the
current article's existing sections and asks the LLM to return a *delta* (new
section ideas, new See Also links, new sources to attribute). The "skeleton"
is then a patch instruction set rather than a full plan.

### Step 6 (LLM + chunked writes) — Write article skeleton

Write the file in **multiple calls**, never one big Write — the LLM stream
times out on long generations. The pattern:

1. **Write 1**: frontmatter + abstract + first section heading (file is now
   a valid article).
2. **Edit 2..N**: append one body section at a time. Per section, run an
   LLM call that consumes the source extracts plus the section's `intent`
   field and emits the section markdown.

Per-section prompt:

```
You are writing a single section of a wiki article.

Article: {title}
Section heading: {heading}
Section intent: {intent}

Source extracts available for this section:
[
  {"path": "...", "extract": "...", "credibility": 4},
  ...
]

Constraints:
- Synthesize. Do NOT copy-paste.
- Self-contained: a reader should not need to consult the raw sources.
- Be specific. Include data points, mechanisms, examples.
- Note honest disagreement when sources disagree.
- When referencing another wiki article inline, use dual-link format:
    [[other-slug|Name]] ([Name](../<category>/other-slug.md))
- No marketing language.

Return JSON ONLY:
{
  "section_markdown": "<the section body, in markdown, NOT including the ## heading line>",
  "inline_cross_refs": ["other-slug-1", "other-slug-2"],
  "new_facts": ["..."]                       // facts now present that weren't in any prior section
}
```

After all sections are written, append `## See Also` and `## Sources` from
the plan (deterministic templating — no LLM call).

### Step 7 (deterministic) — Frontmatter assembly

```yaml
---
title: "{title}"
category: {concept|topic|reference}
sources: [{paths from source_attributions}]
created: {today if NEW, original if EXISTING}
updated: {today}
tags: [{tags}]
aliases: [{aliases}]
confidence: {confidence}
volatility: {volatility}
verified: {today}
summary: "{summary}"
---
```

### Step 8 (deterministic) — Bidirectional linking

For every `See Also` link from article A → B written in Step 5:

```
if not exists(B):
    queue B for creation in this same compile pass
else:
    read B
    if B does not have "See Also" entry pointing to A:
        Edit B to insert: "[[A-slug|A title]] ([A title](../<cat>/A-slug.md)) — <relationship from Step 5>"
```

Bidirectionality is the lint C4 invariant, enforced inline at compile time so
the next lint has nothing to fix.

### Step 9 (deterministic, best-effort) — Update indexes

In order:

1. `wiki/concepts/_index.md`, `wiki/topics/_index.md`,
   `wiki/references/_index.md` — add/update rows.
2. `wiki/_index.md` — aggregated.
3. master `_index.md` — bump article count, update "Last compiled" to today,
   prepend Recent Changes.
4. If `output/projects/` exists, regenerate `output/_index.md` as a
   projects-aware listing: scan each `output/projects/*/WHY.md` for first
   `#` heading (title) and first non-heading paragraph (goal), table them,
   then list any remaining loose `output/*.md` below.

All best-effort: if interrupted, the next read rebuilds via the Derived Index
Protocol.

### Step 10 (deterministic) — Log

```
## [YYYY-MM-DD] compile | N sources → X new articles, Y updated (slug1, slug2, ...)
```

### Step 11 (deterministic) — Report

To the user (not stored):

- Sources processed: count
- New articles created: list with paths
- Existing articles updated: list with paths
- New cross-references added: count
- Suggested next: `/wiki:lint` to verify consistency

## 3. Confidence and volatility scoring

Confidence (used in Step 5):

| Tier | Trigger |
|------|---------|
| `high` | Multiple credibility-≥4 sources agree, OR single peer-reviewed meta/systematic review |
| `medium` | Single credible source, OR partial agreement, OR recent findings not yet replicated |
| `low` | Single non-peer-reviewed source, OR sources disagree, OR anecdotal only |

When the prior pipeline was a research Phase 2b (see [01-research.md](01-research.md)), credibility scores are passed in directly. For
manual ingest → compile, credibility is assessed inline by the same LLM call.

Volatility (used in Step 5 + Step 7):

| Tier | Half-life | When |
|------|-----------|------|
| `hot` | 30 days | product specs, pricing, current events |
| `warm` | 90 days | best practices, framework comparisons (default) |
| `cold` | 365 days | foundations, history, math, stable references |

## 4. Article shape (deterministic template)

```markdown
---
title: "..."
category: concept
sources: [...]
created: 2026-04-04
updated: 2026-04-04
tags: [...]
aliases: [...]
confidence: high
volatility: warm
verified: 2026-04-04
summary: "..."
---

# {title}

> {abstract_paragraph}

## {first section heading}

{first section markdown}

## {second section heading}

{...}

## See Also

- [[other-slug|Other Article]] ([Other Article](../concepts/other-slug.md)) — {relationship}

## Sources

- [Source Title](../../raw/<type>/<file>.md) — {what it contributed}
```

## 5. Persistence summary

| File | Written by |
|------|------------|
| `wiki/<category>/<slug>.md` | Steps 5-7 (skeleton + per-section append + frontmatter) |
| `wiki/<category>/_index.md`, `wiki/_index.md`, master `_index.md`, `output/_index.md` | Step 9 (best-effort) |
| `log.md` | Step 10 |

No durable session files. (Compile is a one-shot.)

## 6. Edge cases

- **Source contradicts existing article**: the LLM in Step 5 surfaces this in
  `confidence_rationale`. The article's `confidence` may drop. The Sources
  section gets both citations with their disagreement noted in body text.
- **Mid-compile crash**: articles already written are valid (each is an
  atomic file). The next compile re-runs Step 2 with `lastCompile` still on
  the old date and reprocesses the same sources idempotently — articles get
  re-updated rather than duplicated, because slugs are stable.
- **Two sessions compile concurrently**: both write articles, both
  best-effort update indexes; the next read rebuilds. Last-write-wins on the
  same article body is acceptable because content is rebuildable from raw.
- **Long article**: chunked writes (one section per LLM call). Articles
  exceeding ~10 sections are a smell — likely should be split into a topic
  article + linked concept articles.
- **Article without sources**: never. Step 4 only marks slugs as
  `NEW_CREATE` if they came from at least one source extract. Lint C2 also
  enforces non-empty `sources:`.

## 7. Java reimplementation outline

```java
public class CompileService {
  public CompileReport compile(CompileRequest req) {
    WikiContext wiki = wikis.resolve(req);
    placementPrecheck(wiki);                                       // Step 1

    List<Path> sources = surveyTargets(wiki, req);                 // Step 2
    if (sources.isEmpty() && !req.full) return CompileReport.noOp();

    Map<Path, SourceSignals> signals = sources.parallelStream()    // Step 3
        .collect(toMap(p -> p, p -> extractSignalsLLM(wiki, p)));

    Map<String, ArticlePlan> plan = mapConceptsToArticles(signals);// Step 4

    for (Entry<String, ArticlePlan> e : plan.entrySet()) {         // Step 5-7
      ArticleDraft draft = planArticleLLM(e.getValue(), signals);
      writeArticleChunked(wiki, draft);
    }

    enforceBidirectionalSeeAlso(wiki, plan);                       // Step 8
    bestEffort(() -> updateAllIndexes(wiki));                      // Step 9
    appendLog(wiki, "compile", summarizeReport(plan));             // Step 10
    return buildReport(plan);
  }

  private void writeArticleChunked(WikiContext w, ArticleDraft d) {
    fs.write(d.path, renderFrontmatter(d) + renderAbstract(d) + firstHeading(d));
    for (SectionPlan s : d.sections) {
      String json = llm.call(SECTION_PROMPT.bind(d, s, signalsFor(d, s)));
      SectionResult r = SectionResult.fromJson(json);
      fs.append(d.path, "\n## " + s.heading + "\n\n" + r.sectionMarkdown);
    }
    fs.append(d.path, renderSeeAlso(d) + renderSources(d));
  }
}
```
