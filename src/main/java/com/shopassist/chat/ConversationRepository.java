package com.shopassist.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Owner is part of the signature for the same reason it is on
     * {@code OrderRepository}: there is deliberately no way to load a
     * conversation without saying whose it is.
     */
    Optional<Conversation> findByPublicRefAndUserId(String publicRef, Long userId);

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
