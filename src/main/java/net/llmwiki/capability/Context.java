package net.llmwiki.capability;

import net.llmwiki.core.AgentExecutor;
import net.llmwiki.core.LLM;
import net.llmwiki.core.WebTools;

import java.nio.file.Path;

/** Shared dependencies passed to every capability. */
public final class Context {
  public final LLM llm;
  public final AgentExecutor agents;
  public final WebTools.WebSearcher search;
  public final WebTools.WebFetcher fetcher;
  public final Path hub;

  public Context(LLM llm, AgentExecutor agents, WebTools.WebSearcher search,
                 WebTools.WebFetcher fetcher, Path hub) {
    this.llm = llm;
    this.agents = agents;
    this.search = search;
    this.fetcher = fetcher;
    this.hub = hub;
  }
}
