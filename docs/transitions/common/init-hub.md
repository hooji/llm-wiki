# init-hub

**Type**: Tool
**Used by**: `wiki init`, `--new-topic` flag in any wiki-creating command

## Inputs
- `HUB` path

## Prompt template
None.

## Procedure
Create at `HUB`:
- `wikis.json` with empty registry: `{ "default": "<HUB>", "wikis": {}, "local_wikis": [] }`
- `_index.md` (hub index, empty topic-wiki table)
- `log.md` with one line: `## [<today>] init | Hub initialized`
- `topics/` directory

The hub MUST NOT contain `raw/`, `wiki/`, `output/`, `inbox/`, `config.md`, or `.obsidian/` — those live in topic sub-wikis. Lint C12 enforces this.

## Output
Files on disk; nothing returned.
