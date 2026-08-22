package com.shopassist.dto.chat;

/**
 * The answer to one chat turn.
 *
 * @param conversationId the thread, so the client can continue it
 * @param reply          the assistant's message
 * @param insight        which tools produced the answer, and whether all of it
 *                       is supported by what they returned
 */
public record ChatResponse(
        String conversationId,
        ChatMessageResponse reply,
        TurnInsight insight
) {
}
