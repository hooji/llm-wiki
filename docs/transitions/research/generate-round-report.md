# generate-round-report

**Type**: LLM
**Used by**: `research` Phase 5 (every round)

## Inputs
- this round's: agents launched, sources found/ingested/skipped, articles created/updated, cross-refs added, average credibility
- existing wiki article count (for cross-ref cap)
- previous round's progress score (for trajectory)

## Prompt template
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
    "sources":<int>,"articles":<int>,"cross_refs":<int>,"credibility":<int>
  },
  "confidence_map": [
    {"article":"<path>","confidence":"high|medium|low","why":"<phrase>"}
  ],
  "new_connections": ["<a-slug ↔ b-slug — relationship>", ...],
  "remaining_gaps": [
    {"gap":"<specific gap>","why_matters":"<phrase>"}
  ],
  "suggested_followups": ["<command or topic>", ...],
  "termination_recommendation": "continue" | "early_complete" | "low_yield_warning"
}
```

## Output
JSON as specified. Drives `evaluate-termination` next.
