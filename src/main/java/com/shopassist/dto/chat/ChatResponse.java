package com.shopassist.dto.chat;

/**
 * The answer to one chat turn.
 *
 * @param conversationId the thread, so the client can continue it
 * @param reply          the assistant's message
 * @param insight        which tools produced the answer, and whether all of it
 *                       is supported by what they returned
 * @param action         a purchase this turn priced or placed, or null. Carried
 *                       apart from the reply text so a client can show the real
 *                       total and the real order number rather than whatever the
 *                       model wrote about them
 */
public record ChatResponse(
        String conversationId,
        ChatMessageResponse reply,
        TurnInsight insight,
        TurnAction action
) {
}
