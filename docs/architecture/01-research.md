# Capability: Research

> 5–10 parallel agents search academic, technical, applied, news, and
> contrarian angles. `--min-time 2h` keeps going in rounds, drilling into
> gaps each round finds.

Research is the flagship capability. It runs a parallel agent swarm, scores
source credibility independently, ingests the survivors, compiles them into
articles, scores the round's progress, reflects on gaps, and (if a time
budget is set) iterates until the budget is spent or returns diminish.

This document covers **topic mode** and **question mode**. **Thesis mode**
shares the same infrastructure and is documented as a delta in
[02-thesis.md](02-thesis.md).

## 1. Inputs

| Flag | Meaning |
|------|---------|
| `<input>` | Topic, question, or thesis. Auto-detected. |
| `--mode thesis "<claim>"` | Force thesis mode. See [02-thesis.md](02-thesis.md). |
| `--new-topic` | Create the topic wiki on the fly. |
| `--sources <N>` | Target sources per round (default 5; retardmax default 15; max 20). |
| `--deep` | 8 agents (adds historical, adjacent, data/stats). |
| `--retardmax` | 10 agents, skip planning, lower quality threshold, ingest aggressively. |
| `--min-time <duration>` | Run multi-round until budget spent (`30m`, `1h`, `2h`, `4h`). |
| `--plan` | Decompose into 3-5 parallel paths, ingest in parallel, compile once. |
| `--project <slug>` | Tag outputs and articles with this project. |
| `--wiki <name>` / `--local` | Standard wiki resolution. |

## 2. Input detection (deterministic)

Before the standard flow, classify the input:

```
if --mode thesis: thesis mode (delta doc)
elif input starts with /\b(prove that|is it true that|verify|test the claim|test the hypothesis)\b/i:
    thesis mode (claim = input minus signal words)
elif input starts with what/why/how/when/where/who, contains "?",
     or matches /^how to|^what makes|^why does/:
    question mode
else:
    topic mode
```

## 3. Multi-round session lifecycle

When `--min-time` is set, the run becomes a multi-round session with
durable provenance. State files in the wiki root:

- `.research-session.json` — ephemeral crash-recovery state. Deleted on
  normal completion.
- `.session-events.jsonl` — append-only durable event log.
- `.session-checkpoint.json` — durable replayable summary, refreshed at each
  milestone.

### Ephemeral schema

```json
{
  "session_id": "2026-04-29-120000",
  "topic": "...",
  "mode": "single" | "plan",
  "start_time": "2026-04-29T12:00:00Z",
  "min_time_budget": "2h",
  "current_round": 2,
  "paths": [...],                                    // only when mode=plan
  "rounds_completed": [
    {
      "round": 1,
      "start_time": "...",
      "end_time": "...",
      "sources_ingested": 5,
      "articles_compiled": 3,
      "gaps": ["..."],
      "progress_score": 65,
      "reflection_notes": "..."
    }
  ],
  "cumulative_sources": 5,
  "cumulative_articles": 3,
  "status": "in_progress" | "completed"
}
```

### Event log lines (one JSON per line)

```json
{"ts":"2026-04-29T12:00:00Z","command":"research","phase":"start","event":"research_started","session_id":"...","topic":"...","mode":"single","min_time_budget":"2h"}
{"ts":"2026-04-29T12:38:00Z","command":"research","phase":"round","event":"research_round_completed","session_id":"...","round":1,"sources_ingested":5,"articles_compiled":3,"progress_score":65}
{"ts":"2026-04-29T12:42:00Z","command":"research","phase":"reflection","event":"research_reflection_completed","session_id":"...","round":1,"top_gaps":["g1","g2","g3"]}
{"ts":"2026-04-29T14:05:00Z","command":"research","phase":"finish","event":"research_completed","session_id":"...","rounds_completed":3,"cumulative_sources":14,"cumulative_articles":9}
```

### Resume detection

At command start:

1. If `.research-session.json` exists and `status: "in_progress"`, read it,
   ask the user "Continue from Round N+1, or start fresh?"
