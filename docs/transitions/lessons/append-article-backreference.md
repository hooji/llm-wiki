# append-article-backreference

**Type**: Tool
**Used by**: `lessons` Step 6 (per article-update entry)

## Inputs
- article path
- `preferred_section` and `appended_text` from `decide-article-backref`
- raw note path (for the back-reference link)

## Prompt template
None.

## Procedure
1. Read the article.
2. Edit-append `appended_text` at `preferred_section`. Common pattern:
   ```markdown
   ## Lessons from practice

   - {rule} ([note](../../raw/notes/<date>-ll-<slug>.md))
   ```
3. Bump article frontmatter `updated:` to today. Do NOT rewrite existing content.

## Output
None.

## Notes
If no article matched in Step 4, this transition is skipped. The lesson stays in `raw/notes/` and gets integrated by the next `/wiki:compile`.
