# write-ephemeral-session

**Type**: Tool
**Used by**: `research --min-time`, `research --plan`, `thesis --min-time`

## Inputs
- wiki root
- command (`research` | `thesis`)
- session object

## Prompt template
None.

## Procedure
Atomic write to `<wiki-root>/.research-session.json` (or `.thesis-session.json` for thesis mode):

```json
{
  "session_id": "YYYY-MM-DD-HHmmss",
  "topic|thesis": "...",
  "mode": "single|plan",
  "start_time": "...",
  "min_time_budget": "2h",
  "current_round": 1,
  "paths": [...],            // plan mode only
  "rounds_completed": [...],
  "cumulative_sources": <int>,
  "cumulative_articles": <int>,
  "status": "in_progress"
}
```

Rewritten in place after each round.

## Output
None.

## Notes
Ephemeral — for crash recovery only. Deleted on normal completion via `delete-ephemeral-session`. Never committed to git.
