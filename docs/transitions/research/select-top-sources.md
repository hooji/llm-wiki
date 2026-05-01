# select-top-sources

**Type**: Tool
**Used by**: `research` Phase 3 (just before ingest)

## Inputs
- scored, deduped source list (with `credibility_score` and `agent_quality_score`)
- `--sources <N>` (default 5; retardmax default 15)

## Prompt template
None.

## Procedure
1. Filter out `tier: reject`.
2. Sort by `credibility_score × agent_quality_score` desc.
3. Take top N.
4. The remainder go to the round report's "skipped sources" list with reason.

## Output
```json
{
  "to_ingest": [<source>, ...],
  "skipped": [{"url":"...","reason":"low quality" | "duplicate" | "paywall" | "thin"}, ...]
}
```
