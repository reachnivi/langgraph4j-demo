package com.example.lg4j.obs;

import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.List;

/**
 * Attaches listeners to any {@link ChatModel}, including ones that know nothing about them.
 *
 * <p>The mechanism is simpler than it first looks. {@code ChatModel.chat(ChatRequest)} is a
 * {@code default} method, and its body already calls {@code listeners()} and fires
 * {@code onRequest}/{@code onResponse}/{@code onError} around the call. So any model gets listener
 * callbacks for free — it just has to <em>return</em> the listeners from {@code listeners()}, which
 * a hand-written model like {@link com.example.lg4j.model.StubChatModel} never does.
 *
 * <p>That is all this class adds: it overrides {@code listeners()} and forwards the actual work to
 * the delegate. Nothing hand-rolls the callback contexts.
 *
 * <p>For real providers you would normally skip this and pass the listener straight to the builder
 * ({@code AnthropicChatModel.builder().listeners(...)}). This exists so the offline stub path
 * produces the same traces, rather than leaving the Langfuse view empty exactly when you are
 * trying to learn what it looks like.
 */
public class TracedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final List<ChatModelListener> listeners;

    public TracedChatModel(ChatModel delegate, List<ChatModelListener> listeners) {
        this.delegate = delegate;
        this.listeners = List.copyOf(listeners);
    }

    public static ChatModel traced(ChatModel delegate) {
        return new TracedChatModel(delegate, List.of(new LangfuseChatModelListener()));
    }

    /**
     * Calls the delegate's {@code doChat} directly, not its {@code chat}.
     *
     * <p>Going through {@code delegate.chat(...)} would run the listener machinery a second time,
     * once for this decorator and once for the delegate — which produces two spans per call.
     */
    @Override
    public ChatResponse doChat(ChatRequest request) {
        return delegate.doChat(request);
    }

    /** The hook the default {@code chat(...)} reads. This is what makes tracing happen at all. */
    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public String toString() {
        return "TracedChatModel(" + delegate + ")";
    }
}
