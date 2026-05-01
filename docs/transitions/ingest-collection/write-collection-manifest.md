# write-collection-manifest

**Type**: Tool
**Used by**: `ingest-collection` Step C5

## Inputs
- collection slug, source URL, adapter, revision, license, summary
- inventory counts (new / skipped / total)

## Prompt template
None.

## Procedure
Write one manifest source to `<wiki>/raw/repos/<YYYY-MM-DD>-<slug>-manifest.md`:

```yaml
---
title: "Collection: <name>"
source: "<upstream URL or path>"
type: repos
ingested: YYYY-MM-DD
tags: [collection, collection-manifest, <adapter>]
summary: "Manifest for a collection ingest of <name>: N child sources captured from <revision>."
collection: "<slug>"
adapter: <adapter>
revision: "<rev>"
canonical_url: "<upstream URL>"
license: "<detected or unknown>"
---

# Collection: <name>

[Inventory table: upstream_id | revision | size | included]
```

## Output
Path of the written manifest.

## Notes
Lint exempts `collection-manifest`-tagged sources from coverage (C6) and orphan-source warnings (C4b). The manifest is operational provenance, not content.
