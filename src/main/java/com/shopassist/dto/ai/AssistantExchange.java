package com.shopassist.dto.ai;

import java.util.List;

/**
 * Everything sent to the model for one turn.
 *
 * @param systemPrompt the standing instructions
 * @param history      earlier turns, oldest first, already windowed
 * @param userMessage  what the shopper just asked
 */
public record AssistantExchange(
        String systemPrompt,
        List<HistoryTurn> history,
        String userMessage
) {
    /**
     * One earlier turn being replayed as context.
     *
     * @param fromShopper true for a shopper turn, false for an assistant turn
     * @param content     what was said
     */
    public record HistoryTurn(boolean fromShopper, String content) {
    }
}
