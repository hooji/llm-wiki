# build-resume-briefing

**Type**: LLM (small)
**Used by**: `query --resume` R6

## Inputs
- interrupted session info (from `detect-interrupted-session`)
- durable provenance summary (from `read-durable-provenance`)
- recent log entries (last 10)
- master index stats
- last 3 updated articles

## Prompt template
```
Given this resume context, suggest the most useful next step(s).

Context:
{
  "wiki_name": "...",
  "wiki_root": "...",
  "interrupted_session": <obj or null>,
  "recent_log": [...],
  "stats": {sources, articles, outputs},
  "last_updated_articles": [...],
  "last_audit_findings": <obj or null>
}

Return JSON:
{
  "suggestions": [
    {"label":"Resume research","command":"/wiki:research --min-time ..."},
    ...
  ]
}
```

## Output
JSON as specified. Rendered as numbered next-step list under the briefing block.

## Notes
Briefing always begins with `<wiki-name> booted from <wiki-root-path>` regardless of session state. Identity comes from `config.md` `title`, falling back to parent dir basename or topic slug.
