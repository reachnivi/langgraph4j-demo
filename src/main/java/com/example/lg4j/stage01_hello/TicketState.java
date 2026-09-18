package com.example.lg4j.stage01_hello;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The graph's state.
 *
 * <p>Two things to notice, because they are the whole idea behind LangGraph4j state:
 *
 * <ol>
 *   <li>State is really just a {@code Map<String, Object>}. {@link AgentState} wraps that map and
 *       gives you typed {@code value(...)} accessors. The getters below are conveniences you write
 *       yourself — the framework never sees them.
 *   <li>{@link #SCHEMA} declares how each key is <em>merged</em> when a node returns it. A key with
 *       no channel is simply overwritten by the last writer. A key bound to
 *       {@link Channels#appender} accumulates instead — which is why {@code log} below collects
 *       every node's message rather than keeping only the last one.
 * </ol>
 */
public class TicketState extends AgentState {

    /** Keys not listed here use last-write-wins. {@code log} accumulates across nodes. */
    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "log", Channels.appender(ArrayList::new));

    public TicketState(Map<String, Object> initData) {
        super(initData);
    }

    public String subject() {
        return this.<String>value("subject").orElse("");
    }

    public String body() {
        return this.<String>value("body").orElse("");
    }

    public String category() {
        return this.<String>value("category").orElse("unknown");
    }

    public String priority() {
        return this.<String>value("priority").orElse("normal");
    }

    public String summary() {
        return this.<String>value("summary").orElse("");
    }

    public List<String> log() {
        return this.<List<String>>value("log").orElseGet(List::of);
    }
}
