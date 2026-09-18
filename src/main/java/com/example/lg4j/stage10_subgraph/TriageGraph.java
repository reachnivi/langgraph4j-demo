package com.example.lg4j.stage10_subgraph;

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
 * Stage 10 — a graph used as a node inside another graph.
 *
 * <pre>
 *   START -> triage -+-(escalate)-> [ escalation subgraph ] -> END
 *                    +-(routine)--> auto_reply ------------> END
 * </pre>
 *
 * <p>{@code addSubgraph("escalation", EscalationGraph.build())} drops an entire graph in where a
 * node would go. The parent wires to it by name and does not care how many nodes are inside.
 *
 * <p>Why bother, when you could inline those nodes? Because the subgraph is independently
 * testable, independently reusable, and keeps the parent readable — the parent expresses the
 * <em>policy</em> ("escalate or not"), the subgraph the <em>procedure</em>. Note in the rendered
 * Mermaid that the subgraph appears as its own cluster.
 */
public class TriageGraph {

    static final NodeAction<TicketState> TRIAGE = state -> {
        var text = (state.subject() + " " + state.body()).toLowerCase();
        var urgent = text.contains("urgent") || text.contains("data loss")
                || text.contains("security") || text.contains("outage");
        return Map.of("log", "triage: " + (urgent ? "needs escalation" : "routine"));
    };

    static final NodeAction<TicketState> AUTO_REPLY = state -> Map.of(
            "outcome", "auto-replied",
            "log", "auto_reply: handled without a human");

    static final EdgeAction<TicketState> ROUTE = state -> {
        var text = (state.subject() + " " + state.body()).toLowerCase();
        return text.contains("urgent") || text.contains("data loss")
                || text.contains("security") || text.contains("outage")
                ? "escalate" : "routine";
    };

    public static StateGraph<TicketState> build() throws GraphStateException {
        return new StateGraph<>(TicketState.SCHEMA, TicketState::new)
                .addNode("triage", node_async(TRIAGE))
                .addNode("auto_reply", node_async(AUTO_REPLY))

                // An entire graph, embedded where a node would normally go.
                .addSubgraph("escalation", EscalationGraph.build())

                .addEdge(START, "triage")
                .addConditionalEdges("triage", edge_async(ROUTE),
                        Map.of("escalate", "escalation", "routine", "auto_reply"))
                .addEdge("escalation", END)
                .addEdge("auto_reply", END);
    }

    private TriageGraph() {}
}
