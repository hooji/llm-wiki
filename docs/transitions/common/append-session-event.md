# append-session-event

**Type**: Tool
**Used by**: `research`, `thesis`, `audit`

## Inputs
- wiki root
- event object

## Prompt template
None.

## Procedure
Atomically append one JSON object (one line, no trailing newline beyond `\n`) to `<wiki-root>/.session-events.jsonl`. Open in append mode.

Recommended fields:
| Field | Type |
|-------|------|
| `ts` | ISO 8601 timestamp |
| `command` | `research` / `audit` / `output` / `refresh` |
| `phase` | `start` / `round` / `reflection` / `scan` / `finish` |
| `event` | stable event name |
| `session_id` | correlates entries |
| `topic` / `thesis` / `scope` | human-readable target |
| additional per-event fields | per the event schema |

## Output
None. The file becomes longer.

## Notes
Durable provenance — preserved after normal completion. Audit's provenance pass classifies the wiki as `replayable` when this file exists.
