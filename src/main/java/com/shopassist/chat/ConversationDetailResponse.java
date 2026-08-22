package com.shopassist.chat;

import java.time.Instant;
import java.util.List;

/** A thread with all of its turns, oldest first. */
public record ConversationDetailResponse(
        String id,
        String title,
        Instant createdAt,
        List<ChatMessageResponse> messages
) {
    public static ConversationDetailResponse from(Conversation conversation,
                                                  List<ChatMessage> messages) {
        return new ConversationDetailResponse(
                conversation.getPublicRef(),
                conversation.getTitle(),
                conversation.getCreatedAt(),
                messages.stream().map(ChatMessageResponse::from).toList());
    }
}
