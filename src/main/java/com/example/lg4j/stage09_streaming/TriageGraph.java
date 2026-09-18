package com.example.lg4j.stage09_streaming;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Stage 9 — watching the graph run, instead of waiting for the answer.
 *
 * <p>The graph itself is deliberately boring: {@code classify -> draft -> finalise}. What changes
 * is how {@code Main} consumes it. {@code invoke(...)} hides everything until the run is over;
 * {@code stream(...)} hands you a {@code NodeOutput} as each node finishes, carrying the node's
 * name and the state as it stood at that moment.
 *
 * <p>That is what you want behind a progress bar or an SSE endpoint — and it is the single most
 * useful debugging tool in this whole repo, because you can see exactly which node changed which
 * field, in order, without adding a single print statement to a node.
 */
public class TriageGraph {

    /** Each node pauses a little so the streamed output is visibly incremental. */
    static final long STEP_MILLIS = 250;

    private static NodeAction<TicketState> step(String name, java.util.function.Function<TicketState, Map<String, Object>> body) {
        return state -> {
            Thread.sleep(STEP_MILLIS);
            return body.apply(state);
        };
    }

    public static StateGraph<TicketState> build() throws GraphStateException {
        return new StateGraph<>(TicketState.SCHEMA, TicketState::new)
                .addNode("classify", node_async(step("classify", state -> {
                    var category = state.subject().toLowerCase().contains("crash") ? "technical" : "general";
                    return Map.of("category", category, "log", "classified as " + category);
                })))
                .addNode("draft", node_async(step("draft", state -> {
                    var draft = "Thanks for reporting \"%s\" - routed to %s."
                            .formatted(state.subject(), state.category());
                    return Map.of("draft", draft, "log", "drafted a reply");
                })))
                .addNode("finalise", node_async(step("finalise", state ->
                        Map.of("log", "ready to send"))))
                .addEdge(START, "classify")
                .addEdge("classify", "draft")
                .addEdge("draft", "finalise")
                .addEdge("finalise", END);
    }

    private TriageGraph() {}
}
