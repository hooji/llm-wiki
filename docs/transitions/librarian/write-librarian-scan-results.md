# write-librarian-scan-results

**Type**: Tool
**Used by**: `librarian` Step 4 (final), `audit` (reads this file in Pass 1)

## Inputs
- per-article scan results from checkpoint
- scan id, wiki, threshold

## Prompt template
None.

## Procedure
Atomic write to `<wiki>/.librarian/scan-results.json` (the source of truth for other skills):

```json
{
  "scan_id": "...",
  "wiki": "...",
  "completed_at": "...",
  "passes": ["staleness","quality"],
  "threshold": 70,
  "summary": {
    "articles_scanned": <int>,
    "stale_count": <int>,
    "low_quality_count": <int>,
    "avg_staleness": <int>,
    "avg_quality": <int>,
    "worst_staleness": {"article":"...","score":<int>},
    "worst_quality":   {"article":"...","score":<int>}
  },
  "articles": {
    "wiki/.../a.md": {
      "staleness": {...},
      "quality": {...},
      "tier": 1|2
    }
  }
}
```

After writing, delete `.librarian/checkpoint.json`.

## Output
None.
