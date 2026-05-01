# Capability: Ingest

> URLs, files, tweets, bulk inbox, and bounded upstream collections. Raw
> sources stay immutable; articles synthesize on top. Every claim traces back
> to its source.

Ingest is the entry point for new material. It writes one immutable file per
source into `raw/{type}/` with structured YAML frontmatter, never edits a
source after it lands, and never compiles. Compilation (the LLM-as-compiler
step) is a separate capability ([04-compile.md](04-compile.md)).

There are two surface commands and one shared protocol:

- `/wiki:ingest <source>` — single URL, file path, or quoted text, plus
  `--inbox` for batch processing.
- `/wiki:ingest-collection <source>` — bounded upstream corpus (Git docs
  repo, BIP set, MediaWiki dump, MediaWiki API site).

## 1. Inputs

| Flag | Meaning |
|------|---------|
| `<source>` | URL (`http://`, `https://`), absolute/relative file path, or quoted freeform text |
| `--type` | Force `articles` / `papers` / `repos` / `notes` / `data` (default: auto-detect) |
| `--title` | Override extracted title |
| `--inbox` | Process every file in `<wiki>/inbox/` |
| `--keep` | When processing inbox, copy to `.processed/` instead of moving |
| `--auto-classify` | Pick the best-matching topic wiki for the item |
| `--new-topic <name>` | Create the topic wiki if it doesn't exist, then ingest into it |
| `--project <slug>` | Tag the ingested source with `project: <slug>` (raw still lands in normal `raw/<type>/`) |
| `--wiki <name>` / `--local` | Standard wiki resolution flags |
| `--adapter` | (collection only) `auto` / `git` / `mediawiki-dump` / `mediawiki-api` |
| `--limit <N>` | (collection only) Cap child sources |
| `--dry-run` | (collection only) Inventory + count, write nothing |
| `--compile` | (collection only) Run compile after ingestion |

## 2. Workflow — single-source ingest

### Step 0 (deterministic) — Resolve hub and wiki

Standard prelude: read `~/.config/llm-wiki/config.json`, expand `~`, write
`resolved_path` back if needed. Then resolve the wiki (`--local` /
`--wiki <name>` / CWD `.wiki/` / HUB). If the wiki doesn't exist and
`--new-topic` is set, run init first.

### Step 1 (deterministic) — Detect source type

```
classifySource(source) → one of {url, twitter, file, text, github}
```

Rules (first match wins):

| Pattern | Type | Default raw type |
|---------|------|------------------|
| `x.com/*/status/*`, `twitter.com/*/status/*` | twitter | `notes` |
| `github.com/*/*` (no `/blob/` or `/tree/`) | github | `repos` |
| starts with `http://` or `https://` | url | `articles` (or `papers` if URL contains `arxiv`, `doi.org`, `/pdf`) |
| contains `/` or starts with `~`, `.` | file | derived from extension |
| anything else (quoted or bare) | text | `notes` |

### Step 2 (mixed) — Fetch content

#### 2a. URL fetch (LLM)

Call WebFetch (which itself uses an LLM-summarized HTML→markdown extraction)
with the prompt:

```
Extract the complete article content from this page. Return: title,
author(s) if listed, date published if listed, and the full article text
preserving all factual claims, data points, code examples, and technical
details. Format as clean markdown.
```

Return JSON:

```json
{
  "title": "...",
  "authors": ["..."],
  "published": "YYYY-MM-DD or null",
  "content_markdown": "..."
}
```

#### 2b. GitHub URL fetch (LLM)

Same WebFetch primitive, different extraction prompt:

```
Extract from this GitHub repository: name, description, key technologies,
main purpose, README content. Format as markdown.
```

JSON:

```json
{
  "title": "owner/repo",
  "description": "...",
  "technologies": ["..."],
  "purpose": "...",
  "readme_markdown": "..."
}
```

#### 2c. Twitter / X fetch (deterministic with LLM fallback)

Try in order, stop on first success:

1. Grok MCP if available (`mcp__grok__*` tools).
2. `https://api.fxtwitter.com/<user>/status/<id>` — returns JSON with
   `tweet.text`, `tweet.author`, `tweet.created_at`, `tweet.media`.
3. `https://api.vxtwitter.com/<user>/status/<id>` — same shape.
4. WebFetch the original URL (often blocked).
5. If all fail, report and ask the user to paste text.

Build a synthetic markdown body of the form:

```markdown
> @<handle> — <display name>
> <date>

<full tweet text>

[Media: <descriptions>]
```

#### 2d. File read (deterministic)

