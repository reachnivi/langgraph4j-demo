package com.example.lg4j.stage05_tools;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * State for an agent loop.
 *
 * <p>The shift from earlier stages: the interesting state is now a <em>conversation</em>. Each trip
 * round the loop appends to {@code messages} — the model's request to call a tool, then the tool's
 * result — and the model decides what to do next by reading that history back.
 *
 * <p>This is the shape langgraph4j's built-in {@code MessagesState} gives you. We spell it out by
 * hand here so the appender channel doing the work stays visible.
 */
public class TicketState extends AgentState {

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            "messages", Channels.appender(ArrayList::new),
            "log", Channels.appender(ArrayList::new));

    public TicketState(Map<String, Object> initData) {
        super(initData);
    }

    public List<ChatMessage> messages() {
        return this.<List<ChatMessage>>value("messages").orElseGet(List::of);
    }

    public Optional<ChatMessage> lastMessage() {
        var all = messages();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(all.size() - 1));
    }

    /** The last assistant turn, if the most recent message is one. */
    public Optional<AiMessage> lastAiMessage() {
        return lastMessage()
                .filter(AiMessage.class::isInstance)
                .map(AiMessage.class::cast);
    }

    public List<String> log() {
        return this.<List<String>>value("log").orElseGet(List::of);
    }
}
