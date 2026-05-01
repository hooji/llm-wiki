# delete-ephemeral-session

**Type**: Tool
**Used by**: `research`, `thesis` on normal completion

## Inputs
- wiki root
- session filename (`.research-session.json` or `.thesis-session.json`)

## Prompt template
None.

## Procedure
Delete the named file. Leave `.session-events.jsonl` and `.session-checkpoint.json` untouched (those are durable provenance).

## Output
None.

## Notes
On abnormal exit (interruption, crash) the file persists with `status: in_progress`. The next invocation detects it and asks "Continue or start fresh?".
