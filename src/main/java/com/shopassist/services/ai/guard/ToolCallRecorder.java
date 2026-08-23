package com.shopassist.services.ai.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Records what the tools returned during a single chat turn.
 *
 * <p>Exists so the answer can be checked against the evidence. Spring AI runs
 * tool calls inside the model call, so by the time a reply comes back there is
 * otherwise no record of what the model was actually told — and without that,
 * "did it make this price up?" is unanswerable.
 *
 * <p>Facts are extracted generically: each tool result is serialised and scanned
 * for the shapes that matter — SKUs, order numbers and money. That keeps the
 * tools themselves free of grounding plumbing, and means a tool added later is
 * covered without anyone remembering to wire it in.
 *
 * <p>State is held in a {@link ThreadLocal} rather than a request-scoped bean
 * because tool calls happen on the request thread, deep inside a library call
 * that has no access to Spring's request scope. The turn must therefore be
 * closed in a finally block; {@link #clear()} exists for that.
 */
@Component
@Slf4j
public class ToolCallRecorder {

    /**
     * Product SKUs, e.g. NIK-TS-001 or BK-001.
     *
     * <p>Must stay identical to the pattern in {@code GroundingCheck}: this side
     * decides what counts as supported and that side decides what was claimed,
     * so a SKU either pattern missed would be judged against the wrong evidence.
     */
    private static final Pattern SKU = Pattern.compile(
            "(?<![A-Z0-9-])[A-Z]{2,4}(?:-[A-Z]{2,4})*-\\d{3}(?![\\d-])");

    /** Order numbers, e.g. ORD-2026-000102. */
    private static final Pattern ORDER_NUMBER = Pattern.compile("\\bORD-\\d{4}-\\d{6}\\b");

    /**
     * ISO dates, as they appear in a serialised instant or local date.
     *
     * <p>Recorded because the model was observed reporting an order as placed on
     * a date months away from the one the tool returned. A date is a factual
     * claim like any other.
     */
    private static final Pattern ISO_DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");

    /** Money as it appears in a JSON tool result: 1799.00, 3598, 29990.00. */
    private static final Pattern AMOUNT = Pattern.compile("\\b\\d{2,9}(?:\\.\\d{1,2})?\\b");

    private final ObjectMapper objectMapper;
    private final ThreadLocal<Turn> current = new ThreadLocal<>();

    public ToolCallRecorder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Opens a recording window for one chat turn.
     *
     * @param conversationRef the thread this turn belongs to. Carried here so a
     *                        tool can scope what it does to this conversation
     *                        without the model having to pass an identifier it
     *                        would get wrong.
     */
    public void startTurn(String conversationRef) {
        Turn turn = new Turn();
        turn.conversationRef = conversationRef;
        current.set(turn);
    }

    /** The conversation this turn belongs to, or null outside a chat turn. */
    public String conversationRef() {
        Turn turn = current.get();
        return turn == null ? null : turn.conversationRef;
    }

    /** Closes the window. Must run in a finally block, or state leaks between
     *  turns on a pooled request thread. */
    public void clear() {
        current.remove();
    }

    /**
     * Records one tool result.
     *
     * <p>Never throws: a fault in bookkeeping must not fail a shopper's request.
     */
    public void record(String toolName, Object result) {
        Turn turn = current.get();
        if (turn == null) {
            return;
        }
        turn.toolNames.add(toolName);
        try {
            String json = objectMapper.writeValueAsString(result);
            collect(SKU, json, turn.facts);
            collect(ORDER_NUMBER, json, turn.facts);
            collect(ISO_DATE, json, turn.facts);
            collect(AMOUNT, json, turn.amounts);
        } catch (Exception e) {
            log.debug("Could not record the result of {}: {}", toolName, e.getMessage());
        }
    }

    /**
     * Records a result and hands it straight back, so a tool can wrap its return
     * value without an extra local variable.
     *
     * @param <T> the tool's result type
     * @return the result, unchanged
     */
    public <T> T recorded(String toolName, T result) {
        record(toolName, result);
        return result;
    }

    /** Records a tool that failed, so the turn shows it was attempted. */
    public void recordFailure(String toolName) {
        Turn turn = current.get();
        if (turn != null) {
            turn.toolNames.add(toolName);
        }
    }

    /** Tool names invoked this turn, in the order first seen. */
    public List<String> toolsUsed() {
        Turn turn = current.get();
        return turn == null ? List.of() : List.copyOf(turn.toolNames);
    }

    /** Identifiers the tools actually returned this turn. */
    public Set<String> identifiers() {
        Turn turn = current.get();
        return turn == null ? Set.of() : Set.copyOf(turn.facts);
    }

    /** Numeric values the tools actually returned this turn. */
    public Set<String> amounts() {
        Turn turn = current.get();
        return turn == null ? Set.of() : Set.copyOf(turn.amounts);
    }

    /**
     * Notes the draft a purchase tool priced this turn.
     *
     * <p>Recorded separately from the generic fact scan because the client needs
     * the reference itself, not merely to know one appeared: it is what a
     * Confirm button posts back. Extracting it from the serialised result would
     * mean pattern-matching a UUID out of arbitrary JSON, which is guesswork
     * where an explicit call is not.
     */
    public void noteDraft(String reference) {
        Turn turn = current.get();
        if (turn != null) {
            turn.draftReference = reference;
        }
    }

    /** Notes an order a purchase tool actually placed this turn. */
    public void noteOrder(String orderNumber) {
        Turn turn = current.get();
        if (turn != null) {
            turn.placedOrderNumber = orderNumber;
        }
    }

    /** The draft priced this turn, or null. */
    public String draftReference() {
        Turn turn = current.get();
        return turn == null ? null : turn.draftReference;
    }

    /** The order placed this turn, or null. */
    public String placedOrderNumber() {
        Turn turn = current.get();
        return turn == null ? null : turn.placedOrderNumber;
    }

    public boolean anyToolRan() {
        Turn turn = current.get();
        return turn != null && !turn.toolNames.isEmpty();
    }

    private static void collect(Pattern pattern, String text, Set<String> into) {
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            into.add(m.group());
        }
    }

    private static final class Turn {
        private final List<String> toolNames = new ArrayList<>();
        private final Set<String> facts = new LinkedHashSet<>();
        private final Set<String> amounts = new LinkedHashSet<>();
        private String conversationRef;
        private String draftReference;
        private String placedOrderNumber;
    }
}
