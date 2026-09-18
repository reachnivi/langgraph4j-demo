package com.example.lg4j.stage08_hitl;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;

import java.util.Map;

/** Run with: {@code mvn -q compile exec:java -Dexec.mainClass=com.example.lg4j.stage08_hitl.Main} */
public class Main {

    public static void main(String[] args) throws Exception {
        var app = TriageGraph.build().compile(CompileConfig.builder()
                .checkpointSaver(new MemorySaver())          // interrupts need somewhere to park
                .interruptBefore(TriageGraph.APPROVAL_GATE)  // stop just before sending
                .build());

        var config = RunnableConfig.builder().threadId("ticket-42").build();

        System.out.println("--- 1. run until the approval gate ---");
        var paused = app.invoke(Map.of("subject", "App crashes on launch"), config).orElseThrow();
        System.out.println("  sent  : " + paused.sent() + "   (nothing has gone out yet)");
        System.out.println("  draft : " + paused.draft());

        System.out.println("--- 2. inspect where it stopped ---");
        var snapshot = app.getState(config);
        System.out.println("  next node: " + snapshot.next());

        System.out.println("--- 3. the human edits the draft ---");
        var edited = "Hi - thanks for reporting the crash. A fix ships this week, and "
                + "we'll email you the moment it lands.";
        app.updateState(config, Map.of("draft", edited));
        System.out.println("  draft is now: " + app.getState(config).state().draft());

        System.out.println("--- 4. approve and resume ---");
        var done = app.invoke(GraphInput.resume(), config).orElseThrow();
        System.out.println("  sent  : " + done.sent());
        System.out.println("  draft : " + done.draft());

        System.out.println("--- log ---");
        done.log().forEach(line -> System.out.println("  " + line));
    }
}
