package com.example.lg4j.stage05_tools;

import com.example.lg4j.model.ModelFactory;
import dev.langchain4j.data.message.AiMessage;
import org.bsc.langgraph4j.GraphRepresentation;

/** Run with: {@code mvn -q compile exec:java -Dexec.mainClass=com.example.lg4j.stage05_tools.Main} */
public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("provider: " + ModelFactory.describe());

        var app = new TriageGraph(ModelFactory.create()).build().compile();

        System.out.println("--- graph (mermaid) ---");
        System.out.println(app.getGraph(GraphRepresentation.Type.MERMAID, "Stage 5: ReAct tools").content());

        var state = app.invoke(TriageGraph.inputFor(
                        "App crashes on launch",
                        "Since the update it crashes with an error every time.")) 
                .orElseThrow();

        System.out.println("--- conversation ---");
        state.messages().forEach(m -> System.out.println("  " + m.getClass().getSimpleName()));

        System.out.println("--- what happened ---");
        state.log().forEach(line -> System.out.println("  " + line));

        System.out.println("--- final answer ---");
        state.lastAiMessage().map(AiMessage::text).ifPresent(t -> System.out.println("  " + t));
    }
}
