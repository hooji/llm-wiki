# gather-output-sources

**Type**: Tool
**Used by**: `output` Step 1

## Inputs
- wiki root
- flags: `--retardmax`, `--sources <paths>`, `--topic <topic>`, none

## Prompt template
None.

## Procedure
```
if --retardmax:
    sources = readAllWikiArticles()                  # every article in wiki/
elif --sources <paths>:
    sources = [readArticle(p) for p in --sources]
elif --topic <topic>:
    matched = grep --topic across wiki/ titles, tags, summaries
    sources = readArticles(matched)
else:
    masterIndex = readMasterIndex()
    sources = readArticles(top_articles_per_category)
```

Stale-check indexes before reading. Follow Derived Index Protocol if counts disagree.

## Output
List of read articles with full body content.

## Notes
`--retardmax` deliberately skips the relevance filter. The mode lowers the bar; doesn't raise the floor.
