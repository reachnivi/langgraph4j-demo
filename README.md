# LangGraph4j demo — support-ticket triage

A progressive, hands-on introduction to [LangGraph4j](https://github.com/bsorrentino/langgraph4j).

One app — a support-ticket triage assistant — rebuilt stage by stage, each stage adding
exactly one new LangGraph4j concept. Every stage keeps its own `main()`, so you can run any
earlier stage again and diff two stages to see precisely what a concept changed.

**Stage 1 needs no LLM, and stages 2–3 default to an offline stub model**, so the whole repo
builds, tests and runs with no API key and no network.

## Requirements

- Java 17+ (LangGraph4j targets 17)
- Maven 3.9+

## Quick start

```bash
mvn test                                                          # 12 tests, fully offline
mvn -q exec:java -Dexec.mainClass=com.example.lg4j.stage01_hello.Main
mvn -q exec:java -Dexec.mainClass=com.example.lg4j.stage02_routing.Main
mvn -q exec:java -Dexec.mainClass=com.example.lg4j.stage03_llm.Main
```

Each stage prints its graph as Mermaid — paste that into any Mermaid renderer to see the
shape of what you just ran.

## Choosing an LLM provider

Stage 3 onward calls a model through `ModelFactory`. It defaults to an offline stub. Every
setting is read as a **system property first, then an environment variable**, so both
`-DLG4J_PROVIDER=...` and `export LG4J_PROVIDER=...` work.

| Setting | Default | Notes |
|---|---|---|
| `LG4J_PROVIDER` | `stub` | `stub`, `anthropic`, or `ollama` |
| `ANTHROPIC_API_KEY` | — | required when provider is `anthropic` |
| `LG4J_ANTHROPIC_MODEL` | `claude-sonnet-5` | any current model id |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | |
| `LG4J_OLLAMA_MODEL` | `llama3.1` | must be pulled locally first |

```bash
# Anthropic
LG4J_PROVIDER=anthropic ANTHROPIC_API_KEY=sk-... \
  mvn -q exec:java -Dexec.mainClass=com.example.lg4j.stage03_llm.Main

# Ollama (run `ollama serve` and `ollama pull llama3.1` first)
LG4J_PROVIDER=ollama \
  mvn -q exec:java -Dexec.mainClass=com.example.lg4j.stage03_llm.Main
```

## Stages

| Stage | Package | What it adds |
|---|---|---|
| 1 ✅ | `stage01_hello` | `AgentState`, channels, nodes, edges, `invoke` — a straight line, no LLM |
| 2 ✅ | `stage02_routing` | `addConditionalEdges` — the graph forks and re-converges |
| 3 ✅ | `stage03_llm` | An LLM behind two nodes; pluggable provider; defensive output handling |
| 4 | `stage04_reflection` | Cycles: draft → critique → revise, with `recursionLimit` |
| 5 | `stage05_tools` | Tool calling, the agent↔tools loop, `MessagesState` |
| 6 | `stage06_parallel` | Parallel nodes, `Channels.appender`, custom `Reducer` |
| 7 | `stage07_memory` | `MemorySaver`, `threadId`, multi-turn memory |
| 8 | `stage08_hitl` | Human-in-the-loop: `interruptsBefore`, `updateState`, `GraphInput.resume()` |
| 9 | `stage09_streaming` | `stream()` over `NodeOutput`, watching nodes fire |
| 10 | `stage10_subgraph` | Subgraphs and `Command` handoff |
| 11 | `stage11_persistence` | `SQLiteSaver`, `getStateHistory`, time-travel replay |

Stages 4–11 are the roadmap; they aren't built yet.

### What each built stage is trying to teach

**Stage 1 — state is a map, nodes are functions.** A node receives the state and returns a
*partial* update — only the keys it wants to change. `TicketState.SCHEMA` decides how each key
merges: unlisted keys are overwritten by the last writer, while `log` uses `Channels.appender`
and accumulates. Run it and look at the log: one line per node, not just the last.

**Stage 2 — routing is decoupled from the nodes.** `addConditionalEdges` takes a function
returning a *route name* (`to_billing`) and a separate map from route names to node names.
The classifier says what kind of thing happened; the map decides who handles it. You can
rewire the graph without touching the decision logic.

**Stage 3 — an LLM is just something a node calls.** Diff `stage03_llm/TriageGraph.java`
against `stage02_routing/TriageGraph.java`: the wiring is identical. Only two node bodies
changed. Note `sanitize()` — a real model replies "Billing." or "I think this is billing",
so its answer is normalised and falls back to `general` rather than throwing. Never let raw
model output reach your routing logic.

## Layout

```
src/main/java/com/example/lg4j/
  model/ModelFactory.java    # provider switch: stub | anthropic | ollama
  model/StubChatModel.java   # deterministic offline ChatModel
  stage01_hello/             # TicketState, TriageGraph, Main
  stage02_routing/
  stage03_llm/
src/test/java/com/example/lg4j/
```

Each stage package is deliberately self-contained — its own `TicketState`, graph and `main` —
so a stage reads end-to-end without chasing shared base classes, and so the diff between two
stages shows exactly what changed. `model/` is the one shared package, since the provider
switch is orthogonal to the graph concepts.

## Version notes

Pinned in `pom.xml`, and worth knowing because the ecosystem moves fast:

- **langgraph4j `1.8.27`** — the latest *stable* release. `1.9.0` is beta-only.
- **langchain4j `1.19.0`** — the version langgraph4j 1.8.27 is built against.
- The chat interface is `dev.langchain4j.model.chat.ChatModel`. The older
  `ChatLanguageModel` no longer exists.
- `ChatModel`'s methods are all `default`, so it is **not** a functional interface — you
  cannot write one as a lambda. Implement `doChat(ChatRequest)` (see `StubChatModel`).
- langchain4j 1.19.0's `AnthropicChatModelName` enum predates the current model line-up, so
  `ModelFactory` passes the model name as a **String**.
