package com.shopassist.services.ai.guard;

import com.shopassist.config.ai.GuardProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a reply against the evidence the tools actually returned.
 *
 * <p>This exists because of something observed in testing rather than imagined.
 * Asked to buy two t-shirts, the model called the drafting tool, received the
 * correct order total, and then quoted the shopper a different figure entirely —
 * a real price from the catalog, for another shirt. Tool calling guarantees the
 * <em>action</em> was correct; it guarantees nothing about the sentence wrapped
 * around it. On another turn the model reported "we have 2 available", a stock
 * count no tool returns at all.
 *
 * <p>So every identifier and amount in the reply is compared against what the
 * tools produced. Anything with no source is unsupported — including, and
 * especially, when the turn called no tool at all. A reply that names products
 * and prices having looked nothing up has invented them, and that case used to
 * be waved through here.
 *
 * <p><b>Flag, do not block.</b> An unsupported figure is usually a wrong number
 * in an otherwise useful answer, and suppressing the whole reply would trade a
 * small error for no help at all. The finding is attached to the response and
 * logged, so a frontend can mark it and Phase 8 can measure how often it
 * happens. Blocking is reserved for {@link OutputGuard}, where the failure is a
 * disclosure rather than an inaccuracy.
 */
@Component
@Slf4j
public class GroundingCheck {

    /**
     * A product SKU: uppercase groups joined by hyphens, ending in three digits.
     *
     * <p>Anchored at both ends, which matters more than it looks. A looser
     * pattern matched <em>inside</em> other tokens: it read {@code ORD-2026} out
     * of a perfectly valid order number and reported it as unsupported, so any
     * reply naming a real order was flagged ungrounded. A check that cries wolf
     * gets switched off, which would have cost more than the bug itself.
     *
     * <p>The lookbehind stops a match starting mid-token, the lookahead stops it
     * ending where more digits or another hyphen follow, and the groups allow
     * two to four characters so a hallucinated SKU like {@code DLP-INS-002} is
     * caught whole rather than as the fragment {@code INS-002}.
     *
     * <p>Exactly three trailing digits, because every SKU in the catalog has
     * three and requiring four as well matched ordinary text like
     * {@code AI-2024}.
     */
    private static final Pattern SKU = Pattern.compile(
            "(?<![A-Z0-9-])[A-Z]{2,4}(?:-[A-Z]{2,4})*-\\d{3}(?![\\d-])");

    private static final Pattern ORDER_NUMBER = Pattern.compile("\\bORD-\\d{4}-\\d{6}\\b");

    /**
     * ISO dates only.
     *
     * <p>Added after the model reported a cancelled order as "placed on
     * 2026-02-21" when the tool had returned an August date. A date is a factual
     * claim like any other.
     *
     * <p>Prose dates ("August 24, 2026") are deliberately not matched. Parsing
     * them reliably enough to compare is more likely to raise false alarms than
     * to catch anything, and a grounding check that cries wolf gets switched off.
     * Missing some is the better failure.
     */
    private static final Pattern ISO_DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

    /**
     * Money as a shopper sees it: $34.99 or $1,099 or USD 69.98.
     *
     * <p>Only currency-marked numbers count. A bare number in prose is usually a
     * quantity or a date, and treating those as price claims would flag almost
     * every reply.
     */
    private static final Pattern MONEY = Pattern.compile(
            "(?:\\$|\\bUSD\\s?)\\s*([\\d,]+(?:\\.\\d{1,2})?)");

    private final GuardProperties properties;

    public GroundingCheck(GuardProperties properties) {
        this.properties = properties;
    }

    /**
     * @param reply         what the model wrote
     * @param supportedIds  identifiers the tools returned
     * @param supportedNums numeric values the tools returned
     * @param anyToolRan    whether the turn involved a tool at all
     */
    public Result check(String reply, Set<String> supportedIds, Set<String> supportedNums,
                        boolean anyToolRan) {

        if (!properties.checkGrounding() || reply == null || reply.isBlank()) {
            return Result.notChecked();
        }

        List<String> unsupported = new ArrayList<>();
        Set<String> claimedIds = new LinkedHashSet<>();
        collect(SKU, reply, claimedIds);
        collect(ORDER_NUMBER, reply, claimedIds);
        collect(ISO_DATE, reply, claimedIds);

        for (String id : claimedIds) {
            if (!supportedIds.contains(id)) {
                unsupported.add(id);
            }
        }

        Matcher money = MONEY.matcher(reply);
        while (money.find()) {
            String normalised = money.group(1).replace(",", "");
            if (!matchesAny(normalised, supportedNums)) {
                unsupported.add("$" + money.group(1));
            }
        }

        if (unsupported.isEmpty()) {
            return new Result(true, List.of());
        }

        // A turn that called no tool used to be excused here, on the reasoning
        // that a conversational reply has nothing to be grounded against. That
        // was wrong, and it excused the worst case rather than a harmless one.
        // A reply with no figures in it never reaches this point — it has no
        // claims, so `unsupported` is empty and it returned grounded above.
        // Getting here without a tool call means the model stated SKUs, prices
        // or dates having looked nothing up: it invented them outright. That is
        // the least grounded a reply can be, not the most excusable.
        //
        // Observed: asked for shoes, the model produced three products with
        // invented SKUs and prices under the heading "based on popular
        // choices". The finding was computed, logged at debug, and then thrown
        // away, so the shopper was shown a fabricated catalogue with no warning
        // and picked from it.
        if (!anyToolRan) {
            log.warn("Reply states {} having called no tool at all", unsupported);
        } else {
            log.warn("Reply contains values no tool returned: {}", unsupported);
        }
        return new Result(false, List.copyOf(unsupported));
    }

    /**
     * Tolerates formatting differences between a JSON figure and prose. A tool
     * returning {@code 1099.00} supports "$1,099"; the comparison is on value,
     * not on text.
     */
    private static boolean matchesAny(String claimed, Set<String> supported) {
        if (supported.contains(claimed)) {
            return true;
        }
        try {
            double value = Double.parseDouble(claimed);
            for (String s : supported) {
                if (Math.abs(Double.parseDouble(s) - value) < 0.005) {
                    return true;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return false;
    }

    private static void collect(Pattern pattern, String text, Set<String> into) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            into.add(m.group());
        }
    }

    /**
     * @param grounded    false when the reply states something no tool supports
     * @param unsupported the specific values with no source
     */
    public record Result(boolean grounded, List<String> unsupported) {

        static Result notChecked() {
            return new Result(true, List.of());
        }
    }
}
