package com.shopassist.chat;

import java.time.Instant;

/**
 * One turn as the frontend sees it.
 *
 * <p>{@link #id} is the message's public reference, which Phase 8 uses to attach
 * a thumbs up or down to this specific answer.
 */
public record ChatMessageResponse(
        String id,
        MessageRole role,
        String content,
        Instant createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getPublicRef(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt());
    }
}
