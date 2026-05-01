# write-article-skeleton

**Type**: Tool
**Used by**: `compile` Step 5 (first write of every new article)

## Inputs
- article plan from `plan-article`
- target path: `wiki/<category>/<slug>.md`

## Prompt template
None.

## Procedure
Write the file in **one** call containing only the skeleton:
```markdown
---
title: "..."
category: concept|topic|reference
sources: [...]
created: <today>
updated: <today>
tags: [...]
aliases: [...]
confidence: ...
volatility: ...
verified: <today>
summary: "..."
---

# {title}

> {abstract_paragraph}

## {first_section_heading}
```

This makes the file a valid article immediately. Subsequent `write-article-section` calls append one section at a time via Edit.

## Output
Path of the written file.

## Notes
Core principle #9 (chunked writes): never write files longer than ~200 lines in one Write call — the LLM stream idles during long generations and times out.
