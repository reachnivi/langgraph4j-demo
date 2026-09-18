package com.example.lg4j.stage09_streaming;

import java.util.Map;

/** Run with: {@code mvn -q compile exec:java -Dexec.mainClass=com.example.lg4j.stage09_streaming.Main} */
public class Main {

    public static void main(String[] args) throws Exception {
        var app = TriageGraph.build().compile();
        var input = Map.<String, Object>of("subject", "App crashes on launch");

        System.out.println("--- invoke(): one result, at the end ---");
        var started = System.currentTimeMillis();
        var finalState = app.invoke(input).orElseThrow();
        System.out.printf("  nothing to show for %dms, then: %s%n",
                System.currentTimeMillis() - started, finalState.draft());

        System.out.println("--- stream(): one event per node, as it happens ---");
        started = System.currentTimeMillis();
        for (var output : app.stream(input)) {
            // NodeOutput carries the node that just finished and the state at that instant.
            System.out.printf("  [+%4dms] %-10s category=%-9s draft=%s%n",
                    System.currentTimeMillis() - started,
                    output.node(),
                    output.state().category(),
                    output.state().draft().isBlank() ? "(none yet)" : "set");
        }

        System.out.println("--- note ---");
        System.out.println("  The START event arrives before any node has run, so early fields are");
        System.out.println("  still empty. That ordering is the point: you are watching state fill in.");
    }
}
