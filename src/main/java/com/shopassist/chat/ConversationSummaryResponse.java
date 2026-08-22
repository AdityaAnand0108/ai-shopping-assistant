package com.shopassist.chat;

import java.time.Instant;

/** A thread in the shopper's history list. */
public record ConversationSummaryResponse(
        String id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        long messageCount
) {
    public static ConversationSummaryResponse from(Conversation conversation, long messageCount) {
        return new ConversationSummaryResponse(
                conversation.getPublicRef(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                messageCount);
    }
}
