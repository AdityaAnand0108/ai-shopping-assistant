package com.shopassist.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Chat threads, always scoped to their owner.
 *
 * <p>As with orders, there is deliberately no lookup that omits the owner, so
 * one shopper cannot read or post into another's conversation even holding a
 * valid reference.
 */
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Owner is part of the signature for the same reason it is on
     * {@code OrderRepository}: there is deliberately no way to load a
     * conversation without saying whose it is.
     */
    Optional<Conversation> findByPublicRefAndUserId(String publicRef, Long userId);

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
