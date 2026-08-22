package com.shopassist.chat;

/**
 * The answer to one chat turn.
 *
 * @param conversationId the thread, so the client can continue it
 * @param reply          the assistant's message
 */
public record ChatResponse(
        String conversationId,
        ChatMessageResponse reply
) {
}
