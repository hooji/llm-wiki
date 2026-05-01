# fetch-twitter-content

**Type**: Tool (multi-stage fallback chain)
**Used by**: `ingest` Step 2c

## Inputs
- X.com / Twitter URL

## Prompt template
None for the API stages. WebFetch fallback uses no extraction prompt (raw HTML).

## Procedure
Try in order, stop on first success:
1. **Grok MCP** if `mcp__grok__*` tools are present. Fetch tweet/thread.
2. **FxTwitter**: rewrite `x.com/<user>/status/<id>` → `https://api.fxtwitter.com/<user>/status/<id>`. WebFetch returns JSON with `tweet.text`, `tweet.author`, `tweet.created_at`, `tweet.media`.
3. **VxTwitter**: same pattern with `api.vxtwitter.com`.
4. **Direct WebFetch** of the original URL (often blocked by login wall).
5. **Manual fallback**: report failure, suggest `ingest "text" --title "@author tweet"`.

## Output
On success, build a synthetic markdown body:
```markdown
> @<handle> — <display name>
> <date>

<full tweet text>

[Media: <descriptions>]
```

```json
{
  "title": "@<handle> — <first 60 chars>",
  "authors": ["<handle>"],
  "published": "<date>",
  "content_markdown": "..."
}
```
