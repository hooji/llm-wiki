package net.llmwiki;

import net.llmwiki.capability.*;
import net.llmwiki.core.AgentExecutor;
import net.llmwiki.core.LLM;
import net.llmwiki.core.WebTools;
import net.llmwiki.core.search.Searchers;
import net.llmwiki.prelude.HubResolver;

import java.nio.file.Path;

/**
 * Single facade. Every major high-level function is a method that takes a
 * String argument (the user's command line for that capability).
 *
 * Replace the agentic / web-search stubs with real implementations once
 * an agent runtime + a search provider are wired in.
 */
public final class LlmWiki {

  private final HubResolver hubResolver = new HubResolver();
  private final Path hub;

  private final Init initCapability;
  private final Ingest ingestCapability;
  private final IngestCollection ingestCollectionCapability;
  private final Compile compileCapability;
  private final Query queryCapability;
  private final Research researchCapability;
  private final Thesis thesisCapability;
  private final Librarian librarianCapability;
  private final Audit auditCapability;
  private final Lessons lessonsCapability;
  private final Plan planCapability;
  private final Output outputCapability;

  public LlmWiki() {
    this(new LLM(), AgentExecutor.STUB, Searchers.auto(), WebTools.HTTP_FETCHER);
  }

  public LlmWiki(LLM llm, AgentExecutor agents,
                 WebTools.WebSearcher search, WebTools.WebFetcher fetcher) {
    this.hub = hubResolver.resolve();
    var ctx = new Context(llm, agents, search, fetcher, hub);
    this.initCapability = new Init(ctx);
    this.ingestCapability = new Ingest(ctx);
    this.ingestCollectionCapability = new IngestCollection(ctx);
    this.compileCapability = new Compile(ctx);
    this.queryCapability = new Query(ctx);
    this.researchCapability = new Research(ctx);
    this.thesisCapability = new Thesis(ctx);
    this.librarianCapability = new Librarian(ctx);
    this.auditCapability = new Audit(ctx);
    this.lessonsCapability = new Lessons(ctx);
    this.planCapability = new Plan(ctx);
    this.outputCapability = new Output(ctx);
  }

  public Path hub() { return hub; }

  /** Set the configured hub path (writes ~/.config/llm-wiki/config.json). */
  public String configHubPath(String path) {
    if (path == null || path.isBlank()) return "config: provide a path";
    Path p = hubResolver.setHubPath(path.trim());
    return "Hub configured at " + p;
  }

  public String init(String args) { return initCapability.run(args); }
  public String ingest(String args) { return ingestCapability.run(args); }
  public String ingestCollection(String args) { return ingestCollectionCapability.run(args); }
  public String compile(String args) { return compileCapability.run(args); }
  public String query(String args) { return queryCapability.run(args); }
  public String research(String args) { return researchCapability.run(args); }
  public String thesis(String args) { return thesisCapability.run(args); }
  public String librarian(String args) { return librarianCapability.run(args); }
  public String audit(String args) { return auditCapability.run(args); }
  public String lessons(String args) { return lessonsCapability.run(args); }
  public String plan(String args) { return planCapability.run(args); }
  public String output(String args) { return outputCapability.run(args); }
}
