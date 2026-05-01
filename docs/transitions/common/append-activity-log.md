# append-activity-log

**Type**: Tool
**Used by**: every command at finalization

## Inputs
- wiki root
- operation name (`init` | `ingest` | `compile` | `query` | `research` | `lint` | `output` | `librarian` | `audit` | `plan` | `ll` | `assess` | `project` | `refresh` | `retract`)
- description string

## Prompt template
None.

## Procedure
Atomically append one line to `<wiki-root>/log.md`:
```
## [<YYYY-MM-DD>] <operation> | <description>
```

Open in append mode. Never read-modify-write — this is the property that makes concurrent appends from multiple sessions safe; lines may interleave but no entry is lost.

## Output
None.
