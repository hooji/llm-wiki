# Capability: Query

> Quick (indexes), standard (articles), or deep (everything + sibling wikis).
> `--resume` picks up where you left off.

Query answers natural-language questions against the compiled wiki. It does
**not** consult the LLM's training knowledge — answers come only from wiki
content, with citations and honest gap reporting.

Three depth levels and one orthogonal mode (`--resume`).

## 1. Inputs

| Flag | Meaning |
|------|---------|
| `<question>` | Free text. Everything not a flag. |
| `--quick` | Indexes only. Fastest. |
| `--deep` | Everything + raw + sibling wikis. |
| `--raw` | Also search `raw/` (implied by `--deep`). |
| `--list` | Return a ranked article list, no synthesis. |
| `--resume` | Briefing of where the user left off. |
| `--tag <tag>` | Filter by frontmatter tag. |
| `--category concepts\|topics\|references` | Filter category. |
| `--with <wiki>` | Load a supplementary wiki for craft/skill knowledge. Multiple allowed. |
| `--wiki <name>` / `--local` | Standard resolution. |

## 2. Workflow — standard depth (default)

### Step 0 (deterministic) — Hub + wiki resolution

Standard prelude.

### Step 1 (deterministic) — Index freshness check

Before reading any `_index.md`, count `.md` files in its directory vs rows in
its Contents table. If they disagree, rebuild from frontmatter inline
(Derived Index Protocol).

### Step 2 (LLM) — Identify relevant categories from master index

Read `_index.md`. Ask the LLM to pick relevant categories.

**Prompt:**

```
You are routing a question to the right corner of a wiki.

Question: "{question}"

Master index Contents (categories at top level):
{master_index_table_as_markdown}

Quick Navigation:
- concepts: {concepts_index_summary_or_count}
- topics:   {topics_index_summary_or_count}
- references: {references_index_summary_or_count}

Return JSON ONLY:

{
  "relevant_categories": ["concepts", "topics"],     // subset of {concepts, topics, references}
  "reasoning": "<one sentence>"
}
```

### Step 3 (LLM) — Pick candidate articles from category indexes

Read each relevant category's `_index.md`. Ask the LLM to pick 3-8 candidate
articles.

**Prompt:**

```
You are picking the most relevant wiki articles for a question.

Question: "{question}"

Articles available (from category indexes):
[
  {"path": "wiki/concepts/transformer-architecture.md",
   "title": "Transformer Architecture",
   "summary": "...",
   "tags": ["transformer","attention"]},
  ...
]

Return JSON ONLY:

{
  "candidates": [
    {"path": "<path>", "relevance": "primary" | "supporting", "why": "<phrase>"}
  ]                                                    // 3-8 entries, ordered by relevance
}
```

### Step 4 (deterministic) — Read candidate articles in full

Read each candidate file. Follow `## See Also` links if their slugs are not
already in the candidate set. Cap recursion at one extra hop.

### Step 5 (deterministic) — Full-text search

Use Grep on `wiki/` for key terms from the question to surface anything the
indexes missed. Add any new hits to the read set.

If `--tag <tag>` is set, prefilter by `tags:.*<tag>` in frontmatter.
If `--category <cat>` is set, restrict to that directory.

### Step 6 (LLM) — Synthesize answer

**Prompt:**

```
You are answering a question from a wiki. Use ONLY the provided article
content. Do NOT use your training knowledge. If the wiki does not have
enough information, say so.

Question: "{question}"

Wiki articles (in order of relevance):
[
  {
    "path": "wiki/concepts/...md",
    "title": "...",
    "confidence": "high",
    "content": "<full article body>"
  },
  ...
]

Sibling wiki overlap (only _index.md content): {sibling_index_summaries}

--with supplementary wiki content (craft/skill, optional): {with_wikis_content_or_empty}

Return JSON ONLY:

{
  "answer_markdown": "<the full answer in markdown, with [text](path) citations to the wiki articles>",
  "sources_used": [
    {"path": "wiki/...md", "confidence": "high", "what_drawn": "<one phrase>"}
  ],
  "related_in_other_wikis": [
    {"wiki": "<name>", "title": "<article>", "why": "<phrase>"}
  ],                                                   // empty unless overlap was found
  "knowledge_gaps": ["<gap 1>", "<gap 2>"],            // empty if none
  "suggested_ingest": ["<topic or URL pattern to add>"]
}

Citation rule: every factual claim that came from an article must have an
inline link [text](path) to that article. Mention confidence when it is
medium or low.
```

