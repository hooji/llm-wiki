# Capability: Thesis mode

> Start from a claim. Agents split across supporting, opposing, mechanistic,
> meta, and adjacent angles. Output is a verdict — not a summary. Round two
> fights confirmation bias.

Thesis mode is invoked as `/wiki:research --mode thesis "<claim>"` (the
older `/wiki:thesis` command is a deprecated shim). It shares the agent
swarm, credibility scoring, ingestion, compilation, multi-round session
registry, and termination logic with [01-research.md](01-research.md). What changes:

- **Phase 0** (decompose claim) replaces input-detection.
- **Wiki-setup** writes a thesis file under `wiki/theses/`.
- **Phase 2** uses for/against/mechanistic/meta/adjacent agents instead of
  academic/technical/applied/news/contrarian, and each agent applies the
  thesis itself as a **scope filter** that drops tangential sources.
- **Phase 4** updates the thesis file with structured evidence tables.
- **Phase 5** renders a **verdict** (supported / partially-supported /
  contradicted / insufficient-evidence / mixed).
- **`--min-time` Round 2+** runs anti-confirmation-bias by drilling into the
  *weaker* side of round 1.

This document spells out only the deltas. Read [01-research.md](01-research.md) first.

## 1. Inputs

Same as Research, plus:

- `--mode thesis "<claim>"` — required activator, OR
- input contains `prove that` / `is it true that` / `verify` / `test the
  claim` / `test the hypothesis` (the claim text is the input minus the
  signal words).

## 2. Phase 0 — Decompose the claim (LLM)

Before any research, ask the LLM to break the thesis down:

**Prompt:**

```
You are decomposing a thesis for a multi-agent investigation pipeline.

Thesis: "{thesis_statement}"

Return JSON ONLY:

{
  "core_claim": "<the central assertion in one sentence>",
  "key_variables": ["<var1>", "<var2>", "<var3>"],
                                            // the specific things being connected
                                            // (e.g. "intermittent fasting", "neuroinflammation",
                                            //  "glymphatic upregulation")
  "testable_prediction": "<what would be true if the thesis is correct>",
  "falsification_criteria": "<what evidence would disprove it>",
  "scope_boundary": "<what is NOT part of this thesis — the bloat filter>",
  "sub_claims": [
    "<sub-claim 1>", "<sub-claim 2>", ...    // optional 2-4 finer-grained pieces
  ]
}
```

Present to the user for confirmation before proceeding.

## 3. Wiki setup — thesis file

In addition to the standard wiki resolution, create `wiki/theses/<slug>.md`:

```markdown
---
title: "Thesis: {thesis_statement}"
type: thesis
status: investigating
created: YYYY-MM-DD
updated: YYYY-MM-DD
verdict: pending
confidence: pending
core_claim: "{core_claim}"
key_variables: [{key_variables}]
falsification: "{falsification_criteria}"
---

# Thesis: {thesis_statement}

## Core Claim
{core_claim}

## Key Variables
- {var1}
- {var2}

## Testable Prediction
{testable_prediction}

## Falsification Criteria
{falsification_criteria}

## Evidence For
(populated during research)

## Evidence Against
(populated during research)

## Nuances & Caveats
(populated during research)

## Verdict
**Status**: Investigating
```

The C11 placement rule routes `type: thesis` to `wiki/theses/`. This file
is co-edited by Phase 4 and Phase 5; never overwritten.

## 4. Phase 2 — Thesis-directed agents

Replace the standard role table with:

| Agent | Focus | Lens |
|-------|-------|------|
| **Supporting** | Evidence that supports the thesis | Studies, data, mechanisms confirming the claim. Strongest evidence first. |
| **Opposing** | Evidence that contradicts the thesis | Counter-evidence, failed replications, alternative explanations. Steelman the opposition. |
| **Mechanistic** | HOW/WHY the thesis could be true or false | Underlying mechanisms, pathways, causal chains connecting the variables. |
| **Meta/Review** | Meta-analyses, systematic reviews, expert consensus | Aggregate evidence — these carry the most weight. |
| **Adjacent** | Related findings that nuance the thesis | Edge cases, moderating variables, conditions under which the thesis holds or fails. |

Deep mode adds:

| Agent | Focus |
|-------|-------|
| **Historical** | Evolution of thinking on this claim |
| **Quantitative** | Effect sizes, confidence intervals, dose-response data |
| **Confounders** | Variables that could make a spurious correlation look causal |

**Per-agent prompt (overrides the standard template):**

