package net.llmwiki.core.search;

import com.fasterxml.jackson.databind.JsonNode;
import net.llmwiki.core.Json;
import net.llmwiki.core.WebTools;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Brave Search API — free tier 2000 queries/month, 1 qps.
 *
 * Set BRAVE_API_KEY in the environment.
 *
 * Endpoint: GET https://api.search.brave.com/res/v1/web/search?q=...
 * Header:   X-Subscription-Token: <key>
 *
 * Returns: {"web":{"results":[{"url":"...","title":"...","description":"..."}]}}
 */
public final class BraveSearcher implements WebTools.WebSearcher {

  private final String apiKey;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(15))
      .build();

  public BraveSearcher(String apiKey) { this.apiKey = apiKey; }

  @Override
  public List<WebTools.SearchHit> search(String query) {
    String url = "https://api.search.brave.com/res/v1/web/search?count=5&q="
        + URLEncoder.encode(query, StandardCharsets.UTF_8);
    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
        .header("Accept", "application/json")
        .header("X-Subscription-Token", apiKey)
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
    try {
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("Brave HTTP " + resp.statusCode() + ": " + resp.body());
      }
      JsonNode root = Json.parse(resp.body());
      List<WebTools.SearchHit> out = new ArrayList<>();
      JsonNode results = root.path("web").path("results");
      if (results.isArray()) {
        for (JsonNode r : results) {
          out.add(new WebTools.SearchHit(
              r.path("url").asText(""),
              r.path("title").asText(""),
              r.path("description").asText("")));
        }
      }
      return out;
    } catch (Exception e) {
      throw new RuntimeException("Brave search failed: " + e.getMessage(), e);
    }
  }
}
