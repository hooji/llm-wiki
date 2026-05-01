# resolve-wiki

**Type**: Tool
**Used by**: every command (prelude step 2)

## Inputs
- `HUB` (from `resolve-hub`)
- CWD
- flags: `--local`, `--wiki <name>`

## Prompt template
None.

## Procedure
First match wins:
1. `--local` flag → `<cwd>/.wiki/`
2. `--wiki <name>` → look up `name` in `HUB/wikis.json`
3. CWD has `.wiki/` → use it
4. Otherwise → `HUB`

Then verify by reading `<wiki>/_index.md`. Variant per command:
- **wiki-required** (`compile`, `lint`, `query`, `output`, `plan`, `retract`, `assess`, `librarian`, `audit`): stop with "No wiki found".
- **wiki-creating** (`ingest`, `research`, `ingest-collection`): if `--new-topic <name>` is set, run `init-topic-wiki`; else stop with the same message.
- **wiki-neutral** (`wiki`, `project`): handle missing state inline.

## Output
`{ "root": "<absolute path>", "exists": true | false, "kind": "hub" | "topic" | "local" }`
