# resolve-hub

**Type**: Tool
**Used by**: every command (prelude step 1)

## Inputs
- `$HOME` (env)
- `~/.config/llm-wiki/config.json` if it exists

## Prompt template
None.

## Procedure
1. Read `~/.config/llm-wiki/config.json`.
   - If `resolved_path` present → `HUB = resolved_path`. Done.
   - Else if `hub_path` present → expand the **leading** `~` only (literal `~` characters elsewhere, e.g. `com~apple~CloudDocs`, are preserved). Set `HUB`. Write `resolved_path` back to config so this never happens again.
2. If no config → check `$HOME/wiki/_index.md`. If present → `HUB = $HOME/wiki`.
3. If nothing found → ask the user where to create the wiki.

## Output
`{ "hub": "<absolute path>" }` plus an in-place rewrite of `config.json` if step 1's second branch ran.

## Side effects
May write `~/.config/llm-wiki/config.json` (adds `resolved_path`).
