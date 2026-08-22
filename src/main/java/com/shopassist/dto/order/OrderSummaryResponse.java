package com.shopassist.dto.order;

import com.shopassist.entity.order.Order;
import com.shopassist.entity.order.OrderItem;
import com.shopassist.enums.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** An order as it appears in the shopper's order list. */
public record OrderSummaryResponse(
        String orderNumber,
        OrderStatus status,
        Instant placedAt,
        LocalDate expectedDeliveryDate,
        BigDecimal totalAmount,
        String currency,
        int itemCount
) {
    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getPlacedAt(),
                order.getExpectedDeliveryDate(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getItems().stream().mapToInt(OrderItem::getQuantity).sum());
    }
}
