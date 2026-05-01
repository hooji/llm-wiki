# write-lessons-note

**Type**: Tool
**Used by**: `lessons` Step 5

## Inputs
- lessons array
- session summary
- target wiki

## Prompt template
None.

## Procedure
Write `<wiki>/raw/notes/<YYYY-MM-DD>-ll-<slug>.md` using chunked writes:

1. Skeleton (frontmatter + first lesson):
   ```yaml
   ---
   title: "Lessons Learned: {session topic}"
   type: notes
   source: "session"
   ingested: <today>
   tags: [lessons-learned, {topic_tags}]
   summary: "<one-line summary>"
   session_topic: "{topic_hint_or_inferred}"
   lesson_count: N
   session_category: lessons-learned
   confidence: high
   ---

   # Lessons Learned: {session topic}

   > Extracted from session on YYYY-MM-DD. N lessons.

   ## Lesson 1: {title}
   **Category**: ...
   **Context**: ...
   **Symptom**: ...
   **Root cause**: ...
   **Fix**: ...
   **Rule**: ...
   ```

2. For each remaining lesson, Edit-append `## Lesson N: ...` block.

## Output
Path of the written note.

## Notes
`type: notes` keeps lint C11 happy. `session_*` and `lesson_count` are extra-canonical metadata; lint C13 tolerates them as unknown-with-warning rather than rewriting.
