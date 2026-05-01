# dispatch-research-agent

**Type**: Agentic
**Used by**: `research` Phase 2 (parallel — 5 / 8 / 10 agents per round depending on mode)

## Inputs
- topic / sub-question
- agent role (one of: `Academic`, `Technical`, `Applied`, `News/Trends`, `Contrarian`, `Historical`, `Adjacent`, `Data/Stats`, `Rabbit Hole 1`, `Rabbit Hole 2`)
- role-specific focus and search strategy
- Phase-1 existing-coverage summary
- mode flags (`--retardmax` raises searches/agent and lowers quality threshold; `--deep` adds the historical/adjacent/data agents)

## Prompt template
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
      "title":"...",
      "url":"...",
      "quality_score":4,
      "key_findings":["...","..."],
      "why_ingest":"...",
      "content_markdown":"<extracted body, kept verbatim>",
      "publication_date":"YYYY-MM-DD" | null,
      "authors":["..."]
    }
  ]
}
```

Question-mode override: replace `Objective` with `Answer this sub-question: "{sub-question}"`. Add `Your deliverable is evidence that answers this specific question.`

Retardmax delta: 4-5 searches per agent, accept quality 2+, follow citations from pages found, Rabbit-Hole roles chase what THAT references.

## Output
JSON as specified. The agent has its own access to web-search and web-fetch and runs its own internal loop.

## Notes
This is **agentic** rather than a single LLM call: the agent decides which queries to run, fetches multiple URLs, and decides what to return. In a Java reimplementation, model this as an inner loop that calls `webSearch`, `webFetch`, and the LLM iteratively until N quality sources are found or the budget is spent.
