package com.example.lg4j.stage07_memory;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Stage 7 — the graph remembers.
 *
 * <p>Every earlier stage started from nothing each time you called {@code invoke}. Add a
 * <b>checkpointer</b> and the graph saves its state after each step; give a run a
 * <b>{@code threadId}</b> and it picks up where that thread left off.
 *
 * <p>The two halves live in different places, which is easy to trip over:
 *
 * <ul>
 *   <li>{@code CompileConfig.checkpointSaver(...)} — set once, when you compile. It decides
 *       <em>where</em> state is stored.
 *   <li>{@code RunnableConfig.threadId(...)} — set per call. It decides <em>which</em> conversation
 *       you are continuing.
 * </ul>
 *
 * <p>Omit the threadId and you get the default thread, so every caller shares one conversation —
 * which is usually a bug, and a memorable one.
 *
 * <p>{@code MemorySaver} keeps checkpoints in a map in this JVM. Stage 11 swaps it for a
 * {@code SQLiteSaver} so they outlive the process; nothing else about the graph changes.
 */
public class TriageGraph {

    /** Appends the incoming message and answers with a reply that cites the history length. */
    static final NodeAction<TicketState> RESPOND = state -> {
        var priorTurns = state.turns().size();
        var reply = priorTurns == 0
                ? "Thanks for getting in touch - how can I help?"
                : "Thanks for the follow-up. I have %d earlier message(s) on this ticket.".formatted(priorTurns);

        return Map.of(
                "turns", "customer: " + state.message(),
                "reply", reply);
    };

    public static StateGraph<TicketState> build() throws GraphStateException {
        return new StateGraph<>(TicketState.SCHEMA, TicketState::new)
                .addNode("respond", node_async(RESPOND))
                .addEdge(START, "respond")
                .addEdge("respond", END);
    }

    private TriageGraph() {}
}
