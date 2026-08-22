package com.shopassist.chat;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    /**
     * The most recent turns, newest first.
     *
     * <p>Only a window of history is replayed to the model. An unbounded thread
     * would grow the prompt until it overflowed the context window, and the
     * failure would arrive as a degraded answer rather than an error.
     */
    List<ChatMessage> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Limit limit);

    Optional<ChatMessage> findByPublicRef(String publicRef);

    long countByConversationId(Long conversationId);
}
