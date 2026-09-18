package com.example.lg4j.stage07_memory;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;

import java.util.Map;

/** Run with: {@code mvn -q compile exec:java -Dexec.mainClass=com.example.lg4j.stage07_memory.Main} */
public class Main {

    public static void main(String[] args) throws Exception {
        // WHERE state is kept: set once, at compile time.
        var app = TriageGraph.build().compile(
                CompileConfig.builder().checkpointSaver(new MemorySaver()).build());

        // WHICH conversation: set per call.
        var alice = RunnableConfig.builder().threadId("ticket-alice").build();
        var bob = RunnableConfig.builder().threadId("ticket-bob").build();

        System.out.println("--- alice, three separate invocations ---");
        for (var message : new String[] {"My app crashes", "It still crashes", "Any update?"}) {
            var state = app.invoke(Map.of("message", message), alice).orElseThrow();
            System.out.printf("  \"%s\" -> %s%n", message, state.reply());
        }

        System.out.println("--- bob, a different thread, unaffected ---");
        var bobState = app.invoke(Map.of("message", "I was billed twice"), bob).orElseThrow();
        System.out.println("  " + bobState.reply());

        System.out.println("--- what each thread accumulated ---");
        System.out.println("  alice: " + app.getState(alice).state().turns());
        System.out.println("  bob  : " + app.getState(bob).state().turns());

        System.out.println("--- alice's checkpoint history ---");
        System.out.println("  " + app.getStateHistory(alice).size() + " checkpoints saved");
    }
}
