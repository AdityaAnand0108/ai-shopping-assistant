package com.shopassist.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A full order, including its lines and tracking timeline.
 *
 * <p>{@link #cancellable} is computed server-side rather than left for a client
 * to infer from the status. The rule about which statuses may still be cancelled
 * belongs in one place, and from Phase 5 the assistant reads this flag instead of
 * reasoning about the lifecycle itself — a model that decides on its own whether
 * a shipped order can be cancelled will eventually get it wrong.
 */
public record OrderDetailResponse(
        String orderNumber,
        OrderStatus status,
        Instant placedAt,
        LocalDate expectedDeliveryDate,
        Instant deliveredAt,
        Instant cancelledAt,
        BigDecimal totalAmount,
        String currency,
        String shippingAddress,
        boolean cancellable,
        List<OrderItemResponse> items,
        List<OrderEventResponse> timeline
) {
    public static OrderDetailResponse from(Order order, List<OrderEvent> events) {
        return new OrderDetailResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getPlacedAt(),
                order.getExpectedDeliveryDate(),
                order.getDeliveredAt(),
                order.getCancelledAt(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getShippingAddress(),
                order.getStatus().isCancellable(),
                order.getItems().stream().map(OrderItemResponse::from).toList(),
                events.stream().map(OrderEventResponse::from).toList());
    }
}
