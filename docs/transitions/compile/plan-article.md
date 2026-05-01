# plan-article

**Type**: LLM (one call per article)
**Used by**: `compile` Step 5

## Inputs
- target slug + kind (`concept` / `topic` / `reference`)
- contributing source extracts (from `extract-source-signals`)
- existing related articles (from survey, used for cross-reference plans)
- mode: `NEW_CREATE` or `EXISTING_UPDATE`

## Prompt template
```
You are planning a new wiki article for an LLM-compiled knowledge base.

Article slug: {slug}
Article kind: {concept|topic|reference}

Sources contributing to this article:
[
  {
    "path": "raw/papers/2026-...md",
    "title": "...",
    "summary": "...",
    "extracts_about_this_concept": ["..."],
    "credibility_score": 4,
    "evidence_strength": "rct"
  }, ...
]

Existing related articles:
[{"slug":"self-attention","title":"Self-Attention","summary":"..."}, ...]

Return JSON ONLY:

{
  "title": "<title case display name>",
  "aliases": ["alternate name", ...],
  "summary": "<2-3 sentence summary for index>",
  "tags": ["..."],
  "confidence": "high" | "medium" | "low",
  "confidence_rationale": "<one sentence>",
  "volatility": "hot" | "warm" | "cold",
  "abstract_paragraph": "<single paragraph: what is this and why does it matter>",
  "sections": [
    {"heading": "## Background", "intent": "what to cover, 1-3 sentences"},
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
- high: multiple credibility-≥4 sources agree, OR single peer-reviewed meta/systematic review
- medium: single credible source, OR partial agreement, OR recent findings not yet replicated
- low: single non-peer-reviewed source, OR sources disagree, OR anecdotal only
```

For `EXISTING_UPDATE` mode, additionally include the current article body in the prompt and ask for a **delta** (added sections, new See-Also links, new sources to attribute) instead of a full plan.

## Output
JSON as specified — used as the article skeleton for chunked writes.
