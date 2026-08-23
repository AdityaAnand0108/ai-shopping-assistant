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

    /**
     * A UUID.
     *
     * <p>Every internal reference in this system is one - draft references,
     * conversation ids, the public key of an account. None of them has any
     * business in a sentence shown to a shopper, so one surviving the note strip
     * is a genuine leak rather than a stray artefact.
     */
    private static final Pattern INTERNAL_ID = Pattern.compile(
            "\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b",
            Pattern.CASE_INSENSITIVE);

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
     * The internal note appended to earlier turns so the model can reuse the
     * identifiers its tools returned.
     *
     * <p>The model is told never to repeat these, and it does anyway — observed
     * echoing one, complete with a draft's internal reference, straight to a
     * shopper. It had seen the format in its own history and imitated it. An
     * instruction is not a mechanism, which is the recurring lesson of this
     * project; stripping the line is.
     */
    private static final Pattern INTERNAL_NOTE = Pattern.compile(
            "(?m)^\\s*\\[[^\\]]*(?:data returned by your tools|draftReference|searchProducts"
                    + "|getProductDetails|checkStock|listMyOrders|getOrderStatus"
                    + "|getDeliveryEstimate|createOrderDraft|confirmOrder|cancelOrder)"
                    + "[^\\]]*\\]\\s*$");

    /**
     * Removes internal notes the model copied out of its own context.
     *
     * <p>Stripped rather than replacing the whole reply, unlike a real leak. The
     * note is a self-contained artefact this application injected, so removing
     * the line leaves a perfectly good answer behind — whereas cutting a table
     * name out of a sentence leaves the sentence, which usually says as much.
     */
    public String stripInternalNotes(String reply) {
        if (reply == null) {
            return null;
        }
        String cleaned = INTERNAL_NOTE.matcher(reply).replaceAll("").strip();
        if (!cleaned.equals(reply.strip())) {
            log.info("Stripped an internal note the model repeated back");
        }
        return cleaned;
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
        if (INTERNAL_ID.matcher(reply).find()) {
            return "internal reference";
        }
        if (JWT.matcher(reply).find()) {
            return "token";
        }
        return null;
    }
}
