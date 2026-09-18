package com.example.lg4j.stage02_routing;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Stage 2 — the graph stops being a straight line.
 *
 * <pre>
 *   START -> ingest -> classify -+-> billing   -+-> summarize -> END
 *                                +-> technical -+
 *                                +-> general   -+
 * </pre>
 *
 * <p>The new idea is {@link StateGraph#addConditionalEdges}. It takes three things:
 *
 * <ol>
 *   <li>the node we are leaving ({@code classify}),
 *   <li>an {@link EdgeAction} that looks at the state and returns a <em>route name</em> — a plain
 *       string that has nothing to do with node names,
 *   <li>a mapping from those route names to the nodes to actually jump to.
 * </ol>
 *
 * <p>That indirection is the whole lesson: the routing function decides <em>what kind</em> of thing
 * happened, and the mapping decides <em>who handles it</em>. You can rewire the graph without
 * touching the decision logic.
 */
public class TriageGraph {

    static final NodeAction<TicketState> INGEST = state -> Map.of(
            "subject", state.subject().trim(),
            "body", state.body().trim(),
            "log", "ingest: received %d chars".formatted(state.body().trim().length()));

    /** Sets {@code category}; it does not decide where to go next. The edge does that. */
    static final NodeAction<TicketState> CLASSIFY = state -> {
        var text = (state.subject() + " " + state.body()).toLowerCase();

        var category = "general";
        if (text.contains("refund") || text.contains("invoice") || text.contains("charge")
                || text.contains("billed")) {
            category = "billing";
        } else if (text.contains("error") || text.contains("crash") || text.contains("bug")
                || text.contains("broken")) {
            category = "technical";
        }

        var priority = text.contains("urgent") || text.contains("asap") ? "high" : "normal";

        return Map.of(
                "category", category,
                "priority", priority,
                "log", "classify: category=%s priority=%s".formatted(category, priority));
    };

    /**
     * The routing function. It returns a route name, not a node name — see {@link #ROUTES} for
     * where those names are turned into nodes.
     */
    static final EdgeAction<TicketState> ROUTE_BY_CATEGORY = state -> switch (state.category()) {
        case "billing" -> "to_billing";
        case "technical" -> "to_technical";
        default -> "to_general";
    };

    /** route name -> node name. */
    static final Map<String, String> ROUTES = Map.of(
            "to_billing", "billing",
            "to_technical", "technical",
            "to_general", "general");

    private static NodeAction<TicketState> handler(String name, String note) {
        return state -> Map.of(
                "handledBy", name,
                "log", "%s: %s".formatted(name, note));
    }

    static final NodeAction<TicketState> SUMMARIZE = state -> {
        var summary = "[%s/%s] %s (handled by %s)"
                .formatted(state.category(), state.priority(), state.subject(), state.handledBy());
        return Map.of(
                "summary", summary,
                "log", "summarize: " + summary);
    };

    public static StateGraph<TicketState> build() throws GraphStateException {
        return new StateGraph<>(TicketState.SCHEMA, TicketState::new)
                .addNode("ingest", node_async(INGEST))
                .addNode("classify", node_async(CLASSIFY))
                .addNode("billing", node_async(handler("billing", "checking the invoice history")))
                .addNode("technical", node_async(handler("technical", "reproducing the fault")))
                .addNode("general", node_async(handler("general", "sending a generic acknowledgement")))
                .addNode("summarize", node_async(SUMMARIZE))

                .addEdge(START, "ingest")
                .addEdge("ingest", "classify")

                // The fork.
                .addConditionalEdges("classify", edge_async(ROUTE_BY_CATEGORY), ROUTES)

                // All three branches converge again.
                .addEdge("billing", "summarize")
                .addEdge("technical", "summarize")
                .addEdge("general", "summarize")
                .addEdge("summarize", END);
    }

    private TriageGraph() {}
}
