package com.example.lg4j.stage10_subgraph;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * The escalation procedure, as a graph in its own right.
 *
 * <pre>
 *   START -> assess_severity -> assign_owner -> END
 * </pre>
 *
 * <p>Note it has its own {@code START} and {@code END}. It is a complete, independently testable
 * graph — you can compile and run this on its own, which is exactly why extracting it is worth
 * doing. The parent then treats the whole thing as one node.
 */
public class EscalationGraph {

    static final NodeAction<TicketState> ASSESS_SEVERITY = state -> {
        var text = (state.subject() + " " + state.body()).toLowerCase();
        var severity = text.contains("data loss") || text.contains("security") ? "sev1" : "sev2";
        return Map.of("severity", severity, "log", "  [sub] assessed severity=" + severity);
    };

    static final NodeAction<TicketState> ASSIGN_OWNER = state -> {
        var owner = "sev1".equals(state.severity()) ? "incident-commander" : "support-lead";
        return Map.of(
                "owner", owner,
                "outcome", "escalated to " + owner,
                "log", "  [sub] assigned to " + owner);
    };

    public static StateGraph<TicketState> build() throws GraphStateException {
        return new StateGraph<>(TicketState.SCHEMA, TicketState::new)
                .addNode("assess_severity", node_async(ASSESS_SEVERITY))
                .addNode("assign_owner", node_async(ASSIGN_OWNER))
                .addEdge(START, "assess_severity")
                .addEdge("assess_severity", "assign_owner")
                .addEdge("assign_owner", END);
    }

    private EscalationGraph() {}
}
