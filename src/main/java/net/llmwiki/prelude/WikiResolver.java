package net.llmwiki.prelude;

import com.fasterxml.jackson.databind.JsonNode;
import net.llmwiki.core.Json;
import net.llmwiki.fs.WikiFS;
import net.llmwiki.model.WikiContext;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Implements the resolve-wiki transition.
 *
 * First match wins:
 *   1. --local                 -> &lt;cwd&gt;/.wiki/
 *   2. --wiki &lt;name&gt;     -> HUB/wikis.json lookup
 *   3. cwd has .wiki/          -> use it
 *   4. else                    -> HUB
 */
public final class WikiResolver {

  /**
   * @param hub          may be null (no hub configured)
   * @param wikiName     value of --wiki, or null
   * @param local        true if --local was set
   */
  public WikiContext resolve(Path hub, String wikiName, boolean local) {
    Path cwd = Paths.get("").toAbsolutePath();

    if (local) {
      Path root = cwd.resolve(".wiki");
      return new WikiContext(hub, root, WikiContext.Kind.LOCAL, WikiFS.exists(root.resolve("_index.md")));
    }
    if (wikiName != null && !wikiName.isBlank() && hub != null) {
      Path wikisJson = hub.resolve("wikis.json");
      if (WikiFS.exists(wikisJson)) {
        JsonNode reg = Json.parse(WikiFS.read(wikisJson));
        JsonNode entry = reg.path("wikis").path(wikiName);
        if (!entry.isMissingNode()) {
          Path p = Paths.get(entry.path("path").asText());
          return new WikiContext(hub, p, WikiContext.Kind.TOPIC, WikiFS.exists(p.resolve("_index.md")));
        }
      }
    }
    Path localGuess = cwd.resolve(".wiki");
    if (WikiFS.exists(localGuess.resolve("_index.md"))) {
      return new WikiContext(hub, localGuess, WikiContext.Kind.LOCAL, true);
    }
    if (hub != null) {
      return new WikiContext(hub, hub, WikiContext.Kind.HUB, WikiFS.exists(hub.resolve("_index.md")));
    }
    return new WikiContext(null, null, WikiContext.Kind.HUB, false);
  }
}
