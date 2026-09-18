package com.example.lg4j.model;

import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

/**
 * Chooses which {@link ChatModel} the LLM-backed stages talk to.
 *
 * <p>Configured entirely from outside the code so you can switch providers without editing
 * anything. Each setting is read as a system property first (so {@code -DLG4J_PROVIDER=ollama}
 * works from Maven) and then as an environment variable.
 *
 * <table>
 *   <tr><th>Setting</th><th>Default</th></tr>
 *   <tr><td>{@code LG4J_PROVIDER}</td><td>{@code stub} — also {@code anthropic}, {@code ollama}</td></tr>
 *   <tr><td>{@code ANTHROPIC_API_KEY}</td><td>required when provider is {@code anthropic}</td></tr>
 *   <tr><td>{@code LG4J_ANTHROPIC_MODEL}</td><td>{@code claude-sonnet-5}</td></tr>
 *   <tr><td>{@code OLLAMA_BASE_URL}</td><td>{@code http://localhost:11434}</td></tr>
 *   <tr><td>{@code LG4J_OLLAMA_MODEL}</td><td>{@code llama3.1}</td></tr>
 * </table>
 *
 * <p>The default is {@code stub} on purpose: everything must run with no key and no network.
 */
public final class ModelFactory {

    public static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-5";
    public static final String DEFAULT_OLLAMA_MODEL = "llama3.1";
    public static final String DEFAULT_OLLAMA_BASE_URL = "http://localhost:11434";

    public static ChatModel create() {
        var provider = setting("LG4J_PROVIDER", "stub").toLowerCase();
        return switch (provider) {
            case "stub" -> new StubChatModel();
            case "anthropic" -> anthropic();
            case "ollama" -> ollama();
            default -> throw new IllegalArgumentException(
                    "Unknown LG4J_PROVIDER '%s' (expected: stub, anthropic, ollama)".formatted(provider));
        };
    }

    /** Describes the active provider, for the banner each stage prints. */
    public static String describe() {
        var provider = setting("LG4J_PROVIDER", "stub").toLowerCase();
        return switch (provider) {
            case "anthropic" -> "anthropic / " + setting("LG4J_ANTHROPIC_MODEL", DEFAULT_ANTHROPIC_MODEL);
            case "ollama" -> "ollama / " + setting("LG4J_OLLAMA_MODEL", DEFAULT_OLLAMA_MODEL)
                    + " @ " + setting("OLLAMA_BASE_URL", DEFAULT_OLLAMA_BASE_URL);
            default -> "stub (offline, deterministic)";
        };
    }

    private static ChatModel anthropic() {
        var apiKey = setting("ANTHROPIC_API_KEY", null);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "LG4J_PROVIDER=anthropic but ANTHROPIC_API_KEY is not set. "
                            + "Export a key, or use LG4J_PROVIDER=stub to run offline.");
        }
        // NOTE: pass the model as a String. langchain4j 1.19.0's AnthropicChatModelName enum
        // predates the current model line-up, so the enum overload cannot express claude-sonnet-5.
        return AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(setting("LG4J_ANTHROPIC_MODEL", DEFAULT_ANTHROPIC_MODEL))
                .maxTokens(1024)
                .build();
    }

    private static ChatModel ollama() {
        return OllamaChatModel.builder()
                .baseUrl(setting("OLLAMA_BASE_URL", DEFAULT_OLLAMA_BASE_URL))
                .modelName(setting("LG4J_OLLAMA_MODEL", DEFAULT_OLLAMA_MODEL))
                .build();
    }

    /** System property wins over environment variable, so {@code -D...} works from Maven. */
    private static String setting(String name, String fallback) {
        var value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private ModelFactory() {}
}
