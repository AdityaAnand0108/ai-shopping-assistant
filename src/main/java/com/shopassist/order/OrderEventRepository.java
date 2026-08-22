package com.shopassist.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Tracking timelines.
 *
 * <p>Loaded separately from an order's items rather than through one entity
 * graph: fetching two collection associations in a single query raises
 * MultipleBagFetchException, and two queries is the correct fix rather than a
 * workaround.
 *
 * <p>Callers must have already established that the order belongs to the
 * caller; this repository is keyed by order id and performs no ownership check
 * of its own.
 */
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    List<OrderEvent> findByOrderIdOrderByOccurredAtAsc(Long orderId);
}
