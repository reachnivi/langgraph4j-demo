package com.example.lg4j.stage02_routing;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Same state as Stage 1, plus {@code handledBy} so we can see which branch actually ran. */
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

    public String priority() {
        return this.<String>value("priority").orElse("normal");
    }

    /** Which handler node processed this ticket — proof that routing did something. */
    public String handledBy() {
        return this.<String>value("handledBy").orElse("nobody");
    }

    public String summary() {
        return this.<String>value("summary").orElse("");
    }

    public List<String> log() {
        return this.<List<String>>value("log").orElseGet(List::of);
    }
}