2. If no active session but `.session-checkpoint.json` exists, summarize the
   most recent completed work in the briefing.

## 4. Standard workflow — single round, topic mode

### Phase 0 (deterministic) — Hub + wiki resolution + `--new-topic` branch

Standard prelude. If `--new-topic`, derive a slug
(lowercase, hyphens, max 40 chars), create the topic wiki under
`HUB/topics/<slug>/`, register in `wikis.json`.

If `--min-time` is set, also create `.research-session.json` and append
`research_started` to `.session-events.jsonl`.

### Phase 1 (LLM) — Existing-knowledge check

Read `wiki/_index.md` and `raw/_index.md`. Grep `wiki/` for the topic and
related terms.

**Prompt:**

```
You are scoping a research run. The wiki currently contains:

Master index summary:
{master_index_table}

Articles already covering this topic (from grep):
[
  {"path": "...", "title": "...", "summary": "...", "tags": [...]}
]

User's research topic: "{topic}"

Return JSON ONLY:

{
  "existing_coverage_summary": "<2-3 sentences on what's covered>",
  "gaps": [
    {"gap": "<specific gap>", "why_matters": "<phrase>"}
  ],
  "search_angles": [
    "<angle 1>", "<angle 2>", ...                  // 5-8 specific angles
  ]
}
```

Skip Phase 1 entirely in `--retardmax` mode.

### Phase 2 (LLM × N agents in parallel) — Web research swarm

Launch N parallel agents (5 standard, 8 deep, 10 retardmax). Each agent gets
the **same prompt template**, parameterized by role.

**Roles (standard mode = first 5; +deep adds next 3; +retardmax adds last 2):**

| Agent | Focus | Search strategy |
|-------|-------|----------------|
| Academic | Peer-reviewed papers, meta-analyses | Google Scholar, PubMed, arxiv. Recent 2 years. Look for landmarks. |
| Technical | Specs, docs, deep-dives | Whitepapers, official docs, engineering blogs. |
| Applied | Case studies, practical guides | How-to guides, industry reports, practitioner posts. |
| News/Trends | Recent developments | News from last 6 months, conference talks, announcements. |
| Contrarian | Critiques, limitations, counterarguments | Critiques, rebuttals, known limitations, what doesn't work. |
| Historical | Origins, evolution, milestones | History, foundational papers, key figures. |
| Adjacent | Cross-domain connections | Cross-domain applications, analogies. |
| Data/Stats | Quantitative, benchmarks | Surveys, benchmarks, statistical analyses, datasets. |
| Rabbit Hole 1 | Follow most interesting link | Start with topic, click most compelling, search what THAT references. |
| Rabbit Hole 2 | Same, different starting terms | Synonyms or adjacent framing. Follow trail. |

**Per-agent prompt:**

```
You are a research agent. Your task:

**Objective**: Research "{topic}" from the {Agent Role} angle.
**Focus**: {Role-specific focus}
**Search strategy**: {Strategy from role table}
**Current wiki state**: The wiki already covers: {Phase 1 summary}.
                        Search for what's NOT covered.
**Constraints**:
- Run 2-3 WebSearch queries (vary terms, don't repeat)
- For each promising result, use WebFetch to extract full content
- Skip: paywalled, SEO spam, thin, duplicate
- Target 3-5 high-quality sources

**Return format**: For each source:
- title, url, quality_score (1-5)
- key_findings (3-5 bullets)
- why_ingest (1 sentence)

**Quality scoring**:
- 5: Peer-reviewed, landmark, primary data
- 4: Authoritative blog, official docs, well-sourced report
- 3: Decent coverage, some original insight
- 2: Thin, mostly derivative
- 1: SEO spam, no original content

Return JSON ONLY:

{
  "agent_role": "{role}",
  "queries_run": ["q1","q2",...],
  "sources": [
    {
      "title": "...",
      "url": "...",
      "quality_score": 4,
      "key_findings": ["...","..."],
      "why_ingest": "...",
      "content_markdown": "<extracted body, kept verbatim>",
      "publication_date": "YYYY-MM-DD" | null,
      "authors": ["..."]
    }
  ]
}
```

