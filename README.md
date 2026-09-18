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
mvn test                                                          # 18 tests, fully offline
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
| 1 | `stage01_hello` | `AgentState`, channels, nodes, edges, `invoke` — a straight line, no LLM |
| 2 | `stage02_routing` | `addConditionalEdges` — the graph forks and re-converges |
| 3 | `stage03_llm` | An LLM behind two nodes; pluggable provider; defensive output handling |
| 4 | `stage04_reflection` | **Cycles**: draft → critique → draft, with a counter *and* `recursionLimit` |
| 5 | `stage05_tools` | **Tool calling**: the ReAct loop, and why the model never executes anything |
| 6 | `stage06_parallel` | **Parallelism** and a custom `Reducer` — plus why `node_async` isn't async |
| 7 | `stage07_memory` | **Checkpointing**: `MemorySaver` + `threadId`, and thread isolation |
| 8 | `stage08_hitl` | **Human-in-the-loop**: `interruptBefore`, `updateState`, `GraphInput.resume()` |
| 9 | `stage09_streaming` | **Streaming**: `stream()` emits a `NodeOutput` per node as it finishes |
| 10 | `stage10_subgraph` | **Composition**: `addSubgraph` embeds a whole graph as one node |
| 11 | `stage11_persistence` | **Durability**: `SQLiteSaver`, surviving restarts, and time-travel replay |
| 12 | `stage12_observability` | **Langfuse tracing** over OpenTelemetry (see below) |

### What each stage is trying to teach

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

**Stage 4 — an edge may point backwards.** That single fact is all a cycle is. What it costs
you is a termination argument: there are two brakes here, a revision counter you control and
`recursionLimit` as the framework's backstop. If you are hitting the second one, the first is
wrong.

**Stage 5 — the model never executes anything.** It returns a *request* to call a tool. The
`tools` node decides whether to honour it — note it refuses tool names it doesn't recognise.
That gap is where authorisation, validation and audit logging belong.

**Stage 6 — `node_async` is not async.** It adapts a synchronous function to the async
signature and still runs it on the calling thread, so branches wired "in parallel" run one
after another: measured at ~957ms for three 300ms analyzers. Returning a future that is
already running on a pool brings the same graph to ~355ms. `Main` prints the timing so you
can tell which one you have. Meanwhile the custom `Reducer` is what stops three concurrent
writes to one key from silently discarding two of them.

**Stage 7 — where vs. which.** `checkpointSaver` is set once at compile time and decides
*where* state lives; `threadId` is set per call and decides *which* conversation continues.
Forget the threadId and every caller shares one conversation.

**Stage 8 — resume, don't re-invoke.** After an `interruptBefore` pause you continue with
`invoke(GraphInput.resume(), config)`. Passing the original input again would start over.
Interrupts require a checkpointer — the pause has to be stored somewhere.

**Stage 9 — watch it run.** `stream()` hands you each node's output as it finishes. It is the
best debugging tool in this repo: you see which node changed which field, in order, without
adding a single print statement to a node.

**Stage 10 — policy vs. procedure.** The parent decides *whether* to escalate; the subgraph
knows *how*. The subgraph keeps its own START/END and stays independently runnable.

**Stage 11 — swapping the saver changes nothing else.** `SQLiteSaver` for `MemorySaver` and
the graph is untouched. One asymmetry to know: `SQLiteSaver` requires an explicit
`stateSerializer` because it writes bytes, where `MemorySaver` just holds references — omit it
and it fails at runtime.

**A serialization gotcha from Stage 5 on.** langgraph4j checkpoints state with Java object
streams, but langchain4j's `ChatMessage` types are not `Serializable`. Putting messages in
state fails with `NotSerializableException` until you use `LC4jStateSerializer` from the
`langgraph4j-langchain4j` module.

## Observability with Langfuse (Stage 12)

Langfuse has **no Java SDK — and does not need one.** It ingests plain OpenTelemetry on
`${LANGFUSE_HOST}/api/public/otel` and maps the OTel GenAI conventions onto its own model:
a root span becomes a trace, child spans nest under it, and any span carrying `gen_ai.*`
attributes is rendered as a *generation* with its model, prompt, completion and token usage.

So the integration is three small pieces, all in `obs/`:

| File | Job |
|---|---|
| `obs/Langfuse.java` | Builds an OTLP/HTTP exporter pointed at Langfuse with basic-auth from your key pair, and registers it globally |
| `obs/Tracing.java` | One span per graph node — reuses langgraph4j's own `OTELWrapCallTraceHook`, no custom hook needed |
| `obs/LangfuseChatModelListener.java` | One span per LLM call, emitting the `gen_ai.*` attributes Langfuse looks for |
| `obs/TracedChatModel.java` | Makes those listener callbacks fire for *any* model, including the offline stub |

