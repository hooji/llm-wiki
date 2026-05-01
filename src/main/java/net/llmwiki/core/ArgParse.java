package net.llmwiki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bare-minimum command-line argument parser.
 *
 * Handles:
 *   - long flags:       --foo            -> flags["foo"]=true
 *   - long with value:  --foo bar        -> flags["foo"]="bar"
 *   - long with =:      --foo=bar        -> flags["foo"]="bar"
 *   - quoted positional values are pre-tokenized using a small shell-like splitter
 *
 * Anything that's not a flag becomes a positional. The original raw string is
 * preserved on the result for capabilities that want to do their own parsing
 * (e.g. ingest with a multi-word --title).
 */
public final class ArgParse {

  public static final class Args {
    public final String raw;
    public final List<String> positionals;
    public final Map<String, String> flags;
    public Args(String raw, List<String> positionals, Map<String, String> flags) {
      this.raw = raw;
      this.positionals = Collections.unmodifiableList(positionals);
      this.flags = Collections.unmodifiableMap(flags);
    }
    public boolean has(String name) { return flags.containsKey(name); }
    public String get(String name) { return flags.get(name); }
    public String getOrDefault(String name, String def) { String v = flags.get(name); return v == null ? def : v; }
    public boolean is(String name) { return "true".equalsIgnoreCase(flags.get(name)); }
    public String first() { return positionals.isEmpty() ? null : positionals.get(0); }
    public String join() { return String.join(" ", positionals); }
  }

  public static Args parse(String input, java.util.Set<String> valued) {
    String raw = input == null ? "" : input.trim();
    List<String> tokens = tokenize(raw);
    List<String> positionals = new ArrayList<>();
    Map<String, String> flags = new LinkedHashMap<>();

    for (int i = 0; i < tokens.size(); i++) {
      String t = tokens.get(i);
      if (t.startsWith("--")) {
        String body = t.substring(2);
        int eq = body.indexOf('=');
        String name; String value;
        if (eq >= 0) {
          name = body.substring(0, eq);
          value = body.substring(eq + 1);
        } else {
          name = body;
          if (valued != null && valued.contains(name) && i + 1 < tokens.size()) {
            value = tokens.get(++i);
          } else {
            value = "true";
          }
        }
        flags.put(name, value);
      } else {
        positionals.add(t);
      }
    }
    return new Args(raw, positionals, flags);
  }

  /** Shell-like tokenizer: respects "..." and '...' as a single token. */
  public static List<String> tokenize(String s) {
    List<String> out = new ArrayList<>();
    if (s == null || s.isEmpty()) return out;
    StringBuilder cur = new StringBuilder();
    char quote = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (quote != 0) {
        if (c == quote) { quote = 0; }
        else cur.append(c);
      } else if (c == '"' || c == '\'') {
        quote = c;
      } else if (Character.isWhitespace(c)) {
        if (cur.length() > 0) { out.add(cur.toString()); cur.setLength(0); }
      } else {
        cur.append(c);
      }
    }
    if (cur.length() > 0) out.add(cur.toString());
    return out;
  }

  private ArgParse() {}
}
