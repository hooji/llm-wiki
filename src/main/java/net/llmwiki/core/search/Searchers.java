package net.llmwiki.core.search;

import net.llmwiki.core.WebTools;

import java.util.List;

/**
 * Auto-select a WebSearcher based on environment.
 *
 *   TAVILY_API_KEY  -> Tavily       (best for LLM agents, free 1000/mo)
 *   BRAVE_API_KEY   -> Brave        (free 2000/mo)
 *   SEARXNG_URL     -> SearXNG      (override default URL)
 *   else            -> SearXNG at DEFAULT_SEARXNG_URL (the local Docker instance)
 *
 * DuckDuckGo HTML scraping is available via {@link #duckDuckGo()} but is
 * NOT in the auto() chain — DDG now blocks most non-browser User-Agents.
 *
 * Override at construction:
 *   new LlmWiki(llm, agents, Searchers.tavily(myKey), fetcher);
 */
public final class Searchers {
  private Searchers() {}

  /** Hardcoded local SearXNG endpoint. Override with SEARXNG_URL if needed. */
  public static final String DEFAULT_SEARXNG_URL = "http://192.168.0.60:8090";

  public static WebTools.WebSearcher auto() {
    String tav = System.getenv("TAVILY_API_KEY");
    if (tav != null && !tav.isBlank()) return new TavilySearcher(tav);
    String brv = System.getenv("BRAVE_API_KEY");
    if (brv != null && !brv.isBlank()) return new BraveSearcher(brv);
    String sx = System.getenv("SEARXNG_URL");
    String url = (sx != null && !sx.isBlank()) ? sx : DEFAULT_SEARXNG_URL;
    return new SearXngSearcher(url);
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
