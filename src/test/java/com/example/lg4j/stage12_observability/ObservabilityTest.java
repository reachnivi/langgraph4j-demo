package com.example.lg4j.stage12_observability;

import com.example.lg4j.model.StubChatModel;
import com.example.lg4j.obs.LangfuseChatModelListener;
import com.example.lg4j.obs.TracedChatModel;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the tracing contract in-process — no collector, no network, no Langfuse.
 *
 * <p>Worth having because the failure mode this guards against is silent: emit the wrong attribute
 * names and spans still arrive at Langfuse perfectly happily, they just render as anonymous spans
 * with no model and no token usage. Nothing errors. A test is the only thing that notices.
 */
class ObservabilityTest {

    private InMemorySpanExporter spans;
    private OpenTelemetrySdk sdk;

    @BeforeEach
    void setUp() {
        spans = InMemorySpanExporter.create();
        // SimpleSpanProcessor, not Batch: exports synchronously so assertions need no flush wait.
        sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(spans))
                        .build())
                .build();
    }

    @AfterEach
    void tearDown() {
        sdk.close();
    }

    @Test
    @DisplayName("every LLM call emits a span carrying the gen_ai.* attributes Langfuse maps")
    void llmCallsCarryGenAiAttributes() {
        var model = new TracedChatModel(
                new StubChatModel(),
                java.util.List.of(new LangfuseChatModelListener(sdk)));

        model.chat("Classify this support ticket into exactly one category.\nBody: I was billed twice");

        var finished = spans.getFinishedSpanItems();
        assertEquals(1, finished.size(), "one model call should produce exactly one span");

        var attributes = finished.get(0).getAttributes().asMap().keySet().stream()
                .map(Object::toString)
                .collect(Collectors.toSet());

        // These exact names are the contract with Langfuse. Renaming any of them is a silent break.
        assertTrue(attributes.containsAll(Set.of(
                        "gen_ai.system",
                        "gen_ai.operation.name",
                        "gen_ai.request.model",
                        "gen_ai.prompt",
                        "gen_ai.completion")),
                "missing gen_ai.* attributes, got: " + attributes);
    }

    @Test
    @DisplayName("a failing model marks the span as an error rather than leaving it green")
    void failedCallsAreRecordedAsErrors() {
        var exploding = new dev.langchain4j.model.chat.ChatModel() {
            @Override
            public dev.langchain4j.model.chat.response.ChatResponse doChat(
                    dev.langchain4j.model.chat.request.ChatRequest request) {
                throw new IllegalStateException("provider is down");
            }
        };
        var model = new TracedChatModel(exploding, java.util.List.of(new LangfuseChatModelListener(sdk)));

        try {
            model.chat("Classify this");
        } catch (IllegalStateException expected) {
            // the decorator must rethrow, not swallow
        }

        var finished = spans.getFinishedSpanItems();
        assertEquals(1, finished.size(), "a failed call should still close its span");
        assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR,
                finished.get(0).getStatus().getStatusCode(),
                "a failed call recorded as OK would make traces actively misleading");
        assertFalse(finished.get(0).getEvents().isEmpty(), "the exception should be recorded");
    }

    @Test
    @DisplayName("the traced graph still produces correct results")
    void tracingDoesNotChangeBehaviour() throws Exception {
        var app = new TriageGraph(TracedChatModel.traced(new StubChatModel())).build().compile();

        var state = app.invoke(Map.of(
                "subject", "Refund for duplicate charge",
                "body", "I was billed twice.")).orElseThrow();

        assertEquals("billing", state.category());
        assertFalse(state.reply().isBlank());
    }

    @Test
    @DisplayName("a chatty classifier answer is still normalised")
    void sanitizeHandlesChattyAnswers() {
        assertEquals("billing", TriageGraph.sanitize("I'd say BILLING."));
        assertEquals("general", TriageGraph.sanitize("no idea"));
        assertEquals("general", TriageGraph.sanitize(null));
    }
}
