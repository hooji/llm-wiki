package net.llmwiki.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Minimal web tools.
 *
 * - WebFetcher.fetch(url) is a real HTTP GET that returns raw response body.
 *   No HTML→markdown conversion; downstream callers pass the body to the LLM
 *   for extraction. This is intentionally simple — replace with jsoup / a
 *   readability extractor / mercury-like pipeline later.
 *
 * - WebSearcher is a stub. Hook in Tavily / Brave / Exa / Google CSE later.
 */
public final class WebTools {

  /** A single web hit. */
  public record SearchHit(String url, String title, String snippet) {}

  public interface WebSearcher {
    List<SearchHit> search(String query);
    WebSearcher STUB = q -> {
      throw new AgentExecutor.NotImplementedYet(
          "WebSearcher not configured. Query='" + q + "'. "
        + "Wire a real implementation (Tavily/Brave/Exa/etc) into LlmWiki.");
    };
  }

  public interface WebFetcher {
    String fetch(String url);
  }

  /** Plain HTTP fetcher — follows redirects, returns raw response body. */
  public static final WebFetcher HTTP_FETCHER = new HttpFetcher();

  private static final class HttpFetcher implements WebFetcher {
    private final HttpClient client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    @Override
    public String fetch(String url) {
      try {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", "llm-wiki/0.1 (+https://llm-wiki.net)")
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
          throw new RuntimeException("HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
      } catch (Exception e) {
        throw new RuntimeException("fetch failed: " + url + " — " + e.getMessage(), e);
      }
    }
  }

  private WebTools() {}
}
