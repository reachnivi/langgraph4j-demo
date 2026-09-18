package com.example.lg4j.stage03_llm;

import com.example.lg4j.model.StubChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Runs entirely against {@link StubChatModel} — no API key, no network. */
class Stage03Test {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "I was billed twice and want a refund, billing",
            "The app crashes with an error on launch, technical",
            "What are your opening hours?,            general"
    })
    void classifiesAndRoutes(String body, String expected) throws Exception {
        var app = new TriageGraph(new StubChatModel()).build().compile();

        var state = app.invoke(Map.of("subject", "ticket", "body", body)).orElseThrow();

        assertEquals(expected, state.category());
        assertEquals(expected, state.handledBy());
        assertFalse(state.reply().isBlank(), "draft_reply should have produced text");
    }

    @Test
    @DisplayName("a chatty model answer is normalised instead of breaking the router")
    void sanitizesChattyAnswers() {
        assertEquals("billing", TriageGraph.sanitize("Billing."));
        assertEquals("billing", TriageGraph.sanitize("I think this is a BILLING issue"));
        assertEquals("technical", TriageGraph.sanitize("  technical\n"));
    }

    @Test
    @DisplayName("an unusable model answer falls back to general rather than throwing")
    void fallsBackOnJunk() {
        assertEquals("general", TriageGraph.sanitize("¯\\_(ツ)_/¯"));
        assertEquals("general", TriageGraph.sanitize(""));
        assertEquals("general", TriageGraph.sanitize(null));
    }

    @Test
    @DisplayName("a model that returns junk still lets the whole graph complete")
    void graphSurvivesABadModel() throws Exception {
        // ChatModel's methods are all `default`, so it is not a functional interface:
        // it cannot be written as a lambda. Override doChat in an anonymous class.
        ChatModel junkModel = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(AiMessage.from("no idea, sorry")).build();
            }
        };

        var state = new TriageGraph(junkModel).build().compile()
                .invoke(Map.of("subject", "ticket", "body", "I was billed twice"))
                .orElseThrow();

        assertEquals("general", state.category());
        assertEquals("general", state.handledBy());
    }
}
