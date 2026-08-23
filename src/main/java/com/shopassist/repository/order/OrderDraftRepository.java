package com.shopassist.repository.order;

import com.shopassist.entity.order.OrderDraft;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

    /**
     * The shopper's most recent draft, whatever its state.
     *
     * <p>Used to confirm a purchase without the assistant having to carry a
     * reference across turns, which it cannot reliably do. Returning the latest
     * draft regardless of status is deliberate: if it is already confirmed, the
     * caller gets the existing order back rather than a second one.
     *
     * <p>Ordered by id rather than by created_at, because two drafts built in
     * the same second would otherwise have no defined order.
     */
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<OrderDraft> findFirstByUserIdOrderByIdDesc(Long userId);
}
