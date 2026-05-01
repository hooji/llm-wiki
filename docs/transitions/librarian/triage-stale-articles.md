# triage-stale-articles

**Type**: Tool
**Used by**: `librarian` Pass 3

## Inputs
- per-article scores from passes 1 and 2

## Prompt template
None.

## Procedure
1. Sort articles by staleness ascending (worst first).
2. Filter to those below `freshness_threshold`.
3. For each, recommend:
   - `refresh` — when `source_freshness` is the worst dimension. Delegate to `/wiki:refresh`.
   - `verify` — when `verification` is worst. User reads + confirms (just bumps `verified:` to today).
   - `expand` — when `integrity` low or `quality_score` low. Suggest `/wiki:research`.
4. Build the triage list to present to the user.

## Output
```json
{
  "triage": [
    {
      "path": "...",
      "score": 31,
      "top_factor": "sources 180d old",
      "recommendation": "refresh"
    },
    ...
  ]
}
```

## Notes
The librarian never auto-applies refresh/expand — those are write operations behind explicit user confirmation.
