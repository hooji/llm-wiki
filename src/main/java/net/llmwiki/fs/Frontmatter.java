package net.llmwiki.fs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiny YAML-frontmatter reader/writer.
 *
 * Handles only the shapes llm-wiki actually uses:
 *   key: scalar              -> String
 *   key: "scalar with spaces" -> String (quotes stripped)
 *   key: [a, b, c]            -> List<String>
 *   key:
 *     - a
 *     - b                     -> List<String>
 *
 * Anything more exotic should be passed verbatim or upgraded to SnakeYAML
 * later. We deliberately keep zero deps.
 *
 * Frontmatter is the YAML between the first two "---" lines.
 */
public final class Frontmatter {

  public static final class Parsed {
    public final Map<String, Object> fields;
    public final String body;
    public Parsed(Map<String, Object> fields, String body) {
      this.fields = fields;
      this.body = body;
    }
  }

  public static Parsed parse(String fileContent) {
    if (fileContent == null) return new Parsed(new LinkedHashMap<>(), "");
    String content = fileContent;
    if (!content.startsWith("---")) {
      return new Parsed(new LinkedHashMap<>(), content);
    }
    String[] lines = content.split("\\R", -1);
    if (lines.length < 2) return new Parsed(new LinkedHashMap<>(), content);
    int end = -1;
    for (int i = 1; i < lines.length; i++) {
      if (lines[i].equals("---")) { end = i; break; }
    }
    if (end < 0) return new Parsed(new LinkedHashMap<>(), content);

    Map<String, Object> out = new LinkedHashMap<>();
    String pendingKey = null;
    List<String> pendingList = null;
    for (int i = 1; i < end; i++) {
      String line = lines[i];
      if (line.isBlank()) continue;

      // Continuation list ("  - foo")
      String trimmed = line.replaceAll("\\s+$", "");
      if (pendingKey != null && trimmed.startsWith("- ")) {
        pendingList.add(stripQuotes(trimmed.substring(2).trim()));
        continue;
      } else if (pendingKey != null && trimmed.startsWith("-") && trimmed.length() > 1 && trimmed.charAt(1) == ' ') {
        pendingList.add(stripQuotes(trimmed.substring(1).trim()));
        continue;
      } else if (pendingKey != null) {
        // closed list
        out.put(pendingKey, pendingList);
        pendingKey = null;
        pendingList = null;
      }

      int colon = line.indexOf(':');
      if (colon < 0) continue;
      String key = line.substring(0, colon).trim();
      String value = line.substring(colon + 1).trim();

      if (value.isEmpty()) {
        // start of block list
        pendingKey = key;
        pendingList = new ArrayList<>();
      } else if (value.startsWith("[") && value.endsWith("]")) {
        out.put(key, parseInlineList(value.substring(1, value.length() - 1)));
      } else {
        out.put(key, stripQuotes(value));
      }
    }
    if (pendingKey != null) out.put(pendingKey, pendingList);

    StringBuilder body = new StringBuilder();
    for (int i = end + 1; i < lines.length; i++) {
      if (i > end + 1) body.append('\n');
      body.append(lines[i]);
    }
    return new Parsed(out, body.toString());
  }

  public static String stringify(Map<String, Object> fields) {
    StringBuilder out = new StringBuilder("---\n");
    for (var e : fields.entrySet()) {
      String key = e.getKey();
      Object val = e.getValue();
      if (val instanceof List<?> list) {
        if (list.isEmpty()) {
          out.append(key).append(": []\n");
        } else {
          out.append(key).append(": [");
          for (int i = 0; i < list.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(escapeIfNeeded(String.valueOf(list.get(i))));
          }
          out.append("]\n");
        }
      } else if (val == null) {
        out.append(key).append(":\n");
      } else {
        String s = String.valueOf(val);
        out.append(key).append(": ").append(escapeIfNeeded(s)).append("\n");
      }
    }
    out.append("---\n");
    return out.toString();
  }

  /** Convenience: produce file content = frontmatter + body. */
  public static String render(Map<String, Object> fields, String body) {
    return stringify(fields) + "\n" + (body == null ? "" : body);
  }

  // ---- helpers ----

  private static List<String> parseInlineList(String inner) {
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQ = false; char q = 0;
    for (int i = 0; i < inner.length(); i++) {
      char c = inner.charAt(i);
      if (inQ) {
        if (c == q) inQ = false;
        else cur.append(c);
      } else if (c == '"' || c == '\'') {
        inQ = true; q = c;
      } else if (c == ',') {
        out.add(cur.toString().trim()); cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    if (!cur.isEmpty()) out.add(cur.toString().trim());
    out.removeIf(String::isEmpty);
    return out;
  }

  private static String stripQuotes(String s) {
    if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"")
                         || s.startsWith("'") && s.endsWith("'"))) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }

  private static String escapeIfNeeded(String s) {
    if (s.contains(":") || s.contains("#") || s.contains("\"") || s.startsWith(" ") || s.endsWith(" ")) {
      return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
    return s;
  }

  @SuppressWarnings("unchecked")
  public static List<String> listField(Map<String, Object> fm, String key) {
    Object v = fm.get(key);
    if (v == null) return List.of();
    if (v instanceof List<?> l) {
      List<String> out = new ArrayList<>();
      for (Object o : l) out.add(String.valueOf(o));
      return out;
    }
    return List.of(String.valueOf(v));
  }

  public static String stringField(Map<String, Object> fm, String key, String def) {
    Object v = fm.get(key);
    return v == null ? def : String.valueOf(v);
  }

  private Frontmatter() {}
}
