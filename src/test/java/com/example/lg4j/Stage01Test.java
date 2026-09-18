package com.example.lg4j;

import com.example.lg4j.stage01_hello.TriageGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage01Test {

    @Test
    @DisplayName("linear graph classifies a billing ticket and accumulates one log line per node")
    void runsEndToEnd() throws Exception {
        var app = TriageGraph.build().compile();

        var state = app.invoke(Map.of(
                        "subject", "  Refund please  ",
                        "body", "  I was charged twice, this is urgent.  "))
                .orElseThrow();

        assertEquals("billing", state.category());
        assertEquals("high", state.priority());
        assertEquals("[billing/high] Refund please", state.summary());

        // ingest trimmed the whitespace before classify ever saw it
        assertEquals("Refund please", state.subject());

        // The appender channel collected every node's line rather than keeping only the last.
        assertEquals(3, state.log().size(), "expected one log line per node, got: " + state.log());
    }

    @Test
    @DisplayName("a ticket with no keywords falls through to general/normal")
    void defaultsToGeneral() throws Exception {
        var app = TriageGraph.build().compile();

        var state = app.invoke(Map.of("subject", "Hello", "body", "Just saying hi")).orElseThrow();

        assertEquals("general", state.category());
        assertEquals("normal", state.priority());
        assertTrue(state.summary().startsWith("[general/normal]"));
    }
}
