package com.example.lg4j.stage11_persistence;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Stage 11 — checkpoints that outlive the process.
 *
 * <p>The graph is the same one-node conversation as Stage 7. The only change is the checkpointer:
 * {@code SQLiteSaver} instead of {@code MemorySaver}. Nothing in the graph knows or cares —
 * which is the point of the {@code BaseCheckpointSaver} abstraction.
 *
 * <p>Persistence buys two distinct things:
 *
 * <ul>
 *   <li><b>Durability</b> — restart the JVM and the conversation is still there. {@code Main} proves
 *       this by building a second, completely fresh graph against the same database file.
 *   <li><b>Time travel</b> — {@code getStateHistory(config)} returns every checkpoint, and each one
 *       carries a {@code RunnableConfig} you can pass back to {@code invoke} to resume from that
 *       exact point. That makes "what if this step had gone differently?" a normal operation
 *       rather than an archaeology exercise.
 * </ul>
 */
public class TriageGraph {

    static final NodeAction<TicketState> RESPOND = state -> Map.of(
            "turns", "customer: " + state.message(),
            "step", state.step() + 1);

    public static StateGraph<TicketState> build() throws GraphStateException {
        return new StateGraph<>(TicketState.SCHEMA, TicketState::new)
                .addNode("respond", node_async(RESPOND))
                .addEdge(START, "respond")
                .addEdge("respond", END);
    }

    private TriageGraph() {}
}
