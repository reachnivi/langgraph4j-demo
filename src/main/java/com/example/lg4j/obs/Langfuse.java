package com.example.lg4j.obs;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Wires OpenTelemetry up to Langfuse.
 *
 * <p>Langfuse has no Java SDK. It does not need one: it accepts plain OTLP on
 * {@code ${LANGFUSE_HOST}/api/public/otel} and maps the OpenTelemetry GenAI conventions onto its
 * own model — a root span becomes a trace, child spans become nested spans, and any span carrying
 * {@code gen_ai.*} attributes is shown as a generation with its model and token usage.
 *
 * <p>So "integrating Langfuse" in Java means three things, and nothing more exotic:
 *
 * <ol>
 *   <li>point an OTLP exporter at that endpoint, with HTTP basic auth built from your key pair
 *       (this class);
 *   <li>make each graph node a span ({@link Tracing}, which reuses langgraph4j's own hook);
 *   <li>make each LLM call a span with {@code gen_ai.*} attributes
 *       ({@link LangfuseChatModelListener}).
 * </ol>
 *
 * <p>Configuration, all optional except the keys:
 *
 * <table>
 *   <tr><th>Variable</th><th>Default</th></tr>
 *   <tr><td>{@code LANGFUSE_HOST}</td><td>{@code http://localhost:3000} (the bundled compose file)</td></tr>
 *   <tr><td>{@code LANGFUSE_PUBLIC_KEY}</td><td>— required</td></tr>
 *   <tr><td>{@code LANGFUSE_SECRET_KEY}</td><td>— required</td></tr>
 * </table>
 */
public final class Langfuse {

    /** The bundled docker-compose stack. Use https://cloud.langfuse.com for the hosted version. */
    public static final String DEFAULT_HOST = "http://localhost:3000";

    public static final String SERVICE_NAME = "langgraph4j-demo";

    /** True when both keys are present, i.e. there is any point trying to export. */
    public static boolean isConfigured() {
        return !setting("LANGFUSE_PUBLIC_KEY", "").isBlank()
                && !setting("LANGFUSE_SECRET_KEY", "").isBlank();
    }

    public static String host() {
        return setting("LANGFUSE_HOST", DEFAULT_HOST);
    }

    /**
     * Builds an SDK that exports to Langfuse.
     *
     * <p>Registered globally because langgraph4j's {@code OTELWrapCallTraceHook} resolves the
     * tracer through {@code GlobalOpenTelemetry}. Register before building any graph.
     *
     * <p>Returns the SDK so the caller can {@code close()} it. That matters: spans go through a
     * {@link BatchSpanProcessor}, which exports on a timer (5s by default), so a program that
     * finishes and exits promptly loses its last batch. {@code close()} flushes and shuts down.
     * This is the single most common reason for "my traces never showed up".
     */
    public static OpenTelemetrySdk install() {
        var credentials = "%s:%s".formatted(
                setting("LANGFUSE_PUBLIC_KEY", ""), setting("LANGFUSE_SECRET_KEY", ""));
        var basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        // NOTE: the OTLP exporter appends "/v1/traces" itself, so the base path stops at /otel.
        var exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(host() + "/api/public/otel/v1/traces")
                .addHeader("Authorization", basicAuth)
                .addHeader("x-langfuse-ingestion-version", "4")
                .setTimeout(Duration.ofSeconds(10))
                .build();

        var tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .setResource(Resource.getDefault().merge(Resource.create(
                        Attributes.builder().put("service.name", SERVICE_NAME).build())))
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .buildAndRegisterGlobal();
    }

    /** System property first, then environment variable — same convention as ModelFactory. */
    private static String setting(String name, String fallback) {
        var value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private Langfuse() {}
}
