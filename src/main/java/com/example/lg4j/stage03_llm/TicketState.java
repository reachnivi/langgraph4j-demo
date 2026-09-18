package com.example.lg4j.stage03_llm;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Stage 2's state plus {@code reply}, the text the model drafts. */
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

    public String category() {
        return this.<String>value("category").orElse("unknown");
    }

    public String handledBy() {
        return this.<String>value("handledBy").orElse("nobody");
    }

    public String reply() {
        return this.<String>value("reply").orElse("");
    }

    public List<String> log() {
        return this.<List<String>>value("log").orElseGet(List::of);
    }
}
