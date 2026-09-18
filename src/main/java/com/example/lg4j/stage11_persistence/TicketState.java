package com.example.lg4j.stage11_persistence;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** State that is written to disk after every step, and can be rewound to any of them. */
public class TicketState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "turns", Channels.appender(ArrayList::new));

    public TicketState(Map<String, Object> initData) {
        super(initData);
    }

    public String message() {
        return this.<String>value("message").orElse("");
    }

    public int step() {
        return this.<Integer>value("step").orElse(0);
    }

    public List<String> turns() {
        return this.<List<String>>value("turns").orElseGet(List::of);
    }
}
