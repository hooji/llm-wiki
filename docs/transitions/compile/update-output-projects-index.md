# update-output-projects-index

**Type**: Tool
**Used by**: `compile` Step 9, `output` Step 6, `lint --fix` C-rules involving `output/projects/`

## Inputs
- wiki root

## Prompt template
None.

## Procedure
If `<wiki>/output/projects/` exists, regenerate `<wiki>/output/_index.md` as a projects-aware listing:
1. Scan each `output/projects/<slug>/WHY.md` for:
   - first `#` heading → project title
   - first non-heading paragraph (first ~120 chars) → project goal
2. Render a markdown table: project, title, goal.
3. Below the project table, list any remaining loose `output/*.md` files (loose markdown is still allowed for backward compatibility).

Member counts per project come from folder scans at render time — there is no cached Members list on disk anymore (the v0.2 simplification removed `_project.md` manifests).

## Output
Updated `output/_index.md` on disk.

## Notes
Best-effort — if skipped or clobbered by a concurrent session, the next lint/compile fixes it.
