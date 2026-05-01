# render-output-markdown

**Type**: Tool
**Used by**: `output` Step 4

## Inputs
- output type
- type-specific JSON from `generate-output-artifact`

## Prompt template
None (deterministic templating).

## Procedure
Per-type rendering:

| Type | Template |
|------|----------|
| `summary` | frontmatter + `# {title}` + `> {tldr}` + sections |
| `report` | frontmatter + `# {title}` + `## Executive Summary\n{executive_summary}` + sections |
| `study-guide` | frontmatter + concepts (subsections) + Q&A list |
| `slides` | frontmatter + `# {title}\n\n---\n\n` + per-slide `## {title}\n- bullet\n- bullet\n\n---\n` |
| `timeline` | frontmatter + `# {title}` + per-entry `## {date} — {event}\n> {significance} ([source](wiki/...md))` |
| `glossary` | frontmatter + `# {title}` + per-term subsection sorted alphabetically |
| `comparison` | frontmatter + table with subjects as columns and dimensions as rows |

Frontmatter shape:
```yaml
---
title: "{title}"
type: summary | report | study-guide | slides | timeline | glossary | comparison
sources: [wiki articles used]
generated: <today>
project: <slug>          # optional
with_wikis: [<name>, <name>]    # optional
---
```

## Output
Markdown string.
