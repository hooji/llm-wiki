# Capability: Output

> Reports, slide decks, study guides, playbooks, implementation plans,
> timelines, glossaries, comparisons. Filed back into the wiki so the next
> output builds on every previous one.

`/wiki:output` is the artifact-generation capability. Given a wiki and a
type, it gathers the relevant articles, optionally loads supplementary
"how-to-write" wikis, calls the LLM to produce a typed artifact, and files
the result back into `output/` (or `output/projects/<slug>/` when a project
is involved). Filing back means the next output can cite the previous one —
the wiki accumulates artifacts the same way it accumulates raw sources.

Plan ([09-plan.md](09-plan.md)) is the richer cousin of output: same general shape, but
with an interview stage and gap research before generation. Output is the
no-interview, no-gap-research version.

## 1. Inputs

| Argument / flag | Meaning |
|-----------------|---------|
| `<type>` (required) | One of: `summary`, `report`, `study-guide`, `slides`, `timeline`, `glossary`, `comparison` |
| `--topic <topic>` | Focus on a specific topic / concept (matches tags + titles) |
| `--sources <paths>` | Comma-separated wiki article paths to use |
| `--with <wiki>` | Supplementary wiki for craft/skill knowledge. Multiple allowed. |
| `--retardmax` | Ship-it-now mode: read everything, don't filter, no plan, more is better |
| `--wiki <name>` / `--local` | Standard wiki resolution |
| `--project <slug>` | Save inside `output/projects/<slug>/` |

## 2. Output types — what each one is

| Type | Shape | Length |
|------|-------|--------|
| `summary` | Condensed overview. Key points, main takeaways. | 1-2 pages |
| `report` | Detailed analytical report with sections, evidence, conclusions. | 3-5 pages |
| `study-guide` | Concepts + definitions + Q&A + concept relationships. | Variable |
| `slides` | Markdown slide deck using `---` separators. 3-5 bullets per slide. Marp-compatible. | 10-30 slides |
| `timeline` | Chronological dated list of developments. | Variable |
| `glossary` | Alphabetically sorted definitions. | Variable |
| `comparison` | Side-by-side feature tables comparing 2+ concepts. | Variable |

(Two more output types — `playbook` from research question-mode and `plan`
from `/wiki:plan` — are produced by their own commands but follow the same
filesystem conventions.)

## 3. Workflow

### Step 0 (deterministic) — Hub + wiki resolution

Standard prelude. Output is wiki-required — if no wiki exists or has no
articles, stop with `No wiki found (or no articles). Run /wiki init and /wiki:compile first.`

### Step 1 (deterministic) — Gather sources

```
if --retardmax:
    sources = readAllWikiArticles()                       // every article in wiki/

elif --sources provided:
    sources = [readArticle(p) for p in --sources]

elif --topic provided:
    matched = grep --topic across wiki/ titles, tags, summaries
    sources = readArticles(matched)

else:
    masterIndex = readMasterIndex()                       // overview
    sources = readArticles(top_articles_per_category)     // a few per category
```

Stale-check indexes before reading. Follow the Derived Index Protocol if
counts disagree.

### Step 2 (deterministic, LLM only if `--with` supplied) — Load supplementary wikis

For each `--with <name>`:

1. Look up in `HUB/wikis.json`.
2. Read its `_index.md`.
3. Read articles relevant to the output type. The relevance pick is one
   small LLM call per `--with` wiki:

   ```
   You are picking craft/skill articles to apply to a generated artifact.

   Output type: {type}
   Output topic (subject matter from primary wiki): "{topic_or_summary}"

   Supplementary wiki: "{with_name}"
   Supplementary wiki articles available:
   [{"path":"...","title":"...","summary":"...","tags":[...]}, ...]

   Return JSON ONLY:
   {
     "articles": [
       {"path":"...","apply_to":"<which part of the output this technique applies to>"}
     ]                                                    // 3-7 max
   }
   ```

The primary wiki provides the **subject matter** — facts, research,
domain knowledge. `--with` wikis provide **craft/skill** — techniques,
frameworks, writing patterns, best practices. When generating, apply the
supplementary wiki's techniques to the primary wiki's content.

Example: `--wiki quantum-computing --with article-writing` uses
quantum-computing for content and article-writing for hooks, structure,
E-E-A-T, viral patterns.

### Step 3 (LLM, chunked writes) — Generate

The generation prompt is type-specific. The pattern is the same: ask for
JSON with sections, then deterministically render markdown with
front-matter.

#### Common prompt header

```
You are generating a {type} from a wiki knowledge base.

Subject articles (from primary wiki):
[
  {"path":"...","title":"...","confidence":"high","content":"<full body>"},
  ...
]

Craft articles (from --with wikis, optional):
[
  {"wiki":"<name>","path":"...","title":"...","apply_to":"<phrase>","content":"<full body>"}
]

Mode: {standard | retardmax}
```

