# web-fetch

**Type**: Tool
**Used by**: `ingest`, `research` agents, `audit` primary-source refetch, `plan` gap-research extraction

## Inputs
- URL
- extraction prompt

## Prompt template
The harness's WebFetch internally runs an LLM to convert HTML → markdown using a provided extraction prompt. From the orchestrator's perspective this is a **tool** call, not a separate LLM step. Typical extraction prompts are documented at the call site (see `ingest/fetch-url-content.md`, `ingest/fetch-github-repo.md`, etc).

## Procedure
1. Fetch the URL (HTTPS upgrade, follow redirects, 15-min cache).
2. Convert HTML → markdown.
3. Run the extraction prompt against the markdown.
4. Return the model's structured response.

## Output
String (typically markdown) or, if the prompt asks for it, JSON.

## Notes
WebFetch will fail on authenticated/private URLs. For X.com / Twitter, use the fallback chain in `ingest/fetch-twitter-content.md`.
