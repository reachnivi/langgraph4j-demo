package com.example.lg4j;

import com.example.lg4j.stage02_routing.TriageGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage02Test {

    /**
     * The point of Stage 2 is that different inputs reach different nodes. Asserting a single
     * ticket would pass even if the router always picked one branch, so cover all three.
     */
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "I was billed twice and want a refund, billing",
            "The app crashes with an error on launch, technical",
            "What are your opening hours?,            general"
    })
    void routesToTheRightHandler(String body, String expected) throws Exception {
        var app = TriageGraph.build().compile();

        var state = app.invoke(Map.of("subject", "ticket", "body", body)).orElseThrow();

        assertEquals(expected, state.category());
        assertEquals(expected, state.handledBy(), "routed to the wrong handler node");
    }

    @Test
    @DisplayName("every branch converges on summarize")
    void allBranchesConverge() throws Exception {
        var app = TriageGraph.build().compile();

        for (var body : new String[] {"refund me", "it crashes", "hello there"}) {
            var state = app.invoke(Map.of("subject", "ticket", "body", body)).orElseThrow();
            assertEquals(
                    "[%s/normal] ticket (handled by %s)".formatted(state.category(), state.handledBy()),
                    state.summary());
        }
    }
}