```
You are investigating: "{thesis_statement}"

Core claim: {core_claim}
Key variables: {key_variables}
Falsification criteria: {falsification_criteria}
Scope boundary (sources outside this scope MUST be skipped): {scope_boundary}

Your lens: {Agent Focus} — {Thesis Lens description}

Run 2-3 WebSearch queries varied by your lens. Use WebFetch on promising
results.

For each source you would consider, evaluate:
- Relevance: direct | indirect | tangential — SKIP tangential.
- Evidence strength: meta-analysis > RCT > cohort > case > expert opinion > anecdotal
- Direction relative to the thesis: supports | opposes | nuances
- Quality (1-5)

The thesis is the BLOAT FILTER: if the source does not bear on the
key_variables, do not return it.

Return JSON ONLY:

{
  "agent_role": "{role}",
  "queries_run": ["..."],
  "sources": [
    {
      "title": "...",
      "url": "...",
      "publication_date": "YYYY-MM-DD" | null,
      "authors": ["..."],
      "relevance": "direct" | "indirect",
      "evidence_strength": "meta-analysis" | "rct" | "cohort" | "case" | "expert-opinion" | "anecdotal",
      "direction": "supports" | "opposes" | "nuances",
      "quality_score": 1-5,
      "key_finding": "<1-2 sentence summary of what this source says about the thesis>",
      "content_markdown": "<extracted body>"
    }
  ],
  "skipped_tangential_count": <int>
}

Sort `sources` by (relevance × evidence_strength), strongest first.
```

A high `skipped_tangential_count` is good — the bloat filter is working. The
report surfaces this.

## 5. Phase 2b — Credibility (unchanged)

Same scoring rubric as Research. The `direction` field carries forward
into Phase 4.

## 6. Phase 3 — Ingest (unchanged)

Same as Research. Tag the raw frontmatter with the additional field
`thesis: <slug>` so future audits can find the thesis the source informs.

## 7. Phase 4 — Compile + thesis-file evidence tables

After standard compilation, the LLM updates the thesis file's evidence
sections. **Edit** the thesis file (do not overwrite):

**Prompt:**

```
You are updating the evidence tables on a thesis file.

Thesis file (current state):
{thesis_md_with_existing_evidence}

New sources just ingested + scored:
[
  {
    "raw_path":"raw/papers/...md",
    "title":"...",
    "credibility_tier":"high",
    "evidence_strength":"meta-analysis",
    "direction":"supports",
    "key_finding":"...",
    "wiki_articles_now_citing":["wiki/concepts/...md"]
  },
  ...
]

For each new source, decide which section it belongs in (Evidence For,
Evidence Against, Nuances & Caveats) and assign a combined strength tag
"Strong" / "Moderate" / "Weak" using:

  Strong: credibility=high AND evidence_strength ∈ {meta-analysis, rct}
  Moderate: credibility=high AND evidence_strength=cohort, OR
            credibility=medium AND evidence_strength ∈ {meta-analysis, rct, cohort}
  Weak: everything else (single case, expert opinion, anecdotal)

Return JSON ONLY:

{
  "edits": [
    {
      "section": "Evidence For" | "Evidence Against" | "Nuances & Caveats",
      "row": {
        "strength": "Strong" | "Moderate" | "Weak",
        "title": "...",
        "evidence": "<one-line summary>",
        "source_link": "[Title](../../raw/.../...md)",
        "wiki_link": "[Concept](../concepts/...md)"
      }
    }
  ],
  "round_evidence_counts": {"for": <int>, "against": <int>, "nuance": <int>}
}
```

Apply edits as appended rows to the corresponding section's table (or create
the table if section is empty). Sort by strength desc within each section.

## 8. Phase 5 — Verdict

The thesis-mode round report extends the standard report with verdict
fields, *and* the thesis file's `## Verdict` section is rewritten.

**Prompt (final round only, or single-round runs):**

```
Render a thesis verdict.

Thesis: "{thesis_statement}"
Cumulative evidence after all rounds:
- Strong supporting: [...]            (raw paths + key findings)
- Strong opposing:   [...]
- Moderate supporting: [...]
- Moderate opposing: [...]
- Weak supporting: [...]
- Weak opposing: [...]
- Nuances & caveats: [...]

Per-round counts:
[ {"round":1,"for":4,"against":2,"verdict_direction":"partially-supported"}, ... ]

Verdict rules:
- supported: clear preponderance of strong evidence in favor; opposing evidence weak/sparse
- partially-supported: supportive overall but with meaningful caveats or moderating conditions
- contradicted: clear preponderance of strong evidence against
- mixed: roughly balanced strong evidence on both sides
- insufficient-evidence: no strong evidence either way

Return JSON ONLY:

{
  "verdict": "supported" | "partially-supported" | "contradicted" | "mixed" | "insufficient-evidence",
  "confidence": "high" | "medium" | "low",
  "summary_2_3_sentences": "...",
  "strongest_supporting_evidence": ["<source title> — <one-liner>", ...],
  "strongest_opposing_evidence": ["..."],
  "key_caveats": ["..."],
  "what_would_change_this_verdict": ["<specific future findings>", ...],
  "suggested_followup_theses": ["<derived testable claim>", ...]
}
```

