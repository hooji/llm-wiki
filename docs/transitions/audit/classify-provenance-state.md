# classify-provenance-state

**Type**: Tool
**Used by**: `audit` Pass 4

## Inputs
- wiki root

## Prompt template
None.

## Procedure
Check existence of:
- `.session-events.jsonl`
- `.session-checkpoint.json`
- `.research-session.json`
- `.thesis-session.json`

| State | When |
|-------|------|
| `replayable` | `.session-events.jsonl` exists |
| `partial` | only `.session-checkpoint.json` exists |
| `missing` | neither |

## Output
```json
{
  "state":"replayable"|"partial"|"missing",
  "files_present":["..."]
}
```

## Notes
Diagnostic, not punitive. Missing event logs are a limitation, not a content failure.
