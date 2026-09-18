package com.example.lg4j.stage12_observability;

import com.example.lg4j.obs.Tracing;
import dev.langchain4j.model.chat.ChatModel;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.Map;
import java.util.Set;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Stage 12 — the same graph, now visible in Langfuse.
 *
 * <pre>
 *   START -> classify(LLM) -> draft_reply(LLM) -> END
 * </pre>
 *
 * <p>Compare the node bodies with Stage 3: they are unchanged. Every line of tracing lives either
 * in {@code obs/} or in the single {@link Tracing#instrument} call below. That is the point worth
 * taking away — observability that requires editing every node is observability you will stop
 * maintaining.
 *
 * <p>What you get in Langfuse:
 *
 * <ul>
 *   <li>one <b>trace</b> per {@code invoke};
 *   <li>a <b>span</b> per node, from langgraph4j's {@code OTELWrapCallTraceHook};
 *   <li>a <b>generation</b> per LLM call, with model, prompt, completion and token usage, from
 *       {@code LangfuseChatModelListener}.
 * </ul>
 */
public class TriageGraph {

    static final Set<String> CATEGORIES = Set.of("billing", "technical", "general");

    private final ChatModel model;

    public TriageGraph(ChatModel model) {
        this.model = model;
    }

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
            return Map.of("category", category, "log", "classify: " + category);
        };
    }

    private NodeAction<TicketState> draftReply() {
        return state -> {
            var prompt = """
                    Draft a short, friendly reply to this support ticket.
                    It has been assigned to the %s team. Two sentences at most.

                    Subject: %s
                    Body: %s
                    """.formatted(state.category(), state.subject(), state.body());

            var reply = model.chat(prompt).strip();
            return Map.of("reply", reply, "log", "draft_reply: %d chars".formatted(reply.length()));
        };
    }

    static String sanitize(String rawAnswer) {
        if (rawAnswer == null) {
            return "general";
        }
        for (var word : rawAnswer.toLowerCase().replaceAll("[^a-z ]", " ").split("\\s+")) {
            if (CATEGORIES.contains(word)) {
                return word;
            }
        }
        return "general";
    }

    public StateGraph<TicketState> build() throws GraphStateException {
        var graph = new StateGraph<>(TicketState.SCHEMA, TicketState::new)
                .addNode("classify", node_async(classify()))
                .addNode("draft_reply", node_async(draftReply()))
                .addEdge(START, "classify")
                .addEdge("classify", "draft_reply")
                .addEdge("draft_reply", END);

        // The only tracing-related line in the whole graph.
        return Tracing.instrument(graph, TicketState::new);
    }
}
