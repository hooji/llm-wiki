# detect-collection-adapter

**Type**: Tool
**Used by**: `ingest-collection` Step C0

## Inputs
- source URL or path
- explicit `--adapter` flag if provided

## Prompt template
None.

## Procedure
If `--adapter` is set, use it. Otherwise:
1. Source ends in `.xml`, `.xml.bz2`, `.xml.gz` → `mediawiki-dump`.
2. Source contains `github.com/`, `gitlab.com/`, ends in `.git`, or is a local dir with `.git/` → `git`.
3. Source URL contains `/wiki/`, `/w/`, or has a reachable `api.php` → `mediawiki-api`.
4. Ambiguous → ask the user.

Never recursively crawl HTML.

## Output
`{ "adapter": "git" | "mediawiki-dump" | "mediawiki-api" }`
