# web-search

**Type**: Tool
**Used by**: `research` agents, `audit` adversarial verification, `plan` gap research, `lint --deep`

## Inputs
- query string

## Prompt template
None (the host harness's WebSearch tool).

## Procedure
Issue the query to the host's WebSearch tool. Return ranked results.

## Output
```json
{
  "query": "...",
  "results": [
    {
      "url": "...",
      "title": "...",
      "snippet": "...",
      "rank": 1
    }
  ]
}
```

## Notes
Whether this is "tool" or "agentic" depends on harness: in Claude Code it's a tool. In a Java reimplementation, wrap any provider (Tavily, Exa, Brave) behind the same shape.
