package com.example.lg4j.stage04_reflection;

import com.example.lg4j.model.StubChatModel;
import dev.langchain4j.model.chat.ChatModel;
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
 * Stage 4 — the graph stops being acyclic.
 *
 * <pre>
 *   START -> draft -> critique -+-(needs work)-> draft   (back edge!)
 *                               +-(good enough)-> END
 * </pre>
 *
 * <p>This is the "reflection" pattern: the model writes something, then judges its own work, and
 * only stops when it is satisfied. Everything new here follows from one fact — <em>an edge is
 * allowed to point backwards</em>. {@code addEdge("critique", "draft")} would be a plain loop;
 * we use a conditional edge so there is a way out.
 *
 * <p>Two independent brakes stop this running forever, and you want both:
 *
 * <ol>
 *   <li><b>A counter in the state.</b> {@link TicketState#revisions()} is checked by
 *       {@link #ROUTE_AFTER_CRITIQUE}, which forces an exit at {@link #MAX_REVISIONS}. This is
 *       the brake you control and can reason about.
 *   <li><b>{@code recursionLimit}.</b> Set on {@link org.bsc.langgraph4j.CompileConfig}, this is
 *       the framework's backstop: exceed it and the graph throws instead of spinning. Treat it as
 *       a seatbelt, not a design decision — if you are hitting it, your own exit condition is wrong.
 * </ol>
 */
public class TriageGraph {

    /** The graph gives up improving after this many rewrites. */
    public static final int MAX_REVISIONS = 3;

    private final ChatModel model;

    public TriageGraph(ChatModel model) {
        this.model = model;
    }

    /**
     * Writes (or rewrites) the reply. On a second pass it is handed the previous critique, which
     * is what makes this a refinement loop rather than the same draft over and over.
     */
    private NodeAction<TicketState> draft() {
        return state -> {
            var previous = state.critique().isBlank()
                    ? ""
                    : "\nPrevious critique: %s\nPrevious draft: %s".formatted(state.critique(), state.draft());

            var prompt = """
                    Draft a short, friendly reply to this support ticket.%s

                    Subject: %s
                    Body: %s
                    """.formatted(previous, state.subject(), state.body());

            var draft = model.chat(prompt).strip();
            var revisions = state.revisions() + (state.draft().isBlank() ? 0 : 1);

            return Map.of(
                    "draft", draft,
                    "revisions", revisions,
                    "log", "draft: revision=%d, %d chars".formatted(revisions, draft.length()));
        };
    }

    /** Judges the draft. Returning the marker {@code APPROVED} is how it votes to stop. */
    private NodeAction<TicketState> critique() {
        return state -> {
            var prompt = """
                    Critique this draft reply. If it is good enough, answer exactly %s.
                    Otherwise say in one sentence what to improve.

                    Revision: %d
                    Ticket: %s
                    Draft: %s
                    """.formatted(StubChatModel.APPROVED, state.revisions(), state.subject(), state.draft());

            var verdict = model.chat(prompt).strip();
            var approved = verdict.toUpperCase().contains(StubChatModel.APPROVED);

            return Map.of(
                    "critique", approved ? "" : verdict,
                    "approved", approved,
                    "log", "critique: " + (approved ? "approved" : verdict));
        };
    }

    /**
     * The loop's exit condition. Note it checks the counter as well as the verdict — never rely on
     * the model alone to end a loop it is also driving.
     */
    static final EdgeAction<TicketState> ROUTE_AFTER_CRITIQUE = state -> {
        if (state.approved() || state.revisions() >= MAX_REVISIONS) {
            return "done";
        }
        return "revise";
    };

    public StateGraph<TicketState> build() throws GraphStateException {
        return new StateGraph<>(TicketState.SCHEMA, TicketState::new)
                .addNode("draft", node_async(draft()))
                .addNode("critique", node_async(critique()))
                .addEdge(START, "draft")
                .addEdge("draft", "critique")
                .addConditionalEdges("critique", edge_async(ROUTE_AFTER_CRITIQUE),
                        Map.of("revise", "draft", "done", END));
    }
}
