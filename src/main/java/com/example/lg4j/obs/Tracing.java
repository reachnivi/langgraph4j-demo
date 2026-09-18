package com.example.lg4j.obs;

import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.otel.OTELWrapCallTraceHook;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.AgentStateFactory;

/**
 * Adds a span per graph node.
 *
 * <p>There is nothing to write here: langgraph4j ships {@link OTELWrapCallTraceHook}, which
 * implements both {@code NodeHook.WrapCall} and {@code EdgeHook.WrapCall}. Registering it wraps
 * every node call in a span, with no change to any node's code — which is the whole argument for
 * hooks over hand-instrumenting your nodes.
 *
 * <p>The hook resolves its tracer through {@code GlobalOpenTelemetry}, so
 * {@link Langfuse#install()} must have run first.
 */
public final class Tracing {

    /** Registers node-level tracing on an existing graph and hands it back for chaining. */
    public static <S extends AgentState> StateGraph<S> instrument(
            StateGraph<S> graph, AgentStateFactory<S> stateFactory) {
        return graph.addWrapCallNodeHook(
                new OTELWrapCallTraceHook<>(new ObjectStreamStateSerializer<>(stateFactory)));
    }

    private Tracing() {}
}
