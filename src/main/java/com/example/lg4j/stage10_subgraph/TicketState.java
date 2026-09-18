package com.example.lg4j.stage10_subgraph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One state type, shared by the parent graph and its subgraph.
 *
 * <p>{@code addSubgraph} embeds a graph with the <em>same</em> state type, so the subgraph reads
 * and writes the same fields. It is a unit of composition, not an isolation boundary.
 */
public class TicketState extends AgentState {

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

    public String severity() {
        return this.<String>value("severity").orElse("unknown");
    }

    public String owner() {
        return this.<String>value("owner").orElse("unassigned");
    }

    public String outcome() {
        return this.<String>value("outcome").orElse("");
    }

    public List<String> log() {
        return this.<List<String>>value("log").orElseGet(List::of);
    }
}
