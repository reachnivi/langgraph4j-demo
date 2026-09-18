package com.example.lg4j.obs;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.HashMap;
import java.util.List;

/**
 * Wraps any {@link ChatModel} so listeners fire around every call.
 *
 * <p>Real providers (Anthropic, Ollama) already invoke listeners you pass to
 * {@code .listeners(...)}, and that is the idiomatic route. But the listener machinery lives
 * inside each provider, so a hand-written {@link com.example.lg4j.model.StubChatModel} gets none of
 * it — meaning the offline path would produce traces with node spans but no generations, and the
 * most interesting part of the Langfuse view would be empty exactly when you are trying to learn it.
 *
 * <p>This decorator closes that gap by building the same callback contexts by hand, so one
 * {@link LangfuseChatModelListener} serves both paths.
 */
public class TracedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final List<ChatModelListener> listeners;

    public TracedChatModel(ChatModel delegate, List<ChatModelListener> listeners) {
        this.delegate = delegate;
        this.listeners = listeners;
    }

    public static ChatModel traced(ChatModel delegate) {
        return new TracedChatModel(delegate, List.of(new LangfuseChatModelListener()));
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        var provider = delegate.provider();
        // Shared across the three callbacks; the listener stashes its span here.
        var attributes = new HashMap<Object, Object>();

        listeners.forEach(l -> l.onRequest(new ChatModelRequestContext(request, provider, attributes)));
        try {
            var response = delegate.chat(request);
            listeners.forEach(l ->
                    l.onResponse(new ChatModelResponseContext(response, request, provider, attributes)));
            return response;
        } catch (RuntimeException e) {
            listeners.forEach(l ->
                    l.onError(new ChatModelErrorContext(e, request, provider, attributes)));
            throw e;
        }
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public String toString() {
        return "TracedChatModel(" + delegate + ")";
    }
}
