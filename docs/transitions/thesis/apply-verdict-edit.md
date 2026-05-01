# apply-verdict-edit

**Type**: Tool
**Used by**: `research --mode thesis` Phase 5 (after `render-thesis-verdict`)

## Inputs
- thesis-file path
- verdict JSON from `render-thesis-verdict`

## Prompt template
None.

## Procedure
1. Edit thesis file's `## Verdict` section to:
   ```markdown
   ## Verdict
   **Status**: {verdict}
   **Confidence**: {confidence}
   **Summary**: {summary_2_3_sentences}
   **Strongest supporting evidence**: ...
   **Strongest opposing evidence**: ...
   **Key caveats**: ...
   **What would change this verdict**: ...
   **Suggested follow-up theses**: ...
   ```
2. Update thesis frontmatter:
   ```yaml
   status: completed
   verdict: {verdict}
   confidence: {confidence}
   updated: <today>
   ```

## Output
None (file mutation).
