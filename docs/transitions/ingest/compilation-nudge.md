# compilation-nudge

**Type**: Tool
**Used by**: `ingest` Step 9, `ingest --inbox` final step

## Inputs
- wiki root

## Prompt template
None.

## Procedure
1. Read `raw/_index.md` and master `_index.md`.
2. Count raw sources whose `ingested:` date is after master `_index.md`'s `Last compiled` date.
3. If count ≥ 5 → emit user hint: `"You have N uncompiled sources. Run /wiki:compile to integrate them."`

## Output
Optional user-facing hint.

## Notes
This is a nudge only — never blocks the user, never auto-runs compile.
