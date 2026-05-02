package net.llmwiki.core.search;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DuckDuckGo HTML scrape — no API key required.
 *
 * Fetches `https://html.duckduckgo.com/html/?q=<query>` and pulls out the
 * non-paid result links. DDG redirects through `//duckduckgo.com/l/?uddg=<encoded>`,
 * so we extract & URL-decode the `uddg` parameter to recover the real URL.
 *
 * This is the no-friction default. If DDG changes their HTML it stops working —
 * switch to Tavily or Brave by setting their API key env vars.
 */
public final class DuckDuckGoSearcher implements WebTools.WebSearcher {

  private static final Pattern RESULT = Pattern.compile(
      "class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", Pattern.DOTALL);
  private static final Pattern SNIPPET = Pattern.compile(
      "class=\"result__snippet\"[^>]*>(.*?)</a>", Pattern.DOTALL);
  private static final Pattern UDDG = Pattern.compile("[?&]uddg=([^&]+)");
  private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");

  private final HttpClient http = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .connectTimeout(Duration.ofSeconds(15))
      .build();

  @Override
  public List<WebTools.SearchHit> search(String query) {
    String url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    String body;
    try {
      HttpRequest req = HttpRequest.newBuilder(URI.create(url))
          .header("User-Agent", "Mozilla/5.0 (compatible; llm-wiki/0.1)")
          .timeout(Duration.ofSeconds(30))
          .GET()
          .build();
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("DDG HTTP " + resp.statusCode());
      }
      body = resp.body();
    } catch (Exception e) {
      throw new RuntimeException("DDG search failed: " + e.getMessage(), e);
    }

    List<WebTools.SearchHit> hits = new ArrayList<>();
    Matcher m = RESULT.matcher(body);
    Matcher sm = SNIPPET.matcher(body);
    while (m.find()) {
      String rawHref = m.group(1);
      String title = stripTags(m.group(2)).trim();
      String snippet = sm.find() ? stripTags(sm.group(1)).trim() : "";
      String real = unwrapDuckDuckGoLink(rawHref);
      if (real == null || real.isBlank()) continue;
      hits.add(new WebTools.SearchHit(real, title, snippet));
    }
    return hits;
  }

  private static String unwrapDuckDuckGoLink(String href) {
    Matcher u = UDDG.matcher(href);
    if (u.find()) {
      String enc = u.group(1);
      return java.net.URLDecoder.decode(enc, StandardCharsets.UTF_8);
    }
    if (href.startsWith("//")) return "https:" + href;
    return href;
  }

  private static String stripTags(String s) {
    return HTML_TAG.matcher(s).replaceAll("").replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
  }
}
