package net.llmwiki.core;

/**
 * Stub for the agentic transitions:
 *   dispatch-research-agent
 *   dispatch-thesis-agent
 *   dispatch-support-agent
 *   dispatch-attack-agent
 *   dispatch-primary-source-agent
 *
 * Each one is an inner loop that does its own web-search + web-fetch + LLM
 * calls until it returns a structured JSON result. Until a real agent
 * runtime is wired in, capabilities that need this throw NotImplementedYet
 * with a clear message.
 *
 * Implement this interface later with whatever agent runtime is chosen
 * (a custom loop, Anthropic's agent SDK, an in-process orchestrator, etc).
 */
public interface AgentExecutor {

  /**
   * @param role         agent role (e.g. "research:academic", "thesis:opposing", "audit:attack")
   * @param contextJson  serialized context the agent needs (claim text, scope, etc)
   * @return             agent's JSON reply (already JSON-parseable)
   */
  String dispatch(String role, String contextJson);

  /** Default no-op implementation — fails loudly so tests don't silently drift. */
  AgentExecutor STUB = (role, ctx) -> {
    throw new NotImplementedYet(
        "AgentExecutor not configured. Role='" + role + "'. "
      + "Wire a real implementation into LlmWiki to enable agentic transitions.");
  };

  class NotImplementedYet extends RuntimeException {
    public NotImplementedYet(String msg) { super(msg); }
  }
}