Apply deterministically:

1. Edit thesis file's `## Verdict` section to the rendered block:
   ```markdown
   ## Verdict
   **Status**: {verdict}
   **Confidence**: {confidence}
   **Summary**: {summary_2_3_sentences}
   **Strongest supporting evidence**: ...
   **Strongest opposing evidence**: ...
   **Key caveats**: ...
   **What would change this verdict**: ...
   **Suggested follow-up theses**: ...
   ```
2. Update thesis file frontmatter:
   ```yaml
   status: completed
   verdict: {verdict}
   confidence: {confidence}
   updated: <today>
   ```

## 9. Multi-round modification — anti-confirmation-bias

When `--min-time` is set:

| Round | Strategy |
|-------|----------|
| 1 | Broad: dispatch all five agent lenses (supporting + opposing + mechanistic + meta + adjacent) |
| 2 | **Drill into the weaker side** of Round 1's evidence. If Round 1 found mostly supporting evidence, Round 2 dispatches **3 opposing agents + 1 confounder agent + 1 meta agent**. If mostly opposing, mirror it. |
| 3+ | Follow-up on specific sub-questions, confounders, moderating variables that came out of Phase 6 reflection |
| Final | Render Phase 5 verdict |

This is the methodological difference between thesis and standard research.
Determined deterministically from Round 1's `evidence_for` vs
`evidence_against` counts:

```
weaker_side = (evidence_for >= evidence_against) ? "opposing" : "supporting"
```

## 10. Session schema delta

Use `.thesis-session.json` instead of `.research-session.json` in the same
wiki root:

```json
{
  "session_id": "...",
  "thesis": "...",
  "core_claim": "...",
  "key_variables": [...],
  "current_round": 2,
  "rounds_completed": [
    {
      "round": 1,
      "evidence_for": 4,
      "evidence_against": 2,
      "verdict_direction": "partially-supported",
      "next_round_focus": "opposing"
    }
  ],
  "status": "in_progress"
}
```

Durable provenance (`.session-events.jsonl`, `.session-checkpoint.json`) is
shared with research — events use `command: "research"`, `mode: "thesis"`.

## 11. Persistence summary (delta)

| File | Written by |
|------|------------|
| `wiki/theses/<slug>.md` | Phase 0 (create) + Phase 4/5 (Edit) |
| `.thesis-session.json` | All phases (ephemeral) |

Everything else is identical to Research.

## 12. Edge cases

- **Thesis is unfalsifiable**: Phase 0 should detect a missing
  `falsification_criteria`. Refuse to proceed and ask the user to refine.
- **All agents return only supportive evidence**: Round 2's anti-bias
  routing intentionally over-weights opposing agents. If Round 2 still
  finds none, the verdict trends `supported (high confidence)` honestly.
- **Mid-thesis ingest into other workflows**: thesis-tagged raw sources
  are still legal targets for `/wiki:compile`; they get standard articles
  in addition to the thesis file's evidence tables.
- **User runs `/wiki:thesis`**: shim that prepends `--mode thesis` to
  arguments and delegates to research.

## 13. Java reimplementation outline

```java
public class ThesisService {
  public ThesisResult run(ResearchRequest req) {
    WikiContext wiki = wikis.resolveOrCreate(req);
    ThesisDecomp d = decomposeLLM(req.input);                       // §2
    Path thesisFile = createThesisFile(wiki, d);                    // §3

    Session session = req.minTime != null ? Session.create(wiki, req, "thesis") : null;
    Trajectory traj = new Trajectory();
    int round = 0;

    while (true) {
      round++;
      List<AgentRole> roles = pickRolesForRound(round, traj);       // §9 anti-bias
      List<AgentResult> ar = roles.parallelStream()
        .map(r -> thesisAgentLLM(d, r, req))                        // §4 per-agent prompt
        .toList();

      List<ScoredSource> scored = scoreCredibility(dedup(ar));      // §5
      ingestService.ingestAll(wiki, scored, /*tagThesis=*/d.slug);  // §6
      compileService.compileNew(wiki, scored);                      // §7 (standard)
      applyEvidenceTableEditsLLM(thesisFile, scored);               // §7 (thesis file)

      RoundReport rr = roundReportLLM(wiki, round, scored, "thesis");
      traj.record(rr);
      if (session != null) session.completeRound(rr);

      if (req.minTime == null) break;
      reflectAndApply(traj);
      if (shouldStop(traj, req.minTime)) break;
    }

    Verdict v = renderVerdictLLM(traj, d);                          // §8
    applyVerdictEdit(thesisFile, v);
    finalizeSession(session, wiki);
    return ThesisResult.from(traj, v);
  }
}
```
