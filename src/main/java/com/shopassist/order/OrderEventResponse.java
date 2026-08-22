package com.shopassist.order;

import java.time.Instant;

/**
 * One step in the tracking timeline.
 *
 * <p>This is the evidence behind a delivery answer. When the assistant is asked
 * when an order will arrive, it reads these recorded events and the stored ETA
 * rather than estimating, which is what makes the answer auditable.
 */
public record OrderEventResponse(
        OrderStatus status,
        Instant occurredAt,
        String note
) {
    public static OrderEventResponse from(OrderEvent event) {
        return new OrderEventResponse(event.getStatus(), event.getOccurredAt(), event.getNote());
    }
}
