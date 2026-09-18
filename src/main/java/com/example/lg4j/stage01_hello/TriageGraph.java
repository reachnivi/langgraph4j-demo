package com.example.lg4j.stage01_hello;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Stage 1 — the smallest useful graph: three nodes in a straight line.
 *
 * <pre>
 *   START -> ingest -> classify -> summarize -> END
 * </pre>
 *
 * No LLM is involved. The point of this stage is only to show the mechanics: a node is a function
 * from state to "the keys I want to change", and an edge says who runs next.
 */
public class TriageGraph {

    /**
     * A node returns a <em>partial</em> state update, never the whole state. Keys you omit are left
     * alone; keys you include are merged according to {@link TicketState#SCHEMA}.
     */
    static final NodeAction<TicketState> INGEST = state -> Map.of(
            "subject", state.subject().trim(),
            "body", state.body().trim(),
            "log", "ingest: received %d chars".formatted(state.body().trim().length()));

    /** Deliberately dumb keyword matching — Stage 3 replaces this exact node with an LLM call. */
    static final NodeAction<TicketState> CLASSIFY = state -> {
        var text = (state.subject() + " " + state.body()).toLowerCase();

        var category = "general";
        if (text.contains("refund") || text.contains("invoice") || text.contains("charge")) {
            category = "billing";
        } else if (text.contains("error") || text.contains("crash") || text.contains("bug")) {
            category = "technical";
        }

        var priority = text.contains("urgent") || text.contains("asap") ? "high" : "normal";

        return Map.of(
                "category", category,
                "priority", priority,
                "log", "classify: category=%s priority=%s".formatted(category, priority));
    };

    static final NodeAction<TicketState> SUMMARIZE = state -> {
        var summary = "[%s/%s] %s".formatted(state.category(), state.priority(), state.subject());
        return Map.of(
                "summary", summary,
                "log", "summarize: " + summary);
    };

    /** Builds the graph. Call {@code .compile()} on the result to get a runnable graph. */
    public static StateGraph<TicketState> build() throws GraphStateException {
        return new StateGraph<>(TicketState.SCHEMA, TicketState::new)
                .addNode("ingest", node_async(INGEST))
                .addNode("classify", node_async(CLASSIFY))
                .addNode("summarize", node_async(SUMMARIZE))
                .addEdge(START, "ingest")
                .addEdge("ingest", "classify")
                .addEdge("classify", "summarize")
                .addEdge("summarize", END);
    }

    private TriageGraph() {}
}
