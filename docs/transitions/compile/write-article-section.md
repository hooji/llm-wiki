# write-article-section

**Type**: LLM (one call per section, sequential)
**Used by**: `compile` Step 5 (after `write-article-skeleton`)

## Inputs
- article plan (for title context)
- one section's heading and intent
- relevant source extracts for this section

## Prompt template
```
You are writing a single section of a wiki article.

Article: {title}
Section heading: {heading}
Section intent: {intent}

Source extracts available for this section:
[
  {"path": "...", "extract": "...", "credibility": 4},
  ...
]

Constraints:
- Synthesize. Do NOT copy-paste.
- Self-contained: a reader should not need to consult the raw sources.
- Be specific. Include data points, mechanisms, examples.
- Note honest disagreement when sources disagree.
- When referencing another wiki article inline, use dual-link format:
    [[other-slug|Name]] ([Name](../<category>/other-slug.md))
- No marketing language.

Return JSON ONLY:
{
  "section_markdown": "<the section body, in markdown, NOT including the ## heading line>",
  "inline_cross_refs": ["other-slug-1", "other-slug-2"],
  "new_facts": ["..."]
}
```

## Output
JSON as specified. Orchestrator Edits the article file to append `\n## <heading>\n\n<section_markdown>`.

After all sections are appended, deterministic templating writes `## See Also` and `## Sources` sections from the plan.
