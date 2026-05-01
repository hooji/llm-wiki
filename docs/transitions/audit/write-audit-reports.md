# write-audit-reports

**Type**: Tool
**Used by**: `audit` Pass 5

## Inputs
- audit scope, all pass outputs, claim verdicts, provenance state

## Prompt template
None (deterministic templating).

## Procedure
Write three files:

1. `<wiki>/.audit/scan-results.json`:
   ```json
   {
     "audit_id":"<ISO 8601>",
     "scope":"full",
     "summary":{
       "wiki_findings":N,"outputs_scanned":N,"drifted_outputs":N,
       "research_escalations":N,
       "verdict_counts":{"supported":N,"weakened":N,"contradicted":N,"unresolved":N},
       "provenance_state":"partial"
     },
     "wiki": { /* selected librarian findings */ },
     "outputs": { /* per-artifact verdicts and flags */ },
     "investigations": [ /* per-claim verdicts */ ],
     "provenance": { ... }
   }
   ```

2. `<wiki>/.audit/REPORT.md` — human-readable rendering of the same.

3. Append to `<wiki>/.audit/log.md`:
   ```
   ## [YYYY-MM-DD] scan | scope=<scope>, outputs=N, drifted=M, escalations=K
   ```

Plus the wiki root `log.md` audit entry (via `append-activity-log`).

## Output
None.
