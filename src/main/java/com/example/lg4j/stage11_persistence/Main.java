package com.example.lg4j.stage11_persistence;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.SQLiteSaver;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Run with: {@code mvn -q compile exec:java -Dexec.mainClass=com.example.lg4j.stage11_persistence.Main} */
public class Main {

    public static void main(String[] args) throws Exception {
        var dbPath = Path.of("target", "stage11-checkpoints.db");
        Files.deleteIfExists(dbPath); // start clean so the demo is repeatable
        Files.createDirectories(dbPath.getParent());

        var config = RunnableConfig.builder().threadId("ticket-persistent").build();

        System.out.println("--- 1. first process: three messages ---");
        var first = compile(dbPath);
        for (var message : new String[] {"My app crashes", "Still crashing", "Any update?"}) {
            first.invoke(Map.of("message", message), config);
        }
        System.out.println("  turns: " + first.getState(config).state().turns());

        System.out.println("--- 2. a brand-new graph against the same file ---");
        // Simulates a restart: nothing is shared with `first` except the database on disk.
        var reopened = compile(dbPath);
        var restored = reopened.getState(config).state();
        System.out.println("  turns: " + restored.turns());
        System.out.println("  step : " + restored.step() + "  (survived the 'restart')");

        System.out.println("--- 3. time travel ---");
        var history = reopened.getStateHistory(config).stream().toList();
        System.out.println("  " + history.size() + " checkpoints on disk");
        for (var snapshot : history) {
            System.out.printf("    step=%d turns=%d next=%s%n",
                    snapshot.state().step(), snapshot.state().turns().size(), snapshot.next());
        }

        // Every snapshot carries a config that points at that exact checkpoint. Resuming from an
        // older one continues the run from there, branching off the recorded history.
        var rewindTo = history.get(history.size() - 1);
        System.out.println("  resuming from the oldest checkpoint and saying something different");
        var branched = reopened.invoke(Map.of("message", "actually, it's fixed now"), rewindTo.config())
                .orElseThrow();
        System.out.println("  branched turns: " + branched.turns());

        System.out.println("--- db file ---");
        System.out.printf("  %s (%d bytes) - delete it and the conversation is gone%n",
                dbPath, Files.size(dbPath));
    }

    /** Builds a graph backed by the SQLite file. Called twice, to prove nothing is shared in memory. */
    private static CompiledGraph<TicketState> compile(Path dbPath) throws Exception {
        // Unlike MemorySaver, SQLiteSaver MUST be given a serializer: it has to turn state into
        // bytes for the database, where MemorySaver just keeps object references in a map.
        // Omit this and it fails at runtime with "stateSerializer cannot be null".
        var saver = SQLiteSaver.builder()
                .databasePath(dbPath.toString())
                .stateSerializer(new ObjectStreamStateSerializer<>(TicketState::new))
                .createTables(true)
                .build();
        return TriageGraph.build().compile(
                CompileConfig.builder().checkpointSaver(saver).build());
    }
}
