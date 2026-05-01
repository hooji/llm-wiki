# decide-article-backref

**Type**: LLM
**Used by**: `lessons` Step 4 (per lesson × per grep-hit article)

## Inputs
- one lesson
- one candidate article (path, frontmatter, body)

## Prompt template
```
Decide whether this article is the right place to append a one-line
back-reference to a new lesson.

Lesson: {title} — {rule}
Article path: {path}
Article frontmatter: {fm}
Article body:
"""{body}"""

Return JSON ONLY:

{
  "should_append": true | false,
  "preferred_section": "<existing section heading or 'tail'>",
  "appended_text": "<exactly what to insert: a one-line bullet or short subsection, including the back-reference link to raw/notes/...md>"
}
```

## Output
JSON as specified. Tracked per lesson; applied by `append-article-backreference`.