Retardmax delta:

- Increase to 4-5 searches per agent.
- Lower quality threshold: accept 2+.
- Add to prompt: "Follow interesting citations and references from pages
  you find."
- Rabbit Hole prompts: "Start with '{topic}', follow the most compelling
  result, then search for what THAT references. Go deep."

Question-mode delta:

- Replace `Objective` with: `Answer this sub-question: "{sub-question}"`.
- Add: `Your deliverable is evidence that answers this specific question.`
- See **Phase 1.b** below for sub-question decomposition.

### Phase 1.b (LLM, question mode only) — Decompose

Before Phase 2, run:

```
Decompose this question into 3-5 focused, independently searchable
sub-questions. The sub-questions together must produce a complete answer
to the original question.

Question: "{question}"

Return JSON ONLY:

{
  "sub_questions": [
    {"id": 1, "tag": "what",  "question": "What patterns do viral long-form articles share?",  "search_strategy": "..."},
    {"id": 2, "tag": "why",   "question": "What psychological/social mechanisms drive sharing?","search_strategy": "..."},
    {"id": 3, "tag": "how",   "question": "What's the step-by-step process to write one?",     "search_strategy": "..."},
    {"id": 4, "tag": "who",   "question": "Who's done this and what do they say?",             "search_strategy": "..."},
    {"id": 5, "tag": "data",  "question": "What does the data say (engagement, virality)?",    "search_strategy": "..."}
  ]
}
```

Present to user, then dispatch one agent per sub-question.

### Phase 1.5 (LLM, `--plan` mode only) — Decompose into paths

```
Decompose into 3-5 independent research paths. Each path must:
- have a clear, non-overlapping scope
- be searchable independently (no dependencies on other paths' findings)
- target a specific aspect (foundational, current state, applications,
  criticisms, adjacent connections)

Topic: "{topic}"
Existing coverage: {Phase 1 summary}

Return JSON ONLY:

{
  "paths": [
    {
      "name": "Cryptographic foundations",
      "focus": "Shor's algorithm vs ECDLP, key sizes, quantum gate counts",
      "search_angles": ["shor algorithm elliptic curve", "quantum gate count ECDLP", "NIST PQC"],
      "target_sources": 4
    },
    ...
  ]
}
```

Present to user. Wait for `y / n / edit`. On `y`, persist `paths[]` into
`.research-session.json` with `mode: "plan"`. Each path-agent then runs its
own internal Phase 2 swarm of 5/8/10 sub-agents. Path agents prefix their
raw filenames: `raw/<type>/YYYY-MM-DD-p<N>-<slug>.md`.

### Phase 2b (LLM) — Credibility review

After all agent JSONs return, deduplicate:

- by exact URL match (keep one)
- by content overlap >80% (keep higher quality_score)

Then score each surviving source for credibility, **independently** of the
agent that found it ("fox guarding the henhouse" prevention):

**Prompt:**

```
You are independently scoring source credibility for a research
ingestion pipeline.

Source:
{
  "title": "...",
  "url": "...",
  "publication_date": "YYYY-MM-DD",
  "authors": ["..."],
  "found_by_agent": "Academic",
  "agent_quality_score": 4,
  "key_findings": [...],
  "corroboration_count": 2          // # of OTHER agents that found a similar source
}

Rubric:
+2 if peer-reviewed (DOI, journal, conference, PubMed, arxiv with venue)
+1 if recent (≤3 years)
0  if 3-10 years old
-1 if >10 years (unless foundational/landmark)
+1 if known author/institution (recognized university, major lab, cited expert)
-1 if potential bias (industry-sponsored without disclosure, activist org, predatory journal)
-1 if vendor primary source (first-party docs/blog about own product)
+1 per other agent that corroborates (max +2)

Non-stacking: bias signals do NOT stack. If both "potential bias" and
"vendor primary" trigger, apply only -1 (the more specific one).

Return JSON ONLY:

{
  "credibility_score": <integer>,
  "tier": "high" | "medium" | "low" | "reject",
  "rationale": "<one sentence>",
  "bias_flags": ["industry-sponsored", "vendor-primary", ...]   // empty if none
}
```

