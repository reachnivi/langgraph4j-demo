package com.example.lg4j.model;

import dev.langchain4j.data.message.AiMessage;
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
 * <p>It answers with crude keyword rules. That is the point: it is a stand-in, not a model.
 * Switch to a real provider with {@code LG4J_PROVIDER=anthropic} or {@code =ollama}.
 */
public class StubChatModel implements ChatModel {

    /**
     * {@link ChatModel}'s methods are all {@code default}; {@code doChat} is the single one an
     * implementation is expected to supply.
     */
    @Override
    public ChatResponse doChat(ChatRequest request) {
        var prompt = request.messages().stream()
                .filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).singleText())
                .reduce((first, second) -> second) // last user message wins
                .orElse("");

        return ChatResponse.builder()
                .aiMessage(AiMessage.from(answer(prompt)))
                .build();
    }

    private static String answer(String prompt) {
        var text = prompt.toLowerCase();

        // Stage 3 asks the model for a bare category word, then asks it to draft a reply.
        // We detect which by looking for the drafting instruction.
        var drafting = text.contains("draft") || text.contains("reply");

        var category = "general";
        if (text.contains("refund") || text.contains("invoice") || text.contains("charge")
                || text.contains("billed") || text.contains("payment")) {
            category = "billing";
        } else if (text.contains("error") || text.contains("crash") || text.contains("bug")
                || text.contains("broken") || text.contains("fail")) {
            category = "technical";
        }

        if (!drafting) {
            return category;
        }
        return switch (category) {
            case "billing" -> "Thanks for reaching out. I've passed this to our billing team "
                    + "and we'll confirm the correction within one business day.";
            case "technical" -> "Sorry about the trouble. Our engineers are looking into this; "
                    + "could you send the exact error message and the time it happened?";
            default -> "Thanks for getting in touch - we've received your message and will "
                    + "follow up shortly.";
        };
    }

    @Override
    public String toString() {
        return "StubChatModel(offline)";
    }
}
