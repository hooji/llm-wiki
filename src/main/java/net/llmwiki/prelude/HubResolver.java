package net.llmwiki.prelude;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.llmwiki.core.Json;
import net.llmwiki.fs.WikiFS;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implements the resolve-hub transition.
 *
 * 1. Read ~/.config/llm-wiki/config.json. If resolved_path -> use it.
 * 2. If only hub_path -> expand the leading ~ only, set hub, write resolved_path back.
 * 3. Else try $HOME/wiki/_index.md as fallback.
 * 4. Else null (caller asks the user).
 */
public final class HubResolver {

  public Path resolve() {
    String home = System.getProperty("user.home");
    Path configPath = Paths.get(home, ".config", "llm-wiki", "config.json");

    if (WikiFS.exists(configPath)) {
      JsonNode cfg = Json.parse(WikiFS.read(configPath));
      String resolved = cfg.path("resolved_path").asText("");
      if (!resolved.isBlank()) return Paths.get(resolved);

      String hub = cfg.path("hub_path").asText("");
      if (!hub.isBlank()) {
        Path expanded = expandLeadingTilde(hub, home);
        // write resolved_path back so this never runs again
        if (cfg instanceof ObjectNode obj) {
          obj.put("resolved_path", expanded.toString());
          WikiFS.writeAtomic(configPath, Json.stringify(obj));
        }
        return expanded;
      }
    }

    Path fallback = Paths.get(home, "wiki");
    if (WikiFS.exists(fallback.resolve("_index.md"))) return fallback;
    return null;
  }

  /** Set the configured hub path (creates the config file). */
  public Path setHubPath(String userPath) {
    String home = System.getProperty("user.home");
    Path expanded = expandLeadingTilde(userPath, home);
    Path configPath = Paths.get(home, ".config", "llm-wiki", "config.json");
    ObjectNode obj = Json.obj();
    obj.put("hub_path", userPath);
    obj.put("resolved_path", expanded.toString());
    WikiFS.writeAtomic(configPath, Json.stringify(obj));
    return expanded;
  }

  /** Replace ONLY the leading ~ with $HOME. Tildes elsewhere (e.g. com~apple~CloudDocs) untouched. */
  static Path expandLeadingTilde(String s, String home) {
    if (s.startsWith("~/")) return Paths.get(home, s.substring(2));
    if (s.equals("~")) return Paths.get(home);
    return Paths.get(s);
  }
}
