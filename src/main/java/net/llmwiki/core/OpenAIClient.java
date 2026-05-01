package net.llmwiki.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Bare-bones OpenAI-compatible chat completions caller.
 *
 * Reads:
 *   OPENAI_API_KEY    (required)
 *   OPENAI_BASE_URL   (default: https://api.openai.com/v1)
 *   OPENAI_MODEL      (default: gpt-4o-mini)
 *
 * No streaming, no retries, no token counting, no error wrapping. The whole
 * point of this class is "almost like curl in Java" — replace it later with
 * the official Anthropic/OpenAI SDK or an LLM-broker library.
 */
public final class OpenAIClient {
  private final String apiKey;
  private final String baseUrl;
  private final String model;
  private final HttpClient http;

  public OpenAIClient() {
    this.apiKey = System.getenv("OPENAI_API_KEY");
    String envBase = System.getenv("OPENAI_BASE_URL");
    this.baseUrl = (envBase == null || envBase.isBlank()) ? "https://api.openai.com/v1" : envBase;
    String envModel = System.getenv("OPENAI_MODEL");
    this.model = (envModel == null || envModel.isBlank()) ? "gpt-4o-mini" : envModel;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
  }

  public String model() { return model; }

  /** Single-turn user message → assistant text. JSON-mode is requested via response_format. */
  public String chat(String userPrompt) {
    return chat(userPrompt, true);
  }

  public String chat(String userPrompt, boolean jsonMode) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY is not set");
    }
    ObjectNode body = Json.obj();
    body.put("model", model);
    if (jsonMode) {
      ObjectNode rf = body.putObject("response_format");
      rf.put("type", "json_object");
    }
    ArrayNode messages = body.putArray("messages");
    ObjectNode userMsg = messages.addObject();
    userMsg.put("role", "user");
    userMsg.put("content", userPrompt);

    HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .timeout(Duration.ofMinutes(5))
        .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body)))
        .build();

    try {
      HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() / 100 != 2) {
        throw new RuntimeException("LLM HTTP " + resp.statusCode() + ": " + resp.body());
      }
      JsonNode root = Json.parse(resp.body());
      JsonNode choice = root.path("choices").path(0).path("message").path("content");
      return choice.isMissingNode() ? "" : choice.asText();
    } catch (Exception e) {
      throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
    }
  }
}