- `.md`, `.txt` → read directly.
- `.pdf` → write a metadata stub noting the file path.
- `.json`, `.csv`, `.tsv` → read schema + first ~20 rows; do *not* embed the
  full dataset.
- Images → metadata stub with path.

#### 2e. Quoted text (deterministic)

Use the text verbatim.

### Step 3 (LLM) — Extract metadata

After Step 2 has produced `content_markdown` and possibly a title, ask the
LLM to derive the structured frontmatter fields:

**Prompt:**

```
You are extracting structured metadata for a knowledge-base raw source.

Content (between fences):
```
{content_markdown}
```

Return JSON ONLY with this exact shape:

{
  "title": "<short descriptive title, max 80 chars>",
  "summary": "<2-3 sentence factual summary; no marketing language>",
  "tags": ["<lowercase-hyphenated-tag>", ...],     // 3-7 specific tags;
                                                   //   prefer specific over general:
                                                   //   "transformer-architecture" not "ai"
  "type_suggestion": "articles" | "papers" | "repos" | "notes" | "data",
  "authors": ["..."]                               // optional, can be []
}

Tag rules:
- lowercase, hyphen-separated
- specific over general (good: "self-attention"; bad: "ml")
- 3-7 tags total
- no near-duplicates ("nlp" vs "natural-language-processing")
```

Apply user overrides: if `--title` is set, replace `title`. If `--type` is
set, replace `type_suggestion`.

### Step 4 (deterministic) — Generate slug and resolve target path

```
slug = slugify(title)                              // lowercase, hyphens,
                                                   //   no special chars,
                                                   //   max 60 chars
date = today().toIsoDate()
filename = "${date}-${slug}.md"
type = userOverride || metadata.type_suggestion
path = "${wikiRoot}/raw/${type}/${filename}"
if exists(path): filename = "${date}-${slug}-2.md"  // bump suffix until unique
```

### Step 5 (deterministic) — Topic-wiki routing (single item, no `--wiki`)

If the resolved wiki was the HUB (i.e. user didn't pass `--wiki` or `--local`
and CWD has no `.wiki/`), and the hub has registered topics:

#### 5a. Read scopes

Read `HUB/wikis.json`. For each registered topic, read its `config.md`
frontmatter `description`/`scope` (first 5 lines is enough).

#### 5b. Match (LLM)

Prompt:

```
You are routing a new source into the best-matching topic wiki.

Source:
  title: {title}
  summary: {summary}
  tags: {tags}

Topic wikis (slug → description):
{
  "ai-security": "AI security, agent safety, prompt injection",
  "geo": "GEO & AI Search Optimization",
  ...
}

Return JSON:

{
  "best_match": "<slug or null>",
  "match_strength": "strong" | "weak" | "none",
  "reasoning": "<one sentence>",
  "alternatives": ["<slug>", ...]                   // up to 2
}
```

Present the choice as a numbered menu (best match first, then alternatives,
then "New wiki" and "Skip"). User picks. If they pick "New wiki", run init
first.

### Step 6 (deterministic) — Write the raw source

Compose the file with frontmatter from Step 3 + body from Step 2:

```markdown
---
title: "{title}"
source: "{originalUrl or filePath or "MANUAL"}"
type: {type}
ingested: {date}
tags: [{tags}]
summary: "{summary}"
---

# {title}

{content_markdown}
```

Write atomically. (No locks needed — slug uniqueness already prevents
collision; simultaneous writers picking the same slug both bump-suffix on
existence check.)

### Step 7 (deterministic, best-effort) — Update indexes

Update in order; abort silently on failure (the next read rebuilds via the
Derived Index Protocol):

1. `raw/<type>/_index.md` — append row.
2. `raw/_index.md` — append row.
3. master `_index.md` — bump source count, prepend Recent Changes entry.

### Step 8 (deterministic) — Append to log

```
## [YYYY-MM-DD] ingest | "{title}" (raw/{type}/{filename})
```

### Step 9 (deterministic) — Compilation nudge

Count uncompiled sources by comparing `raw/_index.md` ingestion dates to
"Last compiled" in master index. If ≥ 5, emit a hint:
`You have N uncompiled sources. Run /wiki:compile to integrate them.`

### Step 10 (deterministic) — Report

Emit (for the user, not stored):

- title, target path, detected type, tags
- skipped fallbacks if any (e.g. "Grok MCP unavailable, used FxTwitter")

## 3. Workflow — `--inbox` batch ingest

### Step A — Scan

`glob inbox/* excluding .processed/ and dotfiles`.

### Step B — Fetch all (parallel)

For each file, run Step 2-3 in parallel. For `.url` / `.webloc` files, parse
the URL first, then run as URL.

### Step C — Classify as a batch (LLM, when no `--wiki` set)

