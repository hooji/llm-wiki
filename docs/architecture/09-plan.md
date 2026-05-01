# Capability: Plan

> Wiki-grounded implementation plans. Reads the knowledge base, interviews
> you about requirements, fills gaps with targeted research, and produces a
> phased plan citing wiki articles as evidence. `--format rfc|adr|spec`.

Plan is the bridge from "what we know" to "what we'll build". It's a
six-stage pipeline that takes the user's goal, reads the wiki deeply,
asks clarifying questions, fills small gaps with targeted research, then
emits a phased plan in one of four formats grounded in wiki citations.

## 1. Inputs

| Flag | Meaning |
|------|---------|
| `<goal>` | What to build. Everything that's not a flag. |
| `--wiki <name>` | Primary knowledge source (subject matter) |
| `--with <wiki>` | Supplementary wiki for craft/skill knowledge. Multiple allowed. |
| `--local` | Use project-local `.wiki/` |
| `--quick` | Skip interview and gap research; plan from wiki content only |
| `--no-interview` | Skip Stage 2 |
| `--no-research` | Skip Stage 3 |
| `--format rfc \| adr \| roadmap \| spec` | Output structure (default `roadmap`) |

## 2. Workflow

### Stage 0 (deterministic) — Hub + wiki resolution

Standard prelude. Plan is a wiki-required command — if no wiki exists,
stop with `No wiki found (or no articles). Run /wiki init and /wiki:research first to build a knowledge base.`

### Stage 1 (LLM) — Context assembly

Read the wiki deeply:

1. (deterministic) Read master `_index.md`, all category indexes
   (concepts, topics, references). Stale-check first.
2. (deterministic) Grep wiki for key terms from the goal (synonyms, related
   concepts).
3. (deterministic) Read every article that matched the index summaries or
   grep. Follow See-Also one hop.
4. (deterministic) Read sibling wiki `_index.md` files via
   `HUB/wikis.json`. Note overlap.
5. (deterministic) Load `--with` wikis: read their `_index.md` and the
   relevant articles (matched by goal terms + a quick LLM relevance pass).

Then ask the LLM to summarize what the wiki knows about this goal:

**Prompt:**

```
You are assembling research context for an implementation plan.

Goal: "{goal}"

Wiki articles read (full content, in order):
[
  {"path":"...","title":"...","confidence":"high","content":"<full body>"},
  ...
]

Sibling wiki overlap (only _index summaries):
[{"wiki":"...","article":"...","summary":"..."}, ...]

--with wiki content (craft/skill, optional):
[
  {"wiki":"<name>","articles":[{"path":"...","content":"..."}]}
]

Return JSON ONLY:

{
  "directly_relevant": [
    {"path":"...","title":"...","contributes":"<one phrase>"}
  ],
  "supporting_context": [
    {"path":"...","title":"...","relevant_because":"<phrase>"}
  ],
  "constraints_from_wiki": ["<constraint>", ...],
  "risks_from_contrarian_articles": [
    {"risk":"...","source":"<path>"}
  ],
  "knowledge_gaps": [
    {"gap":"<specific gap>","why_blocking":"<phrase>"}
  ],
  "summary_for_user": "<2-3 sentence summary of what the wiki knows>"
}
```

Present the context summary to the user before continuing.

### Stage 2 (LLM, skip if `--no-interview` or `--quick`) — Interview

Ask the user 3-7 clarifying questions informed by the wiki content. The
interview is the LLM's chance to surface what the wiki *can't* tell us.

**Prompt:**

```
You are conducting a brief interview to surface requirements the wiki
can't answer.

Goal: "{goal}"
Wiki context summary: "{stage_1_summary_for_user}"

Generate 3-7 clarifying questions. Good questions ask things the wiki
cannot know:
- Constraints the wiki can't know (timeline, budget, team size)
- Scale parameters (volume, traffic, growth assumptions)
- Priority tradeoffs (the wiki documents both X and Y; which matters more here?)
- Edge cases (the wiki notes [risk]; how should we handle it?)
- Non-functional requirements (perf targets, backwards compat needs)

DO NOT ask things the wiki already answers.

Return JSON ONLY:

{
  "questions": [
    {
      "id": 1,
      "question": "<the question>",
      "why_asked": "<one phrase>",
      "expected_answer_shape": "free-text" | "yes-no" | "numeric" | "choice"
    }
  ]
}
```