Tier mapping:

| Tier | Score | Action | Compile-time confidence |
|------|-------|--------|------------------------|
| high | 4-6 | Ingest | `confidence: high` |
| medium | 2-3 | Ingest | `confidence: medium` |
| low | 0-1 | Ingest only if unique angle | `confidence: low` |
| reject | <0 | Skip | — |

Retardmax mode: lower the rejection floor — accept Medium and above without
filtering, but still score (scores carry into article confidence).

### Phase 3 (deterministic + LLM) — Ingest

Sort by `(credibility_score × agent_quality_score)` desc. Take top
`--sources` count. For each, run the Ingest capability ([03-ingest.md](03-ingest.md)) Steps 4-8 — but the
metadata extraction (Step 3) can reuse the agent's already-fetched
`content_markdown`, so ingestion needs only the Step-3 LLM call to format
frontmatter cleanly.

In `--plan` mode, parallel paths each ingest to their own
`p<N>-<slug>.md` files. Indexes are skipped during ingest (rebuilt on next
read).

### Phase 4 (deterministic + LLM) — Compile

Run the Compile capability ([04-compile.md](04-compile.md)) on the just-ingested raw files.
Pass through credibility scores so Step 5 of Compile uses them directly when
setting article `confidence:`.

`--plan` mode: a single sequential compile pass after **all** paths complete,
giving the compiler full visibility for cross-path cross-references.

### Phase 5 (LLM) — Round report

```
Generate the round report.

Topic: "{topic}"
Round: {N} of {M_or_unknown}
Agents launched: {role_list}
Sources found: {total}
Sources ingested: {ingested_with_quality}
Sources skipped: {skipped_with_reason}
Articles created: {paths_with_summaries}
Articles updated: {paths_with_what_was_added}
New cross-references: {count}

Compute:
- progress_score (0-100):
  sources_ingested × 3        (max 30)
  + articles_created_or_updated × 5  (max 30)
  + cross_refs_added × 2      (max max(20, existing_articles × 2))
  + avg_credibility × 4       (max 20)

Determine remaining_gaps and suggested_followups from the source content
that was NOT ingested or that was ingested but raises further questions.

Return JSON ONLY:

{
  "progress_score": <0-100>,
  "score_breakdown": {
    "sources": <int>, "articles": <int>, "cross_refs": <int>, "credibility": <int>
  },
  "confidence_map": [
    {"article": "<path>", "confidence": "high|medium|low", "why": "<phrase>"}
  ],
  "new_connections": ["<a-slug ↔ b-slug — relationship>", ...],
  "remaining_gaps": [
    {"gap": "<specific gap>", "why_matters": "<phrase>"}
  ],
  "suggested_followups": ["<command or topic>", ...],
  "termination_recommendation": "continue" | "early_complete" | "low_yield_warning"
}
```

Termination decision tree (deterministic, applied after the LLM returns):

```
if progress_score >= 80 and no high-impact gap and cross_ref_density > 0.6:
    -> RECOMMEND EARLY COMPLETION
elif progress_score < 40 (this round):
    if previous_round_score < 40:
        -> LOW YIELD WARNING (suggest --deep or different terms or narrower topic)

Trajectory checks:
- 3 consecutive declining rounds totaling 30+ pt drop -> declining-trajectory warning
- 2 consecutive rounds within 5 pt and no new high-impact gap -> plateau, recommend early completion
- any single round score < 20 -> stalled flag
```

Append to `log.md`:

```
## [YYYY-MM-DD] research | "{topic}" → N sources ingested, M articles compiled
```

In `--min-time` mode, append `research_round_completed` to
`.session-events.jsonl` and refresh `.session-checkpoint.json`.

### Phase 6 (LLM, `--min-time` only) — Reflection

Between rounds, reflect **holistically across ALL prior rounds**, not just
the most recent. Testing showed reflection's value is in cross-round
connections (~34% improvement) far more than redirecting research.

