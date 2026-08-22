package com.shopassist.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Proposed purchases, always scoped to their owner.
 *
 * <p>The owner in the signature is what stops one shopper confirming another's
 * draft, and what makes a fabricated reference resolve to nothing.
 */
public interface OrderDraftRepository extends JpaRepository<OrderDraft, Long> {

    /** Owner in the signature, for the same reason as everywhere else. */
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<OrderDraft> findByPublicRefAndUserId(String publicRef, Long userId);
}
