# inventory-git-collection

**Type**: Tool
**Used by**: `ingest-collection` Step C1 (git adapter)

## Inputs
- repo URL or local path

## Prompt template
None.

## Procedure
```bash
git clone --depth 1 <url> <tmp>          # or use local path
git -C <tmp> rev-parse HEAD              # → revision SHA
git -C <tmp> ls-tree -r --format='%(objectname) %(path)' HEAD
```

Filter to text-like files: `.md`, `.mediawiki`, `.wiki`, `.rst`, `.txt`, `.adoc`. Exclude `.git/`, `.github/`, generated assets, binaries, images, archives, vendored deps, scripts, test vectors. For BIP-style repos, prioritize `bip-####.mediawiki` and `bip-####.md` at root.

For each candidate file capture: `upstream_id` (relative path), `revision` (HEAD SHA), `sha` (blob SHA), `canonical_url` (GitHub/GitLab blob URL pinned to commit). For BIPs, parse headers (`BIP`, `Layer`, `Title`, `Authors`, `Status`, `Type`, `Requires`, `License`, `Discussion`).

## Output
```json
{
  "adapter": "git",
  "revision": "<commit sha>",
  "candidates": [
    {"upstream_id":"bip-0001.mediawiki","sha":"...","canonical_url":"...","headers":{...}},
    ...
  ]
}
```