Present all questions at once. Wait for the user's answers (free-text reply).

### Stage 3 (LLM + WebFetch, skip if `--no-research` or `--quick`) — Gap research

For each `knowledge_gap` from Stage 1 (and any new gaps surfaced by Stage
2 answers), run a small targeted research loop. **Lightweight** — this is
not a full Research dispatch.

#### Per gap

##### Step 3a (LLM) — Frame the search

```
You are framing a tight web search to fill a knowledge gap.

Gap: "{gap}"
Why it blocks the plan: "{why_blocking}"
Goal: "{goal}"

Return JSON ONLY:

{
  "queries": ["<query 1>", "<query 2>"],            // 1-2 only
  "what_to_extract": "<3-5 word description of the answer shape>"
}
```

##### Step 3b (deterministic) — WebSearch

Run each query, take top 3 hits.

##### Step 3c (LLM, per hit) — WebFetch summary

```
Extract the answer to this gap from the page.

Gap: "{gap}"
What to extract: "{what_to_extract}"

Page URL: {url}
Page content (between fences):
```
{page_markdown}
```

Return JSON ONLY:

{
  "relevant": true | false,
  "extract_bullets": ["<finding>", ...],            // 3-5 max
  "credibility": 1-5,
  "ingest_into_wiki": true | false                  // true if this would help future plans
}
```

##### Step 3d (deterministic) — Decide on ingestion

For each hit with `ingest_into_wiki: true`, run the Ingest capability ([03-ingest.md](03-ingest.md)) so the source persists in `raw/`. This is
the bonus side-effect: gap research enriches the wiki for next time.

If gaps are large (say, more than 3 with `relevant: true` hits each),
recommend `/wiki:research` as a follow-up rather than continuing to
expand here.

### Stage 4 (deterministic) — Synthesis

Merge into an internal context object (not shown to user):

```
{
  "goal": "...",
  "wiki_evidence": {
    "directly_relevant": [...],
    "supporting": [...],
    "key_facts": [...]              // extracted from Stage 1 article reads
  },
  "user_requirements": {            // from Stage 2 answers
    "<question_id>": "<answer>"
  },
  "gap_fills": [
    {"gap": "...", "findings": [...], "sources": ["url1","url2"]}
  ],
  "constraints": [...],             // wiki + interview
  "risks": [...]                    // wiki contrarian + interview risks
}
```

### Stage 5 (LLM, chunked writes) — Plan generation

The prompt branches on `--format`. The structure of each format is
documented below; the LLM call returns JSON with sections, then the
deterministic step renders markdown.

**Common prompt header:**

```
You are writing an implementation plan grounded in a wiki knowledge base.

Goal: "{goal}"
Format: {roadmap|rfc|adr|spec}
Wiki context (synthesized):
{stage_4_context_json}

Citations: every architectural decision must cite a wiki article using
the dual-link format:
   [[slug|Display]] ([Display](../path/slug.md))
or the standard markdown link form for output:
   [Display](../../wiki/<category>/<slug>.md)
```

#### Roadmap (default)

Append:

```
Return JSON ONLY:

{
  "executive_summary": "<2-3 sentences: what we're building, why, key design decisions>",
  "architecture_decisions": [
    {
      "title": "...",
      "context_from_wiki": [{"path":"...","contributes":"..."}],
      "options": [
        {"name":"Option A","description":"...","wiki_basis":"<path or null>"},
        {"name":"Option B","description":"...","wiki_basis":"<path or null>"}
      ],
      "decision": "Option <X>",
      "rationale": "<one sentence with wiki citations>",
      "consequences": "..."
    }
  ],
  "phases": [
    {
      "n": 1,
      "title": "...",
      "estimated_effort": "<S/M/L or days>",
      "goal": "...",
      "tasks": ["...", "..."],
      "dependencies": ["..."],
      "validation": "<how to verify this phase works>",
      "wiki_grounding": [{"path":"...","says":"<phrase>"}]
    }
  ],
  "risks_table": [
    {"risk":"...","source":"<wiki path>","mitigation":"..."}
  ],
  "open_questions": ["..."],
  "sources_consulted": [
    {"path":"...","what_drawn":"..."}
  ]
}
```

