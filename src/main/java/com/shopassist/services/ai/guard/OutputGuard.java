package com.shopassist.services.ai.guard;

import com.shopassist.config.ai.GuardProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The last check before a reply reaches a shopper.
 *
 * <p>The brief asks that internal data and schema never become available to the
 * front end. Everything upstream is built so they cannot: responses are
 * whitelisted records, errors leave through one handler, and tools return lean
 * DTOs. This is the backstop for the one path none of that covers — the model
 * writing prose of its own, having been told about tables and columns in a tool
 * description, an error message, or its own training data.
 *
 * <p>On a match the whole reply is replaced rather than redacted. Cutting the
 * offending word out of a sentence leaves the sentence around it, which often
 * says as much as the word did.
 */
@Component
@Slf4j
public class OutputGuard {

    /**
     * Schema names. Written as word-boundary matches so ordinary prose survives:
     * "products" is a normal English word and is deliberately absent, while
     * "app_users" and "order_items" have no innocent reading.
     */
    private static final List<Pattern> SCHEMA_TERMS = List.of(
            Pattern.compile("\\b(app_users|order_items|order_events|order_drafts|"
                    + "order_draft_items|chat_messages|conversations|flyway_schema_history)\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(password_hash|public_ref|stock_quantity|failed_login_attempts|"
                    + "locked_until|user_id|product_id|conversation_id|draft_id)\\b",
                    Pattern.CASE_INSENSITIVE));

    /** SQL the assistant has no reason to write. */
    private static final Pattern SQL = Pattern.compile(
            "\\b(select\\s+.*\\s+from\\s+\\w+|insert\\s+into\\s+\\w+|update\\s+\\w+\\s+set\\b"
                    + "|delete\\s+from\\s+\\w+|drop\\s+table|join\\s+\\w+\\s+on\\b)",
            Pattern.CASE_INSENSITIVE);

    /** Implementation detail: package names, stack frames, framework types. */
    private static final Pattern INTERNALS = Pattern.compile(
            "(com\\.shopassist|org\\.springframework|org\\.hibernate|jakarta\\.persistence"
                    + "|java\\.lang\\.|\\bat\\s+com\\.|Exception:|SQLException|JpaRepository"
                    + "|SecurityContextHolder)");

    /** A BCrypt hash, however it got there. */
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]?\\$\\d{2}\\$[./A-Za-z0-9]{20,}");

    /** A JWT. */
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}");

    private static final String SAFE_REPLY =
            "Sorry — I ran into a problem putting that answer together. "
                    + "Could you ask me again, or rephrase it?";

    private final GuardProperties properties;

    public OutputGuard(GuardProperties properties) {
        this.properties = properties;
    }

    /**
     * Inspects a reply.
     *
     * @return a replacement reply if the original leaked something, or empty if
     *         the original is safe to send
     */
    public Optional<String> inspect(String reply) {
        if (!properties.scanOutput() || reply == null || reply.isBlank()) {
            return Optional.empty();
        }

        String leak = firstLeak(reply);
        if (leak == null) {
            return Optional.empty();
        }

        // WARN, not INFO: upstream is supposed to make this impossible, so
        // reaching here means something changed that needs looking at.
        log.warn("Blocked a reply that exposed internals [{}]", leak);
        return Optional.of(SAFE_REPLY);
    }

    private static String firstLeak(String reply) {
        for (Pattern p : SCHEMA_TERMS) {
            if (p.matcher(reply).find()) {
                return "schema term";
            }
        }
        if (SQL.matcher(reply).find()) {
            return "SQL";
        }
        if (INTERNALS.matcher(reply).find()) {
            return "internal type or stack frame";
        }
        if (BCRYPT.matcher(reply).find()) {
            return "password hash";
        }
        if (JWT.matcher(reply).find()) {
            return "token";
        }
        return null;
    }
}
