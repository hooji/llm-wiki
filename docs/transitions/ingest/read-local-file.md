# read-local-file

**Type**: Tool
**Used by**: `ingest` Step 2d, inbox processing

## Inputs
- absolute file path

## Prompt template
None.

## Procedure
- `.md`, `.txt` → read directly.
- `.pdf` → write a metadata stub noting the file path (do not OCR).
- `.json`, `.csv`, `.tsv` → read schema description + first ~20 rows; do **not** embed the full dataset.
- `.url`, `.webloc` → parse the URL; the caller hands off to `fetch-url-content`.
- Images → metadata stub with path + visible content description if available.
- Other → metadata stub with file type + path.

## Output
```json
{
  "title": "<filename or extracted from body>",
  "content_markdown": "...",
  "embedded_data_sample": "..." | null
}
```
