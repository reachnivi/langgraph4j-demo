package com.example.lg4j.stage05_tools;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The one tool this agent can call: a canned support knowledge base.
 *
 * <p>A "tool" is two separate things, and it helps to keep them straight:
 *
 * <ol>
 *   <li>a {@link ToolSpecification} — the <em>description</em> sent to the model, so it knows the
 *       tool exists, what it does, and what arguments it takes;
 *   <li>the actual Java code that runs when the model asks for it ({@link #lookup}). The model
 *       never runs anything — it only ever <em>asks</em>, and your graph decides whether to comply.
 * </ol>
 */
public final class KnowledgeBase {

    public static final String TOOL_NAME = "kb_lookup";

    private static final Map<String, String> ARTICLES = new LinkedHashMap<>(Map.of(
            "billing", "KB-101: Duplicate charges are auto-reversed within 3 business days. "
                    + "Agents can expedite via the Billing console.",
            "technical", "KB-204: Crashes on launch after an update are usually a stale cache. "
                    + "Ask the customer to clear app data and reinstall.",
            "general", "KB-001: Support hours are 09:00-17:00 Mon-Fri. "
                    + "Out-of-hours messages are answered the next working day."));

    /** What the model is told about the tool. */
    public static ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(TOOL_NAME)
                .description("Look up a support knowledge-base article for a customer issue. "
                        + "Returns guidance an agent can act on.")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("query", "The customer's issue, in a few words")
                        .required("query")
                        .build())
                .build();
    }

    /** What actually runs. Deliberately offline and deterministic. */
    public static String lookup(String query) {
        var text = query == null ? "" : query.toLowerCase();
        if (text.contains("refund") || text.contains("charge") || text.contains("billed")
                || text.contains("invoice") || text.contains("payment")) {
            return ARTICLES.get("billing");
        }
        if (text.contains("crash") || text.contains("error") || text.contains("bug")
                || text.contains("broken") || text.contains("fail")) {
            return ARTICLES.get("technical");
        }
        return ARTICLES.get("general");
    }

    private KnowledgeBase() {}
}
