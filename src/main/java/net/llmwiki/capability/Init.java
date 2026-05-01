package net.llmwiki.capability;

import net.llmwiki.core.ArgParse;
import net.llmwiki.core.Json;
import net.llmwiki.fs.*;
import net.llmwiki.model.WikiContext;
import net.llmwiki.prelude.WikiResolver;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * /wiki init &lt;name&gt; [--local]   — create a hub (if missing) and a topic wiki.
 * /wiki config hub-path &lt;path&gt; — handled separately (see Main).
 */
public final class Init {

  private static final Set<String> VALUED = Set.of("wiki");

  private final Context ctx;
  public Init(Context ctx) { this.ctx = ctx; }

  /** args = "<name> [--local] [<description>]" */
  public String run(String args) {
    ArgParse.Args a = ArgParse.parse(args, VALUED);
    if (a.positionals.isEmpty()) {
      return "init: please supply a topic name. Example: /init my-topic [--local]";
    }
    String topic = a.positionals.get(0);
    boolean local = a.is("local");
    String description = a.positionals.size() > 1
        ? String.join(" ", a.positionals.subList(1, a.positionals.size()))
        : "";

    if (local) {
      Path cwd = Path.of("").toAbsolutePath();
      Path root = cwd.resolve(".wiki");
      buildTopicTree(root, topic, description);
      return "Local wiki created at " + root + "\n";
    }

    if (ctx.hub == null) {
      return "No hub configured. Run: /config hub-path <path> first, or pass --local.";
    }
    if (!WikiFS.exists(ctx.hub.resolve("_index.md"))) {
      buildHubTree(ctx.hub);
    }
    String slug = Slugs.slugify(topic);
    Path root = ctx.hub.resolve("topics").resolve(slug);
    if (WikiFS.exists(root.resolve("_index.md"))) {
      return "Topic wiki already exists: " + root;
    }
    buildTopicTree(root, topic, description);
    registerTopic(ctx.hub, slug, root, description);
    return "Topic wiki created at " + root + "\n";
  }

  // ---- helpers ----

  private void buildHubTree(Path hub) {
    WikiFS.mkdirs(hub.resolve("topics"));
    if (!WikiFS.exists(hub.resolve("wikis.json"))) {
      var registry = Json.obj();
      registry.put("default", hub.toString());
      registry.putObject("wikis");
      registry.putArray("local_wikis");
      WikiFS.writeAtomic(hub.resolve("wikis.json"), Json.stringify(registry));
    }
    if (!WikiFS.exists(hub.resolve("_index.md"))) {
      WikiFS.write(hub.resolve("_index.md"),
          "# Hub Index\n\n> Hub. Lightweight, no content directories.\n\n## Topic Wikis\n\n");
    }
    if (!WikiFS.exists(hub.resolve("log.md"))) {
      WikiFS.write(hub.resolve("log.md"), "# Hub Activity Log\n\n");
      Logs.appendActivity(hub, "init", "Hub initialized");
    }
  }

  private void buildTopicTree(Path root, String topic, String description) {
    String[] dirs = {
        "inbox", "inbox/.processed",
        "raw", "raw/articles", "raw/papers", "raw/repos", "raw/notes", "raw/data",
        "wiki", "wiki/concepts", "wiki/topics", "wiki/references", "wiki/theses",
        "output", "output/projects",
    };
    for (String d : dirs) WikiFS.mkdirs(root.resolve(d));

    // empty per-directory _index.md (will rebuild on next read)
    for (String d : new String[] {
        "raw", "raw/articles", "raw/papers", "raw/repos", "raw/notes", "raw/data",
        "wiki", "wiki/concepts", "wiki/topics", "wiki/references", "wiki/theses",
        "output"}) {
      Indexes.rebuild(root.resolve(d));
    }
    Indexes.writeMasterIndex(root, topic);

    // config.md
    Map<String, Object> fm = new LinkedHashMap<>();
    fm.put("title", topic);
    fm.put("description", description == null ? "" : description);
    fm.put("created", LocalDate.now().toString());
    fm.put("freshness_threshold", "70");
    String configBody = """
        # Wiki Configuration

        ## Scope
        %s

        ## Conventions
        Default conventions apply.
        """.formatted(description == null || description.isBlank() ? "(describe scope here)" : description);
    WikiFS.write(root.resolve("config.md"), Frontmatter.render(fm, configBody));

    WikiFS.write(root.resolve("log.md"), "# Wiki Activity Log\n\n");
    Logs.appendActivity(root, "init", "Wiki initialized");
  }

  private void registerTopic(Path hub, String slug, Path root, String description) {
    Path wikisJson = hub.resolve("wikis.json");
    var reg = WikiFS.exists(wikisJson) ? Json.parse(WikiFS.read(wikisJson)) : Json.obj();
    var wikis = reg.path("wikis");
    if (wikis.isMissingNode() && reg instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
      obj.putObject("wikis");
    }
    var entry = Json.obj();
    entry.put("path", root.toString());
    entry.put("description", description == null ? "" : description);
    if (reg instanceof com.fasterxml.jackson.databind.node.ObjectNode obj) {
      ((com.fasterxml.jackson.databind.node.ObjectNode) obj.path("wikis")).set(slug, entry);
    }
    WikiFS.writeAtomic(wikisJson, Json.stringify(reg));
    Logs.appendActivity(hub, "init", "Registered topic wiki: " + slug);

    // patch the hub _index.md
    Indexes.writeMasterIndex(hub, "Hub Index");
  }

  /** Resolve and verify a wiki context, falling back to creating one if requested. */
  public WikiContext resolveOrCreate(String wikiName, boolean local, String newTopic, String description) {
    var ctx2 = new WikiResolver().resolve(this.ctx.hub, wikiName, local);
    if (ctx2.exists()) return ctx2;
    if (newTopic != null && !newTopic.isBlank()) {
      run((local ? "--local " : "") + newTopic + (description == null ? "" : " " + description));
      return new WikiResolver().resolve(this.ctx.hub, wikiName == null ? Slugs.slugify(newTopic) : wikiName, local);
    }
    return ctx2;
  }
}