#### Type-specific prompts

##### summary

```
Return JSON ONLY:
{
  "title": "...",
  "subtitle": "<optional>",
  "tldr": "<2-3 sentence executive takeaway>",
  "sections": [
    {"heading":"## Key Points","markdown":"<3-7 bullets, each citing wiki articles inline>"},
    {"heading":"## What This Means","markdown":"..."},
    {"heading":"## Sources","markdown":"<list of dual-link citations>"}
  ]
}
```

##### report

```
Return JSON ONLY:
{
  "title": "...",
  "executive_summary": "<2-3 sentences>",
  "sections": [
    {"heading":"## Background","markdown":"..."},
    {"heading":"## Findings","markdown":"<each with [Article](path) citations>"},
    {"heading":"## Analysis","markdown":"..."},
    {"heading":"## Conclusions","markdown":"..."},
    {"heading":"## Sources","markdown":"..."}
  ],                                                       // 4-8 sections total
  "wiki_articles_used": ["wiki/...md", ...]
}
```

##### study-guide

```
Return JSON ONLY:
{
  "title": "...",
  "concepts": [
    {
      "name": "...",
      "slug": "...",
      "definition": "<1-2 sentences, plain language>",
      "key_relationships": [{"to":"<slug>","kind":"is-a"|"part-of"|"contrasts-with","description":"..."}],
      "wiki_link": "wiki/concepts/...md"
    }
  ],
  "questions": [
    {
      "q": "...",
      "a": "<short answer with [Article](path) citations>",
      "difficulty": "easy"|"medium"|"hard"
    }                                                      // 8-20 Q&As
  ]
}
```

##### slides

```
Return JSON ONLY:
{
  "title": "...",
  "subtitle": "...",
  "slides": [
    {
      "title": "...",
      "bullets": ["...","...","..."],                     // 3-5 bullets
      "speaker_notes": "<optional>",
      "wiki_citations": ["wiki/...md"]
    }
  ]                                                        // 10-30 slides
}
```

Render with `---` separators between slides for Marp/etc compatibility.

##### timeline

```
Return JSON ONLY:
{
  "title": "...",
  "entries": [
    {
      "date": "YYYY" | "YYYY-MM" | "YYYY-MM-DD",
      "event": "...",
      "significance": "<one sentence>",
      "source_article": "wiki/...md"
    }
  ]                                                        // sorted ascending by date
}
```

##### glossary

```
Return JSON ONLY:
{
  "title": "...",
  "scope": "<what this glossary covers>",
  "entries": [
    {
      "term": "...",
      "aliases": ["..."],
      "definition": "<1-3 sentences, plain language>",
      "see_also": ["<term>", "<term>"],
      "source_article": "wiki/.../...md"
    }
  ]                                                        // sorted alphabetically by term
}
```

##### comparison

```
Return JSON ONLY:
{
  "title": "...",
  "subjects": [
    {"name":"...", "wiki_article":"wiki/...md"},
    {"name":"...", "wiki_article":"wiki/...md"}
  ],
  "dimensions": [
    {
      "dimension": "<feature being compared>",
      "values_per_subject": ["...","..."],                 // same length as subjects
      "notes": "<optional one-line>"
    }
  ],
  "summary": "<2-3 sentence overall takeaway>"
}
```

### Retardmax modifier

When `--retardmax` is set, modify Step 3:

- Read **all** articles in the wiki (Step 1 already does this).
- Add to the prompt: "Don't agonize over structure. Get the content down.
  More is better than less. Polish later."
- Skip the relevance filter on `--with` wikis.
- For `report`/`slides`, allow more sections/slides than the type's
  default range.
- Ship the artifact even if structure is rough.

This is the "act first, think later" mode. The user can iterate with a
non-retardmax pass afterwards.

### Step 4 (deterministic) — Render markdown

Per type, deterministic templating from the JSON. Examples:

- **summary**: frontmatter + `# {title}` + `> {tldr}` + sections.
- **slides**: frontmatter + `# {title}` + `\n---\n` + per-slide
  `## {title}\n- bullet\n- bullet`.
- **timeline**: frontmatter + `# {title}` + `## {date} — {event}\n>
  {significance} ([source](wiki/...md))`.
- **glossary**: frontmatter + `# {title}` + per-term subsection sorted
  alphabetically.
- **comparison**: frontmatter + a markdown table with subjects as columns
  and dimensions as rows.

Frontmatter schema:

```yaml
---
title: "{title}"
type: summary | report | study-guide | slides | timeline | glossary | comparison
sources: [wiki articles used]
generated: YYYY-MM-DD
project: <slug>          # optional
with_wikis: [<name>, <name>]    # optional, when --with was used
---
```

### Step 5 (deterministic, chunked writes) — Save

Path:

```
if --project <slug>:
    path = output/projects/<slug>/<type>-<topic-slug>-<YYYY-MM-DD>.md
elif type produces binary siblings (rare; default no):
    create or use a project folder
else:
    path = output/<type>-<topic-slug>-<YYYY-MM-DD>.md
```

When `--project` is used, verify the project exists at
`output/projects/<slug>/WHY.md` first; if not, fail early with the
suggestion to run `/wiki:project new <slug> "goal"`.

Write the file using chunked writes:

1. Write skeleton: frontmatter + first heading + first section (or first
   slide).
2. For each remaining section/slide/entry, Edit-append.

This avoids LLM-stream timeouts on large outputs.

### Step 6 (deterministic) — Update indexes

1. `output/_index.md` — append a row with title, type, generated date,
   source-count.
2. master `_index.md` — bump output count, prepend Recent Changes entry.
3. If `output/projects/` exists, regenerate `output/_index.md` as a
   projects-aware listing (per Compile Step 9): scan each
   `output/projects/*/WHY.md` for first `#` heading (title) and first
   non-heading paragraph (goal), table them, then list any remaining
   loose `output/*.md` below.

All best-effort.

### Step 7 (deterministic) — Log

```
## [YYYY-MM-DD] output | {type} on {topic} → output/{filename}.md
```

### Step 8 (deterministic) — Report

To the user: artifact path, type, source articles used, total length
(words or slide count).

## 4. Output schemas summary

The seven type-specific JSON shapes are listed under Step 3. Each is
designed to be deterministically rendered into markdown without further
LLM intervention.

## 5. Persistence summary

| File | Written by |
|------|------------|
| `output/<type>-<slug>-<date>.md` (or in `output/projects/<slug>/`) | Step 5 (chunked) |
| `output/_index.md`, master `_index.md` | Step 6 (best-effort) |
| `log.md` | Step 7 |

No durable session state.

## 6. Filing back & cumulative knowledge

Outputs are wiki citizens, not export files. They:

- Carry frontmatter (`title`, `type`, `sources`, `generated`).
- Are scanned by Audit ([07-audit.md](07-audit.md)) for drift — every output's `sources:`
  is a dependency chain that audit traces.
- Can themselves appear in a future output's `sources:` (e.g. a slide deck
  built on top of last week's report).
- Lint C9d clusters loose markdown outputs sharing a common slug prefix
  and proposes wrapping them in a project folder.
- Project folders (`output/projects/<slug>/`) require a `WHY.md`
  (lint C8a) — the project's rationale never gets lost.

Output is the closing arc of every workflow:

```
ingest → compile → query / research / thesis / plan → output → audit
                                                           ↑
                                                  next output cites this one
```

## 7. Edge cases

- **Wiki has no articles**: stop with the standard "no articles" message;
  suggest `/wiki:compile` first.
- **`--topic` matches nothing**: fall back to reading the master index
  overview. Warn the user that the topic filter did nothing.
- **Type doesn't fit the wiki content**: e.g. asking for a `timeline` from
  a wiki with no dated events. The LLM's response will be sparse; the
  rendered artifact will be honest about the gap. Lint C9 may flag the
  output as "thin" later.
- **`--retardmax` plus a small wiki**: still works; the artifact is
  short. The mode lowers the bar, doesn't raise the floor.
- **`--project` with a non-existent slug**: stop early.
- **Concurrent `output` calls**: both write distinct files (date+slug+type
  is generally unique enough); both update indexes best-effort; the next
  read rebuilds.

## 8. Java reimplementation outline

```java
public class OutputService {
  public OutputResult generate(OutputRequest req) {
    WikiContext wiki = wikis.resolveRequired(req);
    if (req.project != null) verifyProject(wiki, req.project);

    List<Article> primary = gatherSources(wiki, req);              // Step 1
    Map<String, List<Article>> withArticles = req.with.stream()
      .collect(toMap(w -> w, w -> pickWithArticlesLLM(w, primary, req.type)));  // Step 2

    String prompt = TYPE_PROMPTS.get(req.type).bind(primary, withArticles, req.retardmax);
    String json = llm.call(prompt);                                // Step 3
    OutputJson out = OutputJson.fromJson(req.type, json);

    Path path = computeOutputPath(wiki, req, out.title);
    writeChunked(path, render(req.type, out, req));                // Step 4-5

    bestEffort(() -> updateIndexes(wiki, path, out));              // Step 6
    appendLog(wiki, "output", req.type + " on " + out.title);
    return new OutputResult(path, out);
  }
}
```

## 9. Cross-references

- Citations in outputs use the standard markdown link form
  `[Article](../wiki/<category>/<slug>.md)` rather than dual-links —
  outputs are not Obsidian-graph nodes the way wiki articles are.
- Outputs are referenced *from* wiki articles only when the output
  becomes canonical (rare; usually the user edits the wiki article to
  cite the output explicitly).
- Outputs filed under `output/projects/<slug>/` are scoped to that
  project; cross-project references use plain markdown links.