### Step 7 (deterministic) — Render and log

Render the answer block:

```
{answer_markdown}

---
**Sources used:**
- [Article 1](path) (confidence: high) — what was drawn
- [Article 2](path) (confidence: medium) — what was drawn

**Related in other wikis:** (only if non-empty)
- [wiki-name]: [Article Title] — why

**Knowledge gaps:** (only if non-empty)
- ...
- Suggested sources to ingest: ...
```

Append to `log.md`:
`## [YYYY-MM-DD] query | "{question}" → answered from N articles (standard)`

## 3. Workflow — `--quick`

Skip Steps 3-5. After Step 2:

### Q3 (LLM) — Answer from index summaries only

```
Answer this question from index summaries alone. Do NOT request
additional articles. If the indexes do not contain enough to answer,
say so.

Question: "{question}"

Index entries (from master + selected categories):
[
  {"path": "...", "title": "...", "summary": "...", "tags": [...]},
  ...
]

Return JSON ONLY:
{
  "answer_markdown": "<short answer or 'insufficient information in indexes'>",
  "sources_used": [{"path": "...", "what_drawn": "..."}],
  "knowledge_gaps": ["..."],
  "suggest_rerun_without_quick": true | false
}
```

## 4. Workflow — `--deep`

Replace Steps 2-5 with:

### D2 — Read **all** category `_index.md` files (no LLM filter).
### D3 — Identify relevant articles via LLM:

Same as Step 3 but with all index data passed in and target count 8-20
candidates instead of 3-8.

### D4 — Read every candidate. Follow **all** See Also links, even
tangential. Cap depth at 3 hops.

### D5 — Grep `wiki/` AND `raw/` for key terms, synonyms, and related
concepts. Read any raw sources that match. (`--raw` is implied by `--deep`.)

### D6 — Sibling wiki peek:

1. Read `HUB/wikis.json` for sibling list.
2. For each, read only its `_index.md`.
3. If overlap found by tags/summary, note path; do **not** read full
   articles unless the user explicitly asks.

### D7 — Synthesize using the same prompt as Step 6, with all the additional
content passed in.

## 5. Workflow — `--list`

Return a ranked list, no synthesis.

### L1 — Index scan + Grep (same as Steps 2/3/5 but without LLM article-picking).

### L2 (LLM) — Rank

```
Rank these wiki search results by relevance to the query.

Query: "{query}"

Results:
[
  {"path": "...", "title": "...", "summary": "...", "tags": [...],
   "match_kind": "title" | "summary" | "body" | "tag",
   "match_count": 3,
   "updated": "2026-04-04"},
  ...
]

Ranking rules: title match > summary match > body match. Multiple-term match
beats single. More recent beats older.

Return JSON ONLY:
{
  "ranked": [
    {"path": "...", "rank": 1, "reason": "<phrase>"},
    ...
  ]
}
```

### L3 — Render the list. Skip Sources/Gaps blocks.

## 6. Workflow — `--resume`

This is the "where did I leave off" briefing. Per CLAUDE.md, every resume
output must start with the wiki identity:

```
<wiki-name> booted from <wiki-root-path>.
```

`<wiki-name>` resolution:

1. `title` from `config.md` frontmatter, else
2. parent directory basename for local `.wiki/`, else
3. topic slug for `HUB/topics/<slug>/`.

### R1 (deterministic) — Detect interrupted sessions

Try reading:

- `.research-session.json`
- `.thesis-session.json`

If either exists with `status: "in_progress"`, use it as the briefing focus.

### R2 (deterministic) — Durable provenance fallback

If no active session file exists, read:

- `.session-checkpoint.json`
- tail of `.session-events.jsonl` (last ~20 lines)

Use these to summarize the most recent completed run.

### R3 (deterministic) — Recent activity

`grep "^## \[" log.md | tail -10` → render compact timeline.

### R4 (deterministic) — Stats