Render as markdown using the deterministic template:

```markdown
# Plan: {goal}

> Generated from [{wiki-name}]({path}) wiki ({N} articles consulted)

## Executive Summary
{executive_summary}

## Architecture Decisions

### Decision 1: {title}
**Context**: {citations} document that...
**Options considered**:
- Option A: ... (per [...])
- Option B: ... (per [...])
**Decision**: {decision} because {rationale}
**Consequences**: {consequences}

...

## Implementation Phases

### Phase 1: {title} (estimated effort: {estimated_effort})
**Goal**: ...
**Tasks**:
- [ ] Task 1
- [ ] Task 2
**Dependencies**: ...
**Validation**: ...
**Wiki grounding**: Based on [Article](path) which says...

...

## Risks & Mitigations
| Risk | Source | Mitigation |
|------|--------|------------|
| ... | [Wiki article](path) | ... |

## Open Questions
- ...

## Sources Consulted
- [Article 1](path) — what was drawn from it
```

#### RFC (`--format rfc`)

Append:

```
Return JSON ONLY:

{
  "context_and_scope": "<from wiki evidence + interview>",
  "goals": ["..."],
  "non_goals": ["..."],
  "design": {
    "overview": "...",
    "components": [{"name":"...","purpose":"...","wiki_basis":"<path or null>"}],
    "data_flows": ["..."]
  },
  "alternatives_considered": [
    {"name":"...","why_not_chosen":"...","wiki_basis":"<path or null>"}
  ],
  "cross_cutting": {
    "security": "...",
    "performance": "...",
    "backwards_compat": "..."
  }
}
```

Renders to a Google/Uber-style RFC document.

#### ADR (`--format adr`)

Generate **one ADR per major decision** (the same `architecture_decisions[]`
shape as roadmap, but each ADR is its own file or section). MADR variant:

```markdown
# ADR-NNN: {title in present-tense imperative}

## Status
Proposed

## Context
{organizational situation drawn from wiki}

## Decision Drivers
- ...

## Considered Options
- Option A — ...
- Option B — ...
- Option C — ...

## Decision Outcome
Chosen: Option {X}, because {rationale with wiki citations}

## Consequences
{positive and negative}

## More Information
- [Wiki article 1](path) — what it informed
```

#### Spec (`--format spec`)

Append:

```
Return JSON ONLY:

{
  "system_architecture": {
    "ascii_diagram": "<diagram in ascii or mermaid>",
    "components": [{"name":"...","purpose":"...","interface":"..."}]
  },
  "api_design": {
    "endpoints": [{"method":"GET","path":"/...","request":"...","response":"..."}],
    "data_models": [{"name":"...","fields":[{"name":"...","type":"...","constraints":"..."}]}]
  },
  "implementation_details": [
    {"area":"<component>","pattern":"...","wiki_basis":"<path>"}
  ],
  "testing_strategy": {
    "unit": "...",
    "integration": "...",
    "e2e": "..."
  },
  "deployment": {
    "migration_path": "...",
    "rollback_plan": "..."
  }
}
```

### Stage 6 (deterministic) — Save & log

1. Decide path:
   - If `--project <slug>`:
     `output/projects/<slug>/plan-<slug>-<YYYY-MM-DD>.md`.
   - else: `output/plan-<goal-slug>-<YYYY-MM-DD>.md`.
2. Frontmatter:
   ```yaml
   ---
   title: "Plan: {goal}"
   type: plan
   format: roadmap | rfc | adr | spec
   sources: [wiki articles used, from sources_consulted]
   generated: YYYY-MM-DD
   project: <slug>             # optional
   ---
   ```
