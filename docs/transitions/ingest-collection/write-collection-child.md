# write-collection-child

**Type**: Tool
**Used by**: `ingest-collection` Step C6 (per item)

## Inputs
- collection slug, adapter, fetched item, summary, tags

## Prompt template
None.

## Procedure
Write one immutable raw source per upstream item, usually to `<wiki>/raw/articles/`:

```yaml
---
title: "<upstream title>"
source: "<canonical upstream URL or file path>"
type: articles
ingested: YYYY-MM-DD
tags: [collection, <slug>, ...]
summary: "<2-3 sentence factual summary>"
collection: "<slug>"
adapter: <adapter>
upstream_id: "<path or page id>"
upstream_type: git-file | mediawiki-page
revision: "<rev id, sha, or timestamp>"
sha: "<blob sha or content hash>"
canonical_url: "<per-item URL>"
content_format: markdown | mediawiki | wikitext | text
license: "<detected or unknown>"
authors: [...]
categories: [...]
outlinks: [...]
fetched: YYYY-MM-DD
---

[Full upstream content, verbatim]
```

## Output
Path of the written child source.

## Notes
Body is the full upstream text. Do not paraphrase or compress — that's compile's job.
