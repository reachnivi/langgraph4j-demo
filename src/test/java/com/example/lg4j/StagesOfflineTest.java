package com.example.lg4j;

import com.example.lg4j.model.StubChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stages 4-6, all offline. */
class StagesOfflineTest {

    @Test
    @DisplayName("stage 4: the reflection loop revises once, then exits on approval")
    void reflectionLoopTerminates() throws Exception {
        var app = new com.example.lg4j.stage04_reflection.TriageGraph(new StubChatModel())
                .build().compile();

        var state = app.invoke(Map.of("subject", "App crashes", "body", "It crashes on launch."))
                .orElseThrow();

        assertTrue(state.approved(), "loop should end by approval, not by hitting the cap");
        assertEquals(1, state.revisions(), "stub approves after exactly one revision");
        assertTrue(state.draft().contains("(revised)"), "second draft should have seen the critique");
    }

    @Test
    @DisplayName("stage 5: the agent calls the tool, then answers from its result")
    void reactLoopUsesTool() throws Exception {
        var app = new com.example.lg4j.stage05_tools.TriageGraph(new StubChatModel())
                .build().compile();

        var state = app.invoke(com.example.lg4j.stage05_tools.TriageGraph.inputFor(
                "App crashes on launch", "It crashes with an error.")).orElseThrow();

        // system, user, ai(tool call), tool result, ai(answer)
        assertEquals(5, state.messages().size(), "expected a full tool round trip");
        assertTrue(state.log().stream().anyMatch(l -> l.contains("kb_lookup")));
        assertFalse(state.lastAiMessage().orElseThrow().hasToolExecutionRequests(),
                "the run should end on a final answer, not another tool request");
    }

    @Test
    @DisplayName("stage 6: every parallel branch's result survives the merge")
    void parallelResultsAllMerge() throws Exception {
        var app = com.example.lg4j.stage06_parallel.TriageGraph.build().compile();

        var state = app.invoke(Map.of(
                        "subject", "Urgent: billed twice",
                        "body", "I was charged twice and I am furious."))
                .orElseThrow();

        // Without a reducer, two of these three would be lost to last-write-wins.
        assertEquals(3, state.findings().size(), "all three analyzers should contribute");
        assertEquals("billing", state.findings().get("category"));
        assertEquals("high", state.findings().get("priority"));
        assertEquals("negative", state.findings().get("sentiment"));
    }

    @ParameterizedTest(name = "stage 6 category: {0} -> {1}")
    @CsvSource({
            "I was charged twice,  billing",
            "the app crashes,      technical",
            "what are your hours?, general"
    })
    void parallelCategoryAnalyzer(String body, String expected) throws Exception {
        var app = com.example.lg4j.stage06_parallel.TriageGraph.build().compile();
        var state = app.invoke(Map.of("subject", "ticket", "body", body)).orElseThrow();
        assertEquals(expected, state.findings().get("category"));
    }
}
