# scan-output-drift

**Type**: Tool
**Used by**: `audit` Pass 2

## Inputs
- output artifact list (from scope)
- librarian findings (for inheriting `stale-upstream`/`weak-upstream`)

## Prompt template
None.

## Procedure
For each artifact:
1. Read frontmatter; capture `sources:`, `generated:`, `project:`.
2. Flag `missing-provenance` if `sources:` missing/empty.
3. Resolve every dep path. `raw/...`, `wiki/...`, `output/...` resolve from wiki root; `../...` relative to artifact file.
4. Flag `broken-source-ref` for unresolvable deps.
5. Compare dep dates against artifact `generated:`:
   - dep `updated`/`ingested`/`generated` > artifact `generated` → flag `drifted-dependency`.
6. If dep is a wiki article → inherit librarian findings (`stale-upstream`, `weak-upstream`).
7. If dep is another output artifact → recurse one hop into its `sources:`.

## Output
```json
{
  "outputs": [
    {
      "path":"output/projects/<slug>/playbook.md",
      "flags":["drifted-dependency","stale-upstream"],
      "drifted_deps":[{"path":"...","reason":"updated 2026-04-25 > generated 2026-04-10"}],
      "broken_deps":[],
      "deps_inherited_findings":[{"path":"...","kind":"stale-upstream"}]
    }
  ]
}
```
