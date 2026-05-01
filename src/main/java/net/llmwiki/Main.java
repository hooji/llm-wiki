package net.llmwiki;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Text-based REPL for llm-wiki.
 *
 * Type a command and arguments. Quote multi-word strings with double quotes:
 *   ingest "https://example.com/foo"
 *   query --quick "what is X"
 *   research "intermittent fasting" --new-topic --min-time 1h
 *
 * Built-in commands: help, hub, exit / quit.
 */
public final class Main {

  public static void main(String[] argv) throws Exception {
    LlmWiki[] wikiHolder = { new LlmWiki() };

    // Map: command name -> (wiki, args) -> output
    Map<String, BiFunction<LlmWiki, String, String>> commands = Map.ofEntries(
        Map.entry("init",              (w, a) -> w.init(a)),
        Map.entry("ingest",            (w, a) -> w.ingest(a)),
        Map.entry("ingest-collection", (w, a) -> w.ingestCollection(a)),
        Map.entry("compile",           (w, a) -> w.compile(a)),
        Map.entry("query",             (w, a) -> w.query(a)),
        Map.entry("research",          (w, a) -> w.research(a)),
        Map.entry("thesis",            (w, a) -> w.thesis(a)),
        Map.entry("librarian",         (w, a) -> w.librarian(a)),
        Map.entry("audit",             (w, a) -> w.audit(a)),
        Map.entry("ll",                (w, a) -> w.lessons(a)),
        Map.entry("lessons",           (w, a) -> w.lessons(a)),
        Map.entry("plan",              (w, a) -> w.plan(a)),
        Map.entry("output",            (w, a) -> w.output(a))
    );

    // One-shot mode: java ... Main <command> <args...>
    if (argv.length > 0) {
      String cmd = argv[0];
      String rest = argv.length == 1 ? "" : String.join(" ", java.util.Arrays.copyOfRange(argv, 1, argv.length));
      System.out.println(dispatch(wikiHolder, commands, cmd, rest));
      return;
    }

    // REPL
    System.out.println(banner(wikiHolder[0]));
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
    while (true) {
      System.out.print("\nllm-wiki> ");
      System.out.flush();
      String line = br.readLine();
      if (line == null) break;
      String trimmed = line.trim();
      if (trimmed.isEmpty()) continue;
      if (trimmed.equals("exit") || trimmed.equals("quit")) break;

      int sp = trimmed.indexOf(' ');
      String cmd = sp < 0 ? trimmed : trimmed.substring(0, sp);
      String rest = sp < 0 ? "" : trimmed.substring(sp + 1).trim();

      try {
        System.out.println(dispatch(wikiHolder, commands, cmd, rest));
      } catch (Exception e) {
        System.out.println("error: " + e.getMessage());
      }
    }
    System.out.println("bye.");
  }

  // --- dispatch ---

  private static String dispatch(LlmWiki[] wikiHolder,
                                 Map<String, BiFunction<LlmWiki, String, String>> commands,
                                 String cmd, String rest) {
    LlmWiki wiki = wikiHolder[0];
    return switch (cmd) {
      case "help", "?", "/?" -> help();
      case "hub" -> hubInfo(wiki);
      case "config" -> {
        String result = configCmd(wiki, rest);
        // Hub may have changed: rebuild facade so subsequent commands pick up the new hub.
        wikiHolder[0] = new LlmWiki();
        yield result;
      }
      default -> {
        var fn = commands.get(cmd);
        if (fn == null) yield "unknown command: " + cmd + " (type 'help')";
        yield fn.apply(wiki, rest);
      }
    };
  }

  private static String configCmd(LlmWiki wiki, String rest) {
    String[] parts = rest.split("\\s+", 2);
    if (parts.length < 2 || !"hub-path".equals(parts[0])) {
      return "config: usage: config hub-path <path>";
    }
    return wiki.configHubPath(parts[1]);
  }

  private static String banner(LlmWiki wiki) {
    return """
        llm-wiki — Java implementation
        Hub: %s
        Type 'help' for commands. 'exit' to quit.
        """.formatted(wiki.hub() == null ? "(not configured — run: config hub-path <path>)" : wiki.hub());
  }

  private static String hubInfo(LlmWiki wiki) {
    return wiki.hub() == null
        ? "No hub configured. Run: config hub-path <path>"
        : "Hub: " + wiki.hub();
  }

  private static String help() {
    return """
        Commands:
          init <name> [--local]                 Create a topic wiki
          config hub-path <path>                Set the configured hub path
          hub                                   Show current hub
          ingest <url|file|"text">              Ingest one source
          ingest --inbox                        Process inbox/
          ingest-collection <repo|dump>         Bulk ingest a Git repo or MediaWiki dump
          compile [--full]                      Compile raw -> wiki articles
          query <question> [--quick|--deep|--list|--resume]
          research <topic|question> [--new-topic] [--min-time 1h] [--mode thesis "claim"]
          thesis "<claim>"                      Thesis-mode shortcut
          librarian scan|report                 Staleness + quality scan
          audit scan|report [--quick] [--artifact] [--project]
          ll [--from <transcript>] [--dry-run]  Extract lessons-learned
          plan <goal> [--format roadmap|rfc|adr|spec] [--answers <json>]
          output <type> [--topic ...] [--retardmax]   types: summary,report,study-guide,slides,timeline,glossary,comparison
          help                                  Show this help
          exit                                  Quit

        All wiki commands accept --wiki <name> and --local for wiki targeting.
        """;
  }
}
