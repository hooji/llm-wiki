# detect-interrupted-session

**Type**: Tool
**Used by**: `query --resume` R1, `research` Phase 0 (resume detection)

## Inputs
- wiki root

## Prompt template
None.

## Procedure
1. Try reading `<wiki>/.research-session.json`.
2. Try reading `<wiki>/.thesis-session.json`.
3. Return whichever exists with `status: "in_progress"`.

## Output
```json
{
  "found": true | false,
  "kind": "research" | "thesis" | null,
  "topic_or_thesis": "...",
  "current_round": <int>,
  "cumulative_sources": <int>,
  "last_round_gaps_or_verdict_direction": "..."
}
```
