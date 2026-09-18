package com.example.lg4j.stage02_routing;

import org.bsc.langgraph4j.GraphRepresentation;

import java.util.List;
import java.util.Map;

/** Run with: {@code mvn -q exec:java -Dexec.mainClass=com.example.lg4j.stage02_routing.Main} */
public class Main {

    private record Ticket(String subject, String body) {}

    public static void main(String[] args) throws Exception {
        var app = TriageGraph.build().compile();

        System.out.println("--- graph (mermaid) ---");
        System.out.println(app.getGraph(GraphRepresentation.Type.MERMAID, "Stage 2: routing").content());

        // Three tickets that must take three different branches. If routing were broken,
        // they'd all still "run fine" — which is why we check more than one.
        var tickets = List.of(
                new Ticket("Refund for duplicate charge", "I was billed twice, please refund. Urgent."),
                new Ticket("App crashes on launch", "Since the update it crashes with an error."),
                new Ticket("Question about your hours", "What time does support open on Saturdays?"));

        for (var ticket : tickets) {
            var state = app.invoke(Map.of("subject", ticket.subject(), "body", ticket.body()))
                    .orElseThrow(() -> new IllegalStateException("graph produced no state"));

            System.out.println("--- " + ticket.subject() + " ---");
            System.out.println("  category  : " + state.category());
            System.out.println("  handledBy : " + state.handledBy());
            System.out.println("  summary   : " + state.summary());
        }
    }
}
