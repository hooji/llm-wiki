# render-librarian-report

**Type**: Tool
**Used by**: `librarian` Step 4

## Inputs
- `scan-results.json` (machine-readable)

## Prompt template
None (deterministic templating, no LLM call).

## Procedure
Render to `<wiki>/.librarian/REPORT.md`:

```markdown
# Librarian Report — YYYY-MM-DD

> Scanned N articles in <wiki-name>. Passes: staleness, quality.

## Summary
| Metric | Value |
|--------|-------|
| Articles scanned | N |
| Below staleness threshold | N |
| Low quality (< 50) | N |
| Average staleness | N/100 |
| Average quality | N/100 |

## Stale Articles (staleness < threshold)
| Article | Score | Top Factor | Recommendation |
|---------|-------|------------|----------------|
| [Title](path) | 31/100 | sources 180d old | refresh |

## Low Quality Articles (quality < 50)
| Article | Score | Flags | Recommendation |
|---------|-------|-------|----------------|
| [Title](path) | 42/100 | thin-coverage, single-source | expand and add sources |

## All Articles (sorted by combined score)
| Article | Staleness | Quality | Flags |
|---------|-----------|---------|-------|
| ... | ... | ... | ... |
```

## Output
None.
