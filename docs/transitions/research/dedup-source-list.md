# dedup-source-list

**Type**: Tool
**Used by**: `research` Phase 2b (before credibility scoring) and after

## Inputs
- combined source list from all agents

## Prompt template
None.

## Procedure
1. Group by exact URL match — keep the first.
2. For remaining sources, compute pairwise content overlap (e.g. shingled hash). If overlap > 80%, keep the one with the higher `quality_score`.
3. Track corroboration: if the same URL or near-duplicate was found by N agents, mark the survivor with `corroboration_count = N - 1`.

## Output
```json
{
  "deduped": [<source>, ...],
  "skipped_duplicates": [{"url":"...","kept_url":"..."}, ...]
}
```
