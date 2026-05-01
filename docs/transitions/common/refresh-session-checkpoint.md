# refresh-session-checkpoint

**Type**: Tool
**Used by**: `research`, `thesis`, `audit`

## Inputs
- wiki root
- checkpoint object

## Prompt template
None.

## Procedure
Atomic write to `<wiki-root>/.session-checkpoint.json`:
1. Write to `.session-checkpoint.tmp`.
2. Rename to `.session-checkpoint.json`.

Standard fields:
```json
{
  "updated_at": "<ISO 8601>",
  "command": "research|thesis|audit",
  "session_id": "...",
  "status": "in_progress|completed|interrupted|failed",
  "topic|thesis|scope": "...",
  "current_round": <int>,
  "summary": { ... },
  "artifacts": [{"path": "...", "sha256": "..."}]
}
```

## Output
None.

## Notes
Durable. Not deleted on normal completion. Used by `query --resume` and `audit` provenance pass when `.session-events.jsonl` is missing.
