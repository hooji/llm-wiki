# read-durable-provenance

**Type**: Tool
**Used by**: `query --resume` R2 (fallback when no active session), `audit` Pass 4

## Inputs
- wiki root

## Prompt template
None.

## Procedure
1. Read `<wiki>/.session-checkpoint.json` if it exists.
2. Read the tail of `<wiki>/.session-events.jsonl` (last ~20 lines).
3. Return both.

## Output
```json
{
  "checkpoint": {...} | null,
  "recent_events": [...],
  "provenance_state": "replayable" | "partial" | "missing"
}
```

Classification:
- `replayable`: `.session-events.jsonl` exists.
- `partial`: only `.session-checkpoint.json` exists.
- `missing`: neither.
