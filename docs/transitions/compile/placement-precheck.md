# placement-precheck

**Type**: Tool
**Used by**: `compile` Step 1

## Inputs
- wiki root

## Prompt template
None.

## Procedure
Walk `raw/`. For each `.md` file:
1. Read frontmatter.
2. Apply C13 alias rewrites (legacy keys → canonical, legacy enum values → canonical). Append-only alias table; currently empty in v0.6 but the framework runs.
3. Compute expected directory from the placement map:
   - `type: thesis` → `wiki/theses/`
   - `type ∈ {articles, papers, repos, notes, data}` → `raw/<type>/`
   - `category ∈ {concept, topic, reference}` → `wiki/<plural>/`
4. If actual directory ≠ expected, `mv` the file. On slug collision at destination, skip and warn.

## Output
List of moves: `[{from, to, reason}]`. The compile orchestrator continues with the canonical view.

## Notes
This is the same rule lint uses; run inline because compile is already reading every frontmatter. Lint-is-the-migration: there is no separate `/wiki:migrate`.
