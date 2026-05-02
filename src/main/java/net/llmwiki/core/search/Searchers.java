package net.llmwiki.core.search;

import net.llmwiki.core.WebTools;

import java.util.List;

/**
 * Auto-select a WebSearcher based on environment, preferring providers most
 * likely to work without friction. As of 2026 the truly-free no-key options
 * (DuckDuckGo HTML scrape, public SearXNG instances) are widely blocked, so
 * the practical recommendations are:
 *
 *   TAVILY_API_KEY  -> Tavily       (best for LLM agents, free 1000/mo)
 *   BRAVE_API_KEY   -> Brave        (free 2000/mo)
 *   SEARXNG_URL     -> SearXNG      (free if you self-host: docker run searxng/searxng)
 *   else            -> DuckDuckGo HTML  (no key — often blocked, kept as last resort)
 *
 * Override at construction:
 *   new LlmWiki(llm, agents, Searchers.tavily(myKey), fetcher);
 */
public final class Searchers {
  private Searchers() {}

  public static WebTools.WebSearcher auto() {
    String tav = System.getenv("TAVILY_API_KEY");
    if (tav != null && !tav.isBlank()) return new TavilySearcher(tav);
    String brv = System.getenv("BRAVE_API_KEY");
    if (brv != null && !brv.isBlank()) return new BraveSearcher(brv);
    String sx = System.getenv("SEARXNG_URL");
    if (sx != null && !sx.isBlank()) return new SearXngSearcher(sx);
    return new DuckDuckGoSearcher();
  }

  public static WebTools.WebSearcher duckDuckGo()         { return new DuckDuckGoSearcher(); }
  public static WebTools.WebSearcher tavily(String key)   { return new TavilySearcher(key); }
  public static WebTools.WebSearcher brave(String key)    { return new BraveSearcher(key); }
  public static WebTools.WebSearcher searxng(String base) { return new SearXngSearcher(base); }

  /**
   * Try a list of searchers in order; return the first non-empty result.
   * Useful if you want to fall back across providers without crashing.
   */
  public static WebTools.WebSearcher chain(WebTools.WebSearcher... searchers) {
    return query -> {
      RuntimeException last = null;
      for (var s : searchers) {
        try {
          var hits = s.search(query);
          if (!hits.isEmpty()) return hits;
        } catch (RuntimeException e) {
          last = e;
        }
      }
      if (last != null) throw last;
      return List.of();
    };
  }
}
