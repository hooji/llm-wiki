# survey-uncompiled-sources

**Type**: Tool
**Used by**: `compile` Step 2

## Inputs
- wiki root
- mode: `incremental` (default) / `full` / `single`
- `--source <path>` for single mode

## Prompt template
None.

## Procedure
- Read `raw/_index.md` (stale-check first) → list of all sources.
- Read `wiki/_index.md` → list of existing articles.
- Read master `_index.md` → `Last compiled` date.

Then:
- **incremental**: filter sources where `frontmatter.ingested > last_compiled`.
- **full**: all sources.
- **single**: just `--source <path>`.

If incremental and target list is empty → return `{ "no_op": true }` and the compile orchestrator reports "All sources already compiled."

## Output
```json
{
  "no_op": false,
  "target_sources": ["raw/papers/...md", ...],
  "existing_articles": [{"slug":"...","path":"...","tags":[...]}, ...],
  "last_compiled": "YYYY-MM-DD"
}
```
