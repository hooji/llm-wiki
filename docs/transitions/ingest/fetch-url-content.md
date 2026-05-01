# fetch-url-content

**Type**: Tool (`web-fetch` with article-extraction prompt)
**Used by**: `ingest` Step 2a, `research` agents during ingest stage

## Inputs
- URL

## Prompt template (passed to web-fetch)
```
Extract the complete article content from this page. Return: title,
author(s) if listed, date published if listed, and the full article text
preserving all factual claims, data points, code examples, and technical
details. Format as clean markdown.
```

## Output
```json
{
  "title": "...",
  "authors": ["..."],
  "published": "YYYY-MM-DD" | null,
  "content_markdown": "..."
}
```
