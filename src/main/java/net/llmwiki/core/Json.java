package net.llmwiki.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

/** Tiny Jackson facade so no other class needs to know about ObjectMapper directly. */
public final class Json {
  private Json() {}

  public static final ObjectMapper MAPPER = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
      .enable(SerializationFeature.INDENT_OUTPUT);

  public static JsonNode parse(String text) {
    try { return MAPPER.readTree(text); }
    catch (JsonProcessingException e) { throw new RuntimeException("invalid JSON: " + e.getMessage(), e); }
  }

  public static String stringify(Object value) {
    try { return MAPPER.writeValueAsString(value); }
    catch (JsonProcessingException e) { throw new RuntimeException(e); }
  }

  public static <T> T fromJson(String text, Class<T> type) {
    try { return MAPPER.readValue(text, type); }
    catch (JsonProcessingException e) { throw new RuntimeException("invalid JSON: " + e.getMessage(), e); }
  }

  public static ObjectNode obj() { return MAPPER.createObjectNode(); }

  public static ObjectNode obj(Map<String, ?> entries) {
    var o = obj();
    entries.forEach((k, v) -> o.set(k, MAPPER.valueToTree(v)));
    return o;
  }

  /**
   * Best-effort extraction of a JSON object/array from an LLM reply. The model
   * sometimes wraps JSON in ```json fences or adds a paragraph of preamble.
   * We strip code fences and trim to the outermost { ... } / [ ... ] pair.
   */
  public static String extractJsonBlob(String text) {
    if (text == null) return "{}";
    String s = text.trim();
    if (s.startsWith("```")) {
      int firstNl = s.indexOf('\n');
      int closing = s.lastIndexOf("```");
      if (firstNl > 0 && closing > firstNl) s = s.substring(firstNl + 1, closing);
    }
    int objStart = s.indexOf('{');
    int arrStart = s.indexOf('[');
    int start;
    char open, close;
    if (objStart < 0 && arrStart < 0) return s;
    if (objStart < 0) { start = arrStart; open = '['; close = ']'; }
    else if (arrStart < 0) { start = objStart; open = '{'; close = '}'; }
    else if (objStart < arrStart) { start = objStart; open = '{'; close = '}'; }
    else { start = arrStart; open = '['; close = ']'; }

    int depth = 0;
    for (int i = start; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == open) depth++;
      else if (c == close) {
        depth--;
        if (depth == 0) return s.substring(start, i + 1);
      }
    }
    return s.substring(start);
  }
}
