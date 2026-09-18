package com.example.lg4j.stage08_hitl;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Stage 8 — the graph stops and waits for a human.
 *
 * <pre>
 *   START -> draft -> [PAUSE] -> send -> END
 * </pre>
 *
 * <p>{@code CompileConfig.interruptBefore("send")} tells the graph to checkpoint and return just
 * before that node runs. The first {@code invoke} comes back with {@code sent=false}; nothing has
 * been sent, and the run is parked in the checkpointer waiting for someone to look at it.
 *
 * <p>A human can then do three things, all of them ordinary API calls:
 *
 * <ol>
 *   <li><b>Inspect</b> — {@code getState(config)}, whose {@code next()} names the node that is
 *       about to run.
 *   <li><b>Edit</b> — {@code updateState(config, Map.of("draft", "..."))} rewrites the checkpoint,
 *       so the pending node will see the corrected value.
 *   <li><b>Resume</b> — {@code invoke(GraphInput.resume(), config)} continues from the pause. Note
 *       it is {@code resume()}, not the original input: passing input again would start over.
 * </ol>
 *
 * <p>Interrupts only work with a checkpointer configured — the pause has to be stored somewhere.
 * That is why this stage builds on Stage 7 rather than standing alone.
 */
public class TriageGraph {

    /** The node whose output a human reviews. */
    public static final String APPROVAL_GATE = "send";

    static final NodeAction<TicketState> DRAFT = state -> {
        var draft = "Hi - thanks for reporting \"%s\". We're on it.".formatted(state.subject());
        return Map.of("draft", draft, "log", "draft: prepared " + draft.length() + " chars");
    };

    /** The irreversible step. In a real system this is the one that actually emails the customer. */
    static final NodeAction<TicketState> SEND = state -> Map.of(
            "sent", true,
            "log", "send: delivered -> " + state.draft());

    public static StateGraph<TicketState> build() throws GraphStateException {
        return new StateGraph<>(TicketState.SCHEMA, TicketState::new)
                .addNode("draft", node_async(DRAFT))
                .addNode(APPROVAL_GATE, node_async(SEND))
                .addEdge(START, "draft")
                .addEdge("draft", APPROVAL_GATE)
                .addEdge(APPROVAL_GATE, END);
    }

    private TriageGraph() {}
}
