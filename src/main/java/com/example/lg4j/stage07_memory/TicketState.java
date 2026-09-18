package com.example.lg4j.stage07_memory;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Conversation state that survives between {@code invoke} calls, keyed by thread. */
public class TicketState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "turns", Channels.appender(ArrayList::new));

    public TicketState(Map<String, Object> initData) {
        super(initData);
    }

    public String message() {
        return this.<String>value("message").orElse("");
    }

    public String reply() {
        return this.<String>value("reply").orElse("");
    }

    /** Everything said so far on this thread. Grows across separate invocations. */
    public List<String> turns() {
        return this.<List<String>>value("turns").orElseGet(List::of);
    }
}
