# detect-source-kind

**Type**: Tool
**Used by**: `ingest` Step 1

## Inputs
- raw `<source>` argument (URL, file path, or quoted text)

## Prompt template
None.

## Procedure
First match wins:
| Pattern | Kind | Default raw type |
|---------|------|------------------|
| `x.com/*/status/*`, `twitter.com/*/status/*` | `twitter` | `notes` |
| `github.com/*/*` (no `/blob/` or `/tree/`) | `github` | `repos` |
| starts with `http://`, `https://` | `url` | `articles` (or `papers` if URL contains `arxiv`, `doi.org`, `/pdf`) |
| contains `/`, starts with `~`, `.` | `file` | derived from extension |
| anything else | `text` | `notes` |

## Output
```json
{ "kind": "url|twitter|github|file|text", "default_type": "articles|papers|repos|notes|data" }
```
