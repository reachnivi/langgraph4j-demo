package com.example.lg4j.stage10_subgraph;

import org.bsc.langgraph4j.GraphRepresentation;

import java.util.List;
import java.util.Map;

/** Run with: {@code mvn -q compile exec:java -Dexec.mainClass=com.example.lg4j.stage10_subgraph.Main} */
public class Main {

    private record Ticket(String subject, String body) {}

    public static void main(String[] args) throws Exception {
        var app = TriageGraph.build().compile();

        System.out.println("--- graph (mermaid; note the subgraph cluster) ---");
        System.out.println(app.getGraph(GraphRepresentation.Type.MERMAID, "Stage 10: subgraph").content());

        var tickets = List.of(
                new Ticket("Security incident", "Possible data loss on the billing service."),
                new Ticket("How do I export invoices?", "Just a quick question about exports."));

        for (var ticket : tickets) {
            var state = app.invoke(Map.of("subject", ticket.subject(), "body", ticket.body()))
                    .orElseThrow();
            System.out.println("--- " + ticket.subject() + " ---");
            System.out.println("  outcome  : " + state.outcome());
            System.out.println("  severity : " + state.severity());
            System.out.println("  owner    : " + state.owner());
            state.log().forEach(line -> System.out.println("  " + line));
        }
    }
}
