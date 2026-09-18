package com.example.lg4j.stage08_hitl;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** State for a graph that pauses for human approval before it does something irreversible. */
public class TicketState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "log", Channels.appender(ArrayList::new));

    public TicketState(Map<String, Object> initData) {
        super(initData);
    }

    public String subject() {
        return this.<String>value("subject").orElse("");
    }

    public String draft() {
        return this.<String>value("draft").orElse("");
    }

    public boolean sent() {
        return this.<Boolean>value("sent").orElse(false);
    }

    public List<String> log() {
        return this.<List<String>>value("log").orElseGet(List::of);
    }
}
