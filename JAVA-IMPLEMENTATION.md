# Java Implementation Notes

A Java 21 implementation of the llm-wiki protocol, mirroring the architecture
documented in `docs/architecture/` and the transition catalog in
`docs/transitions/`. Built with Maven; uses Jackson for JSON parsing.

## Build

```bash
mvn -DskipTests package
```

Produces `target/llm-wiki.jar` and `target/lib/*.jar` (Jackson + jsr310).

## Run

```bash
# REPL
java -cp 'target/llm-wiki.jar:target/lib/*' net.llmwiki.Main

# One-shot
java -cp 'target/llm-wiki.jar:target/lib/*' net.llmwiki.Main query "what is X" --quick
```

## Environment

Anything LLM-driven needs an OpenAI-compatible endpoint:

```bash
export OPENAI_API_KEY=sk-...
export OPENAI_BASE_URL=https://api.openai.com/v1   # optional, defaults to OpenAI
export OPENAI_MODEL=gpt-4o-mini                    # optional, defaults to gpt-4o-mini
```

Web search auto-selects from the first available source, in priority order:

| Env var | Provider | Free tier |
|---------|----------|-----------|
| `TAVILY_API_KEY` | Tavily | 1000 searches/month — designed for LLM agents |
| `BRAVE_API_KEY`  | Brave Search API | 2000 / month, 1 qps |
| `SEARXNG_URL`    | SearXNG | unlimited if self-hosted (`docker run -d -p 8080:8080 searxng/searxng`) |
| (none)           | DuckDuckGo HTML scrape | no key, but DDG now blocks most non-browser User-Agents — kept as a graceful-fail last resort |

Recommended for development: get a free Tavily key (https://tavily.com).
For air-gapped / fully-local use, run SearXNG in Docker.

Hub path is configured via the REPL:

```
llm-wiki> config hub-path ~/wiki
llm-wiki> init my-topic "scope description"
```

…or by manually writing `~/.config/llm-wiki/config.json`.

## Layout

```
src/main/java/net/llmwiki/
├── Main.java                    REPL + one-shot dispatch
├── LlmWiki.java                 Facade — every capability is a method(String)
├── core/
│   ├── LLM.java                 The single LLM seam
│   ├── OpenAIClient.java        Bare HTTP caller for OpenAI-compatible endpoints
│   ├── Templates.java           expand("hi {name}", Map.of("name","world"))
│   ├── Json.java                Jackson facade + JSON-blob extractor
│   ├── ArgParse.java            Tiny CLI flag parser
│   ├── Prompts.java             All prompt templates from docs/architecture/
│   ├── AgentExecutor.java       Stub for agentic transitions
│   └── WebTools.java            Stub WebSearcher + simple HTTP fetcher
├── fs/
│   ├── WikiFS.java              read/write/append/glob, atomic writes
│   ├── Frontmatter.java         tiny YAML frontmatter parser/writer
│   ├── Indexes.java             Derived Index Protocol
│   ├── Logs.java                log.md + .session-events.jsonl
│   └── Slugs.java               slug + dated-filename generation
├── model/WikiContext.java       resolved wiki state for one operation
├── prelude/
│   ├── HubResolver.java         resolve-hub
│   └── WikiResolver.java        resolve-wiki
└── capability/
    ├── Context.java             dependencies passed to every capability
    ├── Init.java                /init
    ├── Ingest.java              /ingest
    ├── IngestCollection.java    /ingest-collection (git adapter only)
    ├── Compile.java             /compile
    ├── Query.java               /query (quick/standard/deep/list/resume)
    ├── Research.java            /research (single round + --min-time loop)
    ├── Thesis.java              /thesis (decompose + thesis file + verdict)
    ├── Librarian.java           /librarian (staleness + quality, two-tier)
    ├── Audit.java               /audit (drift + provenance + escalation)
    ├── Lessons.java             /ll
    ├── Plan.java                /plan
    └── Output.java              /output (summary, report, study-guide, slides, timeline, glossary, comparison)
```

## What's stubbed

The architecture has five **agentic** transitions (research/thesis agent
swarms, audit support/attack/primary-source branches). Without a real agent
runtime, `AgentExecutor.STUB` throws `NotImplementedYet`. Capabilities catch
this gracefully:

- `research` continues the round with the empty source list and reports it.
- `thesis` returns after creating the thesis file and decomposition.
- `audit` Pass 3 still runs `render-claim-verdict` against local-only
  evidence (returns honest `unresolved` verdicts more often).

`WebTools.WebSearcher.STUB` similarly throws. To finish wiring:

```java
LlmWiki wiki = new LlmWiki(new LLM(), myAgentExecutor, myWebSearcher, WebTools.HTTP_FETCHER);
```

## What's incomplete

These are documented stubs marked TODO in code:

| Area | Status |
|------|--------|
| Twitter/X URL fallback chain | stub — only direct fetch attempted |
| MediaWiki dump adapter | stub |
| MediaWiki API adapter | stub |
| `plan --format rfc/adr/spec` | only `roadmap` is fully wired |
| Lessons "current session transcript" | requires `--from <file>` because the Java process has no chat history to scan |
| Reflection between research rounds | placeholder in the loop |

Everything else maps directly to the transitions documented in
`docs/transitions/<capability>/<transition-name>.md`. When making changes,
keep that mapping in sync.

## Template expansion

```java
String s = Templates.expand("Hello {name}, the time is {time}",
    Map.of("name", "world", "time", Instant.now()));
// "Hello world, the time is 2026-05-01T..."
```

Unknown placeholders are left as `{xxx}` so missing values are visible at
runtime. There's also a varargs convenience: `Templates.expand(t, "k1", v1, "k2", v2)`.
