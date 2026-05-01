# fetch-github-repo

**Type**: Tool (`web-fetch` with repo-extraction prompt)
**Used by**: `ingest` Step 2b

## Inputs
- GitHub URL (`github.com/<owner>/<repo>`)

## Prompt template (passed to web-fetch)
```
Extract from this GitHub repository: name, description, key technologies,
main purpose, README content. Format as markdown.
```

## Output
```json
{
  "title": "<owner>/<repo>",
  "description": "...",
  "technologies": ["..."],
  "purpose": "...",
  "readme_markdown": "..."
}
```
