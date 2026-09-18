package com.example.lg4j;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.checkpoint.SQLiteSaver;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stages 7-11: the ones whose whole point is state that outlives a single call. */
class StatefulStagesTest {

    @Test
    @DisplayName("stage 7: threads accumulate independently")
    void threadsAreIsolated() throws Exception {
        var app = com.example.lg4j.stage07_memory.TriageGraph.build()
                .compile(CompileConfig.builder().checkpointSaver(new MemorySaver()).build());

        var alice = RunnableConfig.builder().threadId("alice").build();
        var bob = RunnableConfig.builder().threadId("bob").build();

        app.invoke(Map.of("message", "first"), alice);
        app.invoke(Map.of("message", "second"), alice);
        app.invoke(Map.of("message", "only one"), bob);

        assertEquals(2, app.getState(alice).state().turns().size());
        assertEquals(1, app.getState(bob).state().turns().size(),
                "bob's thread must not see alice's messages");
    }

    @Test
    @DisplayName("stage 7: without a threadId every caller shares one conversation")
    void missingThreadIdSharesState() throws Exception {
        var app = com.example.lg4j.stage07_memory.TriageGraph.build()
                .compile(CompileConfig.builder().checkpointSaver(new MemorySaver()).build());

        var noThread = RunnableConfig.builder().build();
        app.invoke(Map.of("message", "one"), noThread);
        app.invoke(Map.of("message", "two"), noThread);

        // Documents the footgun rather than pretending it isn't there.
        assertEquals(2, app.getState(noThread).state().turns().size());
    }

    @Test
    @DisplayName("stage 8: the graph pauses before the gate and sends only after resume")
    void interruptPausesBeforeSending() throws Exception {
        var app = com.example.lg4j.stage08_hitl.TriageGraph.build().compile(CompileConfig.builder()
                .checkpointSaver(new MemorySaver())
                .interruptBefore(com.example.lg4j.stage08_hitl.TriageGraph.APPROVAL_GATE)
                .build());

        var config = RunnableConfig.builder().threadId("t1").build();

        var paused = app.invoke(Map.of("subject", "Crash"), config).orElseThrow();
        assertFalse(paused.sent(), "nothing may be sent before approval");
        assertEquals(com.example.lg4j.stage08_hitl.TriageGraph.APPROVAL_GATE,
                app.getState(config).next());

        var edited = "A human wrote this instead.";
        app.updateState(config, Map.of("draft", edited));

        var done = app.invoke(GraphInput.resume(), config).orElseThrow();
        assertTrue(done.sent());
        assertEquals(edited, done.draft(), "the edit must reach the node that runs after the pause");
    }

    @Test
    @DisplayName("stage 9: streaming emits one event per node, in order")
    void streamEmitsPerNode() throws Exception {
        var app = com.example.lg4j.stage09_streaming.TriageGraph.build().compile();

        var nodes = new java.util.ArrayList<String>();
        for (var output : app.stream(Map.of("subject", "App crashes"))) {
            nodes.add(output.node());
        }

        assertEquals(java.util.List.of("__START__", "classify", "draft", "finalise", "__END__"), nodes);
    }

    @Test
    @DisplayName("stage 10: only the escalation path runs the subgraph")
    void subgraphRunsOnlyWhenRouted() throws Exception {
        var app = com.example.lg4j.stage10_subgraph.TriageGraph.build().compile();

        var escalated = app.invoke(Map.of(
                "subject", "Security incident", "body", "Possible data loss.")).orElseThrow();
        assertEquals("sev1", escalated.severity());
        assertEquals("incident-commander", escalated.owner());

        var routine = app.invoke(Map.of(
                "subject", "Question", "body", "How do I export invoices?")).orElseThrow();
        assertEquals("auto-replied", routine.outcome());
        assertEquals("unassigned", routine.owner(), "the subgraph must not have run");
    }

    @Test
    @DisplayName("stage 11: state survives a brand-new graph over the same database file")
    void sqliteStateSurvivesRestart(@TempDir Path tmp) throws Exception {
        var db = tmp.resolve("cp.db");
        var config = RunnableConfig.builder().threadId("persistent").build();

        var first = compile(db);
        first.invoke(Map.of("message", "one"), config);
        first.invoke(Map.of("message", "two"), config);

        // Nothing is shared with `first` except the file on disk.
        var reopened = compile(db);
        var restored = reopened.getState(config).state();

        assertEquals(2, restored.turns().size());
        assertEquals(2, restored.step());
        assertFalse(reopened.getStateHistory(config).isEmpty(), "history should be on disk too");
    }

    @Test
    @DisplayName("stage 11: resuming from an older checkpoint branches instead of appending")
    void timeTravelBranches(@TempDir Path tmp) throws Exception {
        var db = tmp.resolve("cp.db");
        var config = RunnableConfig.builder().threadId("branching").build();

        var app = compile(db);
        app.invoke(Map.of("message", "one"), config);
        app.invoke(Map.of("message", "two"), config);

        var history = app.getStateHistory(config).stream().toList();
        var oldest = history.get(history.size() - 1);

        var branched = app.invoke(Map.of("message", "different"), oldest.config()).orElseThrow();

        assertNotEquals(app.getState(config).state().turns().size() + 1, branched.turns().size(),
                "a branch should not simply continue the main line");
        assertTrue(branched.turns().contains("customer: different"));
    }

    private static org.bsc.langgraph4j.CompiledGraph<com.example.lg4j.stage11_persistence.TicketState>
            compile(Path db) throws Exception {
        var saver = SQLiteSaver.builder()
                .databasePath(db.toString())
                .stateSerializer(new ObjectStreamStateSerializer<>(
                        com.example.lg4j.stage11_persistence.TicketState::new))
                .createTables(true)
                .build();
        return com.example.lg4j.stage11_persistence.TriageGraph.build()
                .compile(CompileConfig.builder().checkpointSaver(saver).build());
    }
}
