package net.llmwiki.core.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.llmwiki.core.Json;
import net.llmwiki.core.WebTools;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Tavily search — free tier 1000 searches/month, designed for LLM agents.
 *
 * Set TAVILY_API_KEY in the environment.
 *
 * Endpoint: POST https://api.tavily.com/search
 * Body:     {"api_key": "...", "query": "...", "max_results": 5, "search_depth": "basic"}
 *
 * Returns: {"results":[{"url":"...","title":"...","content":"..."}, ...]}
 */
public final class TavilySearcher implements WebTools.WebSearcher {

  private final String apiKey;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(15))
      .build();

  public TavilySearcher(String apiKey) { this.apiKey = apiKey; }

  @Override
  public List<WebTools.SearchHit> search(String query) {
    ObjectNode body = Json.obj();
    body.put("api_key", apiKey);
    body.put("query", query);
    body.put("max_results", 5);
    body.put("search_depth", "basic");

    HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.tavily.com/search"))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(30))
        .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
        .build();

    try {
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("Tavily HTTP " + resp.statusCode() + ": " + resp.body());
      }
      JsonNode root = Json.parse(resp.body());
      List<WebTools.SearchHit> out = new ArrayList<>();
      if (root.path("results").isArray()) {
        for (JsonNode r : root.path("results")) {
          out.add(new WebTools.SearchHit(
              r.path("url").asText(""),
              r.path("title").asText(""),
              r.path("content").asText("")));
        }
      }
      return out;
    } catch (Exception e) {
      throw new RuntimeException("Tavily search failed: " + e.getMessage(), e);
    }
  }
}
