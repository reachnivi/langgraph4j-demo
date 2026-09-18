package com.example.lg4j.stage03_llm;

import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
import java.util.Set;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Stage 3 — the same graph as Stage 2, with an LLM behind two of the nodes.
 *
 * <pre>
 *   START -> ingest -> classify(LLM) -+-> billing   -+-> draft_reply(LLM) -> END
 *                                     +-> technical -+
 *                                     +-> general   -+
 * </pre>
 *
 * <p>Compare this file with {@code stage02_routing/TriageGraph.java}: the wiring is identical.
 * Only the bodies of {@code classify} and the new {@code draft_reply} changed. That is the lesson —
 * in LangGraph4j an LLM is just something a node happens to call, not a new kind of building block.
 *
 * <p>The graph takes its {@link ChatModel} as a constructor argument rather than reaching for a
 * global. That is what lets the tests run it against a stub, and it is good practice regardless.
 */
public class TriageGraph {

    /** The only categories we accept back from the model. */
    static final Set<String> CATEGORIES = Set.of("billing", "technical", "general");

    private final ChatModel model;

    public TriageGraph(ChatModel model) {
        this.model = model;
    }

    private NodeAction<TicketState> ingest() {
        return state -> Map.of(
                "subject", state.subject().trim(),
                "body", state.body().trim(),
                "log", "ingest: received %d chars".formatted(state.body().trim().length()));
    }

    /**
     * Asks the model for a category.
     *
     * <p>Note the {@code sanitize} step. A model — especially a small local one — will happily
     * reply "Billing." or "I think this is a billing issue". Never let that reach your routing
     * logic: normalise it, and fall back to a safe default instead of throwing, so one bad
     * generation cannot take the whole graph down.
     */
    private NodeAction<TicketState> classify() {
        return state -> {
            var prompt = """
                    Classify this support ticket into exactly one category.
                    Answer with one word only: billing, technical, or general.

                    Subject: %s
                    Body: %s
                    """.formatted(state.subject(), state.body());

            var raw = model.chat(prompt);
            var category = sanitize(raw);

            return Map.of(
                    "category", category,
                    "log", "classify: model said '%s' -> %s".formatted(raw.strip(), category));
        };
    }

    static String sanitize(String rawAnswer) {
        if (rawAnswer == null) {
            return "general";
        }
        var cleaned = rawAnswer.toLowerCase().replaceAll("[^a-z ]", " ");
        for (var word : cleaned.split("\\s+")) {
            if (CATEGORIES.contains(word)) {
                return word;
            }
        }
        return "general";
    }

    private static NodeAction<TicketState> handler(String name, String note) {
        return state -> Map.of(
                "handledBy", name,
                "log", "%s: %s".formatted(name, note));
    }

    /** Second LLM call: turn the ticket plus its assigned team into a customer-facing reply. */
    private NodeAction<TicketState> draftReply() {
        return state -> {
            var prompt = """
                    Draft a short, friendly reply to this support ticket.
                    It has been assigned to the %s team. Two sentences at most.

                    Subject: %s
                    Body: %s
                    """.formatted(state.handledBy(), state.subject(), state.body());

            var reply = model.chat(prompt).strip();

            return Map.of(
                    "reply", reply,
                    "log", "draft_reply: %d chars".formatted(reply.length()));
        };
    }

    static final EdgeAction<TicketState> ROUTE_BY_CATEGORY = state -> switch (state.category()) {
        case "billing" -> "to_billing";
        case "technical" -> "to_technical";
        default -> "to_general";
    };

    static final Map<String, String> ROUTES = Map.of(
            "to_billing", "billing",
            "to_technical", "technical",
            "to_general", "general");

    public StateGraph<TicketState> build() throws GraphStateException {
        return new StateGraph<>(TicketState.SCHEMA, TicketState::new)
                .addNode("ingest", node_async(ingest()))
                .addNode("classify", node_async(classify()))
                .addNode("billing", node_async(handler("billing", "checking the invoice history")))
                .addNode("technical", node_async(handler("technical", "reproducing the fault")))
                .addNode("general", node_async(handler("general", "sending a generic acknowledgement")))
                .addNode("draft_reply", node_async(draftReply()))

                .addEdge(START, "ingest")
                .addEdge("ingest", "classify")
                .addConditionalEdges("classify", edge_async(ROUTE_BY_CATEGORY), ROUTES)
                .addEdge("billing", "draft_reply")
                .addEdge("technical", "draft_reply")
                .addEdge("general", "draft_reply")
                .addEdge("draft_reply", END);
    }
}
