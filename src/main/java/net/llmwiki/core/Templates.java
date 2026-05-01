package net.llmwiki.core;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny templating: {name} placeholders -> values from a map.
 *
 * Same syntax used in the architecture doc prompts, e.g.
 *   "Hello {name}, the time is {time}".
 *
 * - Unknown placeholders are left in place (rendered as "{name}") so a missing
 *   value is visible at runtime, not silently swallowed.
 * - Values are stringified via String.valueOf().
 * - If a value contains "{xxx}" sequences, they are NOT recursively expanded.
 */
public final class Templates {
  private Templates() {}

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)}");

  public static String expand(String template, Map<String, Object> variables) {
    if (template == null) return "";
    Map<String, Object> vars = variables == null ? Map.of() : variables;
    Matcher m = PLACEHOLDER.matcher(template);
    StringBuilder out = new StringBuilder();
    while (m.find()) {
      String key = m.group(1);
      Object val = vars.get(key);
      String replacement = (val == null) ? m.group(0) : String.valueOf(val);
      m.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    m.appendTail(out);
    return out.toString();
  }

  /** Convenience for one-off use: Templates.expand("hi {x}", "x", "world"). */
  public static String expand(String template, Object... kv) {
    if (kv.length % 2 != 0) {
      throw new IllegalArgumentException("expand kv must be even-length");
    }
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      map.put(String.valueOf(kv[i]), kv[i + 1]);
    }
    return expand(template, map);
  }
}
