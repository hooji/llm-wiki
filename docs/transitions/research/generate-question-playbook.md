# generate-question-playbook

**Type**: LLM
**Used by**: `research` (question mode only) Phase 4.b

## Inputs
- original question
- list of sub-questions covered
- new wiki articles produced this run

## Prompt template
```
Generate a playbook that answers the original question using the new wiki
articles.

Original question: "{question}"
Sub-questions covered: [...]
New wiki articles: [
  {"path":"wiki/topics/...md","title":"...","summary":"..."}
]

Return JSON ONLY:

{
  "title": "...",
  "frontmatter": {"type":"playbook","sources":["wiki/.../*.md"],...},
  "sections": [
    {"heading":"## The question","markdown":"..."},
    {"heading":"## Answer in one paragraph","markdown":"..."},
    {"heading":"## Key findings","markdown":"<bulleted, one per sub-question>"},
    {"heading":"## Actionable steps","markdown":"<numbered list>"},
    {"heading":"## Examples","markdown":"..."},
    {"heading":"## Sources","markdown":"<links to wiki articles>"}
  ],
  "derived_theses": [
    "<testable claim 1>",
    "<testable claim 2>"
  ]
}
```

## Output
JSON as specified. Saved to `output/playbook-<slug>-<YYYY-MM-DD>.md` using chunked writes (skeleton + per-section Edit-append).
