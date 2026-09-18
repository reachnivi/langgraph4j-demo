package com.example.lg4j.stage06_parallel;

import org.bsc.langgraph4j.GraphRepresentation;

import java.util.Map;

/** Run with: {@code mvn -q compile exec:java -Dexec.mainClass=com.example.lg4j.stage06_parallel.Main} */
public class Main {

    public static void main(String[] args) throws Exception {
        var app = TriageGraph.build().compile();

        System.out.println("--- graph (mermaid) ---");
        System.out.println(app.getGraph(GraphRepresentation.Type.MERMAID, "Stage 6: parallel analyzers").content());

        var started = System.currentTimeMillis();
        var state = app.invoke(Map.of(
                        "subject", "Urgent: app crashes after being billed twice",
                        "body", "This is unacceptable, it crashes with an error and I was charged twice."))
                .orElseThrow();
        var elapsed = System.currentTimeMillis() - started;

        System.out.println("--- result ---");
        System.out.println("findings : " + state.findings());
        System.out.println("summary  : " + state.summary());
        System.out.println("--- timing ---");
        System.out.printf("  elapsed %dms; serial would be ~%dms (3 x %dms)%n",
                elapsed, 3 * TriageGraph.WORK_MILLIS, TriageGraph.WORK_MILLIS);
        System.out.println("--- log ---");
        state.log().forEach(line -> System.out.println("  " + line));
    }
}
