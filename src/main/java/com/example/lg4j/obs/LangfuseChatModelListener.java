package com.example.lg4j.obs;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;

import java.util.function.Supplier;

/**
 * Turns every langchain4j model call into a Langfuse "generation".
 *
 * <p>langgraph4j's own OTel hook covers nodes and edges, but it knows nothing about the LLM call
 * happening inside a node. This listener fills that gap, and it is where the
 * {@code gen_ai.*} attribute names matter: Langfuse looks for exactly those to decide a span is a
 * generation and to pull out the model, the prompt, the completion and the token counts. Emit
 * arbitrary attribute names instead and the span still arrives — it just shows up as an anonymous
 * span with no model and no usage, which is a confusing way to discover the convention.
 *
 * <p>Attach it when building the model: {@code AnthropicChatModel.builder().listeners(...)}.
 *
 * <p>The span is created in {@code onRequest} and finished in {@code onResponse}/{@code onError},
 * so it is carried between callbacks on the context attribute below.
 */
public class LangfuseChatModelListener implements ChatModelListener {

    private static final String SPAN_KEY = "langfuse.span";

    private final Supplier<OpenTelemetry> openTelemetry;

    /** Uses whatever {@link Langfuse#install()} registered globally. */
    public LangfuseChatModelListener() {
        // Resolved per call, not captured here: the listener is often constructed before the SDK
        // is registered, and capturing early would pin a no-op tracer that silently drops spans.
        this(GlobalOpenTelemetry::get);
    }

    /** Takes an explicit SDK. Used by the tests, which must not touch global state. */
    public LangfuseChatModelListener(OpenTelemetry openTelemetry) {
        this(() -> openTelemetry);
    }

    private LangfuseChatModelListener(Supplier<OpenTelemetry> openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        var request = context.chatRequest();

        var span = openTelemetry.get().getTracer(Langfuse.SERVICE_NAME)
                .spanBuilder("llm " + modelName(request.modelName()))
                .setParent(Context.current())
                .startSpan();

        span.setAttribute("gen_ai.system", "langchain4j");
        span.setAttribute("gen_ai.operation.name", "chat");
        span.setAttribute("gen_ai.request.model", modelName(request.modelName()));
        if (request.temperature() != null) {
            span.setAttribute("gen_ai.request.temperature", request.temperature());
        }
        // Langfuse renders this as the generation's Input.
        span.setAttribute("gen_ai.prompt", truncate(request.messages().toString()));

        context.attributes().put(SPAN_KEY, span);
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        var span = (Span) context.attributes().get(SPAN_KEY);
        if (span == null) {
            return;
        }
        var response = context.chatResponse();

        span.setAttribute("gen_ai.response.model", modelName(response.modelName()));
        // Langfuse renders this as the generation's Output.
        span.setAttribute("gen_ai.completion", truncate(String.valueOf(response.aiMessage().text())));

        var usage = response.tokenUsage();
        if (usage != null) {
            if (usage.inputTokenCount() != null) {
                span.setAttribute("gen_ai.usage.input_tokens", usage.inputTokenCount());
            }
            if (usage.outputTokenCount() != null) {
                span.setAttribute("gen_ai.usage.output_tokens", usage.outputTokenCount());
            }
        }
        span.end();
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        var span = (Span) context.attributes().get(SPAN_KEY);
        if (span == null) {
            return;
        }
        // Without this the failed call still shows as a successful span, which is worse than no
        // span at all - you would be debugging against a trace that says everything was fine.
        span.setStatus(StatusCode.ERROR, String.valueOf(context.error().getMessage()));
        span.recordException(context.error());
        span.end();
    }

    private static String modelName(String name) {
        return name == null ? "unknown" : name;
    }

    /** Langfuse accepts long values, but traces stay readable if prompts are capped. */
    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 4000 ? text : text.substring(0, 4000) + "...[truncated]";
    }
}