After all titles+summaries+tags are known, run a single classification call
that handles the whole batch:

```
You are routing a batch of new sources to topic wikis.

Topic wikis:
{wikis_json}

Items:
[
  {"index": 1, "title": "...", "summary": "...", "tags": [...]},
  ...
]

Return JSON:
{
  "routes": [
    {"index": 1, "wiki": "ai-basics", "match": "strong"},
    {"index": 2, "wiki": "ai-security", "match": "strong"},
    {"index": 3, "wiki": null, "suggested_new": "cloud-infra", "match": "weak"},
    {"index": 4, "wiki": null, "match": "none"}
  ]
}
```

Present a confirmation table to the user. Proceed with grouped writes (all
items for one wiki together).

### Step D — Process each item

Per item, run Steps 4-8 from single-source flow.

### Step E — Move to `.processed/`

`mv inbox/<file> inbox/.processed/<file>` (or `cp` if `--keep`).

### Step F — Final report

If ≥ 5 items processed, suggest `/wiki:compile`.

## 4. Workflow — `/wiki:ingest-collection`

Collection ingestion treats a bounded upstream corpus as a *source
collection*, never as a finished wiki. It writes one **manifest** source plus
one **child** source per upstream item, all with provenance metadata.

### Step C0 (deterministic) — Adapter detection

| Source signal | Adapter |
|---------------|---------|
| ends in `.xml`, `.xml.bz2`, `.xml.gz` | `mediawiki-dump` |
| contains `github.com/`, `gitlab.com/`, ends in `.git`, or local dir with `.git/` | `git` |
| URL contains `/wiki/`, `/w/`, or has reachable `api.php` | `mediawiki-api` |
| ambiguous | ask the user |

Never recursively crawl HTML.

### Step C1 (deterministic) — Inventory

#### Git

```bash
git clone --depth 1 <url> <tmp>
git -C <tmp> rev-parse HEAD                 # → revision
git -C <tmp> ls-tree -r --format='%(objectname) %(path)' HEAD
```

Filter to text-like files (`.md`, `.mediawiki`, `.wiki`, `.rst`, `.txt`,
`.adoc`). Exclude `.git/`, `.github/`, generated assets, binaries,
images, archives, vendored deps, scripts, test vectors. For BIP-style
repos, prioritize `bip-####.mediawiki` and `bip-####.md`.

#### MediaWiki dump

`bunzip2 -c` (or `gunzip -c`) and parse streaming XML with
`xml.etree.ElementTree.iterparse`. Default to namespace `0`. Skip redirects
and `:`-containing titles.

#### MediaWiki API

Discover `api.php`. Inventory:
`action=query&list=allpages&apnamespace=<ns>&aplimit=max&format=json`,
following `continue` tokens.

### Step C2 (deterministic) — Apply filters

`--include`, `--exclude`, `--limit`, `--namespace`. If the inventory exceeds
500 items and the user did not pass `--limit`, show the count and ask for
confirmation.

### Step C3 (deterministic) — Deduplication

For each candidate, compute the dedup key
`(collection, upstream_id, revision/sha)`. Skip if a raw source already has
the same key. If upstream changed (different revision), write a *new*
immutable raw source — never overwrite.

### Step C4 (deterministic per item; LLM optional) — Fetch content

Git: read blob at HEAD. Dump: read latest revision text. API: batch fetch
with `prop=revisions&rvslots=main&rvprop=ids|timestamp|user|comment|content`.

Optional Step C4-LLM: if the upstream is wikitext or unfamiliar markup, the
LLM may be called once per batch to produce a `summary` field. For BIP
proposals, parse headers (`BIP`, `Layer`, `Title`, `Authors`, `Status`,
`Type`, `Requires`, `License`, `Discussion`) deterministically.

### Step C5 (deterministic) — Write manifest

One file per import, in `raw/repos/`, with:

```yaml
---
title: "Collection: <name>"
source: "<upstream URL or path>"
type: repos
ingested: YYYY-MM-DD
tags: [collection, collection-manifest, <adapter>]
summary: "Manifest for a collection ingest of <name>: N child sources captured from <revision>."
collection: "<slug>"
adapter: git | mediawiki-dump | mediawiki-api
revision: "<commit sha or dump rev>"
canonical_url: "<upstream URL>"
license: "<detected or unknown>"
---

# Collection: <name>

[Inventory table: upstream_id | revision | size | included]
```

Lint exempts collection manifests from coverage and orphan-source warnings.

### Step C6 (deterministic) — Write child sources

One file per upstream item, usually in `raw/articles/`:

