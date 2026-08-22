package com.shopassist.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderDraftRepository extends JpaRepository<OrderDraft, Long> {

    /** Owner in the signature, for the same reason as everywhere else. */
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<OrderDraft> findByPublicRefAndUserId(String publicRef, Long userId);
}
