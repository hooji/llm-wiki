# score-source-credibility

**Type**: LLM (one call per source, parallelizable)
**Used by**: `research` Phase 2b (after agent return, before ingest)

## Inputs
- one source from agent return
- corroboration count (number of OTHER agents that found a similar source)

## Prompt template
```
You are independently scoring source credibility for a research
ingestion pipeline.

Source:
{
  "title":"...",
  "url":"...",
  "publication_date":"YYYY-MM-DD",
  "authors":["..."],
  "found_by_agent":"Academic",
  "agent_quality_score":4,
  "key_findings":[...],
  "corroboration_count":2
}

Rubric:
+2 if peer-reviewed (DOI, journal, conference, PubMed, arxiv with venue)
+1 if recent (≤3 years)
0  if 3-10 years old
-1 if >10 years (unless foundational/landmark)
+1 if known author/institution
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
  "bias_flags": ["industry-sponsored","vendor-primary",...]
}
```

Tier mapping:
- 4-6 → high (ingest with confidence:high)
- 2-3 → medium (ingest with confidence:medium)
- 0-1 → low (ingest only if unique angle, confidence:low)
- <0 → reject (skip)

Retardmax: lower the rejection floor — accept Medium and above without filtering, but still score so confidence tags carry forward.

## Output
JSON as specified.

## Notes
"Fox guarding the henhouse" prevention — separates fetcher (the agent) from rater (this independent pass).
