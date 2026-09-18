package com.example.lg4j.stage03_llm;

import com.example.lg4j.model.ModelFactory;
import org.bsc.langgraph4j.GraphRepresentation;

import java.util.List;
import java.util.Map;

/**
 * Run with: {@code mvn -q exec:java -Dexec.mainClass=com.example.lg4j.stage03_llm.Main}
 *
 * <p>Defaults to the offline stub model. To use a real one:
 *
 * <pre>
 *   LG4J_PROVIDER=anthropic ANTHROPIC_API_KEY=sk-... mvn -q exec:java -Dexec.mainClass=...
 *   LG4J_PROVIDER=ollama LG4J_OLLAMA_MODEL=llama3.1   mvn -q exec:java -Dexec.mainClass=...
 * </pre>
 */
public class Main {

    private record Ticket(String subject, String body) {}

    public static void main(String[] args) throws Exception {
        System.out.println("provider: " + ModelFactory.describe());

        var app = new TriageGraph(ModelFactory.create()).build().compile();

        System.out.println("--- graph (mermaid) ---");
        System.out.println(app.getGraph(GraphRepresentation.Type.MERMAID, "Stage 3: LLM triage").content());

        var tickets = List.of(
                new Ticket("Refund for duplicate charge", "I was billed twice, please refund."),
                new Ticket("App crashes on launch", "Since the update it crashes with an error."),
                new Ticket("Question about your hours", "What time does support open on Saturdays?"));

        for (var ticket : tickets) {
            var state = app.invoke(Map.of("subject", ticket.subject(), "body", ticket.body()))
                    .orElseThrow(() -> new IllegalStateException("graph produced no state"));

            System.out.println("--- " + ticket.subject() + " ---");
            System.out.println("  category  : " + state.category());
            System.out.println("  handledBy : " + state.handledBy());
            System.out.println("  reply     : " + state.reply());
        }
    }
}
