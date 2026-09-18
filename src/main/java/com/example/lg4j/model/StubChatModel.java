package com.example.lg4j.model;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * A deterministic, offline {@link ChatModel}.
 *
 * <p>This exists so every stage of the demo runs with no API key and no network — which is what
 * lets {@code mvn test} stay honest in CI, and lets you study the <em>graph</em> without an LLM's
 * randomness muddying what you are looking at.
 *
 * <p>It dispatches on a keyword in the <strong>first line</strong> of the last user message. Using
 * only the first line matters: a ticket body that happens to contain the word "reply" must not be
 * mistaken for a request to draft one.
 *
 * <p>It is a stand-in, not a model. Switch to a real one with {@code LG4J_PROVIDER=anthropic}
 * or {@code =ollama}.
 */
public class StubChatModel implements ChatModel {

    /** Marker the critique step looks for to decide a draft is good enough. */
    public static final String APPROVED = "APPROVED";

    /** Matches "Revision: 1" and up — i.e. the draft has already been rewritten at least once. */
    private static final java.util.regex.Pattern REVISED =
            java.util.regex.Pattern.compile("revision:\\s*[1-9]");

    /** {@link ChatModel}'s methods are all {@code default}; {@code doChat} is the one to supply. */
    @Override
    public ChatResponse doChat(ChatRequest request) {
        var aiMessage = respond(request);
        return ChatResponse.builder().aiMessage(aiMessage).build();
    }

    private AiMessage respond(ChatRequest request) {
        // Stage 5: if the caller offered tools and we have not run one yet, ask for a tool call.
        var tools = request.toolSpecifications();
        if (tools != null && !tools.isEmpty() && !hasToolResult(request)) {
            return AiMessage.from(ToolExecutionRequest.builder()
                    .id("stub-call-1")
                    .name(tools.get(0).name())
                    .arguments("{\"query\":\"%s\"}".formatted(escape(lastUserText(request))))
                    .build());
        }

        var prompt = lastUserText(request);
        var firstLine = prompt.lines().findFirst().orElse("").toLowerCase();
        var body = prompt.toLowerCase();

        if (firstLine.contains("classify")) {
            return AiMessage.from(category(body));
        }
        if (firstLine.contains("critique")) {
            // Approve once the draft has been revised at least once, so Stage 4's reflection
            // loop terminates deterministically after exactly one revision. The caller signals
            // this with a "Revision: N" line.
            return AiMessage.from(REVISED.matcher(body).find()
                    ? APPROVED
                    : "Too generic. Name the specific issue and say what happens next.");
        }
        return AiMessage.from(reply(category(body), body.contains("previous critique")));
    }

    private static boolean hasToolResult(ChatRequest request) {
        return request.messages().stream().anyMatch(ToolExecutionResultMessage.class::isInstance);
    }

    private static String lastUserText(ChatRequest request) {
        return request.messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).singleText())
                .reduce((first, second) -> second) // last user message wins
                .orElse("");
    }

    static String category(String lowercaseText) {
        if (lowercaseText.contains("refund") || lowercaseText.contains("invoice")
                || lowercaseText.contains("charge") || lowercaseText.contains("billed")
                || lowercaseText.contains("payment")) {
            return "billing";
        }
        if (lowercaseText.contains("error") || lowercaseText.contains("crash")
                || lowercaseText.contains("bug") || lowercaseText.contains("broken")
                || lowercaseText.contains("fail")) {
            return "technical";
        }
        return "general";
    }

    private static String reply(String category, boolean revised) {
        var base = switch (category) {
            case "billing" -> "Thanks for reaching out. I've passed this to our billing team "
                    + "and we'll confirm the correction within one business day.";
            case "technical" -> "Sorry about the trouble. Our engineers are looking into this; "
                    + "could you send the exact error message and the time it happened?";
            default -> "Thanks for getting in touch - we've received your message and will "
                    + "follow up shortly.";
        };
        return revised ? base + " (revised)" : base;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    @Override
    public String toString() {
        return "StubChatModel(offline)";
    }
}