```
Reflect across all prior rounds. Priorities (in order):
1. Draw connections between this round's findings and ALL prior rounds.
2. Update cross-references — list See-Also additions to make.
3. Re-evaluate earlier gaps — which are now filled, which still open.
4. Score remaining gaps: impact (1-5) × feasibility (1-5) × specificity (1-5) = composite (1-125).
5. Adjust direction — only if findings clearly indicate a shift (rare).

All prior rounds:
[
  {"round":1, "summary":"...", "gaps":[...], "progress_score":65, "articles":[...]},
  ...
]

This round:
{...}

Return JSON ONLY:

{
  "cross_round_connections": [
    "<round-1 finding about X> ↔ <round-2 finding about Y> -> new gap '<C>'"
  ],
  "see_also_additions": [
    {"from": "wiki/.../a.md", "to": "wiki/.../b.md", "relationship": "<phrase>"}
  ],
  "gap_reevaluation": {
    "filled": ["<gap text>"],
    "still_open_upgraded": ["<gap text>"],
    "new": ["<gap text>"]
  },
  "scored_next_gaps": [
    {"gap": "...", "impact": 5, "feasibility": 4, "specificity": 5, "composite": 100}
  ],
  "direction_shift": null | "<one sentence>",
  "early_completion_recommended": false | true
}
```

Apply `see_also_additions` deterministically (Edit each article to insert
the dual-link). Append `research_reflection_completed` event.

### Phase 7 (deterministic) — Round-loop control

```
elapsed = now() - start_time
top_gaps = scored_next_gaps[:3]                  // top 3 by composite

if early_completion_recommended:
    break
if elapsed >= min_time_budget:
    break
if (elapsed + average_round_duration) > 1.5 × min_time_budget:
    break
if low_yield_count >= 2:
    optionally switch to --deep, then break if still low
else:
    next_round_topics = [g.gap for g in top_gaps]
    goto Phase 2 with new sub-topics
```

### Phase 8 (deterministic, on exit) — Finalize

1. Run `/wiki:lint --fix` to clean up.
2. Generate a final summary (LLM or template — total rounds, cumulative
   sources, cumulative articles, progress trajectory, total time).
3. Append `research_completed` to `.session-events.jsonl`.
4. Refresh `.session-checkpoint.json` with `status: "completed"` and final
   summary.
5. Delete `.research-session.json`. **Keep** `.session-events.jsonl` and
   `.session-checkpoint.json` (durable provenance).

## 5. Question mode — extra Phase 4.b (LLM)

After standard compile, generate a **playbook** artifact filed back into
`output/`:

```
Generate a playbook that answers the original question using the new wiki
articles.

Original question: "{question}"
Sub-questions covered: [...]
New wiki articles: [
  {"path":"wiki/topics/...md", "title":"...", "summary":"..."}
]

Return JSON ONLY:

{
  "title": "...",
  "frontmatter": {"type":"playbook", "sources":["wiki/.../*.md"], ...},
  "sections": [
    {"heading": "## The question", "markdown":"..."},
    {"heading": "## Answer in one paragraph", "markdown":"..."},
    {"heading": "## Key findings", "markdown":"<bulleted, one per sub-question>"},
    {"heading": "## Actionable steps", "markdown":"<numbered list>"},
    {"heading": "## Examples", "markdown":"..."},
    {"heading": "## Sources", "markdown":"<links to wiki articles>"}
  ],
  "derived_theses": [
    "<testable claim 1>",
    "<testable claim 2>"
  ]
}
```

Save to `output/playbook-<slug>-<YYYY-MM-DD>.md` using chunked writes.

## 6. Gap-closing offer (deterministic)

After Phase 5's report (in single-round and `--plan` mode, but **not** in
`--min-time` rounds — those manage their own gap-to-round pipeline), if
`remaining_gaps.length >= 2`:

```
Pick which gaps to research in parallel (all run at once):

1. Dose-response curves for red vs near-infrared wavelengths
2. Long-term safety data for daily exposure
3. Device comparison (clinical vs consumer panels)
4. Combination protocols with other therapies
5. Pediatric and geriatric contraindications

Enter numbers (e.g. 1,2,4), "all", or "skip":
```

