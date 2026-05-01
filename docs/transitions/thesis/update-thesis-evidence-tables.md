# update-thesis-evidence-tables

**Type**: LLM
**Used by**: `research --mode thesis` Phase 4 (after standard compile)

## Inputs
- current thesis-file content (with existing evidence tables)
- newly ingested + scored sources from this round (each with `direction`, `evidence_strength`, `credibility_tier`, `key_finding`, related wiki articles)

## Prompt template
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
"Strong" / "Moderate" / "Weak":

  Strong: credibility=high AND evidence_strength ∈ {meta-analysis, rct}
  Moderate: credibility=high AND evidence_strength=cohort, OR
            credibility=medium AND evidence_strength ∈ {meta-analysis, rct, cohort}
  Weak: everything else

Return JSON ONLY:

{
  "edits":[
    {
      "section":"Evidence For"|"Evidence Against"|"Nuances & Caveats",
      "row":{
        "strength":"Strong"|"Moderate"|"Weak",
        "title":"...",
        "evidence":"<one-line summary>",
        "source_link":"[Title](../../raw/.../...md)",
        "wiki_link":"[Concept](../concepts/...md)"
      }
    }
  ],
  "round_evidence_counts":{"for":<int>,"against":<int>,"nuance":<int>}
}
```

## Output
JSON as specified. Orchestrator Edits the thesis file's tables (append rows; sort by strength desc within each section).

`round_evidence_counts` feeds back into `select-anti-bias-roles` for the next round.
