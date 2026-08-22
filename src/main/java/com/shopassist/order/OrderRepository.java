package com.shopassist.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

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