On selection, treat each picked gap as a path in a `--plan` dispatch and
run a single round. No re-confirmation.

## 7. Output schemas summary

The four LLM-driven artifacts whose JSON shapes drive the pipeline:

- **Phase 1 scope JSON**: existing coverage, gaps, search angles.
- **Phase 2 agent JSON** (one per role): sources with content + quality.
- **Phase 2b credibility JSON** (one per source): score + tier + bias flags.
- **Phase 5 round report JSON**: progress_score, gaps, recommendation.
- **Phase 6 reflection JSON**: cross-round connections, scored next gaps.

## 8. Persistence summary

| File | Written by |
|------|------------|
| `raw/<type>/<date>[-p<N>]-<slug>.md` | Phase 3 (per source) |
| `wiki/<category>/<slug>.md` | Phase 4 (compile) |
| `output/playbook-<slug>-<date>.md` | Phase 4.b (question mode only) |
| `.research-session.json` | Phase 0..7 (ephemeral) |
| `.session-events.jsonl` | Phases 0/5/6/8 (durable, append-only) |
| `.session-checkpoint.json` | Phases 0/5/6/8 (durable, atomic write) |
| `log.md` | Phase 5/8 |

## 9. Edge cases

- **Agent returns nothing useful**: still scored. If many agents return
  nothing, Phase 5 progress_score drops, possibly triggering low-yield.
- **Source already ingested**: dedup by URL in Phase 2b drops it. Compile
  Phase 4 may still update an existing article with the new agent's
  perspective.
- **Network failure mid-round**: ephemeral session preserves prior round
  state. On resume, the user picks `Continue from Round N+1` and only the
  pending round repeats.
- **`--plan` path crashes mid-execution**: ephemeral state shows
  `paths[].status="in_progress"`. On resume, in-progress is treated as
  pending. Prefixed filenames make raw deduplication trivial.
- **Time budget runs out mid-round**: Phase 7 won't start a new round whose
  duration would exceed budget by 50%+, but the current round always
  finishes.

## 10. Java reimplementation outline

```java
public class ResearchService {
  public ResearchResult run(ResearchRequest req) {
    Mode mode = detectMode(req.input);                             // §2
    if (mode == THESIS) return new ThesisService(...).run(req);    // delegate

    WikiContext wiki = wikis.resolveOrCreate(req);                 // Phase 0
    Session session = req.minTime != null ? Session.create(wiki, req) : null;

    int round = 0;
    Trajectory traj = new Trajectory();
    while (true) {
      round++;
      ScopeResult scope = scopeLLM(wiki, req.topic);               // Phase 1
      List<Path> paths = req.plan ? planPathsLLM(wiki, scope, req.topic) : List.of(Path.single(req.topic));

      List<AgentResult> agentResults = paths.parallelStream()
        .flatMap(p -> dispatchAgentsLLM(p, req).stream())          // Phase 2 (parallel)
        .toList();

      List<Source> deduped = dedup(agentResults);                  // deterministic
      List<ScoredSource> scored = deduped.parallelStream()
        .map(s -> credibilityLLM(s, deduped))                      // Phase 2b
        .filter(s -> s.tier != REJECT)
        .sorted(byQuality())
        .limit(req.sourcesCount())
        .toList();

      ingestService.ingestAll(wiki, scored);                       // Phase 3
      compileService.compileNew(wiki, scored);                     // Phase 4
      if (mode == QUESTION) generatePlaybookLLM(wiki, scope, scored); // Phase 4.b

      RoundReport rr = roundReportLLM(wiki, round, scored);        // Phase 5
      traj.record(rr);
      if (session != null) session.completeRound(rr);

      if (req.minTime == null) {
        offerGapClosing(rr);                                       // §6
        break;
      }
      ReflectionResult refl = reflectLLM(traj.allRounds());        // Phase 6
      applySeeAlsoAdditions(refl);

      if (shouldStop(traj, refl, session, req.minTime)) break;     // Phase 7
    }

    finalizeSession(session, wiki);                                // Phase 8
    return ResearchResult.from(traj);
  }
}
```
