package com.example.lg4j.stage04_reflection;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * State for a graph that loops.
 *
 * <p>{@code revisions} is the important field: in a cyclic graph, something in the state has to
 * count, or the only thing standing between you and an infinite loop is the model's goodwill.
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

    public String draft() {
        return this.<String>value("draft").orElse("");
    }

    public String critique() {
        return this.<String>value("critique").orElse("");
    }

    /** How many times the draft has been rewritten. Drives the loop's exit condition. */
    public int revisions() {
        return this.<Integer>value("revisions").orElse(0);
    }

    public boolean approved() {
        return this.<Boolean>value("approved").orElse(false);
    }

    public List<String> log() {
        return this.<List<String>>value("log").orElseGet(List::of);
    }
}
