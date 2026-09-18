package com.example.lg4j.stage05_tools;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.langchain4j.serializer.std.LC4jStateSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Stage 5 — the ReAct loop: the model is allowed to call tools.
 *
 * <pre>
 *   START -> agent -+-(asked for a tool)-> tools -+ (back to agent)
 *                   +-(final answer)-----> END
 * </pre>
 *
 * <p>Structurally this is Stage 4's cycle again. What changed is who decides to loop: the exit
 * condition is no longer a counter we own, it is whether the model's reply contained a tool call.
 *
 * <p>The critical thing to notice is that <b>the model never executes anything</b>. It returns an
 * {@code AiMessage} carrying {@code ToolExecutionRequest}s — a request. The {@code tools} node
 * decides whether to honour it, runs the Java, and appends a {@link ToolExecutionResultMessage}.
 * That gap is where you put authorisation, validation, rate limits and audit logging.
 */
public class TriageGraph {

    /** Backstop for a model that keeps calling tools forever. */
    public static final int MAX_TOOL_ROUNDS = 5;

    private final ChatModel model;

    public TriageGraph(ChatModel model) {
        this.model = model;
    }

    /** Calls the model with the conversation so far, plus the catalogue of tools it may use. */
    private NodeAction<TicketState> agent() {
        return state -> {
            var request = ChatRequest.builder()
                    .messages(state.messages())
                    .toolSpecifications(KnowledgeBase.specification())
                    .build();

            var aiMessage = model.chat(request).aiMessage();

            var note = aiMessage.hasToolExecutionRequests()
                    ? "agent: requested %d tool call(s)".formatted(aiMessage.toolExecutionRequests().size())
                    : "agent: produced a final answer";

            return Map.of("messages", aiMessage, "log", note);
        };
    }

    /** Honours the model's tool requests and feeds the results back into the conversation. */
    private NodeAction<TicketState> tools() {
        return state -> {
            var aiMessage = state.lastAiMessage()
                    .orElseThrow(() -> new IllegalStateException("tools node reached without an AiMessage"));

            List<Object> results = new ArrayList<>();
            var notes = new ArrayList<String>();

            for (var request : aiMessage.toolExecutionRequests()) {
                // Only run tools we actually know. A model can ask for anything.
                if (!KnowledgeBase.TOOL_NAME.equals(request.name())) {
                    results.add(ToolExecutionResultMessage.from(request, "Unknown tool: " + request.name()));
                    notes.add("tools: refused unknown tool " + request.name());
                    continue;
                }
                var answer = KnowledgeBase.lookup(request.arguments());
                results.add(ToolExecutionResultMessage.from(request, answer));
                notes.add("tools: %s -> %.60s...".formatted(request.name(), answer));
            }

            return Map.of("messages", results, "log", notes);
        };
    }

    /** Loop while the model keeps asking for tools, with a hard cap so it cannot run away. */
    static final EdgeAction<TicketState> ROUTE_AFTER_AGENT = state -> {
        var toolRounds = state.messages().stream()
                .filter(ToolExecutionResultMessage.class::isInstance)
                .count();
        if (toolRounds >= MAX_TOOL_ROUNDS) {
            return "done";
        }
        return state.lastAiMessage().filter(m -> m.hasToolExecutionRequests()).isPresent()
                ? "use_tools"
                : "done";
    };

    public StateGraph<TicketState> build() throws GraphStateException {
        // LC4jStateSerializer, not the default: langchain4j's ChatMessage types are not
        // java.io.Serializable, and state holding them cannot be checkpointed without this.
        return new StateGraph<>(TicketState.SCHEMA, new LC4jStateSerializer<TicketState>(TicketState::new))
                .addNode("agent", node_async(agent()))
                .addNode("tools", node_async(tools()))
                .addEdge(START, "agent")
                .addConditionalEdges("agent", edge_async(ROUTE_AFTER_AGENT),
                        Map.of("use_tools", "tools", "done", END))
                .addEdge("tools", "agent");
    }

    /** Seeds the conversation with the system prompt and the customer's ticket. */
    public static Map<String, Object> inputFor(String subject, String body) {
        return Map.of("messages", List.of(
                SystemMessage.from("You are a support agent. Use the knowledge base before answering."),
                UserMessage.from("Subject: %s\nBody: %s".formatted(subject, body))));
    }
}
