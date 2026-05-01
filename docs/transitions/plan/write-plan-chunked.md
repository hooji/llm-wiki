# write-plan-chunked

**Type**: Tool
**Used by**: `plan` Stage 6

## Inputs
- rendered plan markdown
- target path (computed below)

## Prompt template
None.

## Procedure
1. Compute path:
   - `--project <slug>` → `<wiki>/output/projects/<slug>/plan-<slug>-<date>.md`
   - else → `<wiki>/output/plan-<goal-slug>-<date>.md`
2. Verify the project exists (when `--project`); if not, fail early.
3. Compose frontmatter:
   ```yaml
   ---
   title: "Plan: {goal}"
   type: plan
   format: roadmap | rfc | adr | spec
   sources: [wiki articles used, from sources_consulted]
   generated: <today>
   project: <slug>          # optional
   ---
   ```
4. Apply chunked writes:
   - Write skeleton (frontmatter + executive summary / first ADR / system architecture).
   - Edit-append remaining sections one at a time.

## Output
Path of the written plan.