3. Write skeleton (frontmatter + executive summary / context-and-scope /
   first ADR / system architecture) first. Then Edit-append remaining
   sections one at a time (chunked writes, core principle #9).
4. Update `output/_index.md` (best-effort), master `_index.md` Recent
   Changes.
5. Append to `log.md`:
   ```
   ## [YYYY-MM-DD] plan | "{goal}" → output/plan-<slug>-<date>.md (N articles consulted, M decisions, P phases)
   ```
6. Report path, key decisions, open questions to the user.

## 3. Output schemas summary

LLM-driven shapes:

- **Stage 1**: context inventory (relevant articles, gaps, constraints, risks).
- **Stage 2**: interview questions.
- **Stage 3a/3c**: query framing + per-hit extraction.
- **Stage 5**: format-specific plan JSON (roadmap/rfc/adr/spec).

## 4. Persistence summary

| File | Written by |
|------|------------|
| `output/plan-<slug>-<date>.md` (or in `output/projects/<slug>/`) | Stage 6 (chunked) |
| `raw/<type>/<date>-<slug>.md` | Stage 3d (gap research ingestion bonus) |
| `output/_index.md`, master `_index.md` | Stage 6 (best-effort) |
| `log.md` | Stage 6 |

No durable session state.

## 5. Edge cases

- **Wiki has no relevant articles**: Stage 1's `directly_relevant` is
  empty. Tell the user "The wiki doesn't cover this goal yet. Suggest
  running `/wiki:research <topic> --new-topic --min-time 1h` first."
  Don't try to plan from nothing.
- **Interview answers contradict wiki**: Stage 5 must surface the conflict
  in `architecture_decisions[].rationale` rather than silently overriding
  one or the other.
- **`--quick`**: Stages 2 and 3 are skipped; `user_requirements` and
  `gap_fills` in Stage 4 are empty.
- **`--no-interview` but with gaps**: Stage 3 still runs. `--no-research`
  skips it.
- **Plan format the user didn't expect**: Stage 5 always uses the
  format the user requested. If the goal is more like a single decision,
  `--format adr` is more honest than a 5-phase roadmap.
- **Output project slug missing**: if `--project <slug>` is set, verify
  `output/projects/<slug>/WHY.md` exists before writing. If not, stop
  with `Project "<slug>" does not exist. Create it first with /wiki:project new <slug> "goal".`

## 6. Java reimplementation outline

```java
public class PlanService {
  public PlanResult plan(PlanRequest req) {
    WikiContext wiki = wikis.resolveRequired(req);

    Stage1Context ctx1 = assembleContextLLM(wiki, req);            // Stage 1
    presentContextSummary(ctx1);

    Map<Integer, String> answers = (req.quick || req.noInterview)
        ? Map.of()
        : interviewLLM(ctx1, req.goal);                            // Stage 2

    List<GapFill> fills = (req.quick || req.noResearch)
        ? List.of()
        : ctx1.knowledgeGaps.parallelStream()
            .map(g -> fillGap(wiki, g, req.goal))                  // Stage 3
            .toList();

    SynthCtx ctx4 = synthesize(ctx1, answers, fills);              // Stage 4
    PlanJson p = planLLM(ctx4, req.format);                        // Stage 5

    Path out = writePlanChunked(wiki, p, req);                     // Stage 6
    bestEffort(() -> updateIndexes(wiki, out, p));
    appendLog(wiki, "plan", summary(p, out));
    return new PlanResult(out, p);
  }

  private GapFill fillGap(WikiContext w, Gap g, String goal) {
    QueryFraming f = frameSearchLLM(g, goal);
    List<SearchHit> hits = f.queries.stream().flatMap(q -> webSearch.run(q).stream()).toList();
    List<HitExtract> extracts = hits.stream()
        .map(h -> extractLLM(g, h))
        .filter(e -> e.relevant)
        .toList();
    extracts.stream()
        .filter(e -> e.ingestIntoWiki)
        .forEach(e -> ingestService.ingest(w, e.toIngestRequest()));
    return new GapFill(g, extracts);
  }
}
```
