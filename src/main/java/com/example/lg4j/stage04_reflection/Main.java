package com.example.lg4j.stage04_reflection;

import com.example.lg4j.model.ModelFactory;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphRepresentation;

import java.util.Map;

/** Run with: {@code mvn -q exec:java -Dexec.mainClass=com.example.lg4j.stage04_reflection.Main} */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("provider: " + ModelFactory.describe());

        // recursionLimit is the framework's backstop against a loop that never exits.
        // Our own exit condition lives in TriageGraph.ROUTE_AFTER_CRITIQUE.
        var config = CompileConfig.builder().recursionLimit(10).build();
        var app = new TriageGraph(ModelFactory.create()).build().compile(config);

        System.out.println("--- graph (mermaid) ---");
        System.out.println(app.getGraph(GraphRepresentation.Type.MERMAID, "Stage 4: reflection loop").content());

        var state = app.invoke(Map.of(
                        "subject", "App crashes on launch",
                        "body", "Since the update it crashes with an error every time."))
                .orElseThrow();

        System.out.println("--- result ---");
        System.out.println("revisions : " + state.revisions());
        System.out.println("approved  : " + state.approved());
        System.out.println("draft     : " + state.draft());
        System.out.println("--- how the loop went ---");
        state.log().forEach(line -> System.out.println("  " + line));
    }
}
