# TODO — Setup & Outstanding Work

A handoff list of what's left for **you** to do (`[user]`) vs what's left to
**implement in code** (`[dev]`). Items are roughly in priority order.

## Setup you need to do before first run

### 1. `[user]` Set the OpenAI API key

Required for any LLM-driven step (extract metadata, plan article, synthesize
answer, etc).

```bash
export OPENAI_API_KEY=sk-...
```

Optional overrides (defaults are usually fine):

```bash
export OPENAI_BASE_URL=https://api.openai.com/v1     # default
export OPENAI_MODEL=gpt-4o-mini                      # default
```

### 2. `[user]` Verify SearXNG `format=json` is enabled

The hardcoded SearXNG URL is `http://192.168.0.60:8090`. Most Docker images
ship with only HTML output enabled — confirm it returns JSON:

```bash
curl 'http://192.168.0.60:8090/search?q=test&format=json&categories=general' | head -c 200
```

If you get HTML or `403`, edit the instance's `settings.yml` and add `json`
under `search.formats`:

```yaml
search:
  formats:
    - html
    - json
```

Then restart the container.

### 3. `[user]` Configure the wiki hub path

First-run, inside the REPL:

```
llm-wiki> config hub-path ~/wiki
llm-wiki> init my-first-topic "what this wiki is about"
```

The hub path is persisted to `~/.config/llm-wiki/config.json` so this is a
one-time setup.

### 4. `[user]` Build & run

```bash
mvn -DskipTests package
java -cp 'target/llm-wiki.jar:target/lib/*' net.llmwiki.Main
```

One-shot mode also works:

```bash
java -cp 'target/llm-wiki.jar:target/lib/*' net.llmwiki.Main \
     ingest "https://example.com/article"
```

## Optional setup (improves quality)

### `[user]` Get a free Tavily key

If you want better LLM-tuned web search results than SearXNG provides,
sign up at <https://tavily.com> (free 1000 searches/month) and:

```bash
export TAVILY_API_KEY=tvly-...
```

`Searchers.auto()` will pick Tavily over SearXNG once the env var is set.

## Outstanding code work

Roughly ordered by impact. These are documented as stubs in code with `TODO`
or with explicit "not yet implemented" return strings.

### High value — unblock full agentic workflows

#### `[dev]` Implement `AgentExecutor`

The five **agentic transitions** all currently throw `NotImplementedYet` from
`AgentExecutor.STUB`:

- `dispatch-research-agent` — research-mode parallel swarm (5 / 8 / 10 agents)
- `dispatch-thesis-agent` — for / against / mechanistic / meta / adjacent
- `dispatch-support-agent` — audit Pass 3, confirms a claim
- `dispatch-attack-agent` — audit Pass 3, attempts to disprove
- `dispatch-primary-source-agent` — audit Pass 3, finds the original primary source

Each takes `(role, contextJson)` and returns a structured JSON reply
(schema in each transition's doc under `docs/transitions/`). Implementation
is a loop of: `web-search` → `web-fetch` → LLM-evaluate hits → return top N.

Wire it in by passing a real implementation to `LlmWiki`:

```java
LlmWiki wiki = new LlmWiki(new LLM(), myAgentExecutor, Searchers.auto(), WebTools.HTTP_FETCHER);
```

Without this, `/research`, `/thesis`, and `/audit` Pass 3 all run
gracefully but produce empty / "unresolved" results.

### Medium value — close documented gaps

#### `[dev]` Twitter/X URL fallback chain

`capability/Ingest.java` currently does a plain HTTP fetch for X.com URLs.
The architecture spec calls for a four-step fallback:

1. Grok MCP (`mcp__grok__*`) if available
2. fxtwitter API (`https://api.fxtwitter.com/<user>/status/<id>`)
3. vxtwitter API (`https://api.vxtwitter.com/<user>/status/<id>`)
4. Direct WebFetch
5. Manual paste fallback

Steps 2-3 are easy JSON GETs and would be the high-leverage bits to add.

#### `[dev]` MediaWiki collection adapters

`capability/IngestCollection.java` ships with a working **git** adapter but
stubs out:

- `mediawiki-dump` — should `bunzip2 -c | xml.etree.iterparse`-equivalent
  streaming XML parse. Java equivalent: SAX parser or
  `XMLEventReader`.
- `mediawiki-api` — `action=query&list=allpages` + paginated
  `prop=revisions&rvslots=main&rvprop=content`.

#### `[dev]` Plan format renderers

`capability/Plan.java` only fully wires the **roadmap** format. The
`--format rfc | adr | spec` cases need:

- A type-specific prompt in `core/Prompts.java` (mirroring
  `GENERATE_PLAN_DOCUMENT_ROADMAP`).
- A type-specific renderer in `Plan.java` (like the renderers in `Output.java`).

Templates and JSON schemas are documented in
`docs/architecture/09-plan.md` and `docs/transitions/plan/generate-plan-document.md`.

#### `[dev]` Multi-round reflection

`capability/Research.java` runs the `--min-time` loop but the reflection
step (`reflect-across-rounds`) is a placeholder. Wiring it in:

1. Build `history_json` from `traj.allRounds()`.
2. Call `LLM.call(REFLECT_ACROSS_ROUNDS prompt)`.
3. Apply `see_also_additions` deterministically (Edit each article).
4. Use `scored_next_gaps` to drive the next round's topic.

Schema is in `core/Prompts.java` under `REFLECT_ACROSS_ROUNDS`.

### Low value — nice to have

#### `[dev]` Lessons-learned: live transcript source

`/ll` currently requires `--from <transcript-file>` because the Java REPL
has no chat history to scan (unlike Claude Code). Options:

1. Wrap the REPL itself to record the transcript, then have `/ll` scan it.
2. Add a `--paste` flag that reads multi-line from stdin until EOF.
3. Leave it as `--from` — that's actually fine for this use case.

#### `[dev]` Test suite

No tests yet (deferred per original plan). When ready:

- Unit tests for `Templates.expand` (placeholder semantics, missing keys,
  varargs convenience).
- Unit tests for `Frontmatter` round-trip (parse → stringify → parse).
- Unit tests for `Slugs.slugify` (unicode, length cap, edge cases).
- Unit tests for `Indexes.countContentsRows` (the staleness signal).
- Integration test: tmp dir + `init` + `ingest "text"` + `compile` →
  assert filesystem layout matches `docs/architecture/README.md` § "Filesystem
  layout".

## Quick reference — capability methods

Each is `String foo(String args)` on `LlmWiki`:

```java
wiki.init("my-topic --local")
wiki.ingest("https://example.com/article")
wiki.ingestCollection("https://github.com/bitcoin/bips --wiki bitcoin")
wiki.compile("--full")
wiki.query("what is X --quick")
wiki.research("\"intermittent fasting\" --new-topic --min-time 1h")
wiki.thesis("\"X causes Y\"")
wiki.librarian("scan")
wiki.audit("scan --quick")
wiki.lessons("--from /tmp/session.txt")
wiki.plan("\"build a thing\" --format roadmap")
wiki.output("slides --topic transformer")
```

REPL equivalents drop the leading `wiki.` and the parens.

## Pointers

- Architecture overview: [`docs/architecture/README.md`](architecture/README.md)
- Per-capability docs: [`docs/architecture/01-research.md`](architecture/01-research.md) … `10-output.md`
- Transition catalog: [`docs/transitions/README.md`](transitions/README.md)
- Java implementation overview: [`JAVA-IMPLEMENTATION.md`](../JAVA-IMPLEMENTATION.md)