```yaml
---
title: "<upstream title>"
source: "<canonical upstream URL>"
type: articles
ingested: YYYY-MM-DD
tags: [collection, <slug>, ...]
summary: "<2-3 sentence factual summary>"
collection: "<slug>"
adapter: <adapter>
upstream_id: "<path or page id>"
upstream_type: git-file | mediawiki-page
revision: "<rev id, sha, or timestamp>"
sha: "<blob sha or hash>"
canonical_url: "<per-item URL>"
content_format: markdown | mediawiki | wikitext | text
license: "<detected or unknown>"
authors: [...]
categories: [...]
outlinks: [...]
fetched: YYYY-MM-DD
---

[Full upstream content; preserve tables, code, metadata; do not summarize away normative requirements]
```

### Step C7 (deterministic) — Rebuild indexes

After the batch, regenerate `raw/articles/_index.md`, `raw/repos/_index.md`,
and `raw/_index.md` from frontmatter. Don't hand-edit hundreds of rows.

### Step C8 (deterministic) — Append log

```
## [YYYY-MM-DD] ingest-collection | <slug> via <adapter>: N new, M skipped, K total candidates
```

### Step C9 (optional) — Compile

If `--compile`, hand off to the Compile capability with a "collection-aware"
flag that prefers cluster articles over one-article-per-page (e.g. for BIPs:
activation, wallet standards, script upgrades, peer services, Taproot/Schnorr,
mining/RPC, BIP process).

## 5. Persistence summary

| File | Written by |
|------|------------|
| `raw/<type>/<date>-<slug>.md` | Step 6 (single) or C6 (collection child) |
| `raw/repos/<date>-<slug>-manifest.md` | C5 |
| `raw/<type>/_index.md`, `raw/_index.md`, `_index.md` | Step 7 / C7 (best-effort) |
| `log.md` | Step 8 / C8 |

## 6. Edge cases

- **Auth wall / paywall**: Step 2a returns short content. Compare length; if
  obviously a login wall, fall through to manual fallback message.
- **Slug collision**: Bump `-2`, `-3`, … until unique. The Derived Index
  Protocol guarantees the index will reconcile on next read.
- **Unicode / spaces in tilde paths**: Hub resolution rules apply — only the
  *leading* `~` is expanded, never tildes inside `com~apple~CloudDocs`.
- **Concurrent ingests**: Both writes happen; both update the index
  best-effort; the next read rebuilds the canonical index.
- **Inbox interrupted mid-batch**: Already-processed files are in
  `.processed/`. Re-running `--inbox` only processes what remains.
- **Collection upstream changed between runs**: New revision → new raw
  source, old one preserved. Dedup key includes revision.
- **Collection too large**: Default soft cap is 500 candidates without
  `--limit`. Always show count and ask before writing more.

## 7. Java reimplementation outline

```java
public class IngestService {
  private final LLMClient llm;            // wraps LLMCall(prompt) → json
  private final WebFetcher web;           // wraps fetchUrl(url, prompt) → json
  private final WebSearcher search;
  private final Filesystem fs;
  private final WikiResolver wikis;

  public IngestResult ingest(IngestRequest req) {
    WikiContext wiki = wikis.resolve(req);

    SourceKind kind = classify(req.source);
    FetchedContent fetched = switch (kind) {
      case URL      -> fetchUrl(req.source);
      case TWITTER  -> fetchTwitter(req.source);
      case GITHUB   -> fetchGithub(req.source);
      case FILE     -> fs.readFile(req.source);
      case TEXT     -> FetchedContent.fromText(req.source);
    };

    Metadata md = extractMetadataLLM(fetched);   // LLMCall → JSON
    md = applyUserOverrides(md, req);

    if (wiki.isHubLevel() && !req.hasWikiOverride()) {
      RoutingChoice route = classifyToTopicLLM(md, wikis.list());
      wiki = wikis.choose(route, /* userPrompt */ true);
    }

    String slug = slugify(md.title);
    Path target = wiki.rawPath(md.type, today(), slug);
    target = bumpUntilUnique(target);
    fs.write(target, renderRaw(md, fetched));

    bestEffort(() -> updateRawIndexes(wiki, target, md));
    appendLog(wiki, "ingest", "\"" + md.title + "\" (" + target.relativeTo(wiki.root) + ")");
    nudgeCompile(wiki);
    return new IngestResult(target, md);
  }

  private Metadata extractMetadataLLM(FetchedContent fc) {
    String prompt = METADATA_PROMPT.replace("{content_markdown}", fc.markdown);
    String json = llm.call(prompt);
    return Metadata.fromJson(json);
  }
}
```

The collection variant follows the same shape but with batched fetch +
batched write + manifest emission.