Read master `_index.md` Statistics section. Pull source / article / output
counts. (Stale-check first.)

### R5 (deterministic) — Last 3 updated articles

From the master `_index.md` Contents table, sort by `Updated` column desc,
take 3. Show `[title](path) — summary`. Do **not** read article bodies.

### R6 (LLM, optional small) — Suggested next steps

```
Given this resume context, suggest the most useful next step(s).

Context:
{
  "interrupted_session": <obj or null>,
  "recent_log": [...],
  "stats": {sources, articles, outputs},
  "last_updated_articles": [...],
  "last_audit_findings": <obj or null>     // from .audit/scan-results.json if recent
}

Return JSON:
{
  "suggestions": [
    {"label": "Resume research", "command": "/wiki:research --min-time ..."},
    ...
  ]                                          // 1-3 entries
}
```

If the user passed `--resume` plus a question, run the briefing first, then
fall through to standard-depth answer (Steps 2-6).

### R7 (deterministic) — Log

`## [YYYY-MM-DD] query | --resume briefing`

## 7. Output schemas summary

Synthesized answer:

```json
{
  "answer_markdown": "...",
  "sources_used": [{"path": "...", "confidence": "high", "what_drawn": "..."}],
  "related_in_other_wikis": [{"wiki": "...", "title": "...", "why": "..."}],
  "knowledge_gaps": ["..."],
  "suggested_ingest": ["..."]
}
```

List mode:

```json
{
  "ranked": [{"path": "...", "rank": 1, "reason": "..."}]
}
```

Resume:

```json
{
  "wiki_name": "...",
  "wiki_root": "...",
  "interrupted_session": null | {...},
  "recent_log": [...],
  "stats": {"sources": N, "articles": N, "outputs": N},
  "last_updated_articles": [{"path": "...", "title": "...", "summary": "..."}],
  "suggestions": [{"label": "...", "command": "..."}]
}
```

## 8. Persistence

Query writes only `log.md`. No state changes elsewhere. Read-only on every
content artifact.

## 9. Edge cases

- **Wiki has no compiled articles**: Step 2 returns empty. Report
  `No wiki found (or no articles compiled). Run /wiki init and /wiki:compile first.`
- **Question with no index hits**: Step 3 returns no candidates. Step 5's
  Grep is the safety net; if it also returns nothing, the answer's
  `knowledge_gaps` populates with the entire question and suggests
  `/wiki:ingest` or `/wiki:research`.
- **Confidence-low articles dominate**: the answer prompt explicitly
  instructs the LLM to mention confidence when medium/low. The user sees
  uncertainty rather than a confident wrong answer.
- **Ambiguous question**: the master-index router may pick the wrong
  category. Standard depth's Grep step (5) catches the miss.

## 10. Java reimplementation outline

```java
public class QueryService {
  public Answer query(QueryRequest req) {
    WikiContext wiki = wikis.resolve(req);
    if (req.resume) return resumeBriefing(wiki, req);

    Depth depth = req.depth;                                       // QUICK | STANDARD | DEEP | LIST
    rebuildStaleIndexes(wiki);

    if (depth == LIST) return listMode(wiki, req);

    Set<Category> cats = (depth == DEEP)
      ? EnumSet.allOf(Category.class)
      : pickCategoriesLLM(wiki, req.question);                     // Step 2

    List<ArticleRef> candidates = pickArticlesLLM(wiki, cats, req.question, depth);
    Set<Path> readSet = expandWithSeeAlsoAndGrep(wiki, candidates, depth);

    if (depth == QUICK) return answerFromIndexesLLM(wiki, cats, req.question);

    List<SiblingHit> siblings = (depth == DEEP) ? peekSiblings(wiki, req.question) : List.of();
    Map<String, String> withWikis = req.with.stream()
        .collect(toMap(w -> w, w -> loadWithWikiContent(w)));

    String json = llm.call(SYNTH_PROMPT.bind(req.question, readSet, siblings, withWikis));
    Answer a = Answer.fromJson(json);
    appendLog(wiki, "query", "\"" + req.question + "\" → answered from " + a.sourcesUsed.size() + " articles (" + depth + ")");
    return a;
  }
}
```
