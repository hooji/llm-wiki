# reuse-or-rerun-librarian

**Type**: Tool (delegates to entire librarian flow when needed)
**Used by**: `audit` Pass 1

## Inputs
- wiki root
- `--fresh` flag

## Prompt template
None.

## Procedure
1. If `<wiki>/.librarian/scan-results.json` exists and is "recent enough" (< 7 days, configurable) and `--fresh` is NOT set → reuse it.
2. Otherwise → run the full librarian scan (composes `compute-staleness-score`, `score-quality-tier-1`, `score-quality-tier-2`, `write-librarian-checkpoint`, `write-librarian-scan-results`, `render-librarian-report`).
3. Pull forward only the trust-relevant findings: stale articles, low-quality articles, weak source chains.

## Output
```json
{
  "reused": <bool>,
  "wiki_findings": [
    {"path":"...","staleness":<int>,"quality":<int>,"flags":[...]}
  ]
}
```
