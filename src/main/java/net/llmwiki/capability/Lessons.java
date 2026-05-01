package net.llmwiki.capability;

import com.fasterxml.jackson.databind.JsonNode;
import net.llmwiki.core.*;
import net.llmwiki.fs.*;
import net.llmwiki.model.WikiContext;
import net.llmwiki.prelude.WikiResolver;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/**
 * /wiki:ll [--dry-run] [--rules]
 *
 * Note: there is no "current session transcript" available to a long-running
 * Java process. Treat the args (or a path argument) as the transcript text.
 * This means in REPL usage the operator pastes the transcript on the command
 * line / loads it from a file. This matches the spirit of the architecture
 * (the LLM extracts lessons from text) without trying to reach into the
 * harness's chat history (which doesn't exist here).
 */
public final class Lessons {

  private static final Set<String> VALUED = Set.of("wiki", "from", "topic");

  private final Context ctx;
  public Lessons(Context ctx) { this.ctx = ctx; }

  public String run(String args) {
    ArgParse.Args a = ArgParse.parse(args, VALUED);
    WikiContext wiki = new WikiResolver().resolve(ctx.hub, a.get("wiki"), a.is("local"));
    if (!wiki.exists()) return "ll: no wiki found.";

    String transcript;
    if (a.has("from")) {
      Path tp = Path.of(a.get("from"));
      if (!WikiFS.exists(tp)) return "ll: --from path does not exist: " + tp;
      transcript = WikiFS.read(tp);
    } else if (!a.positionals.isEmpty()) {
      transcript = a.join();
    } else {
      return "ll: supply transcript text or --from <path-to-transcript-file>";
    }
    String topicHint = a.getOrDefault("topic", "");

    String scanPrompt = Templates.expand(Prompts.SCAN_SESSION_TRANSCRIPT,
        "transcript", truncate(transcript, 60_000), "topic_hint", topicHint);
    JsonNode events = Json.parse(ctx.llm.call(scanPrompt));

    String genPrompt = Templates.expand(Prompts.GENERALIZE_LESSONS,
        "events_json", Json.stringify(events.path("events")));
    JsonNode lessons = Json.parse(ctx.llm.call(genPrompt));

    if (a.is("dry-run")) {
      return "Dry-run lessons:\n\n" + Json.stringify(lessons);
    }

    // Write raw note
    String summary = events.path("session_summary").asText("session lessons");
    String slug = Slugs.slugify(topicHint.isBlank() ? summary : topicHint);
    Path target = wiki.raw().resolve("notes").resolve("ll-" + LocalDate.now() + "-" + slug + ".md");

    Map<String, Object> fm = new LinkedHashMap<>();
    fm.put("title", "Lessons Learned: " + summary);
    fm.put("type", "notes");
    fm.put("source", "session");
    fm.put("ingested", LocalDate.now().toString());
    fm.put("tags", Frontmatter.listField(Map.of("tags", arrayItems(events.path("topic_keywords"))), "tags"));
    fm.put("summary", summary);

    StringBuilder body = new StringBuilder();
    body.append("# Lessons Learned: ").append(summary).append("\n\n");
    if (lessons.path("lessons").isArray()) {
      int i = 1;
      for (JsonNode l : lessons.path("lessons")) {
        body.append("## Lesson ").append(i++).append(": ").append(l.path("title").asText("")).append("\n\n");
        body.append("**Category**: ").append(l.path("category").asText("")).append("\n");
        body.append("**Context**: ").append(l.path("context").asText("")).append("\n");
        body.append("**Symptom**: ").append(l.path("symptom").asText("")).append("\n");
        body.append("**Root cause**: ").append(l.path("root_cause").asText("")).append("\n");
        body.append("**Fix**: ").append(l.path("fix").asText("")).append("\n");
        body.append("**Rule**: ").append(l.path("rule").asText("")).append("\n\n");
      }
    }
    WikiFS.write(target, Frontmatter.render(fm, body.toString()));
    Logs.appendActivity(wiki.root(), "ll",
        "\"" + summary + "\" -> " + wiki.root().relativize(target));
    return "Lessons captured: " + target;
  }

  private static List<String> arrayItems(JsonNode arr) {
    List<String> out = new ArrayList<>();
    if (arr != null && arr.isArray()) arr.forEach(n -> out.add(n.asText()));
    return out;
  }

  private static String truncate(String s, int max) {
    return (s == null || s.length() <= max) ? s : s.substring(0, max) + "\n...[truncated]";
  }
}
