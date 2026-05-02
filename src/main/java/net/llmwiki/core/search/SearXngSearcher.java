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
 * SearXNG (self-hosted or public) search.
 *
 * Set SEARXNG_URL to the instance root, e.g.
 *   SEARXNG_URL=https://my-searx.local
 *
 * Endpoint:
 *   GET {SEARXNG_URL}/search?q=...&format=json&categories=general
 *
 * Most public instances disable `format=json` to discourage scraping; this
 * works best with a self-hosted instance. Setup is one Docker container:
 *   docker run -d -p 8080:8080 searxng/searxng
 * Then SEARXNG_URL=http://localhost:8080.
 */
public final class SearXngSearcher implements WebTools.WebSearcher {

  private final String baseUrl;
  private final HttpClient http = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .connectTimeout(Duration.ofSeconds(15))
      .build();

  public SearXngSearcher(String baseUrl) {
    this.baseUrl = baseUrl.replaceAll("/$", "");
  }

  @Override
  public List<WebTools.SearchHit> search(String query) {
    String url = baseUrl + "/search?format=json&categories=general&q="
        + URLEncoder.encode(query, StandardCharsets.UTF_8);
    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
        .header("Accept", "application/json")
        .header("User-Agent", "llm-wiki/0.1")
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
    try {
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("SearXNG HTTP " + resp.statusCode() + " (some instances disable format=json)");
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
      throw new RuntimeException("SearXNG search failed: " + e.getMessage(), e);
    }
  }
}
