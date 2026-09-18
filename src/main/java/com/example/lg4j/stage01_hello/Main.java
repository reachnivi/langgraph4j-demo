package com.example.lg4j.stage01_hello;

import org.bsc.langgraph4j.GraphRepresentation;

import java.util.Map;

/** Run with: {@code mvn -q exec:java -Dexec.mainClass=com.example.lg4j.stage01_hello.Main} */
public class Main {

    public static void main(String[] args) throws Exception {
        var graph = TriageGraph.build();
        var app = graph.compile();

        // A compiled graph can draw itself. Paste this into any Mermaid renderer.
        System.out.println("--- graph (mermaid) ---");
        System.out.println(app.getGraph(GraphRepresentation.Type.MERMAID, "Stage 1: linear triage").content());

        var result = app.invoke(Map.of(
                "subject", "  Refund for duplicate charge  ",
                "body", "  I was billed twice this month. Please refund it, this is urgent.  "));

        // invoke(...) returns Optional<State>: empty only if the graph produced no output at all.
        var state = result.orElseThrow(() -> new IllegalStateException("graph produced no state"));

        System.out.println("--- result ---");
        System.out.println("category : " + state.category());
        System.out.println("priority : " + state.priority());
        System.out.println("summary  : " + state.summary());
        System.out.println("--- log (accumulated by the appender channel) ---");
        state.log().forEach(line -> System.out.println("  " + line));
    }
}
