package com.shopassist.dto.chat;

import com.shopassist.entity.chat.ChatMessage;
import com.shopassist.entity.chat.Conversation;
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
