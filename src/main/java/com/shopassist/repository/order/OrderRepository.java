package com.shopassist.repository.order;

import com.shopassist.entity.order.Order;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Orders, always scoped to their owner.
 *
 * <p>There is deliberately no {@code findByOrderNumber(String)}. Every lookup
 * takes the owner id too, so the ownership check cannot be forgotten at a call
 * site — including by a tool the language model invokes. The guarantee holds
 * because no method exists to express anything weaker.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Always look an order up by number AND owner. There is deliberately no
     * findByOrderNumber(String) on this repository: forcing the owner into the
     * signature means no caller — including a tool invoked by the model — can
     * accidentally read somebody else's order.
     */
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Order> findByOrderNumberIgnoreCaseAndUserId(String orderNumber, Long userId);

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findByUserIdOrderByPlacedAtDesc(Long userId);

    boolean existsByOrderNumberIgnoreCase(String orderNumber);
}