Stage 12's graph is Stage 3's graph. Diff them: the node bodies are identical, and the only
tracing-related line is a single `Tracing.instrument(...)` call. Observability that requires
editing every node is observability you will stop maintaining.

### Running it

```bash
podman compose up -d            # Langfuse at http://localhost:3000
                                # (or: podman-compose up -d)
# sign up locally, create a project, copy its two keys
export LANGFUSE_PUBLIC_KEY=pk-lf-...
export LANGFUSE_SECRET_KEY=sk-lf-...
mvn -q compile exec:java -Dexec.mainClass=com.example.lg4j.stage12_observability.Main
```

With no keys set, Stage 12 still runs and simply reports that tracing is off.

| Variable | Default |
|---|---|
| `LANGFUSE_HOST` | `http://localhost:3000` (the bundled compose stack; use `https://cloud.langfuse.com` for hosted) |
| `LANGFUSE_PUBLIC_KEY` | — required to export |
| `LANGFUSE_SECRET_KEY` | — required to export |

### Two traps worth knowing

**Flush before you exit.** Spans go through a `BatchSpanProcessor`, which exports on a ~5s
timer. A short program that finishes and exits loses its final batch — traces simply never
appear. `Langfuse.install()` returns the SDK so you can `close()` it, which flushes
synchronously. Do not "fix" this with a `Thread.sleep`; that is what failed here first.

**Use the `gen_ai.*` names exactly.** Invent your own attribute names and the span still
arrives, it just shows up as an anonymous span with no model and no token usage — a
confusing way to discover the convention.

### What was verified

The exporter wiring was checked against a local mock collector: the request goes to
`POST /api/public/otel/v1/traces` with `Authorization: Basic <base64(pk:sk)>`,
`x-langfuse-ingestion-version: 4` and `Content-Type: application/x-protobuf`, carrying node
spans (`classify`, `draft_reply`), `service.name=langgraph4j-demo`, and generation spans with
`gen_ai.system`, `gen_ai.request.model`, `gen_ai.response.model`, `gen_ai.prompt` and
`gen_ai.completion`.

`compose.yaml` is schema-valid, but **the stack itself was never booted** — this build machine
has neither a container daemon nor podman installed. Treat it as a solid starting point rather
than something proven end-to-end; if an image bump breaks it, compare against the compose file
in the `langfuse/langfuse` repository.

It is written podman-first, and the podman-specific choices are deliberate:

| Choice | Why |
|---|---|
| Fully-qualified images (`docker.io/library/postgres:16-alpine`) | Podman enforces short-name resolution and **errors out in a non-interactive shell** instead of assuming Docker Hub. This is the change most likely to have bitten you. |
| No `ulimits: nofile` on ClickHouse | Rootless podman usually cannot raise nofile above the user's hard limit, and the container refuses to start rather than degrading. Raise it on the host if you hit FD warnings. |
| Named volumes only, no bind mounts | Avoids SELinux `:z`/`:Z` labelling, the other classic rootless-podman failure. |
| All ports > 1024 | Rootless podman cannot bind privileged ports. |

Two things to watch, since I could not run them: `depends_on: condition: service_healthy`
needs a reasonably recent `podman-compose` (older ones ignore it, so the web container may
start before Postgres is ready — just restart it), and the MinIO healthcheck shells out to
`curl`, with `mc ready local` noted inline as the fallback if your image lacks it.

## Layout

```
compose.yaml                 # self-hosted Langfuse for stage 12 (podman or docker)
src/main/java/com/example/lg4j/
  model/                     # provider switch + offline stub model
  obs/                       # Langfuse / OpenTelemetry wiring (stage 12)
  stage01_hello/             # each stage: TicketState, TriageGraph, Main
  stage02_routing/
  ...
  stage12_observability/
src/test/java/com/example/lg4j/
```

Each stage package is deliberately self-contained — its own `TicketState`, graph and `main` —
so a stage reads end-to-end without chasing shared base classes, and so the diff between two
stages shows exactly what changed. `model/` is the one shared package, since the provider
switch is orthogonal to the graph concepts.

## Version notes

Pinned in `pom.xml`, and worth knowing because the ecosystem moves fast:

- **langgraph4j `1.8.27`** — pinned deliberately. `1.9.0` went stable on 2026-09-18; its
  `StateGraph`/`CompiledGraph` signatures are identical for everything used here, so moving
  up is a one-line change in `pom.xml` when you want it.
- **langchain4j `1.19.0`** — the version langgraph4j 1.8.27 is built against.
- The chat interface is `dev.langchain4j.model.chat.ChatModel`. The older
  `ChatLanguageModel` no longer exists.
- `ChatModel`'s methods are all `default`, so it is **not** a functional interface — you
  cannot write one as a lambda. Implement `doChat(ChatRequest)` (see `StubChatModel`).
- langchain4j 1.19.0's `AnthropicChatModelName` enum predates the current model line-up, so
  `ModelFactory` passes the model name as a **String**.
