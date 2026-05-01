package net.llmwiki.core;

/**
 * The single LLM seam.
 *
 * Every transition in the architecture documented as type:LLM funnels through
 * LLM.call(prompt) → JSON-string. Replacing OpenAIClient with a different
 * provider only requires editing this one class.
 */
public final class LLM {

  private final OpenAIClient client = new OpenAIClient();

  /** Send a single-turn prompt; expect a JSON object/array reply. */
  public String call(String prompt) {
    String raw = client.chat(prompt, true);
    return Json.extractJsonBlob(raw);
  }

  /** Send a free-form prompt; return the raw assistant text (used for content extraction prompts). */
  public String callRaw(String prompt) {
    return client.chat(prompt, false);
  }

  public String model() { return client.model(); }
}
