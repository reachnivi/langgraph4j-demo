package com.example.lg4j.stage06_parallel;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.Reducer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * State for a graph whose nodes run at the same time.
 *
 * <p>Concurrency is where channels stop being a convenience and start being the point. When three
 * nodes all write to {@code findings} simultaneously, "last write wins" would silently throw two
 * results away. A {@link Reducer} says how to combine them instead.
 */
public class TicketState extends AgentState {

    /**
     * Merges two maps of findings into one.
     *
     * <p>A reducer is just a {@code BiFunction<T,T,T>}: (what's there already, what just arrived)
     * -> the new value. It must be associative and order-independent, because with parallel nodes
     * you do not control which result lands first.
     */
    static final Reducer<Map<String, String>> MERGE_FINDINGS = (existing, incoming) -> {
        var merged = new LinkedHashMap<String, String>();
        if (existing != null) {
            merged.putAll(existing);
        }
        if (incoming != null) {
            merged.putAll(incoming);
        }
        return merged;
    };

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "findings", Channels.base(MERGE_FINDINGS, LinkedHashMap::new),
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

    /** Everything the parallel analyzers discovered, merged by {@link #MERGE_FINDINGS}. */
    public Map<String, String> findings() {
        return this.<Map<String, String>>value("findings").orElseGet(Map::of);
    }

    public String summary() {
        return this.<String>value("summary").orElse("");
    }

    public List<String> log() {
        return this.<List<String>>value("log").orElseGet(List::of);
    }
}
