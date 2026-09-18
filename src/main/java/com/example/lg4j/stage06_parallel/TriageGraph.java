package com.example.lg4j.stage06_parallel;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.concurrent.CompletableFuture;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Stage 6 — three analyzers run at the same time.
 *
 * <pre>
 *   START -> ingest -+-> sentiment -+-> merge -> END
 *                    +-> priority  -+
 *                    +-> category  -+
 * </pre>
 *
 * <p>Giving one node several outgoing edges creates the branches, and the node they share waits
 * for all of them. But <b>the edges alone do not buy you concurrency</b>, and this is the trap:
 * {@code node_async(...)} merely adapts a synchronous function to the async signature and still
 * runs it on the calling thread. Wire these analyzers with {@code node_async} and the graph is
 * correct but strictly serial — measured here at ~957ms for three 300ms analyzers.
 *
 * <p>Real overlap needs a node that hands back a future which is <em>already running elsewhere</em>
 * ({@code CompletableFuture.supplyAsync(..., POOL)}). With that change the same graph finishes in
 * ~355ms. {@code Main} prints the timing so you can see which of the two you have.
 *
 * <p>The other half of the work is in the <em>state</em>. Three nodes writing the same key at once
 * need {@link TicketState#MERGE_FINDINGS} to combine their results; without a reducer, two of the
 * three would be silently lost.
 */
public class TriageGraph {

    /** Each analyzer pretends to do ~300ms of work, so serial execution would take ~900ms. */
    static final long WORK_MILLIS = 300;

    static final NodeAction<TicketState> INGEST = state -> Map.of(
            "log", "ingest: %s".formatted(state.subject()));

    /**
     * Builds an analyzer that contributes exactly one entry to the shared findings map.
     *
     * <p>This returns an {@link AsyncNodeAction} built with {@code supplyAsync}, NOT
     * {@code node_async(...)}. That distinction is the whole trick of this stage:
     * {@code node_async} only adapts a synchronous function to the async signature — it runs the
     * body on the calling thread, so branches wired "in parallel" still execute one after another.
     * Handing back a future that is already running elsewhere is what actually overlaps them.
     */
    private static AsyncNodeAction<TicketState> analyzer(String key, java.util.function.Function<String, String> analyse) {
        return state -> CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(WORK_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            var text = (state.subject() + " " + state.body()).toLowerCase();
            var verdict = analyse.apply(text);
            return Map.of(
                    "findings", Map.of(key, verdict),
                    "log", "%s: %s (thread %s)".formatted(key, verdict, Thread.currentThread().getName()));
        }, POOL);
    }

    /**
     * A real pool, so the analyzers have somewhere to run concurrently.
     *
     * <p>The threads are daemons on purpose. A plain {@code newFixedThreadPool} creates non-daemon
     * threads, which keep the JVM alive after {@code main} returns — the program appears to hang.
     * Either make them daemons, as here, or shut the pool down when you are done with it.
     */
    static final java.util.concurrent.ExecutorService POOL =
            java.util.concurrent.Executors.newFixedThreadPool(3, runnable -> {
                var thread = new Thread(runnable, "analyzer");
                thread.setDaemon(true);
                return thread;
            });

    static final AsyncNodeAction<TicketState> SENTIMENT = analyzer("sentiment", text ->
            text.contains("angry") || text.contains("unacceptable") || text.contains("furious")
                    ? "negative" : "neutral");

    static final AsyncNodeAction<TicketState> PRIORITY = analyzer("priority", text ->
            text.contains("urgent") || text.contains("asap") ? "high" : "normal");

    static final AsyncNodeAction<TicketState> CATEGORY = analyzer("category", text -> {
        if (text.contains("refund") || text.contains("charge") || text.contains("billed")) {
            return "billing";
        }
        if (text.contains("crash") || text.contains("error") || text.contains("bug")) {
            return "technical";
        }
        return "general";
    });

    /** Runs only after all three branches have finished, and sees all three results. */
    static final NodeAction<TicketState> MERGE = state -> {
        var f = state.findings();
        var summary = "%s / %s / %s".formatted(
                f.getOrDefault("category", "?"),
                f.getOrDefault("priority", "?"),
                f.getOrDefault("sentiment", "?"));
        return Map.of("summary", summary, "log", "merge: " + summary);
    };

    public static StateGraph<TicketState> build() throws GraphStateException {
        return new StateGraph<>(TicketState.SCHEMA, TicketState::new)
                .addNode("ingest", node_async(INGEST))
                .addNode("sentiment", SENTIMENT)
                .addNode("priority", PRIORITY)
                .addNode("category", CATEGORY)
                .addNode("merge", node_async(MERGE))

                .addEdge(START, "ingest")

                // Fan out: three edges from one node == three concurrent branches.
                .addEdge("ingest", "sentiment")
                .addEdge("ingest", "priority")
                .addEdge("ingest", "category")

                // Fan in: merge waits for all three.
                .addEdge("sentiment", "merge")
                .addEdge("priority", "merge")
                .addEdge("category", "merge")
                .addEdge("merge", END);
    }

    private TriageGraph() {}
}
