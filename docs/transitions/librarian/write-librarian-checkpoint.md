# write-librarian-checkpoint

**Type**: Tool
**Used by**: `librarian` (after every article scored)

## Inputs
- scan id, wiki, threshold, scope
- per-article result so far

## Prompt template
None.

## Procedure
Atomic write to `<wiki>/.librarian/checkpoint.json`:
1. Write to `.librarian/.checkpoint.tmp`.
2. Rename to `.librarian/checkpoint.json`.

```json
{
  "scan_id": "...",
  "wiki": "...",
  "passes": ["staleness","quality"],
  "scope": "full"|"single",
  "threshold": 70,
  "completed": ["wiki/.../a.md", ...],
  "pending":   ["wiki/.../c.md", ...],
  "results": {
    "wiki/.../a.md": {
      "staleness": {...},
      "quality": {...} | null,
      "tier": 1 | 2,
      "scanned_at": "..."
    }
  }
}
```

## Output
None.

## Notes
Atomic rename ensures partial writes from a crash are detected (missing/unparseable tmp). Checkpoint is deleted at the end of a successful scan.
