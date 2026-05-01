# write-output-chunked

**Type**: Tool
**Used by**: `output` Step 5

## Inputs
- rendered markdown
- output type
- topic-slug
- `--project <slug>` (optional)

## Prompt template
None.

## Procedure
1. Compute path:
   - `--project <slug>` → `<wiki>/output/projects/<slug>/<type>-<topic-slug>-<date>.md` (verify `WHY.md` exists first; fail early if not).
   - type produces binary siblings (rare) → also use a project folder.
   - else → `<wiki>/output/<type>-<topic-slug>-<date>.md`.
2. Apply chunked writes:
   - Write skeleton: frontmatter + first heading + first section/slide/entry.
   - Edit-append remaining sections / slides / entries one at a time.

## Output
Path of the written file.

## Notes
Lint C9d clusters loose markdown outputs sharing a common slug prefix and proposes wrapping them in a project folder. Loose single-markdown outputs in `output/` are still allowed for backward compatibility.
