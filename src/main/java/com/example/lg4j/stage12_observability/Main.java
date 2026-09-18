package com.example.lg4j.stage12_observability;

import com.example.lg4j.model.ModelFactory;
import com.example.lg4j.obs.Langfuse;
import com.example.lg4j.obs.TracedChatModel;

import java.util.List;
import java.util.Map;

/**
 * Run with: {@code mvn -q compile exec:java -Dexec.mainClass=com.example.lg4j.stage12_observability.Main}
 *
 * <p>With no Langfuse keys set this still runs; it just tells you tracing is off. To see traces:
 *
 * <pre>
 *   docker compose up -d                 # Langfuse on http://localhost:3000
 *   # sign in, create a project, copy the two keys
 *   export LANGFUSE_PUBLIC_KEY=pk-lf-... LANGFUSE_SECRET_KEY=sk-lf-...
 *   mvn -q compile exec:java -Dexec.mainClass=com.example.lg4j.stage12_observability.Main
 * </pre>
 */
public class Main {

    private record Ticket(String subject, String body) {}

    public static void main(String[] args) throws Exception {
        var tracing = Langfuse.isConfigured();
        io.opentelemetry.sdk.OpenTelemetrySdk sdk = null;
        if (tracing) {
            // Must happen BEFORE the graph is built: langgraph4j's hook resolves its tracer
            // through GlobalOpenTelemetry at construction time.
            sdk = Langfuse.install();
            System.out.println("tracing  : on -> " + Langfuse.host());
        } else {
            System.out.println("tracing  : off (set LANGFUSE_PUBLIC_KEY and LANGFUSE_SECRET_KEY)");
        }
        System.out.println("provider : " + ModelFactory.describe());

        var model = TracedChatModel.traced(ModelFactory.create());
        var app = new TriageGraph(model).build().compile();

        var tickets = List.of(
                new Ticket("Refund for duplicate charge", "I was billed twice, please refund."),
                new Ticket("App crashes on launch", "Since the update it crashes with an error."));

        for (var ticket : tickets) {
            var state = app.invoke(Map.of("subject", ticket.subject(), "body", ticket.body()))
                    .orElseThrow();
            System.out.println("--- " + ticket.subject() + " ---");
            System.out.println("  category : " + state.category());
            System.out.println("  reply    : " + state.reply());
        }

        if (sdk != null) {
            // BatchSpanProcessor exports on a timer (5s by default), so exiting straight away
            // drops the last batch. close() flushes and shuts down - do NOT rely on a sleep.
            System.out.println("flushing spans...");
            sdk.close();
            System.out.println("done - open " + Langfuse.host() + " and look at Tracing > Traces");
        }
    }
}
