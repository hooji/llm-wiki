# llm-wiki Architecture

This directory documents the techniques used by [llm-wiki](https://llm-wiki.net) in
enough detail to re-implement the system as a deterministic managed workflow in
a host language (e.g. Java). Each capability has its own document with prompt
templates and JSON schemas. The goal: every LLM interaction can be funneled
through a single call:

```java
String json = LLMCall(String prompt);
```

…and every reply parsed as structured JSON to drive the next step.

## What llm-wiki actually is

llm-wiki is **not** a database, not a vector store, and not a custom server. It
is a *protocol* — a set of file-shape conventions plus a set of commands —
that turns any agentic LLM (Claude Code, Codex, OpenCode, Pi, or a plain
AGENTS.md harness) into a research engine that maintains a markdown knowledge
base on disk. There is no runtime; the runtime is the host agent.

Three guarantees define the system:

1. **Raw sources are immutable.** Once a URL/file/tweet is ingested, the
   captured copy in `raw/` is never edited. Synthesis happens in `wiki/`.
2. **Indexes are a derived cache.** Every directory has an `_index.md`, but
   the source of truth is each file's YAML frontmatter. Indexes are rebuilt on
   read whenever the file count and the row count disagree. This makes
   concurrent writes safe without locks.
3. **Articles are synthesized, not copied.** A wiki article draws from
   multiple raw sources, contextualizes, cross-references, and scores
   confidence. Compilation is the LLM-as-compiler step.

Everything else — research swarms, thesis verdicts, audit drift detection,
lessons-learned extraction, output artifacts — is built on top of those three
properties.

## Filesystem layout

```
HUB/                                # configurable, e.g. ~/wiki or iCloud
├── wikis.json                      # registry of topic wikis
├── _index.md                       # hub index
├── log.md                          # global activity log
└── topics/
    └── <topic-slug>/               # one isolated wiki per topic
        ├── _index.md               # master index for the topic
        ├── config.md               # title, scope, freshness_threshold
        ├── log.md                  # append-only activity log
        ├── inbox/                  # drop zone (.processed/ inside)
        ├── raw/                    # IMMUTABLE source captures
        │   ├── articles/           # general web pages
        │   ├── papers/             # arxiv, doi, peer-reviewed
        │   ├── repos/              # github/gitlab + collection manifests
        │   ├── notes/              # tweets, freeform, lessons-learned
        │   └── data/               # csv/json datasets
        ├── wiki/                   # SYNTHESIZED articles
        │   ├── concepts/           # bounded ideas (1-3 page each)
        │   ├── topics/             # broader themes
        │   ├── references/         # curated lists
        │   └── theses/             # thesis investigations w/ verdicts
        ├── output/                 # generated artifacts
        │   └── projects/<slug>/    # multi-artifact deliverables
        │       └── WHY.md          # required goal/rationale
        ├── .librarian/             # quality + staleness scan reports
        ├── .audit/                 # umbrella trust audit reports
        ├── .research-session.json  # ephemeral crash-recovery state
        ├── .thesis-session.json    # ephemeral crash-recovery state
        ├── .session-events.jsonl   # durable append-only event log
        └── .session-checkpoint.json# durable replayable summary
```

Hub resolution order (every command starts here):

1. Read `~/.config/llm-wiki/config.json`. If `resolved_path` is present, use
   it. If only `hub_path` is present, expand the leading `~` (only the leading
   one — tildes inside `com~apple~CloudDocs` are literal) and write
   `resolved_path` back so this never happens again.
2. Otherwise try `$HOME/wiki/_index.md` as a fallback.
3. Otherwise ask the user.

Wiki resolution order (after HUB is known): `--local` flag → `--wiki <name>`
flag → CWD has `.wiki/` → HUB itself.

## File shapes

### Raw source frontmatter

```yaml
---
title: "Attention Is All You Need"
source: "https://arxiv.org/abs/1706.03762"
type: articles | papers | repos | notes | data
ingested: 2026-04-04
tags: [transformer, attention, deep-learning]
summary: "2-3 sentence factual summary."
# Optional collection-import provenance:
collection: "<slug>"
adapter: git | mediawiki-dump | mediawiki-api
upstream_id: "<repo path or page id>"
revision: "<commit sha or rev id>"
sha: "<blob hash>"
canonical_url: "<stable upstream URL>"
content_format: markdown | mediawiki | wikitext | text
license: "<detected or unknown>"
fetched: 2026-04-04
---
```

Filenames are date-prefixed: `YYYY-MM-DD-descriptive-slug.md` (max 60 chars).

### Wiki article frontmatter

```yaml
---
title: "Transformer Architecture"
category: concept | topic | reference   # determines wiki/<dir>/
sources: [raw/papers/2026-04-04-attention-is-all-you-need.md]
created: 2026-04-04
updated: 2026-04-04
tags: [transformer, self-attention, architecture]
aliases: [Transformer, vanilla transformer]
confidence: high | medium | low
volatility: hot | warm | cold           # controls freshness decay
verified: 2026-04-04
summary: "2-3 sentence summary for index."
---
```

Filenames are *not* date-prefixed (`transformer-architecture.md`) because
articles are living documents.

### Thesis file frontmatter

```yaml
---
title: "Thesis: <statement>"
type: thesis
status: investigating | completed
verdict: pending | supported | partially-supported | contradicted | mixed | insufficient-evidence
confidence: pending | high | medium | low
core_claim: "<one sentence>"
key_variables: [var1, var2, var3]
falsification: "<what would disprove this>"
created: 2026-04-04
updated: 2026-04-04
---
```

### Output artifact frontmatter

```yaml
---
title: "..."
type: summary | report | study-guide | slides | timeline | glossary | comparison | playbook | plan
sources: [wiki/concepts/transformer-architecture.md, ...]
generated: 2026-04-04
project: <slug>     # optional
---
```

### Index files (`_index.md`)

Every directory has one. Format:

```markdown
# <Directory Name> Index

> One-line description.

Last updated: 2026-04-04

## Contents

| File | Summary | Tags | Updated |
|------|---------|------|---------|
| [filename.md](filename.md) | ... | tag1, tag2 | 2026-04-04 |

## Recent Changes

- 2026-04-04: ...
```

The master index also carries Statistics (counts, "Last compiled", "Last lint")
and Quick Navigation.

### Activity log (`log.md`)

Append-only. One entry per operation, grep-friendly:

```
## [2026-04-04] init | Wiki initialized
## [2026-04-04] ingest | "Attention Is All You Need" (raw/papers/...)
## [2026-04-04] compile | 2 sources → 3 new articles, 1 updated
## [2026-04-04] research | "transformer variants" → 5 sources, 4 articles, score 78
## [2026-04-04] audit | scope=full, outputs=8, drifted=2, escalations=4
```

Operations: `init`, `ingest`, `ingest-collection`, `compile`, `query`, `lint`,
`research`, `output`, `refresh`, `librarian`, `audit`, `plan`, `project`,
`ll`, `assess`.

### Dual links

Every cross-reference between wiki articles writes both link forms on the same
line so Obsidian's wikilink graph and the agent's relative-path navigation
both work:

```
[[transformer-architecture|Transformer]] ([Transformer](../concepts/transformer-architecture.md))
```

## Core techniques

These ideas appear across multiple capabilities. The capability docs reference
them rather than restate them.

### 1. Derived Index Protocol

Indexes are caches; frontmatter is truth. Before reading any `_index.md`:

1. Count `.md` files in the directory (excluding `_index.md`).
2. Count rows in the index Contents table.
3. If they differ, rebuild the index inline by reading each file's
   frontmatter, then continue.

This makes writes lock-free: two sessions can write articles in parallel; the
next read will rebuild a single coherent index from whatever is on disk.

### 2. Structural Guardian

Before/after any wiki write, run a lightweight structural check (auto-fix
trivial issues silently, warn on real problems, never block). Triggers
include: post-write, on skill activation if not linted in 7+ days, when
content lands in the wrong directory, and on first-run (which switches to a
guided onboarding flow rather than a command list).

### 3. Lint-is-the-migration

There is no `/wiki:migrate` command. When the schema changes, you update
linting rules (C11 placement map, C12 allowlists, C13 alias tables) and the
next `/wiki:lint --fix` heals every wiki idempotently. Misplaced files from a
user mistake and misplaced files from an old layout are the same defect.

### 4. Two-tier escalation

The librarian and audit passes both use a metadata-first / content-deep
strategy: cheap pass over frontmatter for everything, expensive full-content
read only for items that score below threshold or carry `volatility: hot`.
Token cost scales with problem density, not wiki size.

### 5. Parallel agent swarm with role lensing

Research, thesis, audit-escalation, and `--plan` decomposition all use the
same pattern: launch N specialist agents in parallel (academic / technical /
applied / news / contrarian, or supporting / opposing / mechanistic / meta /
adjacent), each receiving an identical prompt template parameterized by role
and constraints, returning a structured list of evaluated sources.

### 6. Credibility scoring before ingestion (Phase 2b)

After parallel agents return candidate sources, an *independent* scoring step
rates each source (peer-review, recency, author authority, bias signals,
corroboration) and assigns a tier. This prevents the "fox guarding the
henhouse" problem where the same agent that fetched a source rates its own
work. Scores carry forward into article `confidence:` frontmatter.

### 7. Progress + gap scoring with multi-round reflection

Multi-round research uses a 0-100 progress score per round (sources × 3 +
articles × 5 + cross-refs × 2 + avg credibility × 4) plus a 1-125 gap score
per remaining gap (impact × feasibility × specificity). Reflection between
rounds is the primary value: it discovers cross-round connections, not new
research directions. Termination is principled: ≥80 with no high-impact gaps
→ early completion; <40 for two consecutive rounds → low-yield warning.

### 8. Anti-confirmation-bias rounds

In thesis mode, Round 2 automatically focuses on the *weaker* side of Round 1's
evidence. If Round 1 found mostly supporting evidence, Round 2 hunts for
counter-evidence. This is the methodological difference between thesis and
plain research.

### 9. Adversarial verification (audit)

For every escalated audit finding, run *both* a confirming and a disproving
search. Land each in one of: `supported`, `weakened`, `contradicted`,
`unresolved`. "Unresolved" is better than false confidence.

### 10. Durable session provenance

Two file pairs:

- **Ephemeral**: `.research-session.json`, `.thesis-session.json` — for
  crash recovery only. Deleted on normal completion. Resume detects them and
  asks "continue or start fresh?"
- **Durable**: `.session-events.jsonl` (append-only event log) and
  `.session-checkpoint.json` (latest replayable summary) — preserved after
  normal completion. The audit pass classifies provenance as `replayable`
  when both are present, `partial` if only the checkpoint, `missing` if
  neither.

### 11. Volatility-scaled freshness

Each article carries `volatility: hot | warm | cold` with half-lives 30 / 90
/ 365 days. Freshness composites four dimensions (source freshness,
verification recency, compilation recency, source-chain integrity), each
0-25 points, decayed by volatility tier. Threshold is per-wiki
(`freshness_threshold` in `config.md`, default 70).

### 12. Chunked writes

Never write files longer than ~200 lines in one Write call — the LLM stream
idles during long generations and times out. Always: write skeleton
(frontmatter + headers + first section) first, then sequential Edit calls to
append remaining sections.

## How to read the capability docs

Each of the ten capability docs follows the same shape:

1. **Purpose** — what the capability does and why.
2. **Inputs** — flags, arguments, environment.
3. **Workflow** — numbered deterministic steps. Every step is either:
   - **(deterministic)** — file I/O, parsing, regex, math, time check; no LLM.
   - **(LLM)** — calls `LLMCall(prompt)` and parses the JSON reply.
4. **Prompt templates** — the exact text passed to `LLMCall`.
5. **Output schemas** — the JSON the LLM is asked to return at each step.
6. **Persistence** — files written, log entries, frontmatter mutations.
7. **Edge cases** — failure modes, resume protocol, termination conditions.

The ten capabilities, in dependency order:

| # | Capability | Doc |
|---|-----------|-----|
| 1 | Ingest | [03-ingest.md](03-ingest.md) |
| 2 | Compile | [04-compile.md](04-compile.md) |
| 3 | Query | [05-query.md](05-query.md) |
| 4 | Research | [01-research.md](01-research.md) |
| 5 | Thesis mode | [02-thesis.md](02-thesis.md) |
| 6 | Librarian | [06-librarian.md](06-librarian.md) |
| 7 | Audit | [07-audit.md](07-audit.md) |
| 8 | Lessons | [08-lessons.md](08-lessons.md) |
| 9 | Plan | [09-plan.md](09-plan.md) |
| 10 | Output | [10-output.md](10-output.md) |

Research and Thesis share infrastructure (agent swarm, credibility scoring,
session registry, multi-round termination); Thesis is documented as the delta
from Research. Audit reuses Librarian as a sub-pass. Lessons and Plan are
small specializations of the compile-and-write pattern. Output is the simplest
artifact-generation pattern; Plan is its richer cousin with an interview and
gap-research stage.

## Java workflow notes (re-implementation hints)

A few patterns recur and are worth pulling into shared utilities:

- **`LLMCall(String prompt) → String json`**: the only LLM seam. Every prompt
  documented here ends with explicit JSON schema instructions.
- **`fetchUrl(String url) → String markdown`**: the WebFetch equivalent. Should
  handle HTML→markdown conversion and X.com fallback chain (Grok MCP →
  fxtwitter → vxtwitter → direct).
- **`webSearch(String query) → List<SearchHit>`**: WebSearch equivalent.
- **`writeFile`, `editFile`, `readFile`, `glob`, `grep`**: filesystem
  primitives. Keep `editFile` strictly diff-based (no full rewrites) so the
  chunked-writes pattern works.
- **`appendLog(operation, description)`**: atomic append to `log.md`.
- **`appendEvent(jsonObject)`**: atomic append to `.session-events.jsonl`.
- **`writeCheckpoint(jsonObject)`**: atomic write to
  `.session-checkpoint.json` (write tmp, rename).
- **`rebuildIndex(directory)`**: scan directory, read frontmatter, regenerate
  `_index.md` from a template.

A workflow engine that supports parallel branches (e.g. Java Loom virtual
threads or a CompletionService) is needed for the agent-swarm steps in
Research, Thesis, and Audit-escalation. Everything else is sequential.

## Index of capability docs

- [01-research.md](01-research.md) — parallel agent swarm + multi-round drilling
- [02-thesis.md](02-thesis.md) — for/against agents + verdict + anti-bias rounds
- [03-ingest.md](03-ingest.md) — URL/file/tweet/inbox/collection capture
- [04-compile.md](04-compile.md) — raw → synthesized articles + cross-refs
- [05-query.md](05-query.md) — quick / standard / deep + resume
- [06-librarian.md](06-librarian.md) — staleness + quality scoring
- [07-audit.md](07-audit.md) — drift + provenance + adversarial verification
- [08-lessons.md](08-lessons.md) — extract lessons from session
- [09-plan.md](09-plan.md) — wiki-grounded implementation plans
- [10-output.md](10-output.md) — reports / slides / study guides / etc
