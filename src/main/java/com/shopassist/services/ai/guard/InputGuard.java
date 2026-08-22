package com.shopassist.services.ai.guard;

import com.shopassist.config.ai.GuardProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Refuses messages that are trying to subvert the assistant rather than shop.
 *
 * <p>Blocking before the model call, rather than trusting the model to decline,
 * buys three things: it is deterministic, it costs no inference time, and it is
 * testable. The model usually does refuse — but "usually" is not a guarantee,
 * and this runs the same way every time.
 *
 * <p><b>Precision matters more than coverage here.</b> A filter that blocks
 * "show me all my orders" is worse than no filter, because it breaks the product
 * for honest shoppers while a determined attacker simply rephrases. Every
 * pattern below is therefore anchored to wording that has no innocent reading:
 * "ignore previous instructions" is never a shopping question, whereas "show me
 * all" plainly is. Tests assert the innocent phrasings stay allowed.
 *
 * <p>This filter is a speed bump, not the security boundary. Someone who gets a
 * hostile instruction past it still cannot read another shopper's order, because
 * the tools take no customer argument. Defence in depth is the point: this layer
 * is allowed to be imperfect precisely because it is not the one that matters.
 */
@Component
@Slf4j
public class InputGuard {

    /**
     * Attempts to override the assistant's instructions.
     */
    private static final List<Pattern> INSTRUCTION_OVERRIDE = List.of(
            Pattern.compile("ignore\\s+(all\\s+|any\\s+)?(your\\s+|the\\s+)?"
                    + "(previous|prior|above|earlier|initial|system)\\s+"
                    + "(instruction|prompt|rule|direction)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?(your|the|these|any)\\s+"
                    + "(instruction|prompt|rule|guideline)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget\\s+(everything|all)\\s+(you|above|before)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(you\\s+are\\s+now|from\\s+now\\s+on\\s+you\\s+are)\\s+"
                    + "(in\\s+)?(admin|developer|debug|god|dan|jailbreak)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(admin|developer|debug|maintenance)\\s+mode\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(act|behave|pretend|roleplay)\\s+(as|to\\s+be)\\s+"
                    + "(if\\s+you\\s+are\\s+)?(an?\\s+)?"
                    + "(admin|administrator|developer|root|superuser)",
                    Pattern.CASE_INSENSITIVE));

    /**
     * Attempts to extract the prompt or the system's internals.
     */
    private static final List<Pattern> INTERNALS_PROBE = List.of(
            Pattern.compile("(show|reveal|print|repeat|display|output|tell\\s+me)\\s+"
                    + "(me\\s+)?(your|the)\\s+(system\\s+)?"
                    + "(prompt|instruction|rule|configuration|source\\s+code)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("what\\s+(are|were)\\s+your\\s+(original\\s+|initial\\s+|system\\s+)?"
                    + "(instruction|prompt|rule)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(which|what)\\s+(database\\s+)?"
                    + "(tables?|columns?|schemas?|quer(y|ies))\\s+"
                    + "(do\\s+you|are\\s+you|you)\\s+(use|using|query|read|access|hit)",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(drop|truncate)\\s+table\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bunion\\s+select\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdelete\\s+from\\s+\\w+", Pattern.CASE_INSENSITIVE),
            // SQL detection is anchored to punctuation or to a real table name.
            // A looser "select ... from ..." also matches "Select a shirt for me
            // from your range", which is an entirely ordinary thing to say — and
            // a filter that blocks honest shoppers is worse than no filter,
            // because an attacker just rephrases.
            Pattern.compile("\\bselect\\s+\\*\\s*from\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bfrom\\s+(app_users|orders|products|order_items|order_events"
                    + "|order_drafts|chat_messages|conversations)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bwhere\\s+\\w+\\s*=\\s*['\"\\d]", Pattern.CASE_INSENSITIVE));

    /**
     * Attempts to reach other people's data.
     *
     * <p>Deliberately narrow. "My orders" and "all my orders" are ordinary
     * requests; only wording that explicitly reaches past the speaker qualifies.
     */
    private static final List<Pattern> CROSS_ACCOUNT = List.of(
            Pattern.compile("(every|all)\\s+(the\\s+)?(order|customer|user|account)s?\\s+"
                    + "(in\\s+the\\s+(database|system|store)|of\\s+(all|every|other))",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("(other|another|someone\\s+else|everyone)('s|s')?\\s+"
                    + "(order|account|address|email|password|detail)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("list\\s+(all\\s+)?(the\\s+)?(customer|user|account)s\\b",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(password|password_hash|credential)s?\\s+"
                    + "(of|for)\\s+(the\\s+)?(user|customer|account|everyone)",
                    Pattern.CASE_INSENSITIVE));

    private final GuardProperties properties;

    public InputGuard(GuardProperties properties) {
        this.properties = properties;
    }

    /**
     * Inspects a shopper's message.
     *
     * @return the reason to refuse, or empty to let the message through
     */
    public Optional<Refusal> inspect(String message) {
        if (!properties.blockInjection() || message == null || message.isBlank()) {
            return Optional.empty();
        }

        // Zero-width and control characters are used to break up trigger words
        // so they read normally to a model but not to a matcher.
        String normalised = message.replaceAll("[\\p{Cf}\\p{Cc}]", "");

        if (matches(INSTRUCTION_OVERRIDE, normalised)) {
            return refuse(Category.INSTRUCTION_OVERRIDE, message);
        }
        if (matches(INTERNALS_PROBE, normalised)) {
            return refuse(Category.INTERNALS_PROBE, message);
        }
        if (matches(CROSS_ACCOUNT, normalised)) {
            return refuse(Category.CROSS_ACCOUNT, message);
        }
        return Optional.empty();
    }

    private Optional<Refusal> refuse(Category category, String message) {
        // Logged at INFO with the category but a truncated message: the attempt
        // is worth knowing about, the full payload is not worth storing.
        log.info("Refused a message before the model call [{}]: {}",
                category, message.substring(0, Math.min(80, message.length())));
        return Optional.of(new Refusal(category, category.shopperFacingMessage));
    }

    private static boolean matches(List<Pattern> patterns, String text) {
        return patterns.stream().anyMatch(p -> p.matcher(text).find());
    }

    /** Why a message was refused. */
    public enum Category {

        INSTRUCTION_OVERRIDE(
                "I can only help with shopping here. Ask me about products or your orders."),

        INTERNALS_PROBE(
                "I can't share anything about how this system works, but I'm happy to help "
                        + "you find a product or check an order."),

        CROSS_ACCOUNT(
                "I can only look at your own account. Ask me about your orders and I'll help.");

        private final String shopperFacingMessage;

        Category(String shopperFacingMessage) {
            this.shopperFacingMessage = shopperFacingMessage;
        }

        public String shopperFacingMessage() {
            return shopperFacingMessage;
        }
    }

    /**
     * @param category what the message looked like
     * @param reply    what to tell the shopper instead of calling the model
     */
    public record Refusal(Category category, String reply) {
    }
}
