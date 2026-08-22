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
 * Asked to buy two t-shirts, the model called the drafting tool, received a
 * total of ₹3,598.00, and then told the shopper ₹2,499.99 — a real price from
 * the catalog, for a different shirt. Tool calling guarantees the <em>action</em>
 * was correct; it guarantees nothing about the sentence wrapped around it. On
 * another turn the model reported "we have 2 available", a stock count no tool
 * returns at all.
 *
 * <p>So every identifier and amount in the reply is compared against what the
 * tools produced. Anything with no source is unsupported.
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

    private static final Pattern SKU = Pattern.compile("\\b[A-Z]{2,4}-(?:[A-Z]{2}-)?\\d{3,4}\\b");
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
     * Money as a shopper sees it: ₹1,299 or ₹3,598.00 or Rs. 1799.
     *
     * <p>Only currency-marked numbers count. A bare number in prose is usually a
     * quantity or a date, and treating those as price claims would flag almost
     * every reply.
     */
    private static final Pattern MONEY = Pattern.compile(
            "(?:₹|\\bRs\\.?\\s?|\\bINR\\s?)\\s*([\\d,]+(?:\\.\\d{1,2})?)");

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
                unsupported.add("₹" + money.group(1));
            }
        }

        if (unsupported.isEmpty()) {
            return new Result(true, List.of());
        }

        // A turn that called no tool has nothing to be grounded against; a
        // conversational reply mentioning no figures is not a failure. Only
        // treat this as a finding when the model was actually looking things up.
        if (!anyToolRan) {
            log.debug("Reply mentions {} with no tool call to support it", unsupported);
            return new Result(true, List.of());
        }

        log.warn("Reply contains values no tool returned: {}", unsupported);
        return new Result(false, List.copyOf(unsupported));
    }

    /**
     * Tolerates formatting differences between a JSON figure and prose. A tool
     * returning {@code 1799.00} supports "₹1,799"; the comparison is on value,
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
